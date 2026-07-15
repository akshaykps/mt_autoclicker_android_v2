package net.mtautoclicker.android.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.mtautoclicker.android.data.AutomationPlan
import net.mtautoclicker.android.data.AutomationRunState
import net.mtautoclicker.android.data.AutomationSnapshot
import net.mtautoclicker.android.data.ClickTarget
import net.mtautoclicker.android.data.FeatureKind
import net.mtautoclicker.android.data.MultiTargetConfig
import net.mtautoclicker.android.data.SessionStats
import net.mtautoclicker.android.data.SingleTargetConfig

object AutomationHub {
    private val _snapshot = MutableStateFlow(AutomationSnapshot())
    val snapshot: StateFlow<AutomationSnapshot> = _snapshot.asStateFlow()

    private val _sessionStats = MutableStateFlow(SessionStats())
    val sessionStats: StateFlow<SessionStats> = _sessionStats.asStateFlow()

    var activePlan: AutomationPlan? = null
        private set

    var pickingTarget: Boolean = false
        private set

    fun arm(plan: AutomationPlan) {
        activePlan = plan
        _snapshot.value = AutomationSnapshot(
            feature = plan.feature,
            runState = AutomationRunState.ARMED,
            targets = plan.targets,
            message = "Place targets from the float bar, then press Play.",
        )
    }

    fun setTargets(targets: List<ClickTarget>) {
        val plan = activePlan ?: return
        activePlan = when (plan) {
            is AutomationPlan.Single -> plan.copy(targets = targets)
            is AutomationPlan.Multi -> plan.copy(targets = targets)
        }
        _snapshot.update { it.copy(targets = targets) }
    }

    fun updateSingleConfig(config: SingleTargetConfig) {
        val plan = activePlan as? AutomationPlan.Single ?: return
        activePlan = plan.copy(config = config)
        _snapshot.update { it.copy(message = "Settings saved") }
    }

    fun updateMultiConfig(config: MultiTargetConfig) {
        val plan = activePlan as? AutomationPlan.Multi ?: return
        activePlan = plan.copy(config = config)
        _snapshot.update { it.copy(message = "Settings saved") }
    }

    fun removeLastTarget() {
        val current = _snapshot.value.targets
        if (current.isNotEmpty()) setTargets(current.dropLast(1))
    }

    fun addTarget(target: ClickTarget) {
        setTargets(_snapshot.value.targets + target)
    }

    fun removeTarget(index: Int) {
        val current = _snapshot.value.targets.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            setTargets(current)
        }
    }

    fun clearTargets() {
        setTargets(emptyList())
    }

    fun setPickingTarget(enabled: Boolean) {
        pickingTarget = enabled
    }

    fun setRunState(state: AutomationRunState, message: String? = null) {
        _snapshot.update { it.copy(runState = state, message = message) }
    }

    fun updateProgress(cycleCount: Int, elapsedMs: Long) {
        _snapshot.update { it.copy(cycleCount = cycleCount, elapsedMs = elapsedMs) }
    }

    fun recordRun(clicks: Int, runtimeMs: Long) {
        _sessionStats.update {
            it.copy(
                totalClicks = it.totalClicks + clicks,
                totalRuntimeMs = it.totalRuntimeMs + runtimeMs,
                runCount = it.runCount + 1,
            )
        }
    }

    fun stopAll() {
        activePlan = null
        pickingTarget = false
        _snapshot.value = AutomationSnapshot()
    }

    fun featureLabel(kind: FeatureKind?): String? = when (kind) {
        FeatureKind.SINGLE_TARGET -> "Single Target"
        FeatureKind.MULTI_TARGET -> "Multi Target"
        FeatureKind.MACRO_RECORDER -> "Macro Recorder"
        null -> null
    }
}
