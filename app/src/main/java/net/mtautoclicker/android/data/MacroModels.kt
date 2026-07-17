package net.mtautoclicker.android.data

import kotlinx.serialization.Serializable

@Serializable
enum class MacroStepKind {
    TAP,
    LONG_PRESS,
    SWIPE,
    PATH,
    /** Fill focused text field (used for smooth keyboard typing during record). */
    TYPE_TEXT,
}

@Serializable
data class MacroPoint(
    val x: Float,
    val y: Float,
)

@Serializable
data class MacroStep(
    val kind: MacroStepKind,
    /** Wait before executing this step (from previous step end). */
    val delayMs: Long,
    val x: Float? = null,
    val y: Float? = null,
    val x2: Float? = null,
    val y2: Float? = null,
    val points: List<MacroPoint>? = null,
    /** Gesture duration (hold length, swipe duration, etc.). */
    val durationMs: Long = 50L,
    /** For TYPE_TEXT: full text to place into the focused editable. */
    val text: String? = null,
)

@Serializable
data class MacroMetadata(
    val durationMs: Long = 0L,
    val actionCount: Int = 0,
)

@Serializable
data class SavedMacro(
    val id: String,
    val name: String,
    val createdAt: String,
    val steps: List<MacroStep>,
    val metadata: MacroMetadata,
)

@Serializable
data class MacroPlaybackConfig(
    val macroId: String? = null,
    val macroName: String = "Macro",
    val steps: List<MacroStep> = emptyList(),
    val playbackSpeed: Float = 1f,
    val loop: Boolean = false,
    /** When loop is true: 0 = infinite, otherwise play N times. */
    val loopCount: Int = 1,
)

enum class MacroOverlayMode {
    /** No macro session — overlay should show nothing. */
    IDLE,
    RECORD_READY,
    RECORDING,
    PLAYBACK,
}
