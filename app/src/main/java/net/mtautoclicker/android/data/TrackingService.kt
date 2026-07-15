package net.mtautoclicker.android.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID

@Serializable
data class TrackingBatchPayload(
    val device_id: String,
    val app_version: String,
    val os_platform: String,
    val os_version: String,
    val app_variant: String = "chromeos",
    val installation_source: String = "play_store",
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
    private val json = Json { ignoreUnknownKeys = true }
    private val appVersion = "1.0.0"
    private var sessionId: String? = null
    private var sessionStartedAt: String? = null

    suspend fun trackInstallIfNeeded() = withContext(Dispatchers.IO) {
        val deviceId = settings.getOrCreateDeviceId()
        val payload = TrackingBatchPayload(
            device_id = deviceId,
            app_version = appVersion,
            os_platform = settings.osPlatformLabel(),
            os_version = settings.osVersionLabel(),
            installation_source = "play_store",
        )
        postBatch(payload)
    }

    suspend fun startSession() {
        sessionId = UUID.randomUUID().toString()
        sessionStartedAt = Instant.now().toString()
    }

    suspend fun trackEvent(name: String, metadata: Map<String, String> = emptyMap()) = withContext(Dispatchers.IO) {
        val deviceId = settings.getOrCreateDeviceId()
        val sid = sessionId ?: UUID.randomUUID().toString().also { sessionId = it }
        val payload = TrackingBatchPayload(
            device_id = deviceId,
            app_version = appVersion,
            os_platform = settings.osPlatformLabel(),
            os_version = settings.osVersionLabel(),
            events = listOf(
                TrackingEventPayload(
                    session_id = sid,
                    event_type = "feature",
                    event_name = name,
                    app_version = appVersion,
                    metadata = metadata,
                    client_event_id = UUID.randomUUID().toString(),
                    occurred_at = Instant.now().toString(),
                ),
            ),
        )
        postBatch(payload)
    }

    private fun postBatch(payload: TrackingBatchPayload) {
        runCatching {
            val url = URL("https://mtautoclicker.net/api/tracking/batch/")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            conn.outputStream.use { it.write(json.encodeToString(payload).toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "Tracking failed: HTTP $code")
            }
            conn.disconnect()
        }.onFailure {
            Log.w(TAG, "Tracking error", it)
        }
    }

    companion object {
        private const val TAG = "MtTracking"
    }
}
