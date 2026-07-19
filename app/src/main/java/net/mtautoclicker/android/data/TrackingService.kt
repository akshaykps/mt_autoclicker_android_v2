package net.mtautoclicker.android.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.mtautoclicker.android.BuildConfig
import net.mtautoclicker.android.data.telemetry.TelemetryPrivacyClass
import net.mtautoclicker.android.data.telemetry.TelemetryQueue
import java.time.Instant
import java.util.UUID

@Serializable
data class TrackingBatchPayload(
    val device_id: String,
    val app_version: String,
    val os_platform: String,
    val os_version: String,
    val app_variant: String = "",
    val installation_source: String = "",
    val timezone: String = "",
    val language: String = "",
    val manufacturer: String = "",
    val brand: String = "",
    val device_model: String = "",
    val android_sdk: Int? = null,
    val cpu_arch: String = "",
    val client_install_id: String = "",
    val installed_at: String = "",
    val sessions: List<TrackingSessionPayload> = emptyList(),
    val events: List<TrackingEventPayload> = emptyList(),
)

@Serializable
data class TrackingSessionPayload(
    val session_id: String,
    val started_at: String,
    val ended_at: String? = null,
    val duration_seconds: Long? = null,
    val app_version: String,
)

@Serializable
data class TrackingEventPayload(
    val session_id: String,
    val event_type: String,
    val event_name: String,
    val app_version: String,
    val metadata: Map<String, String> = emptyMap(),
    val client_event_id: String? = null,
    val occurred_at: String? = null,
)

class TrackingService(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val queue = TelemetryQueue.get(context)
    private val appVersion: String
        get() = BuildConfig.VERSION_NAME.ifBlank { "1.0.0" }
    private var sessionId: String? = null
    private var sessionStartedAt: String? = null
    private var sessionPosted = false

    suspend fun trackInstallIfNeeded() = withContext(Dispatchers.IO) {
        if (!settings.analyticsEnabled.first()) return@withContext
        val deviceId = settings.getOrCreateDeviceId()
        enqueueBatch(
            basePayload(
                deviceId = deviceId,
                installedAt = settings.getOrCreateInstalledAt(),
            ),
            queueId = "install-$deviceId",
        )
    }

    suspend fun startSession() {
        sessionId = UUID.randomUUID().toString()
        sessionStartedAt = Instant.now().toString()
        sessionPosted = false
    }

    suspend fun trackEvent(name: String, metadata: Map<String, String> = emptyMap()) = withContext(Dispatchers.IO) {
        if (!settings.analyticsEnabled.first()) return@withContext
        val deviceId = settings.getOrCreateDeviceId()
        val sid = sessionId ?: UUID.randomUUID().toString().also {
            sessionId = it
            sessionStartedAt = Instant.now().toString()
            sessionPosted = false
        }
        val startedAt = sessionStartedAt ?: Instant.now().toString()
        val sessions = if (!sessionPosted) {
            sessionPosted = true
            listOf(
                TrackingSessionPayload(
                    session_id = sid,
                    started_at = startedAt,
                    app_version = appVersion,
                ),
            )
        } else {
            emptyList()
        }
        val profile = settings.deviceProfileMap()
        postBatch(
            basePayload(
                deviceId = deviceId,
                installedAt = settings.getOrCreateInstalledAt(),
            ).copy(
                sessions = sessions,
                events = listOf(
                    TrackingEventPayload(
                        session_id = sid,
                        event_type = "feature_used",
                        event_name = name,
                        app_version = appVersion,
                        metadata = profile + metadata,
                        client_event_id = UUID.randomUUID().toString(),
                        occurred_at = Instant.now().toString(),
                    ),
                ),
            ),
        )
    }

    private fun basePayload(deviceId: String, installedAt: String): TrackingBatchPayload {
        val abis = Build.SUPPORTED_ABIS?.firstOrNull().orEmpty()
        return TrackingBatchPayload(
            device_id = deviceId,
            app_version = appVersion,
            os_platform = settings.osPlatformLabel(),
            os_version = settings.osVersionLabel(),
            app_variant = "full",
            installation_source = "play_store",
            timezone = settings.timezoneLabel(),
            language = settings.languageLabel(),
            manufacturer = settings.manufacturerLabel(),
            brand = settings.brandLabel(),
            device_model = settings.modelLabel(),
            android_sdk = settings.androidSdkInt(),
            cpu_arch = abis,
            client_install_id = deviceId,
            installed_at = installedAt,
        )
    }

    private fun postBatch(payload: TrackingBatchPayload) {
        enqueueBatch(payload)
    }

    private fun enqueueBatch(
        payload: TrackingBatchPayload,
        queueId: String = UUID.randomUUID().toString(),
    ) {
        queue.enqueue(
            endpoint = TRACKING_ENDPOINT,
            payloadJson = json.encodeToString(payload),
            privacyClass = TelemetryPrivacyClass.ANALYTICS,
            id = queueId,
        )
    }

    companion object {
        private const val TRACKING_ENDPOINT = "https://mtautoclicker.net/api/tracking/batch/"
    }
}
