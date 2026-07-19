package net.mtautoclicker.android.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.mtautoclicker.android.BuildConfig
import net.mtautoclicker.android.data.telemetry.TelemetryPrivacyClass
import net.mtautoclicker.android.data.telemetry.TelemetryQueue
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID

private val Context.androidCmsStore by preferencesDataStore(name = "mt_android_cms")

@Serializable
data class AndroidTourStepDto(
    val id: Int? = null,
    val sort_order: Int = 0,
    val title: String = "",
    val body: String = "",
    val image_url: String = "",
    val action_key: String = "",
    val cta_text: String = "",
)

@Serializable
data class AndroidTourDto(
    val kind: String = "",
    val title: String = "",
    val steps: List<AndroidTourStepDto> = emptyList(),
)

@Serializable
data class AndroidOverlayDto(
    val id: Int? = null,
    val overlay_type: String = "",
    val title: String = "",
    val body: String = "",
    val image_url: String = "",
    val feature_key: String = "",
    val cta_text: String = "",
    val cta_url: String = "",
    val deep_link: String = "",
    val priority: Int = 0,
)

@Serializable
data class AndroidRemoteFlagDto(
    val enabled: Boolean = false,
    val payload: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class AndroidNotificationDto(
    val id: Int,
    val category: String = "general",
    val title: String = "",
    val body: String = "",
    val cta_text: String = "",
    val cta_url: String = "",
    val deep_link: String = "",
    val priority: Int = 0,
    val published_at: String = "",
    val is_read: Boolean = false,
    val is_feedback_reply: Boolean = false,
    val feedback_id: Int? = null,
)

@Serializable
data class AndroidNotificationsResponse(
    val success: Boolean = false,
    val notifications: List<AndroidNotificationDto> = emptyList(),
    val unread_count: Int = 0,
)

data class ReviewPromptConfig(
    val enabled: Boolean,
    val storeUrl: String,
    val afterRuns: Int,
    val showChance: Double,
    val cooldownHours: Int,
)

/**
 * Talks to the mt_android Django API (/api/android/…).
 * Falls back to bundled offline tour/content when offline.
 */
class AndroidCmsRepository(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val baseUrl = "https://mtautoclicker.net/api/android"
    private val telemetryQueue = TelemetryQueue.get(context)
    private val appVersion: String
        get() = BuildConfig.VERSION_NAME.ifBlank { "1.0.0" }

    private val onboardingDoneKey = booleanPreferencesKey("tour_onboarding_done")
    private val permissionsTourDoneKey = booleanPreferencesKey("tour_permissions_done")
    private val whatsNewDismissedKey = stringPreferencesKey("whats_new_dismissed_ids")
    private val notificationDismissedKey = stringPreferencesKey("notification_dismissed_ids")
    private val whatsNewSeenVersionKey = stringPreferencesKey("whats_new_seen_version")
    private val reviewLastShownKey = longPreferencesKey("review_last_shown_at")
    private val reviewSubmittedKey = booleanPreferencesKey("review_submitted")
    private val npsSubmittedKey = booleanPreferencesKey("nps_submitted")

    val onboardingDone = context.androidCmsStore.data.map { it[onboardingDoneKey] ?: false }
    val permissionsTourDone = context.androidCmsStore.data.map { it[permissionsTourDoneKey] ?: false }

    suspend fun setOnboardingDone(done: Boolean = true) {
        context.androidCmsStore.edit { it[onboardingDoneKey] = done }
    }

    suspend fun setPermissionsTourDone(done: Boolean = true) {
        context.androidCmsStore.edit { it[permissionsTourDoneKey] = done }
    }

    /** True only for first-run / reinstall (DataStore wiped) until tour completes or is skipped. */
    suspend fun shouldShowOnboardingTour(): Boolean = !onboardingDone.first()

    suspend fun shouldShowPermissionsTour(): Boolean = !permissionsTourDone.first()

    /**
     * Decide which auto tour (if any) to show on cold start.
     * - Both overlay + accessibility granted → never show; mark tours done.
     * - First install → onboarding once.
     * - Later launches with missing permission(s) → permissions tour once (until skipped/done).
     * - Otherwise → nothing.
     */
    suspend fun resolveLaunchTour(context: Context): AndroidTourDto? {
        val overlayOk = PermissionHelper.canDrawOverlays(context)
        val a11yOk = PermissionHelper.isAccessibilityEnabled(context)
        val allGranted = overlayOk && a11yOk

        if (allGranted) {
            setOnboardingDone(true)
            setPermissionsTourDone(true)
            return null
        }

        // Welcome tour only on true first run.
        if (shouldShowOnboardingTour()) {
            return fetchTour("onboarding")
        }

        // After welcome: only nudge for missing permissions, and only once until dismissed.
        if (shouldShowPermissionsTour()) {
            return fetchTour("permissions")
        }

        return null
    }

    suspend fun fetchTour(kind: String): AndroidTourDto = withContext(Dispatchers.IO) {
        val remote = getJson("$baseUrl/tour/?kind=$kind&app_version=$appVersion")
        val tours = remote?.let {
            runCatching {
                json.decodeFromString<TourListResponse>(it).tours
            }.getOrNull()
        }.orEmpty()
        tours.firstOrNull { it.kind == kind && it.steps.isNotEmpty() }
            ?: OfflineAndroidDefaults.tour(kind)
    }

    /**
     * Returns What's New only once per overlay id (and once for offline default).
     * Reinstall clears DataStore → can show again. New CMS id → shows again.
     */
    suspend fun fetchWhatsNew(): AndroidOverlayDto? = withContext(Dispatchers.IO) {
        val dismissed = dismissedIds(whatsNewDismissedKey)
        val seenVersion = context.androidCmsStore.data.first()[whatsNewSeenVersionKey]

        val remote = getJson("$baseUrl/overlays/?type=whats_new&app_version=$appVersion")
        val card = remote?.let {
            runCatching { json.decodeFromString<OverlaysResponse>(it).whats_new }.getOrNull()
        }

        val candidate = when {
            card != null && card.id != null -> card
            card == null -> OfflineAndroidDefaults.whatsNew
            else -> null
        } ?: return@withContext null

        val key = overlayKey(candidate)
        if (key in dismissed) return@withContext null
        // Same offline/default content for this app version already acknowledged.
        if (candidate.id == OfflineAndroidDefaults.whatsNew.id && seenVersion == appVersion) {
            return@withContext null
        }
        candidate
    }

    suspend fun fetchNotifications(): List<AndroidOverlayDto> = withContext(Dispatchers.IO) {
        val dismissed = dismissedIds(notificationDismissedKey)
        val remote = getJson("$baseUrl/overlays/?type=notification&app_version=$appVersion")
        val list = remote?.let {
            runCatching { json.decodeFromString<OverlaysResponse>(it).notifications }.getOrNull()
        }.orEmpty()
        list.filter { overlayKey(it) !in dismissed }
    }

    /** Persistent notification inbox: broadcasts plus feedback replies for this device. */
    suspend fun fetchInboxNotifications(): AndroidNotificationsResponse = withContext(Dispatchers.IO) {
        val deviceId = settings.getOrCreateDeviceId()
        val remote = getJson(
            "$baseUrl/notifications/?device_id=$deviceId&app_version=$appVersion",
        ) ?: return@withContext AndroidNotificationsResponse()
        runCatching {
            json.decodeFromString<AndroidNotificationsResponse>(remote)
        }.onFailure {
            Log.w(TAG, "Could not decode notification inbox", it)
        }.getOrDefault(AndroidNotificationsResponse())
    }

    suspend fun markInboxNotification(
        notificationId: Int,
        action: String = "read",
    ): Boolean = withContext(Dispatchers.IO) {
        val deviceId = settings.getOrCreateDeviceId()
        val body = buildJsonObject {
            put("device_id", deviceId)
            put("notification_id", notificationId)
            put("action", action)
            put("client_action_id", UUID.randomUUID().toString())
            put("occurred_at", Instant.now().toString())
        }
        enqueueJson(
            "$baseUrl/notification-action/",
            body.toString(),
            TelemetryPrivacyClass.FUNCTIONAL,
        )
    }

    suspend fun fetchTutorials(featureKey: String): List<AndroidOverlayDto> = withContext(Dispatchers.IO) {
        val remote = getJson(
            "$baseUrl/overlays/?type=tutorial&feature=$featureKey&app_version=$appVersion",
        )
        remote?.let {
            runCatching { json.decodeFromString<OverlaysResponse>(it).tutorials }.getOrNull()
        }.orEmpty()
    }

    suspend fun fetchReviewConfig(): ReviewPromptConfig = withContext(Dispatchers.IO) {
        val remote = getJson("$baseUrl/remote-config/?app_version=$appVersion")
        val flags = remote?.let {
            runCatching { json.decodeFromString<RemoteConfigResponse>(it).flags }.getOrNull()
        }
        if (flags == null) return@withContext OfflineAndroidDefaults.reviewConfig
        val flag = flags["review_prompt_enabled"]
        val payload = flag?.payload.orEmpty()
        ReviewPromptConfig(
            enabled = flag?.enabled == true,
            storeUrl = payload.string("url")
                ?: OfflineAndroidDefaults.reviewConfig.storeUrl,
            afterRuns = payload.int("after_runs") ?: OfflineAndroidDefaults.reviewConfig.afterRuns,
            showChance = payload.double("show_chance") ?: OfflineAndroidDefaults.reviewConfig.showChance,
            cooldownHours = payload.int("cooldown_hours")
                ?: OfflineAndroidDefaults.reviewConfig.cooldownHours,
        )
    }

    suspend fun dismissWhatsNew(card: AndroidOverlayDto?) {
        if (card == null) return
        val key = overlayKey(card)
        context.androidCmsStore.edit { prefs ->
            val set = prefs[whatsNewDismissedKey].orEmpty()
                .split(',').filter { it.isNotBlank() }.toMutableSet()
            set += key
            prefs[whatsNewDismissedKey] = set.joinToString(",")
            prefs[whatsNewSeenVersionKey] = appVersion
        }
    }

    /** @deprecated Use dismissWhatsNew(card) */
    suspend fun dismissWhatsNew(id: Int?) {
        if (id == null) return
        context.androidCmsStore.edit { prefs ->
            val set = prefs[whatsNewDismissedKey].orEmpty()
                .split(',').filter { it.isNotBlank() }.toMutableSet()
            set += id.toString()
            prefs[whatsNewDismissedKey] = set.joinToString(",")
            prefs[whatsNewSeenVersionKey] = appVersion
        }
    }

    suspend fun dismissNotification(card: AndroidOverlayDto?) {
        if (card == null) return
        val key = overlayKey(card)
        context.androidCmsStore.edit { prefs ->
            val set = prefs[notificationDismissedKey].orEmpty()
                .split(',').filter { it.isNotBlank() }.toMutableSet()
            set += key
            prefs[notificationDismissedKey] = set.joinToString(",")
        }
    }

    private fun overlayKey(card: AndroidOverlayDto): String {
        val idPart = card.id?.toString() ?: "noid"
        val titlePart = card.title.take(40).hashCode().toString()
        return "$idPart:$titlePart"
    }

    private suspend fun dismissedIds(key: androidx.datastore.preferences.core.Preferences.Key<String>): Set<String> {
        val raw = context.androidCmsStore.data.first()[key].orEmpty()
        return raw.split(',').filter { it.isNotBlank() }.toSet()
    }

    suspend fun shouldShowReview(runCount: Int): Boolean {
        val cfg = fetchReviewConfig()
        if (!cfg.enabled || runCount < cfg.afterRuns) return false
        val prefs = context.androidCmsStore.data.first()
        if (prefs[reviewSubmittedKey] == true) return false
        val last = prefs[reviewLastShownKey] ?: 0L
        val cooldownMs = cfg.cooldownHours * 3_600_000L
        if (System.currentTimeMillis() - last < cooldownMs) return false
        return Math.random() <= cfg.showChance
    }

    suspend fun markReviewShown() {
        context.androidCmsStore.edit { it[reviewLastShownKey] = System.currentTimeMillis() }
    }

    suspend fun markReviewSubmitted() {
        context.androidCmsStore.edit { it[reviewSubmittedKey] = true }
    }

    suspend fun submitFeedback(
        feedbackType: String,
        message: String,
        rating: Int? = null,
        metadata: Map<String, String> = emptyMap(),
    ): Boolean = withContext(Dispatchers.IO) {
        val deviceId = settings.getOrCreateDeviceId()
        val profile = settings.deviceProfileMap()
        val clientEventId = UUID.randomUUID().toString()
        val occurredAt = Instant.now().toString()
        val body = buildJsonObject {
            put("device_id", deviceId)
            put("client_event_id", clientEventId)
            put("occurred_at", occurredAt)
            put("feedback_type", feedbackType)
            put("message", message)
            if (rating != null) put("rating", rating)
            put("app_version", appVersion)
            put("os_version", settings.osVersionLabel())
            put("os_platform", settings.osPlatformLabel())
            put("manufacturer", settings.manufacturerLabel())
            put("brand", settings.brandLabel())
            put("device_model", settings.modelLabel())
            put("android_release", settings.androidRelease())
            put("android_sdk", settings.androidSdkInt())
            put("language", settings.languageLabel())
            put("timezone", settings.timezoneLabel())
            put("metadata", buildJsonObject {
                profile.forEach { (k, v) -> put(k, v) }
                metadata.forEach { (k, v) -> put(k, v) }
            })
        }
        enqueueJson(
            "$baseUrl/feedback/",
            body.toString(),
            TelemetryPrivacyClass.FUNCTIONAL,
            clientEventId,
        )
    }

    suspend fun reportTourAction(kind: String, action: String, stepId: Int? = null) =
        withContext(Dispatchers.IO) {
            val deviceId = settings.getOrCreateDeviceId()
            val clientEventId = UUID.randomUUID().toString()
            val body = buildJsonObject {
                put("device_id", deviceId)
                put("client_event_id", clientEventId)
                put("occurred_at", Instant.now().toString())
                put("tour_kind", kind)
                put("action", action)
                put("app_version", appVersion)
                if (stepId != null) put("step_id", stepId)
            }
            enqueueJson(
                "$baseUrl/tour-action/",
                body.toString(),
                TelemetryPrivacyClass.ANALYTICS,
                clientEventId,
            )
        }

    suspend fun reportOverlayAction(overlayId: Int?, action: String) = withContext(Dispatchers.IO) {
        val deviceId = settings.getOrCreateDeviceId()
        val clientEventId = UUID.randomUUID().toString()
        val body = buildJsonObject {
            put("device_id", deviceId)
            put("client_event_id", clientEventId)
            put("occurred_at", Instant.now().toString())
            put("action", action)
            put("app_version", appVersion)
            if (overlayId != null) put("overlay_id", overlayId)
        }
        enqueueJson(
            "$baseUrl/overlay-action/",
            body.toString(),
            TelemetryPrivacyClass.ANALYTICS,
            clientEventId,
        )
    }

    suspend fun trackAndroidEvent(name: String, metadata: Map<String, String> = emptyMap()) =
        withContext(Dispatchers.IO) {
            val deviceId = settings.getOrCreateDeviceId()
            val profile = settings.deviceProfileMap()
            val clientEventId = UUID.randomUUID().toString()
            val body = buildJsonObject {
                put("device_id", deviceId)
                put("client_event_id", clientEventId)
                put("occurred_at", Instant.now().toString())
                put("event_name", name)
                put("app_version", appVersion)
                put("os_version", settings.osVersionLabel())
                put("os_platform", settings.osPlatformLabel())
                put("manufacturer", settings.manufacturerLabel())
                put("brand", settings.brandLabel())
                put("device_model", settings.modelLabel())
                put("android_release", settings.androidRelease())
                put("android_sdk", settings.androidSdkInt())
                put("language", settings.languageLabel())
                put("timezone", settings.timezoneLabel())
                put("metadata", buildJsonObject {
                    profile.forEach { (k, v) -> put(k, v) }
                    metadata.forEach { (k, v) -> put(k, v) }
                })
            }
            enqueueJson(
                "$baseUrl/event/",
                body.toString(),
                TelemetryPrivacyClass.ANALYTICS,
                clientEventId,
            )
        }

    private suspend fun dismissedWhatsNewIds(): Set<String> {
        return dismissedIds(whatsNewDismissedKey)
    }

    private fun getJson(url: String): String? {
        if (!hasValidatedInternet()) return null
        return runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.readText()
        conn.disconnect()
        if (code in 200..299) text else null
        }.onFailure { Log.w(TAG, "GET failed $url", it) }.getOrNull()
    }

    private fun hasValidatedInternet(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private suspend fun enqueueJson(
        url: String,
        body: String,
        privacyClass: TelemetryPrivacyClass,
        queueId: String = UUID.randomUUID().toString(),
    ): Boolean {
        if (
            privacyClass == TelemetryPrivacyClass.ANALYTICS &&
            !settings.analyticsEnabled.first()
        ) {
            return false
        }
        return telemetryQueue.enqueue(
            endpoint = url,
            payloadJson = body,
            privacyClass = privacyClass,
            id = queueId,
        )
    }

    @Serializable
    private data class TourListResponse(val tours: List<AndroidTourDto> = emptyList())

    @Serializable
    private data class OverlaysResponse(
        val whats_new: AndroidOverlayDto? = null,
        val notifications: List<AndroidOverlayDto> = emptyList(),
        val tutorials: List<AndroidOverlayDto> = emptyList(),
    )

    @Serializable
    private data class RemoteConfigResponse(
        val flags: Map<String, AndroidRemoteFlagDto> = emptyMap(),
    )

    companion object {
        private const val TAG = "MtAndroidCms"
    }
}

private fun Map<String, JsonElement>.string(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

private fun Map<String, JsonElement>.int(key: String): Int? =
    this[key]?.jsonPrimitive?.intOrNull

private fun Map<String, JsonElement>.double(key: String): Double? =
    this[key]?.jsonPrimitive?.doubleOrNull

/** Bundled defaults when the device is offline or CMS is empty. */
object OfflineAndroidDefaults {
    fun tour(kind: String): AndroidTourDto = when (kind) {
        "permissions" -> AndroidTourDto(
            kind = "permissions",
            title = "Permissions guide",
            steps = listOf(
                AndroidTourStepDto(
                    sort_order = 0,
                    title = "Display over other apps",
                    body = "Lets MT show the float bar and target markers on top of other apps.",
                    action_key = "open_overlay_settings",
                    cta_text = "Open settings",
                ),
                AndroidTourStepDto(
                    sort_order = 1,
                    title = "Accessibility",
                    body = "Lets MT perform taps and gestures where you place targets. We never read passwords.",
                    action_key = "open_accessibility_settings",
                    cta_text = "Open Accessibility",
                ),
                AndroidTourStepDto(
                    sort_order = 2,
                    title = "You are ready",
                    body = "Return to MT Auto Clicker when both permissions are enabled.",
                    action_key = "finish",
                    cta_text = "Done",
                ),
            ),
        )
        else -> AndroidTourDto(
            kind = "onboarding",
            title = "Welcome to MT Auto Clicker",
            steps = listOf(
                AndroidTourStepDto(
                    sort_order = 0,
                    title = "Welcome",
                    body = "Automate taps across apps on your phone or Chromebook — Single Target, Multi Target, Macro Recorder, and more.",
                    action_key = "next",
                    cta_text = "Next",
                ),
                AndroidTourStepDto(
                    sort_order = 1,
                    title = "Grant permissions",
                    body = "Display over other apps and Accessibility are required for the float bar and clicks.",
                    action_key = "open_permissions",
                    cta_text = "Review permissions",
                ),
                AndroidTourStepDto(
                    sort_order = 2,
                    title = "Place a target & Play",
                    body = "Open Single Target, tap Start, place a marker with +, then hit Play on the float bar.",
                    action_key = "finish",
                    cta_text = "Get started",
                ),
            ),
        )
    }

    val whatsNew = AndroidOverlayDto(
        id = -1,
        overlay_type = "whats_new",
        title = "MT Auto Clicker for Android",
        body = "Single Target, Multi Target, Macro Recorder, Auto Refresh, and Full-Page Screenshot — managed from your float bar.",
        cta_text = "Got it",
    )

    val reviewConfig = ReviewPromptConfig(
        enabled = true,
        storeUrl = "https://play.google.com/store/apps/details?id=net.mtautoclicker.android",
        afterRuns = 3,
        showChance = 0.55,
        cooldownHours = 24,
    )
}
