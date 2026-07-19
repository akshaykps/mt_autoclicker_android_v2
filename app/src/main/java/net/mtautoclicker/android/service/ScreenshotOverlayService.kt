package net.mtautoclicker.android.service

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.mtautoclicker.android.R
import net.mtautoclicker.android.engine.FullPageCaptureHub
import net.mtautoclicker.android.engine.FullPageCapturePhase
import net.mtautoclicker.android.ui.screens.AppRoute

/**
 * Compact float bar for Full Page Screenshot: Snapshot + Close,
 * plus a live glowing capture pill while stitching.
 */
class ScreenshotOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var wm: WindowManager
    private var root: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var statusView: TextView? = null
    private var snapshotBtn: ImageButton? = null
    private var panelX = 0
    private var panelY = 0
    private var hidden = false

    private var liveRoot: View? = null
    private var liveParams: WindowManager.LayoutParams? = null
    private var liveLabel: TextView? = null
    private var liveGlow: ImageView? = null
    private var glowAnimator: ObjectAnimator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        val edgeInset = dp(10)
        val chipWidthEstimate = dp(34) + dp(6) * 2
        panelX = (dm.widthPixels - chipWidthEstimate - edgeInset).coerceAtLeast(edgeInset)
        panelY = dp(140)
        showChip()
        scope.launch {
            FullPageCaptureHub.snapshot.collectLatest { snap ->
                statusView?.text = when (snap.phase) {
                    FullPageCapturePhase.CAPTURING -> "…${snap.frameCount}"
                    FullPageCapturePhase.SAVING -> "Save"
                    FullPageCapturePhase.DONE -> "Done"
                    FullPageCapturePhase.ERROR -> "!"
                    else -> "Shot"
                }
                val busy = snap.phase == FullPageCapturePhase.CAPTURING ||
                    snap.phase == FullPageCapturePhase.SAVING
                snapshotBtn?.isEnabled = !busy
                snapshotBtn?.alpha = if (busy) 0.5f else 1f
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> setHiddenInternal(true)
            ACTION_SHOW -> setHiddenInternal(false)
            ACTION_LIVE_SHOW -> setLiveCaptureInternal(true)
            ACTION_LIVE_HIDE -> setLiveCaptureInternal(false)
            ACTION_LIVE_VISIBLE -> setLiveVisibleInternal(intent.getBooleanExtra(EXTRA_VISIBLE, true))
            ACTION_LIVE_FRAMES -> updateLiveFramesInternal(intent.getIntExtra(EXTRA_FRAMES, 0))
            ACTION_LIVE_LABEL -> updateLiveLabelInternal(intent.getStringExtra(EXTRA_LABEL).orEmpty())
            ACTION_DISMISS -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeLiveCapture()
        removeChip()
        scope.cancel()
        if (instance === this) instance = null
        // stopService avoids ACTION_STOP recursion with the capture service.
        stopService(Intent(this, FullPageCaptureService::class.java))
        FullPageCaptureHub.reset()
        super.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun showChip() {
        removeChip()
        val btn = dp(32)
        val chipPad = dp(7)
        val accent = 0xFF06B6D4.toInt()
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
        setupDrag(handle)
        chip.addView(handle)

        statusView = floatbarStatusLabel("Shot", btn, 0xFF67E8F9.toInt())
        chip.addView(statusView)

        chip.addView(
            mtFeatureButton(btn, AppRoute.FULL_PAGE_SCREENSHOT) {
                FullPageCaptureService.stop(this@ScreenshotOverlayService)
                FullPageCaptureHub.reset()
                stopSelf()
            },
        )

        snapshotBtn = floatbarActionButton(
            iconRes = R.drawable.ic_screenshot,
            sizePx = btn,
            fillColor = 0xFF0891B2.toInt(),
        ) {
            if (FullPageCaptureHub.snapshot.value.phase == FullPageCapturePhase.CAPTURING) {
                Toast.makeText(this@ScreenshotOverlayService, "Already capturing…", Toast.LENGTH_SHORT).show()
                return@floatbarActionButton
            }
            FullPageCaptureService.capture(this@ScreenshotOverlayService)
        }
        chip.addView(snapshotBtn)

        chip.addView(
            floatbarCloseButton(btn) { stopSelf() },
        )

        // Keep clear of the right rounded display corner / gesture edge.
        val edgeInset = dp(10)
        val chipWidthEstimate = btn + chipPad * 2
        val dm = resources.displayMetrics
        panelX = (dm.widthPixels - chipWidthEstimate - edgeInset).coerceAtLeast(edgeInset)
        panelY = panelY.coerceIn(dp(48), (dm.heightPixels - dp(200)).coerceAtLeast(dp(48)))

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = panelX
            y = panelY
        }
        params = lp
        root = chip
        wm.addView(chip, lp)
    }

    private fun removeChip() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        params = null
        statusView = null
        snapshotBtn = null
    }

    private fun setHiddenInternal(hide: Boolean) {
        hidden = hide
        val view = root ?: return
        val lp = params ?: return
        if (hide) {
            runCatching { wm.removeView(view) }
        } else if (view.parent == null) {
            runCatching { wm.addView(view, lp) }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setLiveCaptureInternal(show: Boolean) {
        if (show) {
            ensureLiveCapture()
            liveLabel?.text = "Capturing…"
            startGlowPulse()
            setLiveVisibleInternal(true)
        } else {
            removeLiveCapture()
        }
    }

    private fun setLiveVisibleInternal(visible: Boolean) {
        val view = liveRoot ?: return
        val lp = liveParams ?: return
        if (visible) {
            if (view.parent == null) {
                runCatching { wm.addView(view, lp) }
            }
            view.visibility = View.VISIBLE
            startGlowPulse()
        } else {
            view.visibility = View.INVISIBLE
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateLiveFramesInternal(frames: Int) {
        ensureLiveCapture()
        liveLabel?.text = if (frames <= 0) "Capturing…" else "Capturing · $frames"
    }

    private fun updateLiveLabelInternal(label: String) {
        ensureLiveCapture()
        if (label.isNotBlank()) liveLabel?.text = label
    }

    @SuppressLint("SetTextI18n")
    private fun ensureLiveCapture() {
        if (liveRoot != null) return
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(14), dp(8))
            background = resources.getDrawable(R.drawable.bg_capture_live_pill, theme)
            elevation = dp(12).toFloat()
        }

        liveGlow = ImageView(this).apply {
            setImageResource(R.drawable.ic_capture_glow)
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                marginEnd = dp(8)
            }
        }
        pill.addView(liveGlow)

        val cam = ImageView(this).apply {
            setImageResource(R.drawable.ic_screenshot)
            setColorFilter(0xFF22D3EE.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply {
                marginEnd = dp(8)
            }
        }
        pill.addView(cam)

        liveLabel = TextView(this).apply {
            text = "Capturing…"
            setTextColor(0xFFE2E8F0.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
        }
        pill.addView(liveLabel)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // Sit in the status-bar band (cropped out of stitches).
            y = dp(6)
        }
        liveParams = lp
        liveRoot = pill
        runCatching { wm.addView(pill, lp) }
        startGlowPulse()
    }

    private fun startGlowPulse() {
        val glow = liveGlow ?: return
        if (glowAnimator?.isRunning == true) return
        glowAnimator = ObjectAnimator.ofFloat(glow, View.ALPHA, 0.35f, 1f).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        // Soft scale pulse on the whole pill.
        liveRoot?.let { pill ->
            ObjectAnimator.ofFloat(pill, View.SCALE_X, 0.96f, 1.04f).apply {
                duration = 700
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
            ObjectAnimator.ofFloat(pill, View.SCALE_Y, 0.96f, 1.04f).apply {
                duration = 700
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }

    private fun removeLiveCapture() {
        glowAnimator?.cancel()
        glowAnimator = null
        liveRoot?.animate()?.cancel()
        liveRoot?.let { runCatching { wm.removeView(it) } }
        liveRoot = null
        liveParams = null
        liveLabel = null
        liveGlow = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag(handle: View) {
        handle.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val lp = params ?: return false
                val chip = root ?: return false
                val dm = resources.displayMetrics
                val edge = dp(8)
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startX = lp.x
                        startY = lp.y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val w = chip.width.coerceAtLeast(dp(40))
                        val h = chip.height.coerceAtLeast(dp(120))
                        lp.x = (startX + (event.rawX - downX).toInt())
                            .coerceIn(edge, (dm.widthPixels - w - edge).coerceAtLeast(edge))
                        lp.y = (startY + (event.rawY - downY).toInt())
                            .coerceIn(edge, (dm.heightPixels - h - edge).coerceAtLeast(edge))
                        panelX = lp.x
                        panelY = lp.y
                        wm.updateViewLayout(chip, lp)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_DISMISS = "net.mtautoclicker.android.SCREENSHOT_OVERLAY_DISMISS"
        const val ACTION_HIDE = "net.mtautoclicker.android.SCREENSHOT_OVERLAY_HIDE"
        const val ACTION_SHOW = "net.mtautoclicker.android.SCREENSHOT_OVERLAY_SHOW"
        const val ACTION_LIVE_SHOW = "net.mtautoclicker.android.SCREENSHOT_LIVE_SHOW"
        const val ACTION_LIVE_HIDE = "net.mtautoclicker.android.SCREENSHOT_LIVE_HIDE"
        const val ACTION_LIVE_VISIBLE = "net.mtautoclicker.android.SCREENSHOT_LIVE_VISIBLE"
        const val ACTION_LIVE_FRAMES = "net.mtautoclicker.android.SCREENSHOT_LIVE_FRAMES"
        const val ACTION_LIVE_LABEL = "net.mtautoclicker.android.SCREENSHOT_LIVE_LABEL"
        private const val EXTRA_VISIBLE = "visible"
        private const val EXTRA_FRAMES = "frames"
        private const val EXTRA_LABEL = "label"

        @Volatile
        var instance: ScreenshotOverlayService? = null
            private set

        fun show(context: Context) {
            context.startService(Intent(context, ScreenshotOverlayService::class.java))
        }

        fun dismiss(context: Context) {
            context.stopService(Intent(context, ScreenshotOverlayService::class.java))
        }

        fun setHidden(context: Context, hidden: Boolean) {
            val action = if (hidden) ACTION_HIDE else ACTION_SHOW
            context.startService(
                Intent(context, ScreenshotOverlayService::class.java).setAction(action),
            )
        }

        fun showLiveCapture(context: Context, show: Boolean) {
            val action = if (show) ACTION_LIVE_SHOW else ACTION_LIVE_HIDE
            context.startService(
                Intent(context, ScreenshotOverlayService::class.java).setAction(action),
            )
        }

        fun setLiveCaptureVisible(context: Context, visible: Boolean) {
            context.startService(
                Intent(context, ScreenshotOverlayService::class.java)
                    .setAction(ACTION_LIVE_VISIBLE)
                    .putExtra(EXTRA_VISIBLE, visible),
            )
        }

        fun updateLiveCaptureFrames(context: Context, frames: Int) {
            context.startService(
                Intent(context, ScreenshotOverlayService::class.java)
                    .setAction(ACTION_LIVE_FRAMES)
                    .putExtra(EXTRA_FRAMES, frames),
            )
        }

        fun updateLiveCaptureLabel(context: Context, label: String) {
            context.startService(
                Intent(context, ScreenshotOverlayService::class.java)
                    .setAction(ACTION_LIVE_LABEL)
                    .putExtra(EXTRA_LABEL, label),
            )
        }
    }
}
