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

    suspend fun savePreset(
        name: String,
        feature: FeatureKind,
        configJson: String,
        targets: List<ClickTarget>,
    ): MtPreset {
        val preset = MtPreset(
            id = UUID.randomUUID().toString(),
            name = name,
            feature = feature,
            createdAt = Instant.now().toString(),
            configJson = configJson,
            targets = targets,
        )
        context.presetDataStore.edit { prefs ->
            val current = decodeList(prefs[key].orEmpty()).toMutableList()
            current.add(0, preset)
            prefs[key] = encodeList(current)
        }
        return preset
    }

    /**
     * Upserts one pinned "Recent · …" slot per feature so a finished run
     * always appears on the Presets tab without requiring the float-bar Save button.
     */
    suspend fun upsertRecentPreset(
        feature: FeatureKind,
        configJson: String,
        targets: List<ClickTarget>,
    ): MtPreset {
        val id = recentIdFor(feature)
        val name = when (feature) {
            FeatureKind.SINGLE_TARGET -> "Recent · Single Target"
            FeatureKind.MULTI_TARGET -> "Recent · Multi Target"
            FeatureKind.MACRO_RECORDER -> "Recent · Macro"
        }
        val preset = MtPreset(
            id = id,
            name = name,
            feature = feature,
            createdAt = Instant.now().toString(),
            configJson = configJson,
            targets = targets,
        )
        context.presetDataStore.edit { prefs ->
            val current = decodeList(prefs[key].orEmpty()).toMutableList()
            current.removeAll { it.id == id }
            current.add(0, preset)
            prefs[key] = encodeList(current)
        }
        return preset
    }

    suspend fun deletePreset(id: String) {
        context.presetDataStore.edit { prefs ->
            val current = decodeList(prefs[key].orEmpty()).filterNot { it.id == id }
            prefs[key] = encodeList(current)
        }
    }

    suspend fun allPresets(): List<MtPreset> = presets.first()

    private fun decodeList(raw: String): List<MtPreset> {
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<MtPreset>>(raw) }.getOrDefault(emptyList())
    }

    private fun encodeList(items: List<MtPreset>): String = json.encodeToString(items)

    companion object {
        fun recentIdFor(feature: FeatureKind): String = "recent_${feature.name.lowercase()}"
    }
}
