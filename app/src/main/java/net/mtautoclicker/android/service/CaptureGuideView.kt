package net.mtautoclicker.android.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.View
import net.mtautoclicker.android.data.MacroPoint
import net.mtautoclicker.android.data.MacroStep
import net.mtautoclicker.android.data.MacroStepKind
import net.mtautoclicker.android.engine.MacroGestureCoalescer
import net.mtautoclicker.android.engine.NavEdgeBands

/**
 * Visual guide while macro recording: shows exact system-gesture edge bands
 * and flashes markers where taps / swipes were captured.
 */
class CaptureGuideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    private var edges: NavEdgeBands = NavEdgeBands(
        screenW = resources.displayMetrics.widthPixels,
        screenH = resources.displayMetrics.heightPixels,
        leftPx = (MacroGestureCoalescer.EDGE_ZONE_DP * density).toInt(),
        rightPx = (MacroGestureCoalescer.EDGE_ZONE_DP * density).toInt(),
        topPx = (MacroGestureCoalescer.TOP_ZONE_DP * density).toInt(),
        bottomPx = (MacroGestureCoalescer.BOTTOM_ZONE_DP * density).toInt(),
    )

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x18F43F5E
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = 0x88F43F5E.toInt()
    }
    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF8FAFC.toInt()
        textSize = 10f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCE2E8F0.toInt()
        textSize = 9f * density
        textAlign = Paint.Align.CENTER
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = 0xFF22D3EE.toInt()
    }
    private val markerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x5522D3EE
    }
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = 0xFFA78BFA.toInt()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fingerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xAAF43F5E.toInt()
    }

    private data class TouchMarker(
        val points: List<MacroPoint>,
        val kind: MacroStepKind?,
        val createdAt: Long,
    )

    private val markers = ArrayDeque<TouchMarker>()
    private val tmpPath = Path()
    private val tmpRect = RectF()
    private var fingerX = -1f
    private var fingerY = -1f

    init {
        isClickable = true
        isFocusable = false
    }

    fun setEdgeBands(bands: NavEdgeBands) {
        edges = bands
        invalidate()
        refreshGestureExclusion()
    }

    fun showFingerDown(x: Float, y: Float) {
        fingerX = x
        fingerY = y
        invalidate()
    }

    fun clearFinger() {
        fingerX = -1f
        fingerY = -1f
        invalidate()
    }

    fun showCapture(step: MacroStep, points: List<MacroPoint>) {
        clearFinger()
        val pts = when {
            points.isNotEmpty() -> points
            step.points != null -> step.points.orEmpty()
            step.x != null && step.y != null && step.x2 != null && step.y2 != null ->
                listOf(MacroPoint(step.x, step.y), MacroPoint(step.x2, step.y2))
            step.x != null && step.y != null -> listOf(MacroPoint(step.x, step.y))
            else -> emptyList()
        }
        markers.addLast(TouchMarker(pts, step.kind, System.currentTimeMillis()))
        while (markers.size > 6) markers.removeFirst()
        invalidate()
        postDelayed({ pruneAndRedraw() }, 1200L)
    }

    fun refreshGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (width <= 0 || height <= 0) return
        systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
    }

    private fun pruneAndRedraw() {
        val now = System.currentTimeMillis()
        while (markers.isNotEmpty() && now - markers.first().createdAt > 1400L) {
            markers.removeFirst()
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        refreshGestureExclusion()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.drawRect(0f, 0f, w, h, fillPaint)
        tmpRect.set(1f * density, 1f * density, w - 1f * density, h - 1f * density)
        canvas.drawRect(tmpRect, borderPaint)

        val top = edges.topPx.toFloat().coerceAtMost(h)
        val bottom = edges.bottomPx.toFloat().coerceAtMost(h)
        val left = edges.leftPx.toFloat().coerceAtMost(w)
        val right = edges.rightPx.toFloat().coerceAtMost(w)
        val bottomTop = (h - bottom).coerceAtLeast(0f)

        zonePaint.color = 0x66FBBF24
        canvas.drawRect(0f, 0f, w, top, zonePaint)
        canvas.drawText("NOTIF ↓", w / 2f, top * 0.62f, labelPaint)

        zonePaint.color = 0x6634D399
        canvas.drawRect(0f, bottomTop, w, h, zonePaint)
        canvas.drawText("HOME ↑ · RECENTS ↑hold", w / 2f, bottomTop + bottom * 0.55f, labelPaint)

        zonePaint.color = 0x6660A5FA
        canvas.drawRect(0f, 0f, left, h, zonePaint)
        canvas.drawRect(w - right, 0f, w, h, zonePaint)
        drawVerticalLabel(canvas, "BACK →", left * 0.55f, h / 2f)
        drawVerticalLabel(canvas, "← BACK", w - right * 0.55f, h / 2f)

        // Exact size readout so we can verify against the device.
        metaPaint.alpha = 200
        canvas.drawText(
            "${edges.screenW}×${edges.screenH} · L${edges.leftPx} R${edges.rightPx} T${edges.topPx} B${edges.bottomPx}",
            w / 2f,
            h / 2f,
            metaPaint,
        )

        if (fingerX >= 0f && fingerY >= 0f) {
            canvas.drawCircle(fingerX, fingerY, 22f * density, fingerPaint)
            markerPaint.alpha = 255
            canvas.drawCircle(fingerX, fingerY, 22f * density, markerPaint)
        }

        val now = System.currentTimeMillis()
        for (m in markers) {
            val age = (now - m.createdAt).coerceAtLeast(0L)
            val alpha = (255 * (1f - age / 1400f)).toInt().coerceIn(0, 255)
            markerPaint.alpha = alpha
            markerFill.alpha = (alpha * 0.45f).toInt()
            pathPaint.alpha = alpha
            labelPaint.alpha = alpha
            val kind = m.kind

            when {
                kind != null && kind in GLOBAL_KINDS -> {
                    val p = m.points.firstOrNull() ?: MacroPoint(w / 2f, h / 2f)
                    canvas.drawCircle(p.x, p.y, 22f * density, markerFill)
                    canvas.drawCircle(p.x, p.y, 22f * density, markerPaint)
                    canvas.drawText(kind.name.removePrefix("GLOBAL_"), p.x, p.y - 28f * density, labelPaint)
                }
                m.points.size >= 2 -> {
                    tmpPath.reset()
                    tmpPath.moveTo(m.points.first().x, m.points.first().y)
                    for (i in 1 until m.points.size) {
                        tmpPath.lineTo(m.points[i].x, m.points[i].y)
                    }
                    canvas.drawPath(tmpPath, pathPaint)
                    val start = m.points.first()
                    val end = m.points.last()
                    canvas.drawCircle(start.x, start.y, 10f * density, markerFill)
                    canvas.drawCircle(end.x, end.y, 14f * density, markerPaint)
                }
                m.points.isNotEmpty() -> {
                    val p = m.points.first()
                    canvas.drawCircle(p.x, p.y, 18f * density, markerFill)
                    canvas.drawCircle(p.x, p.y, 18f * density, markerPaint)
                    canvas.drawText("TAP", p.x, p.y - 24f * density, labelPaint)
                }
            }
        }
        labelPaint.alpha = 255
        markerPaint.alpha = 255
    }

    private fun drawVerticalLabel(canvas: Canvas, text: String, x: Float, y: Float) {
        canvas.save()
        canvas.rotate(-90f, x, y)
        canvas.drawText(text, x, y, labelPaint)
        canvas.restore()
    }

    companion object {
        private val GLOBAL_KINDS = setOf(
            MacroStepKind.GLOBAL_BACK,
            MacroStepKind.GLOBAL_HOME,
            MacroStepKind.GLOBAL_RECENTS,
            MacroStepKind.GLOBAL_NOTIFICATIONS,
        )
    }
}
