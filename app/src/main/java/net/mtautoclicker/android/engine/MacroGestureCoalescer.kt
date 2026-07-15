package net.mtautoclicker.android.engine

import android.util.DisplayMetrics
import net.mtautoclicker.android.data.MacroPoint
import net.mtautoclicker.android.data.MacroStep
import net.mtautoclicker.android.data.MacroStepKind
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Turns a raw touch stroke into a compact MacroStep (tap / long-press / swipe / path).
 */
object MacroGestureCoalescer {
    private const val LONG_PRESS_MS = 500L
    private const val MIN_MOVE_PX = 28f
    private const val SAMPLE_MIN_DIST = 6f

    fun coalesce(
        points: List<MacroPoint>,
        durationMs: Long,
        density: Float,
    ): MacroStep? {
        if (points.isEmpty()) return null
        val filtered = downsample(points, SAMPLE_MIN_DIST * density)
        val start = filtered.first()
        val end = filtered.last()
        val travel = hypot(end.x - start.x, end.y - start.y)
        val minMove = MIN_MOVE_PX * density

        return when {
            travel < minMove && durationMs >= LONG_PRESS_MS -> MacroStep(
                kind = MacroStepKind.LONG_PRESS,
                delayMs = 0L,
                x = start.x,
                y = start.y,
                durationMs = durationMs.coerceIn(500L, 3000L),
            )
            travel < minMove -> MacroStep(
                kind = MacroStepKind.TAP,
                delayMs = 0L,
                x = start.x,
                y = start.y,
                durationMs = durationMs.coerceIn(28L, 55L),
            )
            filtered.size <= 3 || isMostlyLinear(filtered) -> MacroStep(
                kind = MacroStepKind.SWIPE,
                delayMs = 0L,
                x = start.x,
                y = start.y,
                x2 = end.x,
                y2 = end.y,
                durationMs = durationMs.coerceIn(80L, 2500L),
            )
            else -> MacroStep(
                kind = MacroStepKind.PATH,
                delayMs = 0L,
                points = filtered,
                durationMs = durationMs.coerceIn(80L, 3500L),
            )
        }
    }

    private fun downsample(points: List<MacroPoint>, minDist: Float): List<MacroPoint> {
        if (points.size <= 2) return points
        val out = mutableListOf(points.first())
        for (i in 1 until points.lastIndex) {
            val prev = out.last()
            val cur = points[i]
            if (hypot(cur.x - prev.x, cur.y - prev.y) >= minDist) {
                out += cur
            }
        }
        out += points.last()
        return out
    }

    private fun isMostlyLinear(points: List<MacroPoint>): Boolean {
        if (points.size < 3) return true
        val a = points.first()
        val b = points.last()
        val ab = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(1f)
        var maxDev = 0f
        for (p in points) {
            val dev = pointLineDistance(p, a, b)
            maxDev = max(maxDev, dev)
        }
        return maxDev / ab < 0.18f
    }

    private fun pointLineDistance(p: MacroPoint, a: MacroPoint, b: MacroPoint): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val apx = p.x - a.x
        val apy = p.y - a.y
        val ab2 = abx * abx + aby * aby
        if (ab2 < 1f) return hypot(apx, apy)
        val t = ((apx * abx + apy * aby) / ab2).coerceIn(0f, 1f)
        val cx = a.x + t * abx
        val cy = a.y + t * aby
        return hypot(p.x - cx, p.y - cy)
    }

    fun density(metrics: DisplayMetrics): Float = metrics.density
}
