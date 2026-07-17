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
    private val preferredBrowserKey = stringPreferencesKey("preferred_browser_package")
    private val rememberBrowserKey = booleanPreferencesKey("remember_browser_choice")
    private val preferredRefreshAppKey = stringPreferencesKey("preferred_refresh_app_package")
    private val rememberRefreshAppKey = booleanPreferencesKey("remember_refresh_app_choice")

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

    val preferredAppPackage = context.settingsDataStore.data.map { prefs ->
        prefs[preferredBrowserKey]
    }

    /** @deprecated Use [preferredAppPackage]. */
    val preferredBrowserPackage = preferredAppPackage

    val rememberAppChoice = context.settingsDataStore.data.map { prefs ->
        prefs[rememberBrowserKey] ?: false
    }

    /** @deprecated Use [rememberAppChoice]. */
    val rememberBrowserChoice = rememberAppChoice

    val preferredRefreshAppPackage = context.settingsDataStore.data.map { prefs ->
        prefs[preferredRefreshAppKey]
    }

    val rememberRefreshAppChoice = context.settingsDataStore.data.map { prefs ->
        prefs[rememberRefreshAppKey] ?: false
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

    suspend fun setPreferredApp(packageName: String?, remember: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[rememberBrowserKey] = remember
            if (remember && !packageName.isNullOrBlank()) {
                prefs[preferredBrowserKey] = packageName
            } else if (!remember) {
                prefs.remove(preferredBrowserKey)
            }
        }
    }

    suspend fun setPreferredRefreshApp(packageName: String?, remember: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[rememberRefreshAppKey] = remember
            if (remember && !packageName.isNullOrBlank()) {
                prefs[preferredRefreshAppKey] = packageName
            } else if (!remember) {
                prefs.remove(preferredRefreshAppKey)
            }
        }
    }

    /** @deprecated Use [setPreferredApp]. */
    suspend fun setPreferredBrowser(packageName: String?, remember: Boolean) =
        setPreferredApp(packageName, remember)

    fun osPlatformLabel(): String {
        val model = Build.MODEL.orEmpty()
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val isChromeOs = model.contains("chromebook", ignoreCase = true) ||
            manufacturer.contains("google", ignoreCase = true) && model.contains("chrome", ignoreCase = true)
        return if (isChromeOs) "chromeos" else "android"
    }

    fun osVersionLabel(): String = "${Build.VERSION.RELEASE} (${Build.MODEL})"
}
