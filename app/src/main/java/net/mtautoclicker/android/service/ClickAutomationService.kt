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
import android.os.SystemClock
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
import net.mtautoclicker.android.data.AutomationPlan
import net.mtautoclicker.android.data.AutomationRunState
import net.mtautoclicker.android.data.ClickTarget
import net.mtautoclicker.android.data.FeatureKind
import net.mtautoclicker.android.data.MouseButton
import net.mtautoclicker.android.data.TargetMode
import net.mtautoclicker.android.engine.AutomationHub
import net.mtautoclicker.android.engine.MIN_CLICK_INTERVAL_MS
import net.mtautoclicker.android.engine.delayUntilElapsedRealtime
import net.mtautoclicker.android.engine.resolveIntervalMs
import net.mtautoclicker.android.engine.shouldStop
import kotlin.random.Random

class ClickAutomationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private var paused = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startLoop()
            ACTION_PAUSE -> pauseLoop()
            ACTION_RESUME -> resumeLoop()
            ACTION_STOP -> stopLoop(finishedNaturally = false)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loopJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        loopJob?.cancel()
        FloatingOverlayService.dismiss(this)
        AutomationHub.stopAll()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun startLoop() {
        val plan = AutomationHub.activePlan ?: return stopSelf()
        if (!MtAccessibilityService.isEnabled()) {
            AutomationHub.setRunState(AutomationRunState.ARMED, "Enable Accessibility to start clicking.")
            stopSelf()
            return
        }
        if (AutomationHub.snapshot.value.targets.isEmpty()) {
            AutomationHub.setRunState(AutomationRunState.ARMED, "Add at least one target first.")
            stopSelf()
            return
        }

        paused = false
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_running)))
        AutomationHub.setRunState(AutomationRunState.RUNNING)

        loopJob?.cancel()
        loopJob = scope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            var cycles = 0
            var clicksThisRun = 0
            val delayMs = plan.interval.startDelayMs
            if (delayMs > 0) delay(delayMs)

            // Deadline schedule: interval is click-to-click, not "after click finishes"
            // (same pattern as the Chrome extension high-CPS loop).
            var nextDue = SystemClock.elapsedRealtime()
            val baseInterval = resolveIntervalMs(plan.interval)
            val fast = !plan.interval.variable && baseInterval <= 50L

            while (isActive && !paused) {
                val targets = AutomationHub.snapshot.value.targets
                if (targets.isEmpty()) break

                val clicksInCycle = when (plan) {
                    is AutomationPlan.Single -> performSingleCycle(plan, targets, fast)
                    is AutomationPlan.Multi -> performMultiCycle(plan, targets, fast)
                }
                clicksThisRun += clicksInCycle
                cycles++

                val elapsed = SystemClock.elapsedRealtime() - startedAt
                AutomationHub.updateProgress(cycles, elapsed)
                if (shouldStop(plan.stop, cycles, elapsed)) break

                val waitMs = resolveIntervalMs(plan.interval).coerceAtLeast(MIN_CLICK_INTERVAL_MS)
                nextDue += waitMs
                val now = SystemClock.elapsedRealtime()
                if (now > nextDue + waitMs) {
                    // Fell far behind (system couldn't keep up) — resync so we don't burst.
                    nextDue = now
                } else {
                    delayUntilElapsedRealtime(nextDue)
                }
            }

            val runtime = SystemClock.elapsedRealtime() - startedAt
            if (clicksThisRun > 0) AutomationHub.recordRun(clicksThisRun, runtime)
            saveRecentPresetFromCurrentPlan()
            AutomationHub.setRunState(AutomationRunState.ARMED, "Run finished.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun saveRecentPresetFromCurrentPlan() {
        val plan = AutomationHub.activePlan ?: return
        val targets = AutomationHub.snapshot.value.targets
        if (targets.isEmpty()) return
        val json = Json { encodeDefaults = true }
        runCatching {
            when (plan) {
                is AutomationPlan.Single -> MtApplication.instance.presetRepository.upsertRecentPreset(
                    feature = FeatureKind.SINGLE_TARGET,
                    configJson = json.encodeToString(plan.config),
                    targets = targets,
                )
                is AutomationPlan.Multi -> MtApplication.instance.presetRepository.upsertRecentPreset(
                    feature = FeatureKind.MULTI_TARGET,
                    configJson = json.encodeToString(plan.config),
                    targets = targets,
                )
            }
        }
    }

    private suspend fun performSingleCycle(
        plan: AutomationPlan.Single,
        targets: List<ClickTarget>,
        fast: Boolean,
    ): Int {
        val target = targets.first()
        val service = MtAccessibilityService.instance ?: return 0
        val (x, y) = resolvePoint(plan.config.targetMode, target)
        val ok = service.performClick(
            x,
            y,
            MouseButton.LEFT,
            plan.interval.randomOffsetPx,
            fast = fast,
        )
        return if (ok) 1 else 0
    }

    private suspend fun performMultiCycle(
        plan: AutomationPlan.Multi,
        targets: List<ClickTarget>,
        fast: Boolean,
    ): Int {
        val service = MtAccessibilityService.instance ?: return 0
        var count = 0
        for (target in targets) {
            val ok = service.performClick(
                target.x,
                target.y,
                MouseButton.LEFT,
                plan.interval.randomOffsetPx,
                fast = fast,
            )
            if (ok) count++
        }
        return count
    }

    private fun resolvePoint(mode: TargetMode, target: ClickTarget): Pair<Float, Float> {
        if (mode == TargetMode.ZONE && target.zoneWidth != null && target.zoneHeight != null) {
            val w = target.zoneWidth
            val h = target.zoneHeight
            return target.x + Random.nextFloat() * w to target.y + Random.nextFloat() * h
        }
        return target.x to target.y
    }

    private fun pauseLoop() {
        paused = true
        AutomationHub.setRunState(AutomationRunState.PAUSED)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(getString(R.string.notification_paused)))
    }

    private fun resumeLoop() {
        if (loopJob?.isActive == true && paused) {
            paused = false
            AutomationHub.setRunState(AutomationRunState.RUNNING)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(getString(R.string.notification_running)))
        } else {
            startLoop()
        }
    }

    private fun stopLoop(finishedNaturally: Boolean) {
        // Capture before cancel so we still persist what the user was running.
        val plan = AutomationHub.activePlan
        val targets = AutomationHub.snapshot.value.targets
        loopJob?.cancel()
        loopJob = null
        paused = false
        if (plan != null && targets.isNotEmpty()) {
            scope.launch {
                val json = Json { encodeDefaults = true }
                runCatching {
                    when (plan) {
                        is AutomationPlan.Single -> MtApplication.instance.presetRepository.upsertRecentPreset(
                            feature = FeatureKind.SINGLE_TARGET,
                            configJson = json.encodeToString(plan.config),
                            targets = targets,
                        )
                        is AutomationPlan.Multi -> MtApplication.instance.presetRepository.upsertRecentPreset(
                            feature = FeatureKind.MULTI_TARGET,
                            configJson = json.encodeToString(plan.config),
                            targets = targets,
                        )
                    }
                }
            }
        }
        AutomationHub.setRunState(AutomationRunState.ARMED, "Stopped")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(content: String): Notification {
        ensureChannel()
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_automation),
            NotificationManager.IMPORTANCE_LOW,
        )
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "net.mtautoclicker.android.START"
        const val ACTION_PAUSE = "net.mtautoclicker.android.PAUSE"
        const val ACTION_RESUME = "net.mtautoclicker.android.RESUME"
        const val ACTION_STOP = "net.mtautoclicker.android.STOP"
        private const val CHANNEL_ID = "mt_automation"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ClickAutomationService::class.java).setAction(ACTION_START))
        }

        fun pause(context: Context) {
            context.startService(Intent(context, ClickAutomationService::class.java).setAction(ACTION_PAUSE))
        }

        fun resume(context: Context) {
            context.startService(Intent(context, ClickAutomationService::class.java).setAction(ACTION_RESUME))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ClickAutomationService::class.java).setAction(ACTION_STOP))
        }
    }
}
