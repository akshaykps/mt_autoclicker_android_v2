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
import android.graphics.Rect
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
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.mtautoclicker.android.MainActivity
import net.mtautoclicker.android.MtApplication
import net.mtautoclicker.android.R
import net.mtautoclicker.android.data.LauncherIconHelper
import net.mtautoclicker.android.data.MacroOverlayMode
import net.mtautoclicker.android.data.MacroPoint
import net.mtautoclicker.android.data.MacroStep
import net.mtautoclicker.android.data.MacroStepKind
import net.mtautoclicker.android.engine.AutomationLauncher
import net.mtautoclicker.android.engine.MacroGestureCoalescer
import net.mtautoclicker.android.engine.MacroHub
import net.mtautoclicker.android.engine.MacroSessionSnapshot
import net.mtautoclicker.android.engine.NavEdge
import net.mtautoclicker.android.engine.NavEdgeBands
import net.mtautoclicker.android.ui.screens.AppRoute
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Macro UI:
 * - Ready: system-style "Start recording?" popup
 * - Recording: invisible full-screen capture + ongoing notification
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
    private val edgeStripViews = mutableListOf<View>()
    private val relayMutex = Mutex()
    private var reattachJob: Job? = null
    private var recordNotifRefreshJob: Job? = null
    @Volatile private var captureLocked = false
    @Volatile private var captureLockedAtElapsed = 0L
    @Volatile private var strokeOpen = false
    @Volatile private var recordingFgStarted = false
    private var lastNotifiedStepCount = -1
    private var typedTextBaseline: String? = null
    private var lastFlushedTypedText: String? = null
    private var imeLayoutJob: Job? = null
    @Volatile private var isClosing = false

    private val strokePoints = mutableListOf<MacroPoint>()
    private var strokeStartElapsed = 0L
    private var lastStrokePoints: List<MacroPoint> = emptyList()
    private var navEdges: NavEdgeBands? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        isClosing = false
        appWm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        panelX = (dm.widthPixels - dp(48)).coerceAtLeast(dp(12))
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
            ACTION_CLOSE_SESSION -> closeMacroSession()
            ACTION_RECORD_BACK -> recordAndPerformGlobal(MacroStepKind.GLOBAL_BACK)
            ACTION_RECORD_HOME -> recordAndPerformGlobal(MacroStepKind.GLOBAL_HOME)
            ACTION_RECORD_RECENTS -> recordAndPerformGlobal(MacroStepKind.GLOBAL_RECENTS)
            ACTION_RECORD_NOTIFICATIONS -> recordAndPerformGlobal(MacroStepKind.GLOBAL_NOTIFICATIONS)
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
        recordNotifRefreshJob?.cancel()
        stopRecordingForeground()
        LauncherIconHelper.setRecordingIcon(this, recording = false)
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
        removeEdgeStrips()
        captureOverlay?.let { runCatching { wm().removeView(it) } }
        captureOverlay = null
        captureParams = null
        captureLocked = false
        strokeOpen = false
        typedTextBaseline = null
        lastFlushedTypedText = null
        lastStrokePoints = emptyList()
    }

    /** Tear down macro UI without flashing the record-ready popup. */
    private fun closeMacroSession() {
        isClosing = true
        removePanel()
        recordNotifRefreshJob?.cancel()
        stopRecordingForeground()
        LauncherIconHelper.setRecordingIcon(this, recording = false)
        cancelAllMacroNotifications()
        MacroHub.reset()
        stopSelf()
    }

    private fun renderUi(snap: MacroSessionSnapshot = MacroHub.snapshot.value) {
        if (isClosing || snap.mode == MacroOverlayMode.IDLE) {
            removePanel()
            removeCapture()
            return
        }
        when {
            snap.mode == MacroOverlayMode.RECORDING -> {
                removePanel()
                if (captureOverlay == null) showCaptureOverlay()
                bindAccessibilityChromeListener()
                refreshImeCaptureBounds()
                // Do not swap launcher icon — OxygenOS puts it next to the clock and it
                // clashes with any status caption. "Recording…" lives in the notification.
                LauncherIconHelper.setRecordingIcon(this, recording = false)
                postRecordingNotification(snap)
                // OxygenOS often caches the status-bar icon — refresh after launcher swap.
                recordNotifRefreshJob?.cancel()
                recordNotifRefreshJob = scope.launch {
                    delay(280)
                    if (MacroHub.snapshot.value.mode == MacroOverlayMode.RECORDING) {
                        postRecordingNotification(MacroHub.snapshot.value)
                    }
                    delay(500)
                    if (MacroHub.snapshot.value.mode == MacroOverlayMode.RECORDING) {
                        postRecordingNotification(MacroHub.snapshot.value)
                    }
                }
            }
            snap.mode == MacroOverlayMode.PLAYBACK && snap.isPlaying -> {
                removePanel()
                removeCapture()
                recordNotifRefreshJob?.cancel()
                stopRecordingForeground()
                LauncherIconHelper.setRecordingIcon(this, recording = false)
                cancelNotification(RECORD_NOTIFICATION_ID)
                cancelNotification(PLAYBACK_READY_NOTIFICATION_ID)
            }
            snap.mode == MacroOverlayMode.RECORD_READY -> {
                removeCapture()
                recordNotifRefreshJob?.cancel()
                stopRecordingForeground()
                LauncherIconHelper.setRecordingIcon(this, recording = false)
                cancelNotification(RECORD_NOTIFICATION_ID)
                cancelNotification(PLAYBACK_READY_NOTIFICATION_ID)
                showStartRecordPopup()
            }
            snap.mode == MacroOverlayMode.PLAYBACK -> {
                removeCapture()
                recordNotifRefreshJob?.cancel()
                stopRecordingForeground()
                LauncherIconHelper.setRecordingIcon(this, recording = false)
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
        // After Recents/Home/Notif, reclaim the capture surface if SystemUI covered it.
        if (captureOverlay?.parent == null) {
            reattachCaptureOverlay()
        }
        refreshCaptureGestureExclusion()
        syncTypedTextFromField()
    }

    /**
     * Full-bleed capture during recording (status bar + nav edges included) so
     * edge/top/bottom system gestures can be seen by the overlay.
     * Soft keyboard still shrinks the overlay so typing stays native.
     */
    private fun refreshNavEdges() {
        navEdges = NavEdgeBands.resolve(this, wm())
    }

    private fun refreshCaptureGestureExclusion() {
        // Full-screen exclusion is capped by the OS and mostly ignored — edge strips
        // carry the real exclusion rects (see refreshEdgeStripExclusion).
        val overlay = captureOverlay ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (overlay.width <= 0 || overlay.height <= 0) return
        overlay.systemGestureExclusionRects = emptyList()
        refreshEdgeStripExclusion()
    }

    private fun refreshEdgeStripExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        for (strip in edgeStripViews) {
            if (strip.width <= 0 || strip.height <= 0) continue
            strip.systemGestureExclusionRects = listOf(Rect(0, 0, strip.width, strip.height))
        }
    }

    private fun refreshImeCaptureBounds() {
        val overlay = captureOverlay ?: return
        val params = captureParams ?: return
        if (overlay.parent == null) return
        refreshNavEdges()
        val edges = navEdges
        val screenW = edges?.screenW ?: fullDisplaySize().first
        val screenH = edges?.screenH ?: fullDisplaySize().second
        val ime = MtAccessibilityService.instance?.inputMethodBounds()
        val newHeight = if (ime != null && ime.top in 1 until screenH && ime.height() > dp(100)) {
            if (typedTextBaseline == null) {
                typedTextBaseline = MtAccessibilityService.instance?.focusedEditableText().orEmpty()
                lastFlushedTypedText = typedTextBaseline
            }
            ime.top
        } else {
            flushTypedTextIfNeeded()
            typedTextBaseline = null
            screenH
        }
        if (params.width != screenW || params.height != newHeight || params.x != 0 || params.y != 0) {
            params.width = screenW
            params.height = newHeight
            params.x = 0
            params.y = 0
            applyOverlayInsets(params)
            runCatching { wm().updateViewLayout(overlay, params) }
        }
        refreshCaptureGestureExclusion()
    }

    private fun fullDisplaySize(): Pair<Int, Int> =
        NavEdgeBands.displaySize(wm(), resources.displayMetrics)

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
                text = "While recording, MT Auto Clicker captures taps, holds, swipes, and system nav (buttons or gesture swipes). Review actions before sharing."
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
                setOnClickListener { closeMacroSession() }
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
        runCatching {
            wm().addView(scrim, params)
        }.onFailure { err ->
            Toast.makeText(
                this,
                "Can't show Start popup: ${err.message ?: "overlay blocked"}",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    // ── Vertical playback chip (drag handle only) ─────────────────────────

    @SuppressLint("SetTextI18n")
    private fun showVerticalPlaybackChip(snap: MacroSessionSnapshot) {
        removePanel()
        val cfg = snap.playbackConfig
        val btn = dp(32)
        val chipPad = dp(7)
        val accent = 0xFF8B5CF6.toInt()
        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(chipPad, dp(8), chipPad, dp(8))
            background = floatbarPillBg(accent)
            elevation = dp(12).toFloat()
            clipChildren = false
            clipToPadding = false
        }

        val handle = floatbarDragHandle(btn)
        setupDragHandle(handle)
        chip.addView(handle)

        chip.addView(
            floatbarStatusLabel("${cfg.steps.size}", btn, 0xFFC4B5FD.toInt()),
        )

        chip.addView(
            mtFeatureButton(btn, AppRoute.MACRO_RECORDER) {
                closeMacroSession()
            },
        )

        listOf(0.5f to "0.5x", 1f to "1x", 2f to "2x").forEach { (value, label) ->
            val active = kotlin.math.abs(cfg.playbackSpeed - value) < 0.01f
            chip.addView(
                floatbarSpeedChip(label, btn, active) {
                    MacroHub.patchPlayback(speed = value)
                },
            )
        }

        chip.addView(spacerV(2))
        chip.addView(
            floatbarActionButton(
                iconRes = R.drawable.ic_play,
                sizePx = btn,
                fillColor = 0xFF10B981.toInt(),
            ) {
                removePanel()
                cancelNotification(PLAYBACK_READY_NOTIFICATION_ID)
                MacroPlaybackService.start(this@MacroOverlayService)
            },
        )
        chip.addView(
            floatbarCloseButton(btn) { closeMacroSession() },
        )

        // Keep clear of the right rounded display corner / gesture edge.
        val edgeInset = dp(10)
        val chipWidthEstimate = btn + chipPad * 2
        val dm = resources.displayMetrics
        panelX = (dm.widthPixels - chipWidthEstimate - edgeInset).coerceAtLeast(edgeInset)
        panelY = panelY.coerceIn(dp(48), (dm.heightPixels - dp(280)).coerceAtLeast(dp(48)))

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
        if (pendingMinimizeAfterStart) {
            pendingMinimizeAfterStart = false
            AutomationLauncher.minimizeToBackground(this)
        }
    }

    private fun stopRecordingAndSave() {
        flushTypedTextIfNeeded()
        removeCapture()
        recordNotifRefreshJob?.cancel()
        stopRecordingForeground()
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
        // Slightly non-zero alpha background helps some OEMs hit-test the overlay;
        // fully transparent windows can miss edge touches after the debug guide was removed.
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(0x01000000)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            isClickable = true
            isFocusable = false
        }

        overlay.setOnTouchListener { _, event ->
            if (captureLocked) {
                if (SystemClock.elapsedRealtime() - captureLockedAtElapsed > 800L) {
                    captureLocked = false
                } else {
                    return@setOnTouchListener true
                }
            }
            val raw = MacroPoint(event.rawX, event.rawY)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    strokeOpen = true
                    strokePoints.clear()
                    strokePoints += raw
                    strokeStartElapsed = SystemClock.elapsedRealtime()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!strokeOpen) return@setOnTouchListener true
                    val last = strokePoints.lastOrNull()
                    if (last == null ||
                        kotlin.math.hypot(
                            (raw.x - last.x).toDouble(),
                            (raw.y - last.y).toDouble(),
                        ) >= 3.0
                    ) {
                        strokePoints += raw
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!strokeOpen) return@setOnTouchListener true
                    strokeOpen = false
                    strokePoints += raw
                    val duration = (SystemClock.elapsedRealtime() - strokeStartElapsed).coerceAtLeast(28L)
                    refreshNavEdges()
                    val edges = navEdges ?: NavEdgeBands.resolve(this@MacroOverlayService, wm())
                    val density = resources.displayMetrics.density
                    val points = strokePoints.toList()
                    strokePoints.clear()
                    lastStrokePoints = points
                    val step = MacroGestureCoalescer.classifySystemNav(
                        points,
                        duration,
                        density,
                        edges,
                    ) ?: MacroGestureCoalescer.coalesce(points, duration, density)
                    if (step != null) {
                        val isGlobal = step.kind == MacroStepKind.GLOBAL_BACK ||
                            step.kind == MacroStepKind.GLOBAL_HOME ||
                            step.kind == MacroStepKind.GLOBAL_RECENTS ||
                            step.kind == MacroStepKind.GLOBAL_NOTIFICATIONS
                        if (isGlobal) {
                            queueGlobalRecord(step)
                        } else {
                            captureLocked = true
                            captureLockedAtElapsed = SystemClock.elapsedRealtime()
                            scope.launch {
                                delay(40)
                                if (MacroHub.snapshot.value.mode != MacroOverlayMode.RECORDING) {
                                    captureLocked = false
                                    return@launch
                                }
                                detachCaptureOverlayNow()
                                queueLiveRelay(step)
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    strokeOpen = false
                    strokePoints.clear()
                    true
                }
                else -> false
            }
        }

        val (screenW, screenH) = fullDisplaySize()
        val params = overlayParams(
            width = screenW,
            height = screenH,
            x = 0,
            y = 0,
            touchable = true,
        )
        captureParams = params
        captureOverlay = overlay
        wm().addView(overlay, params)
        refreshNavEdges()
        // Edge strips sit above the main overlay and own the gesture bands.
        // Full-screen systemGestureExclusion is OS-capped and ineffective alone.
        showEdgeStrips()
        overlay.post {
            refreshNavEdges()
            refreshCaptureGestureExclusion()
            refreshImeCaptureBounds()
        }
        refreshImeCaptureBounds()
        imeLayoutJob?.cancel()
        imeLayoutJob = scope.launch {
            while (MacroHub.snapshot.value.mode == MacroOverlayMode.RECORDING) {
                delay(800)
                refreshImeCaptureBounds()
                refreshCaptureGestureExclusion()
                syncTypedTextFromField()
            }
        }
    }

    /**
     * Dedicated edge windows receive Back / Home / Recents / Notifications strokes.
     * Android caps full-window gesture exclusion; per-edge rects stay within the limit.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun showEdgeStrips() {
        removeEdgeStrips()
        refreshNavEdges()
        val edges = navEdges ?: NavEdgeBands.resolve(this, wm())
        val density = resources.displayMetrics.density
        // Match this device's system gesture bands exactly (OnePlus: L/R 90, top 143, bottom 96).
        // Slight inward pad only — oversized strips steal normal taps.
        val pad = (8f * density).toInt()
        val leftW = (edges.leftPx + pad).coerceIn(edges.leftPx, edges.screenW / 4)
        val rightW = (edges.rightPx + pad).coerceIn(edges.rightPx, edges.screenW / 4)
        val topH = (edges.topPx + pad).coerceIn(edges.topPx, edges.screenH / 5)
        val bottomH = (edges.bottomPx + pad).coerceIn(edges.bottomPx, edges.screenH / 5)

        fun addStrip(edge: NavEdge, x: Int, y: Int, w: Int, h: Int) {
            if (w <= 0 || h <= 0) return
            val strip = FrameLayout(this).apply {
                // Barely visible alpha helps OEM hit-testing; still looks invisible.
                setBackgroundColor(0x02000000)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                isClickable = true
                isFocusable = false
            }
            attachEdgeStripTouch(strip, edge)
            val params = overlayParams(width = w, height = h, x = x, y = y, touchable = true)
            runCatching { wm().addView(strip, params) }
            edgeStripViews += strip
            strip.post { refreshEdgeStripExclusion() }
        }

        // Side strips only in the middle band so top/bottom own Home / Shade corners.
        val midY = topH
        val midH = (edges.screenH - topH - bottomH).coerceAtLeast(1)
        addStrip(NavEdge.LEFT, 0, midY, leftW, midH)
        addStrip(NavEdge.RIGHT, edges.screenW - rightW, midY, rightW, midH)
        // Full-width top/bottom last (higher z) for Notifications / Home / Recents.
        addStrip(NavEdge.TOP, 0, 0, edges.screenW, topH)
        addStrip(NavEdge.BOTTOM, 0, edges.screenH - bottomH, edges.screenW, bottomH)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachEdgeStripTouch(strip: View, edge: NavEdge) {
        val points = mutableListOf<MacroPoint>()
        var open = false
        var startElapsed = 0L
        strip.setOnTouchListener { _, event ->
            if (captureLocked) {
                if (SystemClock.elapsedRealtime() - captureLockedAtElapsed > 800L) {
                    captureLocked = false
                } else {
                    return@setOnTouchListener true
                }
            }
            val raw = MacroPoint(event.rawX, event.rawY)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    open = true
                    points.clear()
                    points += raw
                    startElapsed = SystemClock.elapsedRealtime()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!open) return@setOnTouchListener true
                    val last = points.lastOrNull()
                    if (last == null ||
                        kotlin.math.hypot(
                            (raw.x - last.x).toDouble(),
                            (raw.y - last.y).toDouble(),
                        ) >= 2.0
                    ) {
                        points += raw
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!open) return@setOnTouchListener true
                    open = false
                    points += raw
                    val duration = (SystemClock.elapsedRealtime() - startElapsed).coerceAtLeast(28L)
                    val density = resources.displayMetrics.density
                    val step = MacroGestureCoalescer.classifyKnownEdge(
                        edge,
                        points.toList(),
                        duration,
                        density,
                    )
                    points.clear()
                    if (step != null) {
                        queueGlobalRecord(step)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    open = false
                    points.clear()
                    true
                }
                else -> false
            }
        }
    }

    private fun removeEdgeStrips() {
        for (strip in edgeStripViews) {
            runCatching { wm().removeView(strip) }
        }
        edgeStripViews.clear()
    }

    /**
     * Record a system-nav step. During recording we do NOT open Home / Recents /
     * the notification shade — those SystemUI surfaces sit above our overlay and
     * steal every following touch (felt like "works once then dies").
     * Back is safe to fire live. All globals still run on playback.
     */
    private fun queueGlobalRecord(step: MacroStep) {
        val accepted = MacroHub.appendStep(step, dedupeWindowMs = 250L)
        captureLocked = false
        strokeOpen = false
        if (!accepted) return

        val label = when (step.kind) {
            MacroStepKind.GLOBAL_BACK -> "Back"
            MacroStepKind.GLOBAL_HOME -> "Home"
            MacroStepKind.GLOBAL_RECENTS -> "Recents"
            MacroStepKind.GLOBAL_NOTIFICATIONS -> "Notifications"
            else -> "Nav"
        }

        val fireLive = step.kind == MacroStepKind.GLOBAL_BACK
        Toast.makeText(
            this,
            if (fireLive) "Recorded · $label"
            else "Recorded · $label (runs on Play)",
            Toast.LENGTH_SHORT,
        ).show()

        if (fireLive) {
            MtAccessibilityService.instance?.performGlobalNav(step.kind)
        }

        // Keep capture armed for the next gesture immediately.
        refreshCaptureGestureExclusion()
        if (captureOverlay?.parent == null) {
            reattachCaptureOverlay()
        }
        if (edgeStripViews.isEmpty() && MacroHub.snapshot.value.mode == MacroOverlayMode.RECORDING) {
            showEdgeStrips()
        }
    }

    /**
     * Reliable live handoff for OxygenOS:
     * 1) Overlay already removed on finger-up
     * 2) Await a short Accessibility reinject (so the tap actually lands)
     * 3) Put capture back immediately — no long idle window
     */
    private fun queueLiveRelay(step: MacroStep) {
        val accepted = MacroHub.appendStep(step)
        reattachJob?.cancel()
        if (!accepted) {
            captureLocked = false
            scheduleCaptureReattach(40L)
            return
        }
        scope.launch {
            relayMutex.withLock {
                if (MacroHub.snapshot.value.mode != MacroOverlayMode.RECORDING) {
                    captureLocked = false
                    return@withLock
                }
                captureLocked = true
                captureLockedAtElapsed = SystemClock.elapsedRealtime()
                try {
                    detachCaptureOverlayNow()
                    delay(6)
                    val timeoutMs = when (step.kind) {
                        MacroStepKind.SWIPE, MacroStepKind.PATH -> 220L
                        MacroStepKind.LONG_PRESS -> 320L
                        else -> 140L
                    }
                    withTimeoutOrNull(timeoutMs) {
                        MtAccessibilityService.instance?.performMacroStepLive(step)
                    }
                    delay(12)
                } finally {
                    captureLocked = false
                    reattachCaptureOverlay()
                    refreshCaptureGestureExclusion()
                }
            }
        }
    }

    private fun detachCaptureOverlayNow() {
        removeEdgeStrips()
        val overlay = captureOverlay ?: return
        runCatching {
            if (overlay.parent != null) wm().removeView(overlay)
        }
    }

    private fun scheduleCaptureReattach(delayMs: Long) {
        reattachJob?.cancel()
        reattachJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            if (MacroHub.snapshot.value.mode != MacroOverlayMode.RECORDING) return@launch
            relayMutex.withLock { reattachCaptureOverlay() }
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
        if (edgeStripViews.isEmpty()) {
            showEdgeStrips()
        }
        refreshCaptureGestureExclusion()
    }

    // ── Notifications (styled one-line custom view) ───────────────────────

    private fun postRecordingNotification(snap: MacroSessionSnapshot) {
        ensureChannel(RECORD_CHANNEL_ID, "Macro recording", NotificationManager.IMPORTANCE_DEFAULT)
        val stopPi = servicePending(1, ACTION_STOP_RECORD)
        val backPi = servicePending(11, ACTION_RECORD_BACK)
        val homePi = servicePending(12, ACTION_RECORD_HOME)
        val recentsPi = servicePending(13, ACTION_RECORD_RECENTS)
        val notifPi = servicePending(14, ACTION_RECORD_NOTIFICATIONS)
        val openPi = activityPending(0)
        val count = snap.stepCount
        val timer = formatElapsed(snap.recordingStartedAtMs)
        val startedAt = if (snap.recordingStartedAtMs > 0) {
            snap.recordingStartedAtMs
        } else {
            System.currentTimeMillis()
        }

        val views = RemoteViews(packageName, R.layout.notification_macro_recording).apply {
            setTextViewText(R.id.notif_rec_title, getString(R.string.recording_status_label))
            setTextViewText(R.id.notif_rec_timer, timer)
            setTextViewText(
                R.id.notif_rec_count,
                if (count == 0) "· waiting" else "· $count actions",
            )
            setOnClickPendingIntent(R.id.notif_rec_stop, stopPi)
        }

        val notification = NotificationCompat.Builder(this, RECORD_CHANNEL_ID)
            // White "REC" silhouette. OnePlus often still forces the launcher icon —
            // LauncherRecording alias uses a REC tile + "Recording…" label as backup.
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setContentTitle(getString(R.string.recording_status_label))
            .setContentText(if (count == 0) "$timer · waiting" else "$timer · $count actions")
            .setTicker(getString(R.string.recording_status_label))
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
            .setColor(0xFFE11D28.toInt())
            .setWhen(startedAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Back", backPi)
            .addAction(0, "Home", homePi)
            .addAction(0, "Recents", recentsPi)
            .addAction(0, "Shade", notifPi)
            .addAction(R.drawable.ic_stop, "Stop", stopPi)
            .build()
        lastNotifiedStepCount = count
        startRecordingForeground(notification)
    }

    private fun startRecordingForeground(notification: Notification) {
        runCatching {
            startSpecialUseForeground(RECORD_NOTIFICATION_ID, notification)
            recordingFgStarted = true
        }.onFailure {
            // Fallback if FGS type is rejected — still show the chip.
            recordingFgStarted = false
            notify(RECORD_NOTIFICATION_ID, notification)
        }
    }

    private fun stopRecordingForeground() {
        if (!recordingFgStarted) return
        recordingFgStarted = false
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
    }

    /** Insert a nav step into the macro. Avoid opening Home/Recents/Shade live while recording. */
    private fun recordAndPerformGlobal(kind: MacroStepKind) {
        if (MacroHub.snapshot.value.mode != MacroOverlayMode.RECORDING) return
        queueGlobalRecord(MacroStep(kind = kind, delayMs = 0L, durationMs = 50L))
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
                val dm = resources.displayMetrics
                val edge = dp(8)
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startX = params.x
                        startY = params.y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val w = root.width.coerceAtLeast(dp(40))
                        val h = root.height.coerceAtLeast(dp(120))
                        params.x = (startX + (event.rawX - downX).toInt())
                            .coerceIn(edge, (dm.widthPixels - w - edge).coerceAtLeast(edge))
                        params.y = (startY + (event.rawY - downY).toInt())
                            .coerceIn(edge, (dm.heightPixels - h - edge).coerceAtLeast(edge))
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
        // Must be APPLICATION_OVERLAY — TYPE_ACCESSIBILITY_OVERLAY can only be added
        // by AccessibilityService itself; using it here fails silently (no Start popup).
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
            applyOverlayInsets(this)
        }
    }

    private fun applyOverlayInsets(params: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Critical on OnePlus: default inset fitting leaves gaps at gesture edges
            // so bottom/side swipes never reach the capture overlay.
            params.setFitInsetsTypes(0)
            params.setFitInsetsSides(0)
            runCatching {
                @Suppress("DEPRECATION")
                params.isFitInsetsIgnoringVisibility = true
            }
        }
    }

    private fun wm(): WindowManager = appWm

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_DISMISS = "net.mtautoclicker.android.MACRO_OVERLAY_DISMISS"
        const val ACTION_STOP_RECORD = "net.mtautoclicker.android.MACRO_STOP_RECORD"
        const val ACTION_PLAY = "net.mtautoclicker.android.MACRO_NOTIF_PLAY"
        const val ACTION_CLOSE_SESSION = "net.mtautoclicker.android.MACRO_NOTIF_CLOSE"
        const val ACTION_RECORD_BACK = "net.mtautoclicker.android.MACRO_RECORD_BACK"
        const val ACTION_RECORD_HOME = "net.mtautoclicker.android.MACRO_RECORD_HOME"
        const val ACTION_RECORD_RECENTS = "net.mtautoclicker.android.MACRO_RECORD_RECENTS"
        const val ACTION_RECORD_NOTIFICATIONS = "net.mtautoclicker.android.MACRO_RECORD_NOTIFICATIONS"
        private const val RECORD_CHANNEL_ID = "mt_macro_record_v10"
        private const val PLAYBACK_CHANNEL_ID = "mt_macro_playback_ready_v6"
        private const val RECORD_NOTIFICATION_ID = 1003
        private const val PLAYBACK_READY_NOTIFICATION_ID = 1004

        @Volatile
        var instance: MacroOverlayService? = null
            private set

        /** Set by [AutomationLauncher.startMacroRecord]; cleared after Start. */
        @Volatile
        var pendingMinimizeAfterStart: Boolean = true

        fun show(context: Context) {
            context.startService(Intent(context, MacroOverlayService::class.java))
        }

        fun dismiss(context: Context) {
            context.stopService(Intent(context, MacroOverlayService::class.java))
        }
    }
}
