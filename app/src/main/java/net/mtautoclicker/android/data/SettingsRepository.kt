package net.mtautoclicker.android.data

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.mtautoclicker.android.data.telemetry.TelemetryQueue
import java.util.UUID

private val Context.settingsDataStore by preferencesDataStore(name = "mt_settings")

enum class ThemePreference {
    LIGHT,
    DARK,
    SYSTEM,
}

class SettingsRepository(private val context: Context) {

    private val deviceIdKey = stringPreferencesKey("device_id")
    private val installedAtKey = stringPreferencesKey("installed_at")
    private val analyticsKey = booleanPreferencesKey("analytics_enabled")
    private val themeKey = stringPreferencesKey("theme_preference")
    private val preferredBrowserKey = stringPreferencesKey("preferred_browser_package")
    private val rememberBrowserKey = booleanPreferencesKey("remember_browser_choice")
    private val preferredRefreshAppKey = stringPreferencesKey("preferred_refresh_app_package")
    private val rememberRefreshAppKey = booleanPreferencesKey("remember_refresh_app_choice")
    private val targetMarkerScaleKey = intPreferencesKey("target_marker_scale_percent")
    private val floatBarScaleKey = intPreferencesKey("float_bar_scale_percent")
    private val notificationSoundMutedKey = booleanPreferencesKey("notification_sound_muted")
    private val hapticsEnabledKey = booleanPreferencesKey("haptics_enabled")
    private val featureUseCountKey = intPreferencesKey("feature_use_count")
    private val reviewDismissedKey = booleanPreferencesKey("review_dismissed")
    private val reviewReviewedKey = booleanPreferencesKey("review_reviewed")
    private val reviewRemindAtKey = longPreferencesKey("review_remind_at")
    private val reviewNudgeAtKey = longPreferencesKey("review_nudge_shown_at")
    private val reviewEventAtDismissKey = intPreferencesKey("review_event_count_at_dismiss")

    companion object {
        const val DEFAULT_MARKER_SCALE = 100
        const val MIN_MARKER_SCALE = 50
        const val MAX_MARKER_SCALE = 200
        const val MARKER_SCALE_STEP = 10
        const val DEFAULT_FLOAT_BAR_SCALE = 100
        const val MIN_FLOAT_BAR_SCALE = 50
        const val MAX_FLOAT_BAR_SCALE = 200
        const val FLOAT_BAR_SCALE_STEP = 10
        const val REVIEW_FIRST_SHOW_THRESHOLD = 5
        const val REVIEW_AFTER_DISMISS_THRESHOLD = 10
        const val REVIEW_REMIND_MS = 7L * 24 * 60 * 60 * 1000
        const val NUDGE_SNOOZE_MS = 14L * 24 * 60 * 60 * 1000
        const val WEBSITE_URL = "https://mtautoclicker.com"
        const val WEBSITE_LABEL = "MTAutoclicker.com"
        const val COMMUNITY_URL = "https://community.mtautoclicker.net"
        const val COMMUNITY_LABEL = "community.mtautoclicker.net"
        const val WEBTRETA_URL = "https://www.webtreta.com/"
        const val WEBTRETA_LABEL = "WebTreta"
        const val PLAY_STORE_URL =
            "https://play.google.com/store/apps/details?id=net.mtautoclicker.android"
        const val REDDIT_URL = "https://www.reddit.com/r/mtautoclicker"
        const val X_URL = "https://x.com/mtautoclicker"
        const val WEB_FEEDBACK_URL = "https://www.mtautoclicker.com/feedback-form/"
    }

    val deviceId = context.settingsDataStore.data.map { prefs ->
        prefs[deviceIdKey] ?: ""
    }

    val analyticsEnabled = context.settingsDataStore.data.map { prefs ->
        prefs[analyticsKey] ?: true
    }

    val notificationSoundMuted = context.settingsDataStore.data.map { prefs ->
        prefs[notificationSoundMutedKey] ?: false
    }

    val hapticsEnabled = context.settingsDataStore.data.map { prefs ->
        prefs[hapticsEnabledKey] ?: true
    }

    val featureUseCount = context.settingsDataStore.data.map { prefs ->
        prefs[featureUseCountKey] ?: 0
    }

    val themePreference = context.settingsDataStore.data.map { prefs ->
        when (prefs[themeKey]) {
            "light" -> ThemePreference.LIGHT
            "dark" -> ThemePreference.DARK
            else -> ThemePreference.SYSTEM
        }
    }

    /** Target marker size for Single/Multi Target (50–200%, default 100). */
    val targetMarkerScalePercent = context.settingsDataStore.data.map { prefs ->
        (prefs[targetMarkerScaleKey] ?: DEFAULT_MARKER_SCALE)
            .coerceIn(MIN_MARKER_SCALE, MAX_MARKER_SCALE)
    }

    /** Float bar size for Single/Multi Target (50–200%, default 100). */
    val floatBarScalePercent = context.settingsDataStore.data.map { prefs ->
        (prefs[floatBarScaleKey] ?: DEFAULT_FLOAT_BAR_SCALE)
            .coerceIn(MIN_FLOAT_BAR_SCALE, MAX_FLOAT_BAR_SCALE)
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

    suspend fun getOrCreateInstalledAt(): String {
        val existing = context.settingsDataStore.data.first()[installedAtKey]
        if (!existing.isNullOrBlank()) return existing
        val created = java.time.Instant.now().toString()
        context.settingsDataStore.edit { prefs -> prefs[installedAtKey] = created }
        return created
    }

    suspend fun setAnalyticsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[analyticsKey] = enabled }
        if (!enabled) {
            TelemetryQueue.get(context).purgeAnalytics()
        }
    }

    suspend fun setNotificationSoundMuted(muted: Boolean) {
        context.settingsDataStore.edit { it[notificationSoundMutedKey] = muted }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[hapticsEnabledKey] = enabled }
    }

    suspend fun incrementFeatureUseCount(): Int {
        var next = 0
        context.settingsDataStore.edit { prefs ->
            next = (prefs[featureUseCountKey] ?: 0) + 1
            prefs[featureUseCountKey] = next
        }
        return next
    }

    suspend fun getFeatureUseCount(): Int =
        context.settingsDataStore.data.first()[featureUseCountKey] ?: 0

    data class ReviewGateState(
        val dismissed: Boolean,
        val reviewed: Boolean,
        val remindAt: Long,
        val nudgeShownAt: Long,
        val eventCountAtDismiss: Int,
    )

    suspend fun getReviewGateState(): ReviewGateState {
        val prefs = context.settingsDataStore.data.first()
        return ReviewGateState(
            dismissed = prefs[reviewDismissedKey] ?: false,
            reviewed = prefs[reviewReviewedKey] ?: false,
            remindAt = prefs[reviewRemindAtKey] ?: 0L,
            nudgeShownAt = prefs[reviewNudgeAtKey] ?: 0L,
            eventCountAtDismiss = prefs[reviewEventAtDismissKey] ?: 0,
        )
    }

    /** Mac-parity gate: show after 5 uses, remind later +7d, soft dismiss +10 uses. */
    suspend fun shouldShowReviewPrompt(): Boolean {
        val state = getReviewGateState()
        if (state.dismissed && state.reviewed) return false
        val count = getFeatureUseCount()
        if (state.remindAt > 0L && System.currentTimeMillis() >= state.remindAt) return true
        if (state.dismissed && !state.reviewed) {
            return count >= state.eventCountAtDismiss + REVIEW_AFTER_DISMISS_THRESHOLD
        }
        return !state.dismissed && count >= REVIEW_FIRST_SHOW_THRESHOLD
    }

    suspend fun shouldShowFeedbackNudge(): Boolean {
        val state = getReviewGateState()
        if (!(state.dismissed && state.reviewed)) return false
        if (state.nudgeShownAt <= 0L) return true
        return System.currentTimeMillis() - state.nudgeShownAt >= NUDGE_SNOOZE_MS
    }

    suspend fun setReviewRemindLater() {
        context.settingsDataStore.edit {
            it[reviewRemindAtKey] = System.currentTimeMillis() + REVIEW_REMIND_MS
        }
    }

    suspend fun setReviewDontAskAgain() {
        val count = getFeatureUseCount()
        context.settingsDataStore.edit {
            it[reviewDismissedKey] = true
            it[reviewReviewedKey] = true
            it[reviewEventAtDismissKey] = count
            it.remove(reviewRemindAtKey)
        }
    }

    suspend fun setReviewCompleted() {
        context.settingsDataStore.edit {
            it[reviewDismissedKey] = true
            it[reviewReviewedKey] = true
            it.remove(reviewRemindAtKey)
        }
    }

    suspend fun markFeedbackNudgeShown() {
        context.settingsDataStore.edit {
            it[reviewNudgeAtKey] = System.currentTimeMillis()
        }
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

    /** Wipe app preferences (theme/analytics/etc). Device ID is regenerated on next read. */
    suspend fun factoryResetSettings() {
        context.settingsDataStore.edit { it.clear() }
    }

    suspend fun setTargetMarkerScalePercent(percent: Int) {
        context.settingsDataStore.edit {
            it[targetMarkerScaleKey] = percent.coerceIn(MIN_MARKER_SCALE, MAX_MARKER_SCALE)
        }
    }

    suspend fun setFloatBarScalePercent(percent: Int) {
        context.settingsDataStore.edit {
            it[floatBarScaleKey] = percent.coerceIn(MIN_FLOAT_BAR_SCALE, MAX_FLOAT_BAR_SCALE)
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

    fun isChromeOsDevice(): Boolean {
        val model = Build.MODEL.orEmpty()
        val manufacturer = Build.MANUFACTURER.orEmpty()
        return model.contains("chromebook", ignoreCase = true) ||
            (manufacturer.contains("google", ignoreCase = true) && model.contains("chrome", ignoreCase = true))
    }

    fun osPlatformLabel(): String = if (isChromeOsDevice()) "chromeos" else "android"

    fun osVersionLabel(): String = "${Build.VERSION.RELEASE} (${Build.MODEL})"

    fun androidRelease(): String = Build.VERSION.RELEASE.orEmpty()

    fun androidSdkInt(): Int = Build.VERSION.SDK_INT

    fun manufacturerLabel(): String = Build.MANUFACTURER.orEmpty()

    fun brandLabel(): String = Build.BRAND.orEmpty()

    fun modelLabel(): String = Build.MODEL.orEmpty()

    fun languageLabel(): String =
        java.util.Locale.getDefault().toLanguageTag().ifBlank { "en" }

    fun timezoneLabel(): String =
        java.util.TimeZone.getDefault().id.ifBlank { "UTC" }

    /** Structured device fingerprint for tracking / feedback APIs. */
    fun deviceProfileMap(): Map<String, String> = buildMap {
        put("platform", osPlatformLabel())
        put("manufacturer", manufacturerLabel())
        put("brand", brandLabel())
        put("model", modelLabel())
        put("android_release", androidRelease())
        put("android_sdk", androidSdkInt().toString())
        put("language", languageLabel())
        put("timezone", timezoneLabel())
        put("is_chromeos", isChromeOsDevice().toString())
    }

    suspend fun exportSettingsBackup(): SettingsBackup {
        val prefs = context.settingsDataStore.data.first()
        return SettingsBackup(
            theme = prefs[themeKey] ?: "system",
            analyticsEnabled = prefs[analyticsKey] ?: true,
            notificationSoundMuted = prefs[notificationSoundMutedKey] ?: false,
            hapticsEnabled = prefs[hapticsEnabledKey] ?: true,
            targetMarkerScalePercent = (prefs[targetMarkerScaleKey] ?: DEFAULT_MARKER_SCALE)
                .coerceIn(MIN_MARKER_SCALE, MAX_MARKER_SCALE),
            floatBarScalePercent = (prefs[floatBarScaleKey] ?: DEFAULT_FLOAT_BAR_SCALE)
                .coerceIn(MIN_FLOAT_BAR_SCALE, MAX_FLOAT_BAR_SCALE),
            preferredAppPackage = prefs[preferredBrowserKey],
            rememberAppChoice = prefs[rememberBrowserKey] ?: false,
            preferredRefreshAppPackage = prefs[preferredRefreshAppKey],
            rememberRefreshAppChoice = prefs[rememberRefreshAppKey] ?: false,
        )
    }

    suspend fun applySettingsBackup(backup: SettingsBackup) {
        context.settingsDataStore.edit { prefs ->
            prefs[themeKey] = when (backup.theme.lowercase()) {
                "light" -> "light"
                "dark" -> "dark"
                else -> "system"
            }
            prefs[analyticsKey] = backup.analyticsEnabled
            prefs[notificationSoundMutedKey] = backup.notificationSoundMuted
            prefs[hapticsEnabledKey] = backup.hapticsEnabled
            prefs[targetMarkerScaleKey] = backup.targetMarkerScalePercent
                .coerceIn(MIN_MARKER_SCALE, MAX_MARKER_SCALE)
            prefs[floatBarScaleKey] = backup.floatBarScalePercent
                .coerceIn(MIN_FLOAT_BAR_SCALE, MAX_FLOAT_BAR_SCALE)
            prefs[rememberBrowserKey] = backup.rememberAppChoice
            if (backup.rememberAppChoice && !backup.preferredAppPackage.isNullOrBlank()) {
                prefs[preferredBrowserKey] = backup.preferredAppPackage
            }
            prefs[rememberRefreshAppKey] = backup.rememberRefreshAppChoice
            if (backup.rememberRefreshAppChoice && !backup.preferredRefreshAppPackage.isNullOrBlank()) {
                prefs[preferredRefreshAppKey] = backup.preferredRefreshAppPackage
            }
        }
        if (!backup.analyticsEnabled) {
            TelemetryQueue.get(context).purgeAnalytics()
        }
    }
}
