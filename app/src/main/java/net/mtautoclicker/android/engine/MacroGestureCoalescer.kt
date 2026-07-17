package net.mtautoclicker.android.engine

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowManager
import net.mtautoclicker.android.data.MacroPoint
import net.mtautoclicker.android.data.MacroStep
import net.mtautoclicker.android.data.MacroStepKind
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** Which physical edge a dedicated capture strip covers. */
enum class NavEdge { LEFT, RIGHT, TOP, BOTTOM }

/**
 * Exact display bounds + system-gesture edge bands used for nav classification
 * and the on-screen capture guide.
 */
data class NavEdgeBands(
    val screenW: Int,
    val screenH: Int,
    /** Pixels from left edge that count as Back-start. */
    val leftPx: Int,
    val rightPx: Int,
    /** Pixels from top that count as Notification-start. */
    val topPx: Int,
    /** Pixels from bottom that count as Home/Recents-start. */
    val bottomPx: Int,
) {
    companion object {
        /**
         * Prefer live [WindowInsets] system-gesture sizes (device-exact).
         * Fallback matches common gesture-nav insets (e.g. OnePlus 1080×2400 → 90/96/107).
         */
        fun resolve(context: Context, wm: WindowManager): NavEdgeBands {
            val density = context.resources.displayMetrics.density
            val (w, h) = displaySize(wm, context.resources.displayMetrics)

            var left = 0
            var right = 0
            var top = 0
            var bottom = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Prefer live insets from the display (device-exact).
                // OnePlus Nord CE4 Lite dumpsys example:
                //   systemGestures L/R = 90, mandatory top = 143, bottom = 96
                val insets = runCatching {
                    wm.maximumWindowMetrics.windowInsets
                }.getOrNull()
                if (insets != null) {
                    val side = insets.getInsetsIgnoringVisibility(
                        WindowInsets.Type.systemGestures(),
                    )
                    val mandatory = insets.getInsetsIgnoringVisibility(
                        WindowInsets.Type.mandatorySystemGestures(),
                    )
                    val status = insets.getInsetsIgnoringVisibility(
                        WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout(),
                    )
                    left = max(side.left, mandatory.left)
                    right = max(side.right, mandatory.right)
                    // Top shade / status: use mandatory (often taller than status bar alone).
                    top = maxOf(mandatory.top, status.top, side.top)
                    bottom = max(side.bottom, mandatory.bottom)
                }
            }

            // Fallbacks when insets are missing (some overlay contexts).
            // Match common ColorOS / OxygenOS gesture-nav bands @3x density.
            if (left <= 0) left = (30f * density).toInt().coerceAtLeast(90)
            if (right <= 0) right = (30f * density).toInt().coerceAtLeast(90)
            if (bottom <= 0) bottom = (32f * density).toInt().coerceAtLeast(96)
            if (top <= 0) top = (48f * density).toInt().coerceAtLeast(143)

            // Small inward pad: first ACTION_DOWN is often a few px inside the band.
            val pad = (4f * density).toInt().coerceIn(6, 14)
            return NavEdgeBands(
                screenW = w,
                screenH = h,
                leftPx = (left + pad).coerceAtMost(w / 5),
                rightPx = (right + pad).coerceAtMost(w / 5),
                topPx = (top + pad).coerceAtMost(h / 6),
                bottomPx = (bottom + pad).coerceAtMost(h / 6),
            )
        }

        fun displaySize(wm: WindowManager, metrics: DisplayMetrics): Pair<Int, Int> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val b = wm.maximumWindowMetrics.bounds
                return b.width() to b.height()
            }
            @Suppress("DEPRECATION")
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            if (dm.widthPixels > 0 && dm.heightPixels > 0) {
                return dm.widthPixels to dm.heightPixels
            }
            return metrics.widthPixels to metrics.heightPixels
        }
    }
}

/**
 * Turns a raw touch stroke into a compact MacroStep (tap / long-press / swipe / path),
 * or maps *edge-started* strokes to system global actions.
 */
object MacroGestureCoalescer {
    private const val LONG_PRESS_MS = 500L
    private const val MIN_MOVE_PX = 28f
    private const val SAMPLE_MIN_DIST = 6f
    private const val RECENTS_HOLD_MS = 320L

    /** Guide drawing fallbacks (overridden by live [NavEdgeBands] when available). */
    const val EDGE_ZONE_DP = 30f
    const val BOTTOM_ZONE_DP = 32f
    const val TOP_ZONE_DP = 36f

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

    fun isGestureNavigation(context: Context): Boolean {
        val secure = runCatching {
            Settings.Secure.getInt(context.contentResolver, "navigation_mode", -1)
        }.getOrDefault(-1)
        if (secure == 2) return true
        if (secure == 0 || secure == 1) return false
        val id = context.resources.getIdentifier("config_navBarInteractionMode", "integer", "android")
        if (id > 0) {
            return runCatching { context.resources.getInteger(id) == 2 }.getOrDefault(false)
        }
        return false
    }

    /**
     * Map edge-started strokes to system global actions.
     *
     * OEMs often deliver the first touch a bit *inside* the true system-gesture
     * inset, so we expand bands slightly and also check the first few points.
     * Failed nav matches fall through (return null) so [coalesce] can still
     * record a normal swipe/tap — never drop the stroke.
     */
    fun classifySystemNav(
        points: List<MacroPoint>,
        durationMs: Long,
        density: Float,
        edges: NavEdgeBands,
    ): MacroStep? {
        if (points.size < 2 || edges.screenW <= 0 || edges.screenH <= 0) return null
        val start = points.first()
        val end = points.last()
        val dx = end.x - start.x
        val dy = end.y - start.y
        // ~36dp travel; 48dp was too strict on quick edge flicks @3x density.
        val minTravel = 36f * density
        val dominant = 1.05f
        // Pad past system insets — first ACTION_DOWN is often inset by the OEM.
        val pad = (18f * density).toInt().coerceIn(24, 64)
        val leftBand = (edges.leftPx + pad).coerceAtMost(edges.screenW / 5)
        val rightBand = (edges.rightPx + pad).coerceAtMost(edges.screenW / 5)
        val topBand = (edges.topPx + pad).coerceAtMost(edges.screenH / 6)
        val bottomBand = (edges.bottomPx + pad).coerceAtMost(edges.screenH / 6)

        val early = points.take(3)
        val nearTop = early.any { it.y <= topBand }
        val nearLeft = early.any { it.x <= leftBand }
        val nearRight = early.any { it.x >= edges.screenW - rightBand }
        val nearBottom = early.any { it.y >= edges.screenH - bottomBand }

        // --- Notifications: start near top, swipe down ---
        if (nearTop) {
            val mostlyVertical = abs(dy) >= abs(dx) * dominant
            if (dy >= minTravel && mostlyVertical) {
                return MacroStep(
                    kind = MacroStepKind.GLOBAL_NOTIFICATIONS,
                    delayMs = 0L,
                    durationMs = 50L,
                )
            }
        }

        // --- Back: start near left/right, swipe inward ---
        if (nearLeft || nearRight) {
            val mostlyHorizontal = abs(dx) >= abs(dy) * dominant
            val inward = when {
                nearLeft && !nearRight -> dx >= minTravel
                nearRight && !nearLeft -> dx <= -minTravel
                nearLeft && nearRight -> abs(dx) >= minTravel
                else -> false
            }
            if (inward && mostlyHorizontal) {
                return MacroStep(
                    kind = MacroStepKind.GLOBAL_BACK,
                    delayMs = 0L,
                    durationMs = 50L,
                )
            }
        }

        // --- Home / Recents: start near bottom, swipe up ---
        if (nearBottom) {
            val mostlyVertical = abs(dy) >= abs(dx) * dominant
            if ((-dy) >= minTravel && mostlyVertical) {
                val kind = if (durationMs >= RECENTS_HOLD_MS) {
                    MacroStepKind.GLOBAL_RECENTS
                } else {
                    MacroStepKind.GLOBAL_HOME
                }
                return MacroStep(kind = kind, delayMs = 0L, durationMs = 50L)
            }
        }

        return null
    }

    /**
     * Classify a stroke that was captured on a dedicated edge strip.
     * Start-position checks are skipped — the strip identity is the edge.
     */
    fun classifyKnownEdge(
        edge: NavEdge,
        points: List<MacroPoint>,
        durationMs: Long,
        density: Float,
    ): MacroStep? {
        if (points.size < 2) return null
        val start = points.first()
        val end = points.last()
        val dx = end.x - start.x
        val dy = end.y - start.y
        val minTravel = 28f * density
        val dominant = 0.85f
        return when (edge) {
            NavEdge.LEFT -> {
                if (dx >= minTravel && abs(dx) >= abs(dy) * dominant) {
                    MacroStep(kind = MacroStepKind.GLOBAL_BACK, delayMs = 0L, durationMs = 50L)
                } else null
            }
            NavEdge.RIGHT -> {
                if (dx <= -minTravel && abs(dx) >= abs(dy) * dominant) {
                    MacroStep(kind = MacroStepKind.GLOBAL_BACK, delayMs = 0L, durationMs = 50L)
                } else null
            }
            NavEdge.TOP -> {
                if (dy >= minTravel && abs(dy) >= abs(dx) * dominant) {
                    MacroStep(
                        kind = MacroStepKind.GLOBAL_NOTIFICATIONS,
                        delayMs = 0L,
                        durationMs = 50L,
                    )
                } else null
            }
            NavEdge.BOTTOM -> {
                if ((-dy) >= minTravel && abs(dy) >= abs(dx) * dominant) {
                    val kind = if (durationMs >= RECENTS_HOLD_MS) {
                        MacroStepKind.GLOBAL_RECENTS
                    } else {
                        MacroStepKind.GLOBAL_HOME
                    }
                    MacroStep(kind = kind, delayMs = 0L, durationMs = 50L)
                } else null
            }
        }
    }

    /** @deprecated Prefer [classifySystemNav] with [NavEdgeBands]. */
    fun classifySystemNav(
        points: List<MacroPoint>,
        durationMs: Long,
        density: Float,
        screenW: Int,
        screenH: Int,
    ): MacroStep? = classifySystemNav(
        points,
        durationMs,
        density,
        NavEdgeBands(
            screenW = screenW,
            screenH = screenH,
            leftPx = (EDGE_ZONE_DP * density).toInt(),
            rightPx = (EDGE_ZONE_DP * density).toInt(),
            topPx = (TOP_ZONE_DP * density).toInt(),
            bottomPx = (BOTTOM_ZONE_DP * density).toInt(),
        ),
    )

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
