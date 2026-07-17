package net.mtautoclicker.android.engine

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.mtautoclicker.android.data.AutoRefreshConfig
import net.mtautoclicker.android.data.AutomationRunState

data class AutoRefreshSnapshot(
    val runState: AutomationRunState = AutomationRunState.IDLE,
    val config: AutoRefreshConfig = AutoRefreshConfig(),
    val refreshCount: Int = 0,
    val elapsedMs: Long = 0,
    val message: String? = null,
)

object AutoRefreshHub {
    private val _snapshot = MutableStateFlow(AutoRefreshSnapshot())
    val snapshot: StateFlow<AutoRefreshSnapshot> = _snapshot.asStateFlow()

    @Volatile
    private var suppressMainAppDismissUntilMs: Long = 0

    fun arm(config: AutoRefreshConfig) {
        suppressMainAppDismissUntilMs = SystemClock.elapsedRealtime() + 2_500L
        _snapshot.value = AutoRefreshSnapshot(
            runState = AutomationRunState.ARMED,
            config = config,
            message = "Tap Refresh on the float bar to start",
        )
    }

    fun shouldAutoDismissOnMainApp(): Boolean {
        val phase = _snapshot.value.runState
        if (phase == AutomationRunState.IDLE) return false
        if (SystemClock.elapsedRealtime() < suppressMainAppDismissUntilMs) return false
        if (phase == AutomationRunState.RUNNING || phase == AutomationRunState.PAUSED) return false
        return true
    }

    fun setRunning(message: String? = "Refreshing…") {
        _snapshot.update {
            it.copy(runState = AutomationRunState.RUNNING, message = message)
        }
    }

    fun setPaused(message: String? = "Paused") {
        _snapshot.update {
            it.copy(runState = AutomationRunState.PAUSED, message = message)
        }
    }

    fun setArmed(message: String? = "Ready") {
        _snapshot.update {
            it.copy(runState = AutomationRunState.ARMED, message = message)
        }
    }

    fun updateProgress(refreshCount: Int, elapsedMs: Long) {
        _snapshot.update {
            it.copy(refreshCount = refreshCount, elapsedMs = elapsedMs)
        }
    }

    fun reset() {
        _snapshot.value = AutoRefreshSnapshot()
    }
}
