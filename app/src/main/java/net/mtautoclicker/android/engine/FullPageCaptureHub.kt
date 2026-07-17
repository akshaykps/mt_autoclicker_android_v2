package net.mtautoclicker.android.engine

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class FullPageCapturePhase {
    IDLE,
    READY,
    CAPTURING,
    SAVING,
    DONE,
    ERROR,
}

data class FullPageCaptureSnapshot(
    val phase: FullPageCapturePhase = FullPageCapturePhase.IDLE,
    val browserPackage: String? = null,
    val message: String? = null,
    val frameCount: Int = 0,
    val lastSavedPath: String? = null,
)

/**
 * Holds MediaProjection consent + UI state for full-page screenshot sessions.
 */
object FullPageCaptureHub {
    private val _snapshot = MutableStateFlow(FullPageCaptureSnapshot())
    val snapshot: StateFlow<FullPageCaptureSnapshot> = _snapshot.asStateFlow()

    @Volatile
    var projectionResultCode: Int = 0
        private set

    @Volatile
    var projectionData: Intent? = null
        private set

    @Volatile
    var pendingBrowserPackage: String? = null

    /** Ignore main-app auto-dismiss briefly after starting a capture session. */
    @Volatile
    private var suppressMainAppDismissUntilMs: Long = 0

    fun setProjectionConsent(resultCode: Int, data: Intent) {
        projectionResultCode = resultCode
        projectionData = Intent(data)
    }

    fun clearProjectionConsent() {
        projectionResultCode = 0
        projectionData = null
    }

    fun armReady(browserPackage: String) {
        pendingBrowserPackage = browserPackage
        suppressMainAppDismissUntilMs = SystemClock.elapsedRealtime() + 2_500L
        _snapshot.value = FullPageCaptureSnapshot(
            phase = FullPageCapturePhase.READY,
            browserPackage = browserPackage,
            message = "Open a scrollable screen, then tap Snapshot",
        )
    }

    fun shouldAutoDismissOnMainApp(): Boolean {
        val phase = _snapshot.value.phase
        if (phase == FullPageCapturePhase.IDLE) return false
        if (SystemClock.elapsedRealtime() < suppressMainAppDismissUntilMs) return false
        // Keep session alive while actively capturing/saving.
        if (phase == FullPageCapturePhase.CAPTURING || phase == FullPageCapturePhase.SAVING) return false
        return true
    }

    fun setCapturing(frameCount: Int = 0) {
        _snapshot.update {
            it.copy(
                phase = FullPageCapturePhase.CAPTURING,
                frameCount = frameCount,
                message = "Capturing page…",
            )
        }
    }

    fun setFrameCount(count: Int) {
        _snapshot.update { it.copy(frameCount = count, message = "Capturing… $count frames") }
    }

    fun setSaving() {
        _snapshot.update { it.copy(phase = FullPageCapturePhase.SAVING, message = "Saving image…") }
    }

    fun setDone(path: String) {
        _snapshot.update {
            it.copy(
                phase = FullPageCapturePhase.DONE,
                lastSavedPath = path,
                message = "Saved to Pictures/MT Auto Clicker",
            )
        }
    }

    fun setError(message: String) {
        _snapshot.update {
            it.copy(phase = FullPageCapturePhase.ERROR, message = message)
        }
    }

    fun resetToReady() {
        val browser = pendingBrowserPackage ?: _snapshot.value.browserPackage
        val lastPath = _snapshot.value.lastSavedPath
        _snapshot.value = FullPageCaptureSnapshot(
            phase = FullPageCapturePhase.READY,
            browserPackage = browser,
            message = if (lastPath != null) {
                "Saved! Tap Snapshot again for another page"
            } else {
                "Open a tab, then tap Snapshot"
            },
            lastSavedPath = lastPath,
        )
    }

    fun reset() {
        clearProjectionConsent()
        pendingBrowserPackage = null
        _snapshot.value = FullPageCaptureSnapshot()
    }

    /** Debug helper — not used in production path. */
    fun lastPreviewBitmap(): Bitmap? = null
}
