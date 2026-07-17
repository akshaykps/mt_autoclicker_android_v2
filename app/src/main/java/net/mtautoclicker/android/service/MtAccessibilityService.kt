package net.mtautoclicker.android.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import net.mtautoclicker.android.data.MacroOverlayMode
import net.mtautoclicker.android.data.MacroPoint
import net.mtautoclicker.android.data.MacroStep
import net.mtautoclicker.android.data.MacroStepKind
import net.mtautoclicker.android.data.MouseButton
import net.mtautoclicker.android.engine.MacroHub
import kotlin.coroutines.resume
import kotlin.random.Random

class MtAccessibilityService : AccessibilityService() {

    private val gestureMutex = Mutex()

    @Volatile
    var onWindowsOrFocusChanged: (() -> Unit)? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            -> onWindowsOrFocusChanged?.invoke()
            AccessibilityEvent.TYPE_VIEW_CLICKED -> maybeRecordNavFromClick(e)
        }
    }

    /**
     * Soft nav bar buttons (3-button mode) often never deliver KEYCODE_*.
     * Capture them from SystemUI click events instead.
     */
    private fun maybeRecordNavFromClick(event: AccessibilityEvent) {
        if (MacroHub.snapshot.value.mode != MacroOverlayMode.RECORDING) return
        val kind = resolveNavKind(event) ?: return
        MacroHub.appendStep(
            MacroStep(kind = kind, delayMs = 0L, durationMs = 50L),
            dedupeWindowMs = 500L,
        )
    }

    private fun resolveNavKind(event: AccessibilityEvent): MacroStepKind? {
        val viewId = runCatching { event.source?.viewIdResourceName }.getOrNull().orEmpty().lowercase()
        val pkg = event.packageName?.toString().orEmpty().lowercase()
        val desc = buildString {
            append(event.contentDescription ?: "")
            append(' ')
            event.text?.forEach { append(it); append(' ') }
            append(event.className ?: "")
            append(' ')
            append(viewId)
        }.lowercase()

        val looksLikeSystemUi =
            pkg.contains("systemui") ||
                pkg.contains("navigationbar") ||
                viewId.contains("systemui") ||
                viewId.contains("navigation")

        // Prefer view-id matches (most reliable on AOSP / ColorOS).
        when {
            viewId.endsWith("/back") || viewId.contains(":id/back") -> return MacroStepKind.GLOBAL_BACK
            viewId.endsWith("/home") || viewId.contains(":id/home") -> return MacroStepKind.GLOBAL_HOME
            viewId.contains("recent") || viewId.contains("overview") ||
                viewId.contains("app_switch") -> return MacroStepKind.GLOBAL_RECENTS
        }

        if (!looksLikeSystemUi && viewId.isBlank()) {
            // Still allow content-description matches from SystemUI-like labels.
        }

        return when {
            desc.contains("recent") || desc.contains("overview") ||
                desc.contains("app switch") || desc.contains("任务") -> MacroStepKind.GLOBAL_RECENTS
            Regex("""\bhome\b|主屏幕|桌面""").containsMatchIn(desc) -> MacroStepKind.GLOBAL_HOME
            Regex("""\bback\b|返回""").containsMatchIn(desc) -> MacroStepKind.GLOBAL_BACK
            else -> null
        }
    }

    /**
     * Capture Back / Home / Recents while macro recording.
     * Must not consume the event (return false) so the system still handles navigation.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (MacroHub.snapshot.value.mode != MacroOverlayMode.RECORDING) return false
        val kind = when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> MacroStepKind.GLOBAL_BACK
            KeyEvent.KEYCODE_HOME -> MacroStepKind.GLOBAL_HOME
            KeyEvent.KEYCODE_APP_SWITCH -> MacroStepKind.GLOBAL_RECENTS
            else -> return false
        }
        MacroHub.appendStep(
            MacroStep(kind = kind, delayMs = 0L, durationMs = 50L),
            dedupeWindowMs = 400L,
        )
        return false
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        onWindowsOrFocusChanged = null
        super.onDestroy()
    }

    suspend fun performClick(
        x: Float,
        y: Float,
        button: MouseButton,
        randomOffsetPx: Int,
    ): Boolean {
        val jitterX = if (randomOffsetPx > 0) Random.nextInt(-randomOffsetPx, randomOffsetPx + 1) else 0
        val jitterY = if (randomOffsetPx > 0) Random.nextInt(-randomOffsetPx, randomOffsetPx + 1) else 0
        val clickX = x + jitterX
        val clickY = y + jitterY

        return when (button) {
            MouseButton.LEFT -> dispatchTap(clickX, clickY)
            MouseButton.RIGHT -> dispatchLongPress(clickX, clickY, 600L)
            MouseButton.MIDDLE -> dispatchTap(clickX, clickY, durationMs = 80L)
        }
    }

    fun performGlobalNav(kind: MacroStepKind): Boolean {
        val action = when (kind) {
            MacroStepKind.GLOBAL_BACK -> GLOBAL_ACTION_BACK
            MacroStepKind.GLOBAL_HOME -> GLOBAL_ACTION_HOME
            MacroStepKind.GLOBAL_RECENTS -> GLOBAL_ACTION_RECENTS
            MacroStepKind.GLOBAL_NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
            else -> return false
        }
        return runCatching { performGlobalAction(action) }.getOrDefault(false)
    }

    suspend fun performMacroStep(step: MacroStep): Boolean {
        return when (step.kind) {
            MacroStepKind.TAP -> {
                val x = step.x ?: return false
                val y = step.y ?: return false
                dispatchTap(x, y, step.durationMs.coerceIn(28L, 55L))
            }
            MacroStepKind.LONG_PRESS -> {
                val x = step.x ?: return false
                val y = step.y ?: return false
                dispatchLongPress(x, y, step.durationMs.coerceIn(400L, 3000L))
            }
            MacroStepKind.SWIPE -> {
                val x1 = step.x ?: return false
                val y1 = step.y ?: return false
                val x2 = step.x2 ?: return false
                val y2 = step.y2 ?: return false
                dispatchSwipe(x1, y1, x2, y2, step.durationMs.coerceIn(80L, 2500L))
            }
            MacroStepKind.PATH -> {
                val pts = step.points
                if (pts.isNullOrEmpty()) return false
                dispatchPath(pts, step.durationMs.coerceIn(80L, 3500L))
            }
            MacroStepKind.TYPE_TEXT -> setFocusedEditableText(step.text.orEmpty())
            MacroStepKind.GLOBAL_BACK,
            MacroStepKind.GLOBAL_HOME,
            MacroStepKind.GLOBAL_RECENTS,
            MacroStepKind.GLOBAL_NOTIFICATIONS,
            -> performGlobalNav(step.kind)
        }
    }

    /**
     * Live reinjection while recording. Overlay must be fully removed first
     * (OxygenOS often ignores FLAG_NOT_TOUCHABLE). Short durations + short
     * timeouts keep recording responsive; full timing is saved for playback.
     */
    suspend fun performMacroStepLive(step: MacroStep): Boolean {
        return when (step.kind) {
            MacroStepKind.TAP, MacroStepKind.LONG_PRESS -> {
                val x = step.x ?: return false
                val y = step.y ?: return false
                dispatchTap(x, y, 38L, timeoutMs = 140L)
            }
            MacroStepKind.SWIPE -> {
                val x1 = step.x ?: return false
                val y1 = step.y ?: return false
                val x2 = step.x2 ?: return false
                val y2 = step.y2 ?: return false
                dispatchSwipe(
                    x1, y1, x2, y2,
                    step.durationMs.coerceIn(80L, 200L),
                    timeoutMs = 220L,
                )
            }
            MacroStepKind.PATH -> {
                val pts = step.points
                if (pts.isNullOrEmpty()) return false
                dispatchPath(pts, step.durationMs.coerceIn(80L, 200L), timeoutMs = 220L)
            }
            MacroStepKind.TYPE_TEXT -> setFocusedEditableText(step.text.orEmpty())
            MacroStepKind.GLOBAL_BACK,
            MacroStepKind.GLOBAL_HOME,
            MacroStepKind.GLOBAL_RECENTS,
            MacroStepKind.GLOBAL_NOTIFICATIONS,
            -> performGlobalNav(step.kind)
        }
    }

    fun inputMethodBounds(): Rect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null
        return runCatching {
            windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                ?.let { win ->
                    Rect().also { win.getBoundsInScreen(it) }
                }
                ?.takeIf { it.height() > 80 && it.width() > 80 }
        }.getOrNull()
    }

    fun focusedEditableText(): String? {
        val root = rootInActiveWindow ?: return null
        var focused: AccessibilityNodeInfo? = null
        return try {
            focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            val node = focused ?: findEditable(root)
            node?.text?.toString()
        } finally {
            focused?.recycle()
            root.recycle()
        }
    }

    fun setFocusedEditableText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        var focused: AccessibilityNodeInfo? = null
        return try {
            focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            val node = focused ?: findEditable(root) ?: return false
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } finally {
            focused?.recycle()
            root.recycle()
        }
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditable(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    suspend fun dispatchTap(
        x: Float,
        y: Float,
        durationMs: Long = 50L,
        timeoutMs: Long = 3_000L,
    ): Boolean =
        gestureMutex.withLock {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGestureAwait(gesture, timeoutMs)
        }

    suspend fun dispatchLongPress(x: Float, y: Float, durationMs: Long): Boolean =
        gestureMutex.withLock {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGestureAwait(gesture, (durationMs + 800L).coerceAtMost(4_000L))
        }

    suspend fun dispatchSwipe(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long,
        timeoutMs: Long = 3_500L,
    ): Boolean = gestureMutex.withLock {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGestureAwait(gesture, timeoutMs)
    }

    suspend fun dispatchPath(
        points: List<MacroPoint>,
        durationMs: Long,
        timeoutMs: Long = 4_000L,
    ): Boolean =
        gestureMutex.withLock {
            if (points.isEmpty()) return@withLock false
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGestureAwait(gesture, timeoutMs)
        }

    private suspend fun dispatchGestureAwait(
        gesture: GestureDescription,
        timeoutMs: Long,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                var resumed = false
                val mainHandler = Handler(Looper.getMainLooper())
                val callback = object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (!resumed) {
                            resumed = true
                            cont.resume(true)
                        }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (!resumed) {
                            resumed = true
                            cont.resume(false)
                        }
                    }
                }
                mainHandler.post {
                    val dispatched = runCatching { dispatchGesture(gesture, callback, null) }.getOrDefault(false)
                    if (!dispatched && !resumed) {
                        resumed = true
                        cont.resume(false)
                    }
                }
            }
        }
        return result == true
    }

    companion object {
        @Volatile
        var instance: MtAccessibilityService? = null

        fun isEnabled(): Boolean = instance != null
    }
}
