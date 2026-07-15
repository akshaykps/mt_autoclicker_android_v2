package net.mtautoclicker.android.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.mtautoclicker.android.data.MouseButton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random

class MtAccessibilityService : AccessibilityService() {

    private val gestureMutex = Mutex()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
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

        // Do NOT hide the float bar during clicks — that caused rapid blinking at short
        // intervals and made Pause/Stop impossible to tap.
        return when (button) {
            MouseButton.LEFT -> dispatchTap(clickX, clickY)
            MouseButton.RIGHT -> dispatchLongPress(clickX, clickY, 600L)
            MouseButton.MIDDLE -> dispatchTap(clickX, clickY, durationMs = 80L)
        }
    }

    private suspend fun dispatchTap(x: Float, y: Float, durationMs: Long = 50L): Boolean =
        gestureMutex.withLock {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGestureAwait(gesture)
        }

    private suspend fun dispatchLongPress(x: Float, y: Float, durationMs: Long): Boolean =
        gestureMutex.withLock {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGestureAwait(gesture)
        }

    private suspend fun dispatchGestureAwait(gesture: GestureDescription): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return suspendCoroutine { cont ->
            var resumed = false
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
            val dispatched = dispatchGesture(gesture, callback, null)
            if (!dispatched && !resumed) {
                resumed = true
                cont.resume(false)
            }
        }
    }

    companion object {
        @Volatile
        var instance: MtAccessibilityService? = null

        fun isEnabled(): Boolean = instance != null
    }
}
