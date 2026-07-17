package net.mtautoclicker.android.service

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
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
import android.view.animation.OvershootInterpolator
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
import net.mtautoclicker.android.data.AutomationRunState
import net.mtautoclicker.android.engine.AutoRefreshHub

/**
 * Float bar for Auto Refresh: Refresh / Pause / Stop + Close.
 */
class RefreshOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var wm: WindowManager
    private var root: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var statusView: TextView? = null
    private var actionBtn: ImageView? = null
    private var panelX = 0
    private var panelY = 0
    private var lastShownCount = -1
    private var countAnimator: AnimatorSet? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        panelX = (dm.widthPixels - dp(64)).coerceAtLeast(dp(12))
        panelY = dp(160)
        showChip()
        scope.launch {
            AutoRefreshHub.snapshot.collectLatest { snap ->
                when (snap.runState) {
                    AutomationRunState.RUNNING -> {
                        applyRunningCount(snap.refreshCount)
                    }
                    AutomationRunState.PAUSED -> {
                        stopCountGlow()
                        styleStatusLabel("Paused", 0xFFFBBF24.toInt(), sizeSp = 11f)
                    }
                    AutomationRunState.ARMED -> {
                        stopCountGlow()
                        lastShownCount = -1
                        styleStatusLabel("Ready", 0xFFE2E8F0.toInt(), sizeSp = 11f)
                    }
                    else -> {
                        stopCountGlow()
                        lastShownCount = -1
                        styleStatusLabel("Refresh", 0xFFE2E8F0.toInt(), sizeSp = 11f)
                    }
                }
                actionBtn?.setImageResource(
                    when (snap.runState) {
                        AutomationRunState.RUNNING -> R.drawable.ic_pause
                        AutomationRunState.PAUSED -> R.drawable.ic_play
                        else -> R.drawable.ic_refresh
                    },
                )
                val running = snap.runState == AutomationRunState.RUNNING
                actionBtn?.background = circleBg(if (running) 0xFFEA580C.toInt() else 0xFFF59E0B.toInt())
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeChip()
        scope.cancel()
        if (instance === this) instance = null
        AutoRefreshService.stop(this)
        AutoRefreshHub.reset()
        super.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun showChip() {
        removeChip()
        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(12), dp(10), dp(12))
            background = GradientDrawable().apply {
                setColor(0xF2111827.toInt())
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), 0x55F59E0B.toInt())
            }
            elevation = dp(10).toFloat()
        }

        val handle = TextView(this).apply {
            text = "⠿"
            gravity = Gravity.CENTER
            setTextColor(0xFF94A3B8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(4), dp(2), dp(4), dp(8))
        }
        setupDrag(handle)
        chip.addView(handle)

        statusView = TextView(this).apply {
            text = "Ready"
            gravity = Gravity.CENTER
            setTextColor(0xFFE2E8F0.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.02f
            setPadding(0, 0, 0, dp(8))
            minWidth = dp(36)
        }
        chip.addView(statusView)

        actionBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_refresh)
            setColorFilter(Color.WHITE)
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = circleBg(0xFFF59E0B.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(8)
            }
            setOnClickListener {
                when (AutoRefreshHub.snapshot.value.runState) {
                    AutomationRunState.RUNNING -> AutoRefreshService.pause(this@RefreshOverlayService)
                    AutomationRunState.PAUSED -> AutoRefreshService.resume(this@RefreshOverlayService)
                    else -> {
                        if (!MtAccessibilityService.isEnabled()) {
                            Toast.makeText(
                                this@RefreshOverlayService,
                                "Enable Accessibility first",
                                Toast.LENGTH_LONG,
                            ).show()
                            return@setOnClickListener
                        }
                        AutoRefreshService.start(this@RefreshOverlayService)
                    }
                }
            }
        }
        chip.addView(actionBtn)

        chip.addView(
            TextView(this).apply {
                text = "✕"
                gravity = Gravity.CENTER
                setTextColor(0xFFE2E8F0.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = GradientDrawable().apply {
                    setColor(0xFF334155.toInt())
                    cornerRadius = dp(999).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                setOnClickListener { stopSelf() }
            },
        )

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

    private fun applyRunningCount(count: Int) {
        val view = statusView ?: return
        val bumped = count != lastShownCount
        lastShownCount = count
        styleStatusLabel(
            text = count.toString(),
            color = 0xFFFBBF24.toInt(),
            sizeSp = 16f,
            mono = true,
        )
        // Soft amber glow behind the digits
        view.setShadowLayer(dp(8).toFloat(), 0f, 0f, 0xAAF59E0B.toInt())
        if (bumped) pulseCount(view)
    }

    private fun styleStatusLabel(
        text: String,
        color: Int,
        sizeSp: Float,
        mono: Boolean = false,
    ) {
        val view = statusView ?: return
        view.text = text
        view.setTextColor(color)
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        view.typeface = if (mono) {
            Typeface.create("sans-serif-black", Typeface.BOLD)
        } else {
            Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        view.letterSpacing = if (mono) 0.04f else 0.02f
        if (!mono) {
            view.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }
    }

    private fun pulseCount(view: TextView) {
        countAnimator?.cancel()
        view.scaleX = 1f
        view.scaleY = 1f
        view.alpha = 1f
        val growX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.28f, 1f)
        val growY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.28f, 1f)
        val flash = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.55f, 1f)
        countAnimator = AnimatorSet().apply {
            playTogether(growX, growY, flash)
            duration = 320
            interpolator = OvershootInterpolator(1.4f)
            start()
        }
    }

    private fun stopCountGlow() {
        countAnimator?.cancel()
        countAnimator = null
        statusView?.apply {
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
            setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }
    }

    private fun circleBg(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun removeChip() {
        stopCountGlow()
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        params = null
        statusView = null
        actionBtn = null
        lastShownCount = -1
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
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startX = lp.x
                        startY = lp.y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = startX + (event.rawX - downX).toInt()
                        lp.y = startY + (event.rawY - downY).toInt()
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
        const val ACTION_DISMISS = "net.mtautoclicker.android.REFRESH_OVERLAY_DISMISS"

        @Volatile
        var instance: RefreshOverlayService? = null
            private set

        fun show(context: Context) {
            context.startService(Intent(context, RefreshOverlayService::class.java))
        }

        fun dismiss(context: Context) {
            context.stopService(Intent(context, RefreshOverlayService::class.java))
        }
    }
}
