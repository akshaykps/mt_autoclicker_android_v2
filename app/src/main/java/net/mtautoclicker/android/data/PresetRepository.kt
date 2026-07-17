package net.mtautoclicker.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

private val Context.presetDataStore by preferencesDataStore(name = "mt_presets")

class PresetRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("presets_json")

    val presets = context.presetDataStore.data.map { prefs ->
        decodeList(prefs[key].orEmpty())
    }

    /** User-saved presets only (shown on the main Presets screen). */
    val savedPresets = presets.map { list -> list.filterNot { isRecent(it) } }

    suspend fun savePreset(
        name: String,
        feature: FeatureKind,
        configJson: String,
        targets: List<ClickTarget>,
    ): MtPreset {
        lateinit var preset: MtPreset
        context.presetDataStore.edit { prefs ->
            val current = decodeList(prefs[key].orEmpty()).toMutableList()
            val finalName = name.trim().ifBlank { nextSavedName(feature, current) }
            preset = MtPreset(
                id = UUID.randomUUID().toString(),
                name = finalName,
                feature = feature,
                createdAt = Instant.now().toString(),
                configJson = configJson,
                targets = targets,
            )
            current.add(0, preset)
            prefs[key] = encodeList(current)
        }
        return preset
    }

    /**
     * Adds a recent run for a feature (keeps up to [MAX_RECENTS_PER_FEATURE]).
     * Identical config for the same feature replaces the older recent entry.
     */
    suspend fun addRecentPreset(
        feature: FeatureKind,
        configJson: String,
        targets: List<ClickTarget>,
        displayName: String? = null,
    ): MtPreset {
        val preset = MtPreset(
            id = "recent_${feature.name.lowercase()}_${UUID.randomUUID()}",
            name = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: defaultRecentName(feature),
            feature = feature,
            createdAt = Instant.now().toString(),
            configJson = configJson,
            targets = targets,
        )
        context.presetDataStore.edit { prefs ->
            val current = decodeList(prefs[key].orEmpty()).toMutableList()
            // Drop legacy single-slot recent id.
            current.removeAll { it.id == recentIdFor(feature) }
            // Dedupe same config among recents for this feature.
            current.removeAll {
                it.feature == feature &&
                    isRecent(it) &&
                    it.configJson == configJson &&
                    it.targets == targets
            }
            current.add(0, preset)
            trimRecentsLocked(current, feature)
            prefs[key] = encodeList(current)
        }
        return preset
    }

    /** @deprecated Prefer [addRecentPreset] — kept so older call sites still compile. */
    suspend fun upsertRecentPreset(
        feature: FeatureKind,
        configJson: String,
        targets: List<ClickTarget>,
    ): MtPreset = addRecentPreset(feature, configJson, targets)

    /**
     * Promotes a recent run into a user-saved preset and removes it from Recent.
     * Blank [name] allocates the next code for that feature (e.g. STAC-3).
     */
    suspend fun promoteRecentToSaved(recentId: String, name: String): MtPreset? {
        lateinit var saved: MtPreset
        var found = false
        context.presetDataStore.edit { prefs ->
            val current = decodeList(prefs[key].orEmpty()).toMutableList()
            val recent = current.firstOrNull { it.id == recentId && isRecent(it) } ?: return@edit
            found = true
            val finalName = name.trim().ifBlank { nextSavedName(recent.feature, current) }
            saved = MtPreset(
                id = UUID.randomUUID().toString(),
                name = finalName,
                feature = recent.feature,
                createdAt = Instant.now().toString(),
                configJson = recent.configJson,
                targets = recent.targets,
            )
            current.removeAll { it.id == recentId }
            current.add(0, saved)
            prefs[key] = encodeList(current)
        }
        return if (found) saved else null
    }

    /** Next default code for [feature], e.g. `STAC-2`. */
    suspend fun nextDefaultSavedName(feature: FeatureKind): String =
        nextSavedName(feature, allPresets())

    suspend fun deletePreset(id: String) {
        context.presetDataStore.edit { prefs ->
            val current = decodeList(prefs[key].orEmpty()).filterNot { it.id == id }
            prefs[key] = encodeList(current)
        }
    }

    suspend fun allPresets(): List<MtPreset> = presets.first()

    private fun trimRecentsLocked(current: MutableList<MtPreset>, feature: FeatureKind) {
        val recents = current.filter { it.feature == feature && isRecent(it) }
        if (recents.size <= MAX_RECENTS_PER_FEATURE) return
        val dropIds = recents.drop(MAX_RECENTS_PER_FEATURE).map { it.id }.toSet()
        current.removeAll { it.id in dropIds }
    }

    private fun decodeList(raw: String): List<MtPreset> {
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<MtPreset>>(raw) }.getOrDefault(emptyList())
    }

    private fun encodeList(items: List<MtPreset>): String = json.encodeToString(items)

    companion object {
        const val MAX_RECENTS_PER_FEATURE = 10

        fun recentIdFor(feature: FeatureKind): String = "recent_${feature.name.lowercase()}"

        fun isRecent(preset: MtPreset): Boolean = preset.id.startsWith("recent_")

        fun savedNamePrefix(feature: FeatureKind): String = when (feature) {
            FeatureKind.SINGLE_TARGET -> "STAC"
            FeatureKind.MULTI_TARGET -> "MTAC"
            FeatureKind.MACRO_RECORDER -> "MAC"
            FeatureKind.FULL_PAGE_SCREENSHOT -> "FPS"
            FeatureKind.AUTO_REFRESH -> "AR"
        }

        /**
         * Next unique default name for [feature] among [existing] presets,
         * e.g. STAC-1, STAC-2 (uses max existing number + 1 for that prefix).
         */
        fun nextSavedName(feature: FeatureKind, existing: List<MtPreset>): String {
            val prefix = savedNamePrefix(feature)
            val regex = Regex("^${Regex.escape(prefix)}-(\\d+)$", RegexOption.IGNORE_CASE)
            val max = existing
                .asSequence()
                .filter { !isRecent(it) && it.feature == feature }
                .mapNotNull { regex.find(it.name.trim())?.groupValues?.getOrNull(1)?.toIntOrNull() }
                .maxOrNull() ?: 0
            return "$prefix-${max + 1}"
        }

        fun defaultRecentName(feature: FeatureKind): String = when (feature) {
            FeatureKind.SINGLE_TARGET -> "Recent · Single Target"
            FeatureKind.MULTI_TARGET -> "Recent · Multi Target"
            FeatureKind.MACRO_RECORDER -> "Recent · Macro"
            FeatureKind.FULL_PAGE_SCREENSHOT -> "Recent · Full page capture"
            FeatureKind.AUTO_REFRESH -> "Recent · Auto Refresh"
        }

        /** Sync fallback without scanning storage — prefer [nextDefaultSavedName] / [nextSavedName]. */
        fun defaultSavedName(feature: FeatureKind): String = "${savedNamePrefix(feature)}-1"
    }
}
