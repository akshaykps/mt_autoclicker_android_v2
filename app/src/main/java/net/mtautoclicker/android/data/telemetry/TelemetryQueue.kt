package net.mtautoclicker.android.data.telemetry

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class TelemetryPrivacyClass(val wireValue: String) {
    ANALYTICS("analytics"),
    FUNCTIONAL("functional"),
}

data class QueuedTelemetry(
    val id: String,
    val endpoint: String,
    val payloadJson: String,
    val privacyClass: String,
    val createdAt: Long,
    val attemptCount: Int,
)

/**
 * Durable, app-private SQLite outbox. Rows survive process death, device restarts,
 * app updates, and any number of offline days. Uninstalling/clearing app data
 * intentionally removes the anonymous queue and device identifier.
 */
private class TelemetryQueueDb(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE telemetry_outbox (
                id TEXT PRIMARY KEY NOT NULL,
                endpoint TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                privacy_class TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                attempt_count INTEGER NOT NULL DEFAULT 0,
                last_error TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX idx_telemetry_outbox_created ON telemetry_outbox(created_at)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(item: QueuedTelemetry): Boolean {
        val values = ContentValues().apply {
            put("id", item.id)
            put("endpoint", item.endpoint)
            put("payload_json", item.payloadJson)
            put("privacy_class", item.privacyClass)
            put("created_at", item.createdAt)
            put("attempt_count", item.attemptCount)
        }
        return writableDatabase.insertWithOnConflict(
            "telemetry_outbox",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE,
        ) != -1L
    }

    fun oldest(limit: Int): List<QueuedTelemetry> {
        val items = mutableListOf<QueuedTelemetry>()
        readableDatabase.query(
            "telemetry_outbox",
            arrayOf("id", "endpoint", "payload_json", "privacy_class", "created_at", "attempt_count"),
            null,
            null,
            null,
            null,
            "created_at ASC",
            limit.coerceIn(1, 100).toString(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items += QueuedTelemetry(
                    id = cursor.getString(0),
                    endpoint = cursor.getString(1),
                    payloadJson = cursor.getString(2),
                    privacyClass = cursor.getString(3),
                    createdAt = cursor.getLong(4),
                    attemptCount = cursor.getInt(5),
                )
            }
        }
        return items
    }

    fun delete(id: String) {
        writableDatabase.delete("telemetry_outbox", "id = ?", arrayOf(id))
    }

    fun markAttempt(id: String, error: String) {
        val values = ContentValues().apply {
            put("last_error", error.take(500))
        }
        writableDatabase.execSQL(
            """
            UPDATE telemetry_outbox
            SET attempt_count = attempt_count + 1, last_error = ?
            WHERE id = ?
            """.trimIndent(),
            arrayOf(values.getAsString("last_error"), id),
        )
    }

    fun purgeAnalytics() {
        writableDatabase.delete(
            "telemetry_outbox",
            "privacy_class = ?",
            arrayOf(TelemetryPrivacyClass.ANALYTICS.wireValue),
        )
    }

    companion object {
        private const val DB_NAME = "mt_telemetry_outbox.db"
        private const val DB_VERSION = 1
    }
}

class TelemetryQueue private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val db = TelemetryQueueDb(appContext)

    fun enqueue(
        endpoint: String,
        payloadJson: String,
        privacyClass: TelemetryPrivacyClass,
        id: String = UUID.randomUUID().toString(),
        createdAt: Long = System.currentTimeMillis(),
    ): Boolean {
        require(endpoint.startsWith("https://mtautoclicker.net/")) {
            "Telemetry endpoint is not allowlisted"
        }
        val inserted = db.insert(
            QueuedTelemetry(
                id = id,
                endpoint = endpoint,
                payloadJson = payloadJson,
                privacyClass = privacyClass.wireValue,
                createdAt = createdAt,
                attemptCount = 0,
            ),
        )
        scheduleDrain()
        return inserted
    }

    fun scheduleDrain() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<TelemetryUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    internal fun oldest(limit: Int = 50): List<QueuedTelemetry> = db.oldest(limit)

    internal fun delete(id: String) = db.delete(id)

    internal fun markAttempt(id: String, error: String) = db.markAttempt(id, error)

    fun purgeAnalytics() = db.purgeAnalytics()

    companion object {
        private const val WORK_NAME = "mt-telemetry-outbox-drain"

        @Volatile
        private var instance: TelemetryQueue? = null

        fun get(context: Context): TelemetryQueue =
            instance ?: synchronized(this) {
                instance ?: TelemetryQueue(context).also { instance = it }
            }
    }
}

class TelemetryUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val queue = TelemetryQueue.get(applicationContext)
        var sent = 0

        while (sent < MAX_PER_RUN) {
            val items = queue.oldest(minOf(BATCH_SIZE, MAX_PER_RUN - sent))
            if (items.isEmpty()) return@withContext Result.success()

            for (item in items) {
                when (val result = upload(item)) {
                    is UploadResult.Success -> queue.delete(item.id)
                    is UploadResult.PermanentFailure -> {
                        Log.w(TAG, "Dropping rejected telemetry ${item.id}: ${result.reason}")
                        queue.delete(item.id)
                    }
                    is UploadResult.Retry -> {
                        queue.markAttempt(item.id, result.reason)
                        return@withContext Result.retry()
                    }
                }
                sent += 1
            }
        }

        // Yield after a bounded drain. WorkManager will retry this same durable
        // job; this avoids one worker monopolizing the process for huge backlogs.
        Result.retry()
    }

    private fun upload(item: QueuedTelemetry): UploadResult = try {
        val connection = (URL(item.endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-MT-Queued-Event-ID", item.id)
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        connection.outputStream.use { it.write(item.payloadJson.toByteArray()) }
        val code = connection.responseCode
        connection.disconnect()
        when {
            code in 200..299 -> UploadResult.Success
            // Rate limits should not be retried — that would amplify spam load.
            code == 429 -> UploadResult.PermanentFailure("HTTP 429")
            code == 408 || code == 425 || code >= 500 ->
                UploadResult.Retry("HTTP $code")
            else -> UploadResult.PermanentFailure("HTTP $code")
        }
    } catch (error: Exception) {
        UploadResult.Retry(error.message ?: error.javaClass.simpleName)
    }

    private sealed interface UploadResult {
        data object Success : UploadResult
        data class Retry(val reason: String) : UploadResult
        data class PermanentFailure(val reason: String) : UploadResult
    }

    companion object {
        private const val TAG = "MtTelemetryWorker"
        private const val BATCH_SIZE = 50
        private const val MAX_PER_RUN = 500
    }
}
