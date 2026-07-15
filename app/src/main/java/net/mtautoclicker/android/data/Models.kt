package net.mtautoclicker.android.data

import kotlinx.serialization.Serializable

@Serializable
enum class FeatureKind {
    SINGLE_TARGET,
    MULTI_TARGET,
    MACRO_RECORDER,
}

@Serializable
enum class TargetMode {
    POINT,
    ZONE,
}

@Serializable
enum class MouseButton {
    LEFT,
    RIGHT,
    MIDDLE,
}

@Serializable
enum class IntervalUnit {
    MS,
    S,
    MIN,
}

@Serializable
enum class StopType {
    NEVER,
    CYCLES,
    DURATION,
}

@Serializable
data class IntervalConfig(
    val value: Double = 100.0,
    val unit: IntervalUnit = IntervalUnit.MS,
    val variable: Boolean = false,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val startDelayMs: Long = 0,
    val randomOffsetPx: Int = 0,
)

@Serializable
data class StopCondition(
    val type: StopType = StopType.DURATION,
    val cycles: Int? = null,
    val durationMs: Long? = 10_000L,
)

@Serializable
data class ClickTarget(
    val x: Float,
    val y: Float,
    val label: String? = null,
    val zoneWidth: Float? = null,
    val zoneHeight: Float? = null,
)

@Serializable
data class SingleTargetConfig(
    val targetMode: TargetMode = TargetMode.POINT,
    val mouseButton: MouseButton = MouseButton.LEFT,
    val interval: IntervalConfig = IntervalConfig(),
    val stop: StopCondition = StopCondition(
        type = StopType.DURATION,
        durationMs = 10_000L,
    ),
)

@Serializable
data class MultiTargetConfig(
    val mouseButton: MouseButton = MouseButton.LEFT,
    val interval: IntervalConfig = IntervalConfig(),
    val stop: StopCondition = StopCondition(
        type = StopType.DURATION,
        durationMs = 10_000L,
    ),
    val parallel: Boolean = false,
)

@Serializable
data class MtPreset(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val feature: FeatureKind,
    val createdAt: String,
    val configJson: String,
    val targets: List<ClickTarget> = emptyList(),
)

data class SessionStats(
    val totalClicks: Long = 0,
    val totalRuntimeMs: Long = 0,
    val runCount: Int = 0,
)

enum class AutomationRunState {
    IDLE,
    ARMED,
    RUNNING,
    PAUSED,
}

data class AutomationSnapshot(
    val feature: FeatureKind? = null,
    val runState: AutomationRunState = AutomationRunState.IDLE,
    val targets: List<ClickTarget> = emptyList(),
    val cycleCount: Int = 0,
    val elapsedMs: Long = 0,
    val message: String? = null,
)

sealed class AutomationPlan {
    abstract val feature: FeatureKind
    abstract val targets: List<ClickTarget>
    abstract val interval: IntervalConfig
    abstract val stop: StopCondition
    abstract val mouseButton: MouseButton

    data class Single(
        val config: SingleTargetConfig,
        override val targets: List<ClickTarget>,
    ) : AutomationPlan() {
        override val feature = FeatureKind.SINGLE_TARGET
        override val interval = config.interval
        override val stop = config.stop
        override val mouseButton = config.mouseButton
    }

    data class Multi(
        val config: MultiTargetConfig,
        override val targets: List<ClickTarget>,
    ) : AutomationPlan() {
        override val feature = FeatureKind.MULTI_TARGET
        override val interval = config.interval
        override val stop = config.stop
        override val mouseButton = config.mouseButton
        val parallel: Boolean = config.parallel
    }
}
