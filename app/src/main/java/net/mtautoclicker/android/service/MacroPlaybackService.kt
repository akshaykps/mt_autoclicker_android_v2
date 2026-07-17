package net.mtautoclicker.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.mtautoclicker.android.MainActivity
import net.mtautoclicker.android.R
import net.mtautoclicker.android.data.MacroStepKind
import net.mtautoclicker.android.engine.MacroHub

/**
 * Plays a saved macro. While playing, the float panel stays hidden and status
 * is shown only in an ongoing status-bar notification (with progress + actions).
 */
class MacroPlaybackService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private var notifyJob: Job? = null
    @Volatile private var paused = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startPlayback()
            ACTION_PAUSE -> {
                paused = true
                MacroHub.setPlaying(playing = true, paused = true)
                refreshNotification()
            }
            ACTION_RESUME -> {
                paused = false
                MacroHub.setPlaying(playing = true, paused = false)
                refreshNotification()
            }
            ACTION_STOP -> stopPlayback()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loopJob?.cancel()
        notifyJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startPlayback() {
        val cfg = MacroHub.snapshot.value.playbackConfig
        val steps = cfg.steps
        if (steps.isEmpty()) {
            stopSelf()
            return
        }
        if (!MtAccessibilityService.isEnabled()) {
            MacroHub.setPlaying(false)
            stopSelf()
            return
        }

        paused = false
        // Ask overlay to drop any floating UI immediately.
        MacroHub.setPlaying(true, paused = false)
        MacroOverlayService.instance?.let {
            // Overlay watches MacroHub and hides itself when isPlaying.
        }

        startForeground(NOTIFICATION_ID, buildNotification(0, steps.size, paused = false))

        notifyJob?.cancel()
        notifyJob = scope.launch {
            MacroHub.snapshot.collect { snap ->
                if (snap.isPlaying) {
                    refreshNotification(
                        current = snap.progressCurrent,
                        total = snap.progressTotal.coerceAtLeast(1),
                        paused = snap.isPaused,
                    )
                }
            }
        }

        loopJob?.cancel()
        loopJob = scope.launch {
            val speed = cfg.playbackSpeed.coerceIn(0.25f, 4f)
            var loopsDone = 0
            val maxLoops = when {
                !cfg.loop -> 1
                cfg.loopCount == 0 -> Int.MAX_VALUE
                else -> cfg.loopCount.coerceAtLeast(1)
            }

            while (isActive && loopsDone < maxLoops) {
                for ((index, step) in steps.withIndex()) {
                    while (paused && isActive) delay(80)
                    if (!isActive) break

                    MacroHub.setProgress(index + 1, steps.size)
                    val wait = (step.delayMs / speed).toLong().coerceAtLeast(0L)
                    if (wait > 0) delay(wait)
                    // Small guard so Gboard never sees two spaces/keys in one frame.
                    if (wait < 40L && index > 0) delay((40L - wait).coerceAtLeast(0L))

                    while (paused && isActive) delay(80)
                    if (!isActive) break

                    val service = MtAccessibilityService.instance ?: break
                    service.performMacroStep(step)
                    // Let the target app/IME finish handling before the next stroke.
                    val settle = when (step.kind) {
                        MacroStepKind.TYPE_TEXT -> (80L / speed).toLong().coerceIn(60L, 160L)
                        MacroStepKind.GLOBAL_BACK,
                        MacroStepKind.GLOBAL_HOME,
                        MacroStepKind.GLOBAL_RECENTS,
                        MacroStepKind.GLOBAL_NOTIFICATIONS,
                        -> (280L / speed).toLong().coerceIn(180L, 450L)
                        else -> (18L / speed).toLong().coerceIn(12L, 40L)
                    }
                    delay(settle)
                }
                loopsDone++
                if (!cfg.loop || loopsDone >= maxLoops) break
            }

            MacroHub.setPlaying(false)
            MacroHub.setProgress(steps.size, steps.size)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopPlayback() {
        loopJob?.cancel()
        loopJob = null
        notifyJob?.cancel()
        notifyJob = null
        paused = false
        MacroHub.setPlaying(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun refreshNotification(
        current: Int = MacroHub.snapshot.value.progressCurrent,
        total: Int = MacroHub.snapshot.value.progressTotal.coerceAtLeast(1),
        paused: Boolean = this.paused,
    ) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(current, total, paused))
    }

    private fun buildNotification(current: Int, total: Int, paused: Boolean): Notification {
        ensureChannel()
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pauseResume = if (paused) {
            PendingIntent.getService(
                this,
                2,
                Intent(this, MacroPlaybackService::class.java).setAction(ACTION_RESUME),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            PendingIntent.getService(
                this,
                3,
                Intent(this, MacroPlaybackService::class.java).setAction(ACTION_PAUSE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val stopPi = PendingIntent.getService(
            this,
            4,
            Intent(this, MacroPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val name = MacroHub.snapshot.value.playbackConfig.macroName.ifBlank { "Macro" }
        val title = if (paused) "Paused · $name" else "Playing macro"
        val body = if (total <= 0) "Starting…" else "$current / $total actions"
        val views = android.widget.RemoteViews(packageName, R.layout.notification_macro_playing).apply {
            setTextViewText(R.id.notif_play_title, title)
            setTextViewText(R.id.notif_play_progress, if (total <= 0) "…" else "$current/$total")
            setImageViewResource(
                R.id.notif_play_pause,
                if (paused) R.drawable.ic_play else R.drawable.ic_pause,
            )
            setOnClickPendingIntent(R.id.notif_play_pause, pauseResume)
            setOnClickPendingIntent(R.id.notif_play_stop, stopPi)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(title)
            .setContentText(body)
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFF34C759.toInt())
            .setShowWhen(false)
            .addAction(
                if (paused) R.drawable.ic_play else R.drawable.ic_pause,
                if (paused) "Resume" else "Pause",
                pauseResume,
            )
            .addAction(R.drawable.ic_stop, "Stop", stopPi)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Macro playback",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows playback progress in the status bar"
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val ACTION_START = "net.mtautoclicker.android.MACRO_PLAY_START"
        const val ACTION_PAUSE = "net.mtautoclicker.android.MACRO_PLAY_PAUSE"
        const val ACTION_RESUME = "net.mtautoclicker.android.MACRO_PLAY_RESUME"
        const val ACTION_STOP = "net.mtautoclicker.android.MACRO_PLAY_STOP"
        private const val CHANNEL_ID = "mt_macro_playback_v6"
        private const val NOTIFICATION_ID = 1002

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, MacroPlaybackService::class.java).setAction(ACTION_START),
            )
        }

        fun pause(context: Context) {
            context.startService(Intent(context, MacroPlaybackService::class.java).setAction(ACTION_PAUSE))
        }

        fun resume(context: Context) {
            context.startService(Intent(context, MacroPlaybackService::class.java).setAction(ACTION_RESUME))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MacroPlaybackService::class.java).setAction(ACTION_STOP))
        }
    }
}
