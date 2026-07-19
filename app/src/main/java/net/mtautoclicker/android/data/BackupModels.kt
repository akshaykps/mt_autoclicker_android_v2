package net.mtautoclicker.android.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

@Serializable
data class SettingsBackup(
    val theme: String = "system",
    val analyticsEnabled: Boolean = false,
    val notificationSoundMuted: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val targetMarkerScalePercent: Int = SettingsRepository.DEFAULT_MARKER_SCALE,
    val floatBarScalePercent: Int = SettingsRepository.DEFAULT_FLOAT_BAR_SCALE,
    val preferredAppPackage: String? = null,
    val rememberAppChoice: Boolean = false,
    val preferredRefreshAppPackage: String? = null,
    val rememberRefreshAppChoice: Boolean = false,
)

@Serializable
data class AppBackupBundle(
    val version: Int = 1,
    val app: String = "mt-autoclicker-android",
    val exportedAt: String = "",
    val presets: List<MtPreset> = emptyList(),
    val macros: List<SavedMacro> = emptyList(),
    val settings: SettingsBackup? = null,
)

object AppBackup {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    suspend fun exportJson(
        presets: PresetRepository,
        macros: MacroRepository,
        settings: SettingsRepository,
    ): String {
        val bundle = AppBackupBundle(
            exportedAt = Instant.now().toString(),
            presets = presets.allPresets(),
            macros = macros.allMacros(),
            settings = settings.exportSettingsBackup(),
        )
        return json.encodeToString(bundle)
    }

    /**
     * Import a full backup or a legacy presets-only JSON array.
     * @return human-readable summary
     */
    suspend fun importJson(
        raw: String,
        presets: PresetRepository,
        macros: MacroRepository,
        settings: SettingsRepository,
    ): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "File is empty."

        // Full backup bundle
        runCatching { json.decodeFromString<AppBackupBundle>(trimmed) }
            .getOrNull()
            ?.takeIf { it.version >= 1 && (it.presets.isNotEmpty() || it.macros.isNotEmpty() || it.settings != null) }
            ?.let { bundle ->
                val presetCount = presets.importPresetsList(bundle.presets)
                val macroCount = macros.importMacros(bundle.macros)
                bundle.settings?.let { settings.applySettingsBackup(it) }
                val parts = buildList {
                    if (presetCount > 0) add("$presetCount presets")
                    if (macroCount > 0) add("$macroCount macros")
                    if (bundle.settings != null) add("settings")
                }
                return if (parts.isEmpty()) "Nothing new to import."
                else "Imported ${parts.joinToString(" · ")}."
            }

        // Legacy: presets array only
        val n = presets.importPresetsJson(trimmed)
        return if (n > 0) "Imported $n presets." else "No presets found in file."
    }
}
