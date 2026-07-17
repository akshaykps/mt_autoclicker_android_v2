package net.mtautoclicker.android.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.mtautoclicker.android.MtApplication
import net.mtautoclicker.android.R
import net.mtautoclicker.android.data.AutomationPlan
import net.mtautoclicker.android.data.AutomationRunState
import net.mtautoclicker.android.data.ClickTarget
import net.mtautoclicker.android.data.FeatureKind
import net.mtautoclicker.android.data.IntervalConfig
import net.mtautoclicker.android.data.IntervalUnit
import net.mtautoclicker.android.data.MouseButton
import net.mtautoclicker.android.data.MultiTargetConfig
import net.mtautoclicker.android.data.SingleTargetConfig
import net.mtautoclicker.android.data.StopCondition
import net.mtautoclicker.android.data.StopType
import net.mtautoclicker.android.data.TargetMode
import net.mtautoclicker.android.engine.AutomationHub

/**
 * Vertical MT float pill (matches Chrome extension / desktop control bar)
 * + optional settings panel for interval / stop / etc. without opening the main activity.
 */
class FloatingOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    /** App WM — fallback only. Prefer accessibility WM to avoid OEM overlay warnings. */
    private lateinit var appWindowManager: WindowManager

    private var floatBar: LinearLayout? = null
    private var settingsPanel: View? = null
    private var pickerOverlay: FrameLayout? = null
    private val markerViews = mutableListOf<View>()

    private var playBtn: ImageButton? = null
    private var pickBtn: ImageButton? = null
    private var eyeBtn: ImageButton? = null
    private var brandBtn: ImageView? = null
    private var removeBtn: ImageButton? = null
    private var topSection: LinearLayout? = null
    private var bottomSection: LinearLayout? = null
    private var runningStrip: LinearLayout? = null

    private var collapsed = false
    private var markersVisible = true
    private var barParams: WindowManager.LayoutParams? = null
    private var barX = 0
    private var barY = 0
    private var hiddenForClick = false
    private var zoneDragStartX = 0f
    private var zoneDragStartY = 0f
    private var zonePreview: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        appWindowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        instance = this
        // Default: right-center like extension
        val dm = resources.displayMetrics
        barX = (dm.widthPixels - dp(56))
        barY = (dm.heightPixels / 2 - dp(110)).coerceAtLeast(dp(48))
        showArmedPill()
        scope.launch {
            AutomationHub.snapshot.collectLatest { snap ->
                when (snap.runState) {
                    AutomationRunState.RUNNING, AutomationRunState.PAUSED -> showRunningPill(snap.runState)
                    else -> {
                        if (floatBar == null || runningStrip != null) showArmedPill()
                        else refreshArmedButtons(snap.targets.size)
                    }
                }
                refreshMarkers(if (markersVisible) snap.targets else emptyList())
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS) {
            ClickAutomationService.stop(this)
            AutomationHub.stopAll()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeAllOverlays()
        scope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        ClickAutomationService.stop(this)
        AutomationHub.stopAll()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    // ─── Vertical armed pill (extension layout) ───────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun showArmedPill() {
        removeFloatBarOnly()
        hideSettingsPanel()

        val isMulti = AutomationHub.activePlan is AutomationPlan.Multi
        val targets = AutomationHub.snapshot.value.targets.size

        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(5), dp(8), dp(5), dp(10))
            background = pillBg()
            elevation = dp(10).toFloat()
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 0)
        }
        topSection = top

        playBtn = iconBtn(R.drawable.ic_play, enabled = targets > 0) { togglePlay() }
        val saveBtn = iconBtn(R.drawable.ic_save) { savePresetQuick() }
        eyeBtn = iconBtn(if (markersVisible) R.drawable.ic_eye_open else R.drawable.ic_eye_off) {
            markersVisible = !markersVisible
            refreshMarkers(if (markersVisible) AutomationHub.snapshot.value.targets else emptyList())
            eyeBtn?.setImageResource(if (markersVisible) R.drawable.ic_eye_open else R.drawable.ic_eye_off)
        }
        top.addView(playBtn)
        top.addView(spacerV(3))
        top.addView(saveBtn)
        top.addView(spacerV(3))
        top.addView(eyeBtn)

        brandBtn = brandRoundBtn {
            collapsed = !collapsed
            applyCollapsedState()
        }

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        bottomSection = bottom

        pickBtn = iconBtn(
            if (!isMulti && targets > 0) R.drawable.ic_remove else R.drawable.ic_add,
        ) {
            val multi = AutomationHub.activePlan is AutomationPlan.Multi
            val count = AutomationHub.snapshot.value.targets.size
            when {
                multi -> startTargetPick()
                count > 0 -> AutomationHub.clearTargets()
                else -> startTargetPick()
            }
        }
        bottom.addView(pickBtn)
        removeBtn = iconBtn(R.drawable.ic_remove) { AutomationHub.removeLastTarget() }.also {
            it.visibility = if (isMulti && targets > 0) View.VISIBLE else View.GONE
        }
        bottom.addView(spacerV(3))
        bottom.addView(removeBtn)
        bottom.addView(spacerV(3))
        bottom.addView(iconBtn(R.drawable.ic_settings) { toggleSettingsPanel() })
        bottom.addView(spacerV(3))
        bottom.addView(iconBtn(R.drawable.ic_close) {
            ClickAutomationService.stop(this@FloatingOverlayService)
            hideSettingsPanel()
            AutomationHub.stopAll()
            stopSelf()
        })

        pill.addView(top)
        pill.addView(spacerV(8))
        pill.addView(brandBtn)
        pill.addView(spacerV(8))
        pill.addView(bottom)

        setupPillDrag(pill)

        val params = overlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.TOP or Gravity.START,
            x = barX,
            y = barY,
            touchable = true,
        )
        barParams = params
        floatBar = pill
        runningStrip = null
        wm().addView(pill, params)
        applyCollapsedState()
    }

    private fun refreshArmedButtons(targetCount: Int) {
        playBtn?.alpha = if (targetCount > 0) 1f else 0.4f
        playBtn?.isEnabled = targetCount > 0
        val isMulti = AutomationHub.activePlan is AutomationPlan.Multi
        pickBtn?.setImageResource(if (!isMulti && targetCount > 0) R.drawable.ic_remove else R.drawable.ic_add)
        removeBtn?.visibility = if (isMulti && targetCount > 0) View.VISIBLE else View.GONE
    }

    private fun applyCollapsedState() {
        val gone = if (collapsed) View.GONE else View.VISIBLE
        topSection?.visibility = gone
        bottomSection?.visibility = gone
        floatBar?.setPadding(
            dp(5),
            if (collapsed) dp(6) else dp(8),
            dp(5),
            if (collapsed) dp(6) else dp(10),
        )
        // Collapse spacers by rebuilding is heavy — hide sections is enough
    }

    // ─── Running vertical control pill (play/pause + stop only) ───────────

    private fun showRunningPill(state: AutomationRunState) {
        val paused = state == AutomationRunState.PAUSED

        // Reuse existing running pill — only flip play/pause glyph
        if (runningStrip != null) {
            playBtn?.setImageResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause)
            playBtn?.background = circleFillBg(
                if (paused) 0xFF059669.toInt() else 0xFFD97706.toInt(),
            )
            brandBtn?.background = if (paused) {
                GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFF64748B.toInt(), 0xFF475569.toInt()),
                ).apply { shape = GradientDrawable.OVAL }
            } else {
                GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFF059669.toInt(), 0xFF2563EB.toInt()),
                ).apply { shape = GradientDrawable.OVAL }
            }
            return
        }

        removeFloatBarOnly()
        hideSettingsPanel()

        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(5), dp(10), dp(5), dp(10))
            background = pillBg()
            elevation = dp(12).toFloat()
        }

        // Live status dot above controls
        val statusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                bottomMargin = dp(6)
                gravity = Gravity.CENTER_HORIZONTAL
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (paused) 0xFFF59E0B.toInt() else 0xFF22C55E.toInt())
            }
        }

        playBtn = iconBtn(
            iconRes = if (paused) R.drawable.ic_play else R.drawable.ic_pause,
            enabled = true,
            fillColor = if (paused) 0xFF059669.toInt() else 0xFFD97706.toInt(),
        ) {
            if (AutomationHub.snapshot.value.runState == AutomationRunState.PAUSED) {
                ClickAutomationService.resume(this)
            } else {
                ClickAutomationService.pause(this)
            }
        }

        brandBtn = brandRoundBtn {
            // Tap MT while running = pause/resume (quick toggle)
            if (AutomationHub.snapshot.value.runState == AutomationRunState.PAUSED) {
                ClickAutomationService.resume(this)
            } else {
                ClickAutomationService.pause(this)
            }
        }.also {
            it.background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    if (paused) 0xFF64748B.toInt() else 0xFF059669.toInt(),
                    if (paused) 0xFF475569.toInt() else 0xFF2563EB.toInt(),
                ),
            ).apply { shape = GradientDrawable.OVAL }
        }

        val stopBtn = iconBtn(
            iconRes = R.drawable.ic_stop,
            enabled = true,
            fillColor = 0xFFDC2626.toInt(),
        ) {
            ClickAutomationService.stop(this)
            AutomationHub.setRunState(AutomationRunState.ARMED, "Stopped")
            showArmedPill()
        }

        pill.addView(statusDot)
        pill.addView(playBtn)
        pill.addView(spacerV(6))
        pill.addView(brandBtn)
        pill.addView(spacerV(6))
        pill.addView(stopBtn)

        setupPillDrag(pill)

        val params = overlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.TOP or Gravity.START,
            x = barX,
            y = barY,
            touchable = true,
        )
        barParams = params
        floatBar = pill
        runningStrip = pill
        wm().addView(pill, params)
    }

    // ─── Settings panel (extension feature settings) ──────────────────────

    private fun toggleSettingsPanel() {
        if (settingsPanel != null) {
            hideSettingsPanel()
            return
        }
        showSettingsPanel()
    }

    @SuppressLint("SetTextI18n")
    private fun showSettingsPanel() {
        hideSettingsPanel()
        val plan = AutomationHub.activePlan ?: return
        val isMulti = plan is AutomationPlan.Multi
        val interval = plan.interval
        val stop = plan.stop
        val mouse = plan.mouseButton
        val parallel = (plan as? AutomationPlan.Multi)?.config?.parallel == true

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = settingsPanelBg()
            elevation = dp(16).toFloat()
        }

        panel.addView(headerRow(if (isMulti) "Multi Target Settings" else "Single Target Settings") {
            hideSettingsPanel()
        })

        panel.addView(label("Stop condition"))
        val stopSpinner = spinner(
            listOf("Never", "Cycles", "Duration"),
            when (stop.type) {
                StopType.NEVER -> 0
                StopType.CYCLES -> 1
                StopType.DURATION -> 2
            },
        )
        panel.addView(stopSpinner)

        val cyclesInput = numberField(stop.cycles?.toString() ?: "100")
        val durationSec = numberField(((stop.durationMs ?: 10_000) / 1000).toString())
        val cyclesBlock = labeledBlock("Cycles", cyclesInput)
        val durationBlock = labeledBlock("Duration (seconds)", durationSec)
        panel.addView(cyclesBlock)
        panel.addView(durationBlock)
        fun syncStopRows() {
            when (stopSpinner.selectedItemPosition) {
                1 -> {
                    cyclesBlock.visibility = View.VISIBLE
                    durationBlock.visibility = View.GONE
                }
                2 -> {
                    cyclesBlock.visibility = View.GONE
                    durationBlock.visibility = View.VISIBLE
                }
                else -> {
                    cyclesBlock.visibility = View.GONE
                    durationBlock.visibility = View.GONE
                }
            }
        }
        stopSpinner.onItemSelectedListener = simpleSelect { syncStopRows() }
        syncStopRows()

        panel.addView(label("Click interval"))
        val intervalRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val intervalInput = numberField(interval.value.toInt().toString()).also {
            it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        }
        val unitSpinner = spinner(
            listOf("ms", "sec", "min"),
            when (interval.unit) {
                IntervalUnit.MS -> 0
                IntervalUnit.S -> 1
                IntervalUnit.MIN -> 2
            },
        ).also {
            it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        intervalRow.addView(intervalInput)
        intervalRow.addView(unitSpinner)
        panel.addView(intervalRow)

        panel.addView(label("Mouse button"))
        val mouseSpinner = spinner(
            listOf("left", "right", "middle"),
            when (mouse) {
                MouseButton.LEFT -> 0
                MouseButton.RIGHT -> 1
                MouseButton.MIDDLE -> 2
            },
        )
        panel.addView(mouseSpinner)

        var targetModeSpinner: Spinner? = null
        if (!isMulti) {
            val single = (plan as AutomationPlan.Single).config
            panel.addView(label("Target type"))
            targetModeSpinner = spinner(
                listOf("point", "zone"),
                if (single.targetMode == TargetMode.ZONE) 1 else 0,
            )
            panel.addView(targetModeSpinner)
        }

        var parallelCheck: TextView? = null
        var parallelOn = parallel
        if (isMulti) {
            parallelCheck = TextView(this).apply {
                text = if (parallelOn) "☑ Parallel clicks" else "☐ Parallel clicks"
                setTextColor(0xFFCBD5E1.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(10), 0, dp(4))
                setOnClickListener {
                    parallelOn = !parallelOn
                    text = if (parallelOn) "☑ Parallel clicks" else "☐ Parallel clicks"
                }
            }
            panel.addView(parallelCheck)
        }

        panel.addView(label("Start delay (ms)"))
        val delayInput = numberField(interval.startDelayMs.toString())
        panel.addView(delayInput)

        panel.addView(label("Random offset (px)"))
        val offsetInput = numberField(interval.randomOffsetPx.toString())
        panel.addView(offsetInput)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
        }
        val cancel = chipBtn("Cancel", 0xFF334155.toInt()) { hideSettingsPanel() }.also {
            it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        }
        val save = chipBtn("Save", 0xFF3B82F6.toInt()) {
            val newInterval = IntervalConfig(
                value = intervalInput.text.toString().toDoubleOrNull() ?: interval.value,
                unit = when (unitSpinner.selectedItemPosition) {
                    1 -> IntervalUnit.S
                    2 -> IntervalUnit.MIN
                    else -> IntervalUnit.MS
                },
                startDelayMs = delayInput.text.toString().toLongOrNull() ?: 0L,
                randomOffsetPx = offsetInput.text.toString().toIntOrNull() ?: 0,
            )
            val newStop = when (stopSpinner.selectedItemPosition) {
                1 -> StopCondition(
                    type = StopType.CYCLES,
                    cycles = cyclesInput.text.toString().toIntOrNull() ?: 100,
                )
                2 -> StopCondition(
                    type = StopType.DURATION,
                    durationMs = (durationSec.text.toString().toLongOrNull() ?: 60) * 1000,
                )
                else -> StopCondition(type = StopType.NEVER)
            }
            val newMouse = when (mouseSpinner.selectedItemPosition) {
                1 -> MouseButton.RIGHT
                2 -> MouseButton.MIDDLE
                else -> MouseButton.LEFT
            }
            when (plan) {
                is AutomationPlan.Single -> {
                    val mode = if (targetModeSpinner?.selectedItemPosition == 1) {
                        TargetMode.ZONE
                    } else {
                        TargetMode.POINT
                    }
                    AutomationHub.updateSingleConfig(
                        plan.config.copy(
                            interval = newInterval,
                            stop = newStop,
                            mouseButton = newMouse,
                            targetMode = mode,
                        ),
                    )
                }
                is AutomationPlan.Multi -> {
                    AutomationHub.updateMultiConfig(
                        plan.config.copy(
                            interval = newInterval,
                            stop = newStop,
                            mouseButton = newMouse,
                            parallel = parallelOn,
                        ),
                    )
                }
            }
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            hideSettingsPanel()
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        actions.addView(cancel)
        actions.addView(save)
        panel.addView(actions)

        scroll.addView(panel)

        // Place panel to the left of the pill
        val panelW = dp(280)
        val params = overlayParams(
            width = panelW,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.TOP or Gravity.START,
            x = (barX - panelW - dp(12)).coerceAtLeast(dp(8)),
            y = barY.coerceAtLeast(dp(24)),
            touchable = true,
            focusable = true,
        )
        // Allow typing in EditTexts
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()

        settingsPanel = scroll
        wm().addView(scroll, params)
    }

    private fun hideSettingsPanel() {
        settingsPanel?.let { runCatching { wm().removeView(it) } }
        settingsPanel = null
    }

    // ─── Target picker / markers ──────────────────────────────────────────

    private fun startTargetPick() {
        if (AutomationHub.snapshot.value.runState == AutomationRunState.RUNNING) return
        hideSettingsPanel()
        AutomationHub.setPickingTarget(true)
        val zoneMode = (AutomationHub.activePlan as? AutomationPlan.Single)?.config?.targetMode == TargetMode.ZONE
        if (zoneMode) showZonePickerOverlay() else showPointPickerOverlay()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showPointPickerOverlay() {
        removePicker()
        val overlay = FrameLayout(this).apply { setBackgroundColor(0x55000000) }
        overlay.addView(
            pickerBanner("Tap to place click point"),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply { topMargin = dp(80) },
        )
        overlay.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                commitTarget(
                    ClickTarget(
                        x = event.rawX,
                        y = event.rawY,
                        label = "#${AutomationHub.snapshot.value.targets.size + 1}",
                    ),
                )
                true
            } else false
        }
        attachPickerOverlay(overlay)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showZonePickerOverlay() {
        removePicker()
        val overlay = FrameLayout(this).apply { setBackgroundColor(0x55000000) }
        overlay.addView(
            pickerBanner("Drag to draw a click zone"),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply { topMargin = dp(80) },
        )

        val preview = View(this).apply {
            visibility = View.GONE
            background = GradientDrawable().apply {
                setColor(0x333B82F6)
                setStroke(dp(2), 0xFF3B82F6.toInt())
                cornerRadius = dp(4).toFloat()
            }
        }
        zonePreview = preview
        overlay.addView(
            preview,
            FrameLayout.LayoutParams(0, 0).apply {
                gravity = Gravity.TOP or Gravity.START
            },
        )

        overlay.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    zoneDragStartX = event.rawX
                    zoneDragStartY = event.rawY
                    preview.visibility = View.VISIBLE
                    updateZonePreviewLayout(preview, overlay, zoneDragStartX, zoneDragStartY, event.rawX, event.rawY)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    updateZonePreviewLayout(preview, overlay, zoneDragStartX, zoneDragStartY, event.rawX, event.rawY)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val x1 = minOf(zoneDragStartX, event.rawX)
                    val y1 = minOf(zoneDragStartY, event.rawY)
                    val x2 = maxOf(zoneDragStartX, event.rawX)
                    val y2 = maxOf(zoneDragStartY, event.rawY)
                    val w = (x2 - x1).coerceAtLeast(dp(24).toFloat())
                    val h = (y2 - y1).coerceAtLeast(dp(24).toFloat())
                    commitTarget(
                        ClickTarget(
                            x = x1,
                            y = y1,
                            label = "#${AutomationHub.snapshot.value.targets.size + 1}",
                            zoneWidth = w,
                            zoneHeight = h,
                        ),
                    )
                    true
                }
                else -> false
            }
        }
        attachPickerOverlay(overlay)
    }

    private fun pickerBanner(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = roundedBg(0xE60D1333.toInt(), 16f)
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun updateZonePreviewLayout(
        preview: View,
        overlay: FrameLayout,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ) {
        val loc = IntArray(2)
        overlay.getLocationOnScreen(loc)
        val left = (minOf(x1, x2) - loc[0]).toInt()
        val top = (minOf(y1, y2) - loc[1]).toInt()
        val width = (maxOf(x1, x2) - minOf(x1, x2)).toInt()
        val height = (maxOf(y1, y2) - minOf(y1, y2)).toInt()
        val lp = (preview.layoutParams as FrameLayout.LayoutParams).apply {
            this.width = width.coerceAtLeast(1)
            this.height = height.coerceAtLeast(1)
            this.leftMargin = left
            this.topMargin = top
            gravity = Gravity.TOP or Gravity.START
        }
        preview.layoutParams = lp
        overlay.requestLayout()
    }

    private fun commitTarget(target: ClickTarget) {
        AutomationHub.addTarget(target)
        AutomationHub.setPickingTarget(false)
        removePicker()
    }

    private fun attachPickerOverlay(overlay: FrameLayout) {
        pickerOverlay = overlay
        wm().addView(
            overlay,
            overlayParams(
                width = WindowManager.LayoutParams.MATCH_PARENT,
                height = WindowManager.LayoutParams.MATCH_PARENT,
                gravity = Gravity.TOP or Gravity.START,
                touchable = true,
            ),
        )
    }

    private fun refreshMarkers(targets: List<ClickTarget>) {
        clearMarkers()
        val isMulti = AutomationHub.activePlan is AutomationPlan.Multi
        targets.forEachIndexed { index, target ->
            val zw = target.zoneWidth
            val zh = target.zoneHeight
            if (zw != null && zh != null && zw > 0f && zh > 0f) {
                // Zone rectangle (extension-style dashed box). Single target: no number badge.
                val zone = View(this).apply {
                    background = GradientDrawable().apply {
                        setColor(0x1F3B82F6)
                        setStroke(dp(2), 0xFF3B82F6.toInt())
                        cornerRadius = dp(4).toFloat()
                    }
                }
                markerViews.add(zone)
                wm().addView(
                    zone,
                    overlayParams(
                        width = zw.toInt().coerceAtLeast(1),
                        height = zh.toInt().coerceAtLeast(1),
                        gravity = Gravity.TOP or Gravity.START,
                        x = target.x.toInt(),
                        y = target.y.toInt(),
                        touchable = false,
                    ),
                )
                if (isMulti) {
                    addNumberBadge(
                        index = index + 1,
                        cx = target.x + zw / 2f,
                        cy = target.y + zh / 2f,
                    )
                }
            } else if (isMulti) {
                addNumberBadge(index = index + 1, cx = target.x, cy = target.y)
            } else {
                // Single-target point: plain blue dot + soft ring, no number
                val ringSize = dp(36)
                val ring = View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(0x143B82F6)
                        setStroke(dp(2), 0xFF3B82F6.toInt())
                    }
                }
                markerViews.add(ring)
                wm().addView(
                    ring,
                    overlayParams(
                        width = ringSize,
                        height = ringSize,
                        gravity = Gravity.TOP or Gravity.START,
                        x = (target.x - ringSize / 2f).toInt(),
                        y = (target.y - ringSize / 2f).toInt(),
                        touchable = false,
                    ),
                )
                val dotSize = dp(14)
                val dot = View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(0xFF3B82F6.toInt())
                        setStroke(dp(2), 0xFFFFFFFF.toInt())
                    }
                }
                markerViews.add(dot)
                wm().addView(
                    dot,
                    overlayParams(
                        width = dotSize,
                        height = dotSize,
                        gravity = Gravity.TOP or Gravity.START,
                        x = (target.x - dotSize / 2f).toInt(),
                        y = (target.y - dotSize / 2f).toInt(),
                        touchable = false,
                    ),
                )
            }
        }
    }

    private fun addNumberBadge(index: Int, cx: Float, cy: Float) {
        val size = dp(26)
        val marker = TextView(this).apply {
            text = "$index"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            background = circleStrokeBg(0xE63B82F6.toInt(), 0xFFFFFFFF.toInt())
        }
        markerViews.add(marker)
        wm().addView(
            marker,
            overlayParams(
                width = size,
                height = size,
                gravity = Gravity.TOP or Gravity.START,
                x = (cx - size / 2f).toInt(),
                y = (cy - size / 2f).toInt(),
                touchable = false,
            ),
        )
    }
    private fun togglePlay() {
        when (AutomationHub.snapshot.value.runState) {
            AutomationRunState.RUNNING -> ClickAutomationService.pause(this)
            AutomationRunState.PAUSED -> ClickAutomationService.resume(this)
            else -> {
                if (AutomationHub.snapshot.value.targets.isEmpty()) {
                    Toast.makeText(this, "Add a target with + first", Toast.LENGTH_SHORT).show()
                    return
                }
                hideSettingsPanel()
                ClickAutomationService.start(this)
            }
        }
    }

    private fun savePresetQuick() {
        val plan = AutomationHub.activePlan
        if (plan == null) {
            Toast.makeText(applicationContext, "Start a feature first", Toast.LENGTH_SHORT).show()
            return
        }
        val liveTargets = AutomationHub.snapshot.value.targets
        val json = Json { encodeDefaults = true }
        scope.launch {
            try {
                val feature = when (plan) {
                    is AutomationPlan.Single -> FeatureKind.SINGLE_TARGET
                    is AutomationPlan.Multi -> FeatureKind.MULTI_TARGET
                }
                val name = MtApplication.instance.presetRepository.nextDefaultSavedName(feature)
                when (plan) {
                    is AutomationPlan.Single -> {
                        val cfg = plan.config
                        MtApplication.instance.presetRepository.savePreset(
                            name = name,
                            feature = FeatureKind.SINGLE_TARGET,
                            configJson = json.encodeToString(cfg),
                            targets = liveTargets,
                        )
                    }
                    is AutomationPlan.Multi -> {
                        val cfg = plan.config
                        MtApplication.instance.presetRepository.savePreset(
                            name = name,
                            feature = FeatureKind.MULTI_TARGET,
                            configJson = json.encodeToString(cfg),
                            targets = liveTargets,
                        )
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Preset saved: $name", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "Save failed: ${e.message ?: "unknown error"}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    // ─── UI helpers ───────────────────────────────────────────────────────

    private fun iconBtn(
        iconRes: Int,
        enabled: Boolean = true,
        fillColor: Int? = null,
        onClick: (View) -> Unit,
    ): ImageButton {
        val size = dp(34)
        return ImageButton(this).apply {
            setImageResource(iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = if (fillColor != null) circleFillBg(fillColor) else circleOutlineBg()
            layoutParams = LinearLayout.LayoutParams(size, size)
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.4f
            isClickable = true
            elevation = dp(2).toFloat()
            setOnClickListener { if (isEnabled) onClick(this) }
        }
    }

    private fun roundBtn(
        label: String,
        enabled: Boolean = true,
        fillColor: Int? = null,
        onClick: (View) -> Unit,
    ): TextView {
        val size = dp(34)
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            background = if (fillColor != null) circleFillBg(fillColor) else circleOutlineBg()
            layoutParams = LinearLayout.LayoutParams(size, size)
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.4f
            isClickable = true
            elevation = dp(2).toFloat()
            setOnClickListener { if (isEnabled) onClick(this) }
        }
    }

    private fun circleFillBg(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(dp(1), 0x66FFFFFF)
    }

    private fun brandRoundBtn(onClick: () -> Unit): ImageView {
        val size = dp(30)
        return ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF0D1333.toInt())
                setStroke(dp(1), 0x8060A5FA.toInt())
            }
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            layoutParams = LinearLayout.LayoutParams(size, size)
            elevation = dp(3).toFloat()
            isClickable = true
            setOnClickListener { onClick() }
        }
    }
    private fun chipBtn(label: String, color: Int, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundedBg(color, 8f)
            setOnClickListener { onClick() }
        }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text.uppercase()
        setTextColor(0xFF94A3B8.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(10), 0, dp(6))
    }

    private fun numberField(value: String): EditText = EditText(this).apply {
        setText(value)
        inputType = InputType.TYPE_CLASS_NUMBER
        setTextColor(0xFFF8FAFC.toInt())
        setHintTextColor(0xFF94A3B8.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = roundedBg(0xFF0A0F2A.toInt(), 8f)
        setSingleLine()
        // Avoid OEM/dark themes forcing black text
        highlightColor = 0x403B82F6
    }

    private fun spinner(items: List<String>, selected: Int): Spinner = Spinner(this).apply {
        val adapter = object : ArrayAdapter<String>(
            this@FloatingOverlayService,
            android.R.layout.simple_spinner_item,
            items,
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = super.getView(position, convertView, parent) as TextView
                tv.setTextColor(0xFFF8FAFC.toInt())
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                tv.setPadding(dp(12), dp(12), dp(12), dp(12))
                tv.setBackgroundColor(Color.TRANSPARENT)
                return tv
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = super.getDropDownView(position, convertView, parent) as TextView
                tv.setTextColor(0xFFF8FAFC.toInt())
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                tv.setPadding(dp(14), dp(12), dp(14), dp(12))
                tv.setBackgroundColor(0xFF0D1333.toInt())
                return tv
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        this.adapter = adapter
        setSelection(selected.coerceIn(0, items.lastIndex))
        setPadding(0, dp(2), 0, dp(2))
        background = roundedBg(0xFF0A0F2A.toInt(), 8f)
        setPopupBackgroundDrawable(roundedBg(0xFF0D1333.toInt(), 10f))
    }

    private fun labeledBlock(title: String, child: View): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title))
            addView(child)
        }

    private fun headerRow(title: String, onClose: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                TextView(this@FloatingOverlayService).apply {
                    text = title
                    setTextColor(0xFFF8FAFC.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                },
            )
            addView(
                TextView(this@FloatingOverlayService).apply {
                    text = "×"
                    gravity = Gravity.CENTER
                    setTextColor(0xFF94A3B8.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    setPadding(dp(8), dp(4), dp(4), dp(4))
                    setOnClickListener { onClose() }
                },
            )
        }

    private fun simpleSelect(onSelect: () -> Unit): android.widget.AdapterView.OnItemSelectedListener =
        object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                (view as? TextView)?.setTextColor(0xFFF8FAFC.toInt())
                onSelect()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPillDrag(view: View) {
        view.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            var dragging = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                // Don't steal button taps
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val child = (v as? ViewGroup)?.let { findChildAt(it, event.x, event.y) }
                    if (child != null && child.isClickable && child !== v) return false
                }
                val params = barParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startX = params.x
                        startY = params.y
                        dragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (!dragging && (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8)) {
                            dragging = true
                        }
                        if (dragging) {
                            params.x = (startX + dx).toInt()
                            params.y = (startY + dy).toInt()
                            wm().updateViewLayout(v, params)
                            barX = params.x
                            barY = params.y
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (!dragging) v.performClick()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun findChildAt(group: ViewGroup, x: Float, y: Float): View? {
        for (i in group.childCount - 1 downTo 0) {
            val child = group.getChildAt(i)
            if (x >= child.left && x <= child.right && y >= child.top && y <= child.bottom) {
                if (child is ViewGroup) {
                    val nested = findChildAt(child, x - child.left, y - child.top)
                    if (nested != null) return nested
                }
                return child
            }
        }
        return null
    }

    private fun pillBg(): GradientDrawable = GradientDrawable().apply {
        setColor(0xF20F172A.toInt())
        cornerRadius = dp(999).toFloat()
        setStroke(dp(1), 0x8060A5FA.toInt())
    }

    private fun settingsPanelBg(): GradientDrawable = GradientDrawable().apply {
        colors = intArrayOf(0xF20D1333.toInt(), 0xF2151B3D.toInt())
        orientation = GradientDrawable.Orientation.TL_BR
        cornerRadius = dp(14).toFloat()
        setStroke(dp(2), 0x733B82F6.toInt())
    }

    private fun circleOutlineBg(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0x4D3B82F6.toInt())
        setStroke(dp(1), 0x8060A5FA.toInt())
    }

    private fun circleStrokeBg(fill: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            setStroke(dp(2), stroke)
        }

    private fun roundedBg(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
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
        gravity: Int,
        x: Int = 0,
        y: Int = 0,
        touchable: Boolean,
        focusable: Boolean = false,
    ): WindowManager.LayoutParams {
        // Prefer accessibility overlay type — avoids OEM "displaying over other apps" blocking dialogs
        val type = if (MtAccessibilityService.instance != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return WindowManager.LayoutParams(width, height, type, flags, PixelFormat.TRANSLUCENT).apply {
            this.gravity = gravity
            this.x = x
            this.y = y
        }
    }

    /**
     * Window manager that owns the overlay. Must use AccessibilityService WM
     * when using TYPE_ACCESSIBILITY_OVERLAY.
     */
    private fun wm(): WindowManager {
        val acc = MtAccessibilityService.instance
        return if (acc != null) {
            acc.getSystemService(WINDOW_SERVICE) as WindowManager
        } else {
            appWindowManager
        }
    }

    /** Kept for compatibility; float bar must stay visible during automation. */
    fun setHiddenForGestureClick(hidden: Boolean) {
        // Intentionally no-op: hiding the pill on every click made it blink and untappable.
        hiddenForClick = false
        floatBar?.visibility = View.VISIBLE
        // Only markers may be hidden if requested — never the control pill itself.
        if (!hidden) {
            markerViews.forEach { it.visibility = View.VISIBLE }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun removeFloatBarOnly() {
        floatBar?.let { runCatching { wm().removeView(it) } }
        floatBar = null
        runningStrip = null
        playBtn = null
        pickBtn = null
        brandBtn = null
        topSection = null
        bottomSection = null
    }

    private fun removePicker() {
        zonePreview = null
        pickerOverlay?.let { runCatching { wm().removeView(it) } }
        pickerOverlay = null
        AutomationHub.setPickingTarget(false)
    }

    private fun clearMarkers() {
        markerViews.forEach { runCatching { wm().removeView(it) } }
        markerViews.clear()
    }

    private fun removeAllOverlays() {
        removeFloatBarOnly()
        hideSettingsPanel()
        removePicker()
        clearMarkers()
    }

    companion object {
        const val ACTION_DISMISS = "net.mtautoclicker.android.OVERLAY_DISMISS"

        @Volatile
        var instance: FloatingOverlayService? = null
            private set

        fun show(context: Context) {
            context.startService(Intent(context, FloatingOverlayService::class.java))
        }

        fun dismiss(context: Context) {
            context.stopService(Intent(context, FloatingOverlayService::class.java))
        }
    }
}
