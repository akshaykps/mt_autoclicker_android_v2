package net.mtautoclicker.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.mtautoclicker.android.MainActivity
import net.mtautoclicker.android.MtApplication
import net.mtautoclicker.android.R
import net.mtautoclicker.android.data.AutomationRunState
import net.mtautoclicker.android.data.FeatureKind
import net.mtautoclicker.android.engine.AutoRefreshHub
import net.mtautoclicker.android.engine.resolveIntervalMs
import net.mtautoclicker.android.engine.shouldStop

/**
 * Runs timed pull-to-refresh gestures in the foreground app.
 */
class AutoRefreshService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private var paused = false
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startLoop()
            ACTION_PAUSE -> pauseLoop()
            ACTION_RESUME -> resumeLoop()
            ACTION_STOP -> stopLoop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loopJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startLoop() {
        val config = AutoRefreshHub.snapshot.value.config
        if (!MtAccessibilityService.isEnabled()) {
            Toast.makeText(this, "Enable Accessibility to refresh", Toast.LENGTH_LONG).show()
            AutoRefreshHub.setArmed("Enable Accessibility first")
            stopSelf()
            return
        }
        if (loopJob?.isActive == true) {
            paused = false
            AutoRefreshHub.setRunning()
            return
        }
        paused = false
        startForegroundNotification("Auto Refresh running")
        AutoRefreshHub.setRunning()

        loopJob = scope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            var cycles = AutoRefreshHub.snapshot.value.refreshCount
            val startDelay = config.interval.startDelayMs
            if (startDelay > 0 && cycles == 0) delay(startDelay)

            while (isActive) {
                while (paused && isActive) delay(200)
                if (!isActive) break

                val ok = performRefresh()
                if (ok) {
                    cycles++
                    val elapsed = SystemClock.elapsedRealtime() - startedAt
                    AutoRefreshHub.updateProgress(cycles, elapsed)
                    postProgressNotification(cycles)
                }

                val elapsed = SystemClock.elapsedRealtime() - startedAt
                if (shouldStop(config.stop, cycles, elapsed)) break

                delay(resolveIntervalMs(config.interval).coerceAtLeast(500L))
            }

            saveRecentPreset()
            AutoRefreshHub.setArmed(
                if (cycles > 0) "Finished · $cycles refreshes" else "Stopped",
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun performRefresh(): Boolean {
        val service = MtAccessibilityService.instance ?: return false
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()
        val x = w / 2f
        val y1 = h * 0.26f
        val y2 = h * 0.70f
        return service.dispatchSwipe(x, y1, x, y2, 480L, timeoutMs = 1_200L)
    }

    private fun pauseLoop() {
        if (loopJob?.isActive != true) return
        paused = true
        AutoRefreshHub.setPaused()
        postProgressNotification(AutoRefreshHub.snapshot.value.refreshCount, paused = true)
    }

    private fun resumeLoop() {
        if (AutoRefreshHub.snapshot.value.runState != AutomationRunState.PAUSED) return
        paused = false
        AutoRefreshHub.setRunning()
        postProgressNotification(AutoRefreshHub.snapshot.value.refreshCount)
    }

    private fun stopLoop() {
        paused = false
        loopJob?.cancel()
        loopJob = null
        AutoRefreshHub.setArmed("Stopped")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun saveRecentPreset() {
        val config = AutoRefreshHub.snapshot.value.config
        runCatching {
            MtApplication.instance.presetRepository.upsertRecentPreset(
                feature = FeatureKind.AUTO_REFRESH,
                configJson = json.encodeToString(config),
                targets = emptyList(),
            )
        }
    }

    private fun startForegroundNotification(text: String) {
        ensureChannel()
        val notification = buildNotification(text)
        startSpecialUseForeground(NOTIFICATION_ID, notification)
    }

    private fun postProgressNotification(cycles: Int, paused: Boolean = false) {
        ensureChannel()
        val title = if (paused) "Auto Refresh paused" else "Auto Refresh running"
        val body = "$cycles refreshes"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification("$title · $body"))
    }

    private fun buildNotification(text: String): android.app.Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPi = PendingIntent.getService(
            this,
            1,
            Intent(this, AutoRefreshService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val views = RemoteViews(packageName, R.layout.notification_refresh_running).apply {
            setTextViewText(R.id.notif_refresh_title, text)
            setOnClickPendingIntent(R.id.notif_refresh_stop, stopPi)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_refresh)
            .setContentTitle("Auto Refresh")
            .setContentText(text)
            .setCustomContentView(views)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setColor(0xFFF59E0B.toInt())
            .addAction(R.drawable.ic_stop, "Stop", stopPi)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Auto Refresh", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while auto refresh is running"
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val ACTION_START = "net.mtautoclicker.android.REFRESH_START"
        const val ACTION_PAUSE = "net.mtautoclicker.android.REFRESH_PAUSE"
        const val ACTION_RESUME = "net.mtautoclicker.android.REFRESH_RESUME"
        const val ACTION_STOP = "net.mtautoclicker.android.REFRESH_STOP"
        private const val CHANNEL_ID = "mt_auto_refresh_v1"
        private const val NOTIFICATION_ID = 1201

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, AutoRefreshService::class.java).setAction(ACTION_START),
            )
        }

        fun pause(context: Context) {
            context.startService(Intent(context, AutoRefreshService::class.java).setAction(ACTION_PAUSE))
        }

        fun resume(context: Context) {
            context.startService(Intent(context, AutoRefreshService::class.java).setAction(ACTION_RESUME))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AutoRefreshService::class.java).setAction(ACTION_STOP))
        }
    }
}
