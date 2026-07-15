package net.mtautoclicker.android.data

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.settingsDataStore by preferencesDataStore(name = "mt_settings")

enum class ThemePreference {
    LIGHT,
    DARK,
    SYSTEM,
}

class SettingsRepository(private val context: Context) {

    private val deviceIdKey = stringPreferencesKey("device_id")
    private val analyticsKey = booleanPreferencesKey("analytics_enabled")
    private val themeKey = stringPreferencesKey("theme_preference")

    val deviceId = context.settingsDataStore.data.map { prefs ->
        prefs[deviceIdKey] ?: ""
    }

    val analyticsEnabled = context.settingsDataStore.data.map { prefs ->
        prefs[analyticsKey] ?: true
    }

    val themePreference = context.settingsDataStore.data.map { prefs ->
        when (prefs[themeKey]) {
            "light" -> ThemePreference.LIGHT
            "dark" -> ThemePreference.DARK
            else -> ThemePreference.SYSTEM
        }
    }

    suspend fun getOrCreateDeviceId(): String {
        var result = ""
        context.settingsDataStore.edit { prefs ->
            result = prefs[deviceIdKey] ?: UUID.randomUUID().toString().also {
                prefs[deviceIdKey] = it
            }
        }
        return result
    }

    suspend fun setAnalyticsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[analyticsKey] = enabled }
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.settingsDataStore.edit {
            it[themeKey] = when (theme) {
                ThemePreference.LIGHT -> "light"
                ThemePreference.DARK -> "dark"
                ThemePreference.SYSTEM -> "system"
            }
        }
    }

    fun osPlatformLabel(): String {
        val model = Build.MODEL.orEmpty()
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val isChromeOs = model.contains("chromebook", ignoreCase = true) ||
            manufacturer.contains("google", ignoreCase = true) && model.contains("chrome", ignoreCase = true)
        return if (isChromeOs) "chromeos" else "android"
    }

    fun osVersionLabel(): String = "${Build.VERSION.RELEASE} (${Build.MODEL})"
}
