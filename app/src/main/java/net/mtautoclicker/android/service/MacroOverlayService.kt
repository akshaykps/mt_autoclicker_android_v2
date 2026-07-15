package net.mtautoclicker.android.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.mtautoclicker.android.MainActivity
import net.mtautoclicker.android.MtApplication
import net.mtautoclicker.android.R
import net.mtautoclicker.android.data.MacroOverlayMode
import net.mtautoclicker.android.data.MacroPoint
import net.mtautoclicker.android.data.MacroStep
import net.mtautoclicker.android.data.MacroStepKind
import net.mtautoclicker.android.engine.MacroGestureCoalescer
import net.mtautoclicker.android.engine.MacroHub
import net.mtautoclicker.android.engine.MacroSessionSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Macro UI:
 * - Ready: system-style "Start recording?" popup
 * - Recording: invisible capture + system ongoing notification (status-bar icon / chronometer)
 * - Idle playback: vertical chip + Play notification
 */
class MacroOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var appWm: WindowManager

    private var panelRoot: View? = null
    private var captureOverlay: FrameLayout? = null
    private var captureParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelX = 0
    private var panelY = 0
    private val relayMutex = kotlinx.coroutines.sync.Mutex()
    private var reattachJob: Job? = null
    @Volatile private var captureLocked = false
    @Volatile private var strokeOpen = false
    private var lastNotifiedStepCount = -1
    private var typedTextBaseline: String? = null
    private var lastFlushedTypedText: String? = null
    private var imeLayoutJob: Job? = null

    private val strokePoints = mutableListOf<MacroPoint>()
    private var strokeStartElapsed = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        appWm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        panelX = (dm.widthPixels - dp(56)).coerceAtLeast(dp(12))
        panelY = dp(120)
        MtAccessibilityService.instance?.onWindowsOrFocusChanged = {
            scope.launch { onAccessibilityChromeChanged() }
        }
        renderUi()
        var lastMode = MacroHub.snapshot.value.mode
        var lastPlaying = MacroHub.snapshot.value.isPlaying
        var lastSpeed = MacroHub.snapshot.value.playbackConfig.playbackSpeed
        var lastLoop = MacroHub.snapshot.value.playbackConfig.loop
        var lastLoopCount = MacroHub.snapshot.value.playbackConfig.loopCount
        scope.launch {
            MacroHub.snapshot.collectLatest { snap ->
                val rebuild = snap.mode != lastMode ||
                    snap.isPlaying != lastPlaying ||
                    snap.playbackConfig.playbackSpeed != lastSpeed ||
                    snap.playbackConfig.loop != lastLoop ||
                    snap.playbackConfig.loopCount != lastLoopCount
                if (rebuild) {
                    lastMode = snap.mode
                    lastPlaying = snap.isPlaying
                    lastSpeed = snap.playbackConfig.playbackSpeed
                    lastLoop = snap.playbackConfig.loop
                    lastLoopCount = snap.playbackConfig.loopCount
                    renderUi(snap)
                } else if (snap.mode == MacroOverlayMode.RECORDING &&
                    snap.stepCount != lastNotifiedStepCount
                ) {
                    postRecordingNotification(snap)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> {
                teardown()
                stopSelf()
            }
            ACTION_STOP_RECORD -> stopRecordingAndSave()
            ACTION_PLAY -> {
                removePanel()
                cancelNotification(PLAYBACK_READY_NOTIFICATION_ID)
                MacroPlaybackService.start(this)
            }
            ACTION_CLOSE_SESSION -> {
                cancelAllMacroNotifications()
                MacroHub.reset()
                stopSelf()
            }
            else -> renderUi()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        MtAccessibilityService.instance?.onWindowsOrFocusChanged = null
        teardown()
        scope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun teardown() {
        imeLayoutJob?.cancel()
        cancelAllMacroNotifications()
        removeCapture()
        removePanel()
        MacroPlaybackService.stop(this)
    }

    private fun removePanel() {
        panelRoot?.let { runCatching { wm().removeView(it) } }
        panelRoot = null
        panelParams = null
    }

    private fun removeCapture() {
        reattachJob?.cancel()
        reattachJob = null
        imeLayoutJob?.cancel()
        imeLayoutJob = null
        captureOverlay?.let { runCatching { wm().removeView(it) } }
        captureOverlay = null
        captureParams = null
        captureLocked = false
        strokeOpen = false
        typedTextBaseline = null
        lastFlushedTypedText = null
    }

    private fun renderUi(snap: MacroSessionSnapshot = MacroHub.snapshot.value) {
        when {
            snap.mode == MacroOverlayMode.RECORDING -> {
                removePanel()
                if (captureOverlay == null) showCaptureOverlay()
                bindAccessibilityChromeListener()
                refreshImeCaptureBounds()
                postRecordingNotification(snap)
            }
            snap.mode == MacroOverlayMode.PLAYBACK && snap.isPlaying -> {
                removePanel()
                removeCapture()
                cancelNotification(RECORD_NOTIFICATION_ID)
                cancelNotification(PLAYBACK_READY_NOTIFICATION_ID)
            }
            snap.mode == MacroOverlayMode.RECORD_READY -> {
                removeCapture()
                cancelNotification(RECORD_NOTIFICATION_ID)
                cancelNotification(PLAYBACK_READY_NOTIFICATION_ID)
                showStartRecordPopup()
            }
            snap.mode == MacroOverlayMode.PLAYBACK -> {
                removeCapture()
                cancelNotification(RECORD_NOTIFICATION_ID)
                showVerticalPlaybackChip(snap)
                postPlaybackReadyNotification(snap)
            }
        }
    }

    private fun bindAccessibilityChromeListener() {
        MtAccessibilityService.instance?.onWindowsOrFocusChanged = {
            scope.launch { onAccessibilityChromeChanged() }
        }
    }

    private fun onAccessibilityChromeChanged() {
        if (MacroHub.snapshot.value.mode != MacroOverlayMode.RECORDING) return
        refreshImeCaptureBounds()
        // Keep typed message in sync while keyboard is open (native typing).
        syncTypedTextFromField()
    }

    /**
     * Leave the soft-keyboard region uncovered so typing is native & snappy.
     * Capture overlay only covers the area above the IME.
     */
    private fun refreshImeCaptureBounds() {
        val overlay = captureOverlay ?: return
        val params = captureParams ?: return
        if (overlay.parent == null) return
        val screenH = resources.displayMetrics.heightPixels
        val screenW = resources.displayMetrics.widthPixels
        val ime = MtAccessibilityService.instance?.inputMethodBounds()
        val newHeight = if (ime != null && ime.top in 1 until screenH && ime.height() > dp(100)) {
            if (typedTextBaseline == null) {
                typedTextBaseline = MtAccessibilityService.instance?.focusedEditableText().orEmpty()
                lastFlushedTypedText = typedTextBaseline
            }
            ime.top
        } else {
            // Keyboard closed — flush any typed text, restore full-screen capture.
            flushTypedTextIfNeeded()
            typedTextBaseline = null
            WindowManager.LayoutParams.MATCH_PARENT
        }
        if (params.width != screenW || params.height != newHeight) {
            params.width = screenW
            params.height = newHeight
            params.x = 0
            params.y = 0
            runCatching { wm().updateViewLayout(overlay, params) }
        }
    }

    private fun syncTypedTextFromField() {
        val ime = MtAccessibilityService.instance?.inputMethodBounds() ?: return
        if (ime.height() <= dp(100)) return
        val text = MtAccessibilityService.instance?.focusedEditableText() ?: return
        if (text.isEmpty()) return
        if (text == lastFlushedTypedText) return
        if (MacroHub.upsertTrailingTypeText(text)) {
            lastFlushedTypedText = text
        }
    }

    private fun flushTypedTextIfNeeded() {
        val text = MtAccessibilityService.instance?.focusedEditableText() ?: return
        if (text.isEmpty()) return
        if (text == typedTextBaseline) return
        if (text == lastFlushedTypedText) return
        if (MacroHub.upsertTrailingTypeText(text)) {
            lastFlushedTypedText = text
        }
    }

    // ── System-style Start recording? popup ───────────────────────────────

    private fun showStartRecordPopup() {
        removePanel()
        val dm = resources.displayMetrics
        val cardWidth = (dm.widthPixels * 0.86f).toInt().coerceAtMost(dp(340))

        val scrim = FrameLayout(this).apply {
            setBackgroundColor(0x66000000)
            setOnClickListener { /* keep open until Cancel */ }
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(20).toFloat()
            }
            elevation = dp(18).toFloat()
        }

        card.addView(
            TextView(this).apply {
                text = "Start recording?"
                setTextColor(0xFF111827.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            },
        )
        card.addView(spacerV(12))
        card.addView(
            TextView(this).apply {
                text = "While recording, MT Auto Clicker captures taps, holds and swipes on your screen. Review actions before sharing."
                setTextColor(0xFF6B7280.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER
                setLineSpacing(dp(2).toFloat(), 1.1f)
            },
        )
        card.addView(spacerV(18))
        card.addView(
            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                setBackgroundColor(0xFFE5E7EB.toInt())
            },
        )
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        actions.addView(
            TextView(this).apply {
                text = "Cancel"
                gravity = Gravity.CENTER
                setTextColor(0xFFE11D48.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(8), dp(16), dp(8), dp(16))
                setOnClickListener {
                    MacroHub.reset()
                    stopSelf()
                }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        actions.addView(
            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(0xFFE5E7EB.toInt())
            },
        )
        actions.addView(
            TextView(this).apply {
                text = "Start recording"
                gravity = Gravity.CENTER
                setTextColor(0xFFE11D48.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(8), dp(16), dp(8), dp(16))
                setOnClickListener { startRecordingMode() }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        card.addView(actions)

        scrim.addView(
            card,
            FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            },
        )

        val params = overlayParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            x = 0,
            y = 0,
            touchable = true,
            focusable = true,
        )
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        panelParams = params
        panelRoot = scrim
        wm().addView(scrim, params)
    }

    // ── Vertical playback chip (drag handle only) ─────────────────────────

    @SuppressLint("SetTextI18n")
    private fun showVerticalPlaybackChip(snap: MacroSessionSnapshot) {
        removePanel()
        val cfg = snap.playbackConfig
        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = GradientDrawable().apply {
                setColor(0xF2111827.toInt())
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), 0x558B5CF6.toInt())
            }
            elevation = dp(10).toFloat()
        }

        val handle = TextView(this).apply {
            text = "⠿"
            gravity = Gravity.CENTER
            setTextColor(0xFF94A3B8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(4), dp(2), dp(4), dp(6))
        }
        setupDragHandle(handle)
        chip.addView(handle)

        chip.addView(
            TextView(this).apply {
                text = "${cfg.steps.size}"
                gravity = Gravity.CENTER
                setTextColor(0xFFF8FAFC.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(8))
            },
        )

        listOf(0.5f to "0.5×", 1f to "1×", 2f to "2×").forEach { (value, label) ->
            val active = kotlin.math.abs(cfg.playbackSpeed - value) < 0.01f
            chip.addView(
                TextView(this).apply {
                    text = label
                    gravity = Gravity.CENTER
                    setTextColor(if (active) Color.WHITE else 0xFF94A3B8.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(8), dp(7), dp(8), dp(7))
                    background = if (active) roundedBg(0xFF3B82F6.toInt(), 999f) else null
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { bottomMargin = dp(4) }
                    setOnClickListener { MacroHub.patchPlayback(speed = value) }
                },
            )
        }

        chip.addView(spacerV(6))
        chip.addView(
            TextView(this).apply {
                text = "▶"
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = roundedBg(0xFF10B981.toInt(), 999f)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(6)
                }
                setOnClickListener {
                    removePanel()
                    cancelNotification(PLAYBACK_READY_NOTIFICATION_ID)
                    MacroPlaybackService.start(this@MacroOverlayService)
                }
            },
        )
        chip.addView(
            TextView(this).apply {
                text = "✕"
                gravity = Gravity.CENTER
                setTextColor(0xFFE2E8F0.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = roundedBg(0xFF334155.toInt(), 999f)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                setOnClickListener {
                    cancelAllMacroNotifications()
                    MacroHub.reset()
                    stopSelf()
                }
            },
        )

        val params = overlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            x = panelX,
            y = panelY,
            touchable = true,
        )
        panelParams = params
        panelRoot = chip
        wm().addView(chip, params)
    }

    private fun startRecordingMode() {
        if (!MtAccessibilityService.isEnabled()) {
            Toast.makeText(this, "Enable Accessibility first", Toast.LENGTH_LONG).show()
            return
        }
        MacroHub.startRecording()
    }

    private fun stopRecordingAndSave() {
        flushTypedTextIfNeeded()
        removeCapture()
        cancelNotification(RECORD_NOTIFICATION_ID)
        lastNotifiedStepCount = -1
        val steps = MacroHub.stopRecordingSteps()
        if (steps.isEmpty()) {
            Toast.makeText(this, "No actions recorded", Toast.LENGTH_SHORT).show()
            MacroHub.armRecordReady()
            return
        }
        scope.launch {
            val stamp = SimpleDateFormat("MM-dd-yyyy-HH-mm-ss", Locale.US).format(Date())
            val name = "Macro $stamp"
            val macro = withContext(Dispatchers.IO) {
                MtApplication.instance.macroRepository.saveNewMacro(name, steps)
            }
            MacroHub.setLastSaved(macro)
            Toast.makeText(
                this@MacroOverlayService,
                "Saved · ${macro.metadata.actionCount} actions",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showCaptureOverlay() {
        removeCapture()
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        overlay.setOnTouchListener { _, event ->
            if (captureLocked) return@setOnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    strokeOpen = true
                    strokePoints.clear()
                    strokePoints += MacroPoint(event.rawX, event.rawY)
                    strokeStartElapsed = SystemClock.elapsedRealtime()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!strokeOpen) return@setOnTouchListener true
                    val last = strokePoints.lastOrNull()
                    val next = MacroPoint(event.rawX, event.rawY)
                    if (last == null ||
                        kotlin.math.hypot(
                            (next.x - last.x).toDouble(),
                            (next.y - last.y).toDouble(),
                        ) >= 3.0
                    ) {
                        strokePoints += next
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!strokeOpen) return@setOnTouchListener true
                    strokeOpen = false
                    strokePoints += MacroPoint(event.rawX, event.rawY)
                    val duration = (SystemClock.elapsedRealtime() - strokeStartElapsed).coerceAtLeast(28L)
                    val density = resources.displayMetrics.density
                    val points = strokePoints.toList()
                    strokePoints.clear()
                    val step = MacroGestureCoalescer.coalesce(points, duration, density)
                    if (step != null) {
                        // Flush any native keyboard text before this UI gesture.
                        flushTypedTextIfNeeded()
                        detachCaptureOverlayNow()
                        queueLiveRelay(step)
                    }
                    true
                }
                // OEM cancel after FLAG flips must NOT become a second recorded tap.
                MotionEvent.ACTION_CANCEL -> {
                    strokeOpen = false
                    strokePoints.clear()
                    true
                }
                else -> false
            }
        }
        val params = overlayParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            x = 0,
            y = 0,
            touchable = true,
        )
        captureParams = params
        captureOverlay = overlay
        wm().addView(overlay, params)
        refreshImeCaptureBounds()
        imeLayoutJob?.cancel()
        imeLayoutJob = scope.launch {
            while (MacroHub.snapshot.value.mode == MacroOverlayMode.RECORDING) {
                refreshImeCaptureBounds()
                syncTypedTextFromField()
                delay(350)
            }
        }
    }

    /**
     * Live handoff while recording (UI region only — keyboard is native pass-through):
     * 1) Detach capture overlay immediately
     * 2) Replay the gesture once via Accessibility
     * 3) Re-attach after a short idle (app-open grace), shaped around IME bounds
     */
    private fun queueLiveRelay(step: MacroStep) {
        val accepted = MacroHub.appendStep(step)
        reattachJob?.cancel()
        if (!accepted) {
            scheduleCaptureReattach()
            return
        }
        scope.launch {
            relayMutex.withLock {
                if (MacroHub.snapshot.value.mode != MacroOverlayMode.RECORDING) return@withLock
                captureLocked = true
                try {
                    detachCaptureOverlayNow()
                    delay(8)
                    val timeoutMs = when (step.kind) {
                        MacroStepKind.SWIPE, MacroStepKind.PATH -> 480L
                        MacroStepKind.LONG_PRESS -> 650L
                        MacroStepKind.TYPE_TEXT -> 200L
                        else -> 280L
                    }
                    withContext(Dispatchers.Default) {
                        withTimeoutOrNull(timeoutMs) {
                            MtAccessibilityService.instance?.performMacroStepLive(step)
                        }
                    }
                } finally {
                    captureLocked = false
                }
            }
            scheduleCaptureReattach()
        }
    }

    private fun detachCaptureOverlayNow() {
        val overlay = captureOverlay ?: return
        runCatching {
            if (overlay.parent != null) wm().removeView(overlay)
        }
    }

    private fun scheduleCaptureReattach() {
        reattachJob?.cancel()
        reattachJob = scope.launch {
            delay(200)
            if (MacroHub.snapshot.value.mode != MacroOverlayMode.RECORDING) return@launch
            relayMutex.withLock {
                reattachCaptureOverlay()
            }
        }
    }

    private fun reattachCaptureOverlay() {
        if (MacroHub.snapshot.value.mode != MacroOverlayMode.RECORDING) return
        val overlay = captureOverlay ?: return
        val params = captureParams ?: return
        params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        runCatching {
            if (overlay.parent == null) {
                wm().addView(overlay, params)
            }
        }
        captureLocked = false
        refreshImeCaptureBounds()
    }

    private fun setCaptureTouchable(touchable: Boolean) {
        val overlay = captureOverlay ?: return
        val params = captureParams ?: return
        if (overlay.parent == null) return
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        params.flags = flags
        runCatching { wm().updateViewLayout(overlay, params) }
    }

    // ── Notifications (styled one-line custom view) ───────────────────────

    private fun postRecordingNotification(snap: MacroSessionSnapshot) {
        ensureChannel(RECORD_CHANNEL_ID, "Macro recording", NotificationManager.IMPORTANCE_DEFAULT)
        val stopPi = servicePending(1, ACTION_STOP_RECORD)
        val openPi = activityPending(0)
        val count = snap.stepCount
        val timer = formatElapsed(snap.recordingStartedAtMs)
        val startedAt = if (snap.recordingStartedAtMs > 0) {
            snap.recordingStartedAtMs
        } else {
            System.currentTimeMillis()
        }

        val views = RemoteViews(packageName, R.layout.notification_macro_recording).apply {
            setTextViewText(R.id.notif_rec_timer, timer)
            setTextViewText(
                R.id.notif_rec_count,
                if (count == 0) "· waiting" else "· $count actions",
            )
            setOnClickPendingIntent(R.id.notif_rec_stop, stopPi)
        }

        val notification = NotificationCompat.Builder(this, RECORD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rec_dot)
            .setContentTitle("Recording $timer")
            .setContentText(if (count == 0) "Waiting…" else "$count actions")
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(openPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFFFF3B30.toInt())
            .setWhen(startedAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .addAction(R.drawable.ic_stop, "Stop", stopPi)
            .build()
        lastNotifiedStepCount = count
        notify(RECORD_NOTIFICATION_ID, notification)
    }

    private fun postPlaybackReadyNotification(snap: MacroSessionSnapshot) {
        ensureChannel(PLAYBACK_CHANNEL_ID, "Macro playback", NotificationManager.IMPORTANCE_DEFAULT)
        val cfg = snap.playbackConfig
        val playPi = servicePending(2, ACTION_PLAY)
        val closePi = servicePending(3, ACTION_CLOSE_SESSION)
        val openPi = activityPending(0)
        val subtitle = "${cfg.steps.size} actions"

        val views = RemoteViews(packageName, R.layout.notification_macro_ready).apply {
            setTextViewText(R.id.notif_ready_subtitle, subtitle)
            setOnClickPendingIntent(R.id.notif_ready_play, playPi)
            setOnClickPendingIntent(R.id.notif_ready_close, closePi)
        }

        val notification = NotificationCompat.Builder(this, PLAYBACK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle("Macro ready")
            .setContentText("$subtitle · tap Play")
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(openPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFF34C759.toInt())
            .setShowWhen(false)
            .addAction(R.drawable.ic_play, "Play", playPi)
            .addAction(R.drawable.ic_close, "Close", closePi)
            .build()
        notify(PLAYBACK_READY_NOTIFICATION_ID, notification)
    }

    private fun formatElapsed(startedAtMs: Long): String {
        val start = if (startedAtMs > 0) startedAtMs else System.currentTimeMillis()
        val sec = ((System.currentTimeMillis() - start) / 1000L).coerceAtLeast(0L)
        val m = sec / 60
        val s = sec % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    private fun notify(id: Int, notification: Notification) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, notification)
    }

    private fun cancelNotification(id: Int) {
        runCatching {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(id)
        }
    }

    private fun cancelAllMacroNotifications() {
        cancelNotification(RECORD_NOTIFICATION_ID)
        cancelNotification(PLAYBACK_READY_NOTIFICATION_ID)
    }

    private fun ensureChannel(id: String, name: String, importance: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(id) != null) return
        nm.createNotificationChannel(
            NotificationChannel(id, name, importance).apply {
                description = name
                setShowBadge(false)
                setSound(null, null)
            },
        )
    }

    private fun servicePending(requestCode: Int, action: String): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, MacroOverlayService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun activityPending(requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            this,
            requestCode,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragHandle(handle: View) {
        handle.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val params = panelParams ?: return false
                val root = panelRoot ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startX = params.x
                        startY = params.y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = startX + (event.rawX - downX).toInt()
                        params.y = startY + (event.rawY - downY).toInt()
                        panelX = params.x
                        panelY = params.y
                        wm().updateViewLayout(root, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun roundedBg(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp.toInt().coerceAtLeast(1)).toFloat()
    }

    private fun spacerV(h: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }

    private fun spacerH(w: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(w), 1)
    }

    private fun overlayParams(
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        touchable: Boolean,
        focusable: Boolean = false,
    ): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return WindowManager.LayoutParams(width, height, type, flags, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun wm(): WindowManager = appWm

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_DISMISS = "net.mtautoclicker.android.MACRO_OVERLAY_DISMISS"
        const val ACTION_STOP_RECORD = "net.mtautoclicker.android.MACRO_STOP_RECORD"
        const val ACTION_PLAY = "net.mtautoclicker.android.MACRO_NOTIF_PLAY"
        const val ACTION_CLOSE_SESSION = "net.mtautoclicker.android.MACRO_NOTIF_CLOSE"
        private const val RECORD_CHANNEL_ID = "mt_macro_record_v6"
        private const val PLAYBACK_CHANNEL_ID = "mt_macro_playback_ready_v6"
        private const val RECORD_NOTIFICATION_ID = 1003
        private const val PLAYBACK_READY_NOTIFICATION_ID = 1004

        @Volatile
        var instance: MacroOverlayService? = null
            private set

        fun show(context: Context) {
            context.startService(Intent(context, MacroOverlayService::class.java))
        }

        fun dismiss(context: Context) {
            context.stopService(Intent(context, MacroOverlayService::class.java))
        }
    }
}
