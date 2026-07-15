package net.mtautoclicker.android.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.mtautoclicker.android.data.MacroOverlayMode
import net.mtautoclicker.android.data.MacroPlaybackConfig
import net.mtautoclicker.android.data.MacroStep
import net.mtautoclicker.android.data.MacroStepKind
import net.mtautoclicker.android.data.SavedMacro

data class MacroSessionSnapshot(
    val mode: MacroOverlayMode = MacroOverlayMode.RECORD_READY,
    val steps: List<MacroStep> = emptyList(),
    val stepCount: Int = 0,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val message: String? = null,
    val lastSavedMacro: SavedMacro? = null,
    val playbackConfig: MacroPlaybackConfig = MacroPlaybackConfig(),
    /** Epoch millis when recording started (for notification chronometer). */
    val recordingStartedAtMs: Long = 0L,
)

/**
 * In-memory macro recording / playback session (extension macro-session equivalent).
 */
object MacroHub {
    private val _snapshot = MutableStateFlow(MacroSessionSnapshot())
    val snapshot: StateFlow<MacroSessionSnapshot> = _snapshot.asStateFlow()

    private var lastStepAtMs: Long = 0L
    private val pendingSteps = mutableListOf<MacroStep>()

    fun armRecordReady() {
        pendingSteps.clear()
        lastStepAtMs = 0L
        _snapshot.value = MacroSessionSnapshot(
            mode = MacroOverlayMode.RECORD_READY,
            message = "Ready to record — tap Record, then interact.",
        )
    }

    fun armPlayback(macro: SavedMacro, config: MacroPlaybackConfig = MacroPlaybackConfig()) {
        pendingSteps.clear()
        _snapshot.value = MacroSessionSnapshot(
            mode = MacroOverlayMode.PLAYBACK,
            lastSavedMacro = macro,
            playbackConfig = config.copy(
                macroId = macro.id,
                macroName = macro.name,
                steps = macro.steps,
            ),
            progressTotal = macro.steps.size,
            message = "Macro saved — press Play to start.",
        )
    }

    fun startRecording() {
        pendingSteps.clear()
        lastStepAtMs = System.currentTimeMillis()
        _snapshot.update {
            it.copy(
                mode = MacroOverlayMode.RECORDING,
                steps = emptyList(),
                stepCount = 0,
                recordingStartedAtMs = System.currentTimeMillis(),
                message = "Recording…",
            )
        }
    }

    fun appendStep(stepWithoutDelay: MacroStep, dedupeWindowMs: Long? = null): Boolean {
        if (_snapshot.value.mode != MacroOverlayMode.RECORDING) return false
        val now = System.currentTimeMillis()
        val last = pendingSteps.lastOrNull()
        val window = dedupeWindowMs ?: when (stepWithoutDelay.kind) {
            MacroStepKind.TAP, MacroStepKind.LONG_PRESS -> 700L
            MacroStepKind.TYPE_TEXT -> 0L
            else -> 120L
        }
        if (window > 0L &&
            last != null &&
            last.kind == stepWithoutDelay.kind &&
            (now - lastStepAtMs) < window &&
            sameSpot(last, stepWithoutDelay)
        ) {
            return false
        }
        val delay = if (lastStepAtMs <= 0L) 0L else (now - lastStepAtMs).coerceAtLeast(0L)
        lastStepAtMs = now
        val step = stepWithoutDelay.copy(delayMs = delay)
        pendingSteps += step
        _snapshot.update {
            it.copy(
                steps = pendingSteps.toList(),
                stepCount = pendingSteps.size,
                message = "${pendingSteps.size} action${if (pendingSteps.size == 1) "" else "s"} recorded",
            )
        }
        return true
    }

    /** Keep a single trailing TYPE_TEXT step updated while the user types. */
    fun upsertTrailingTypeText(text: String): Boolean {
        if (_snapshot.value.mode != MacroOverlayMode.RECORDING) return false
        if (text.isEmpty()) return false
        val now = System.currentTimeMillis()
        val last = pendingSteps.lastOrNull()
        if (last?.kind == MacroStepKind.TYPE_TEXT) {
            if (last.text == text) return false
            pendingSteps[pendingSteps.lastIndex] = last.copy(text = text)
            lastStepAtMs = now
            _snapshot.update {
                it.copy(
                    steps = pendingSteps.toList(),
                    stepCount = pendingSteps.size,
                    message = "${pendingSteps.size} action${if (pendingSteps.size == 1) "" else "s"} recorded",
                )
            }
            return true
        }
        return appendStep(
            MacroStep(kind = MacroStepKind.TYPE_TEXT, delayMs = 0L, text = text),
            dedupeWindowMs = 0L,
        )
    }

    private fun sameSpot(a: MacroStep, b: MacroStep): Boolean {
        val ax = a.x ?: a.points?.firstOrNull()?.x ?: return false
        val ay = a.y ?: a.points?.firstOrNull()?.y ?: return false
        val bx = b.x ?: b.points?.firstOrNull()?.x ?: return false
        val by = b.y ?: b.points?.firstOrNull()?.y ?: return false
        val dx = ax - bx
        val dy = ay - by
        return dx * dx + dy * dy < 48f * 48f
    }

    fun stopRecordingSteps(): List<MacroStep> {
        val result = pendingSteps.toList()
        lastStepAtMs = 0L
        return result
    }

    fun setPlaybackConfig(config: MacroPlaybackConfig) {
        _snapshot.update { it.copy(playbackConfig = config) }
    }

    fun patchPlayback(speed: Float? = null, loop: Boolean? = null, loopCount: Int? = null) {
        _snapshot.update {
            val cfg = it.playbackConfig
            it.copy(
                playbackConfig = cfg.copy(
                    playbackSpeed = speed ?: cfg.playbackSpeed,
                    loop = loop ?: cfg.loop,
                    loopCount = loopCount ?: cfg.loopCount,
                ),
            )
        }
    }

    fun setPlaying(playing: Boolean, paused: Boolean = false) {
        _snapshot.update {
            it.copy(
                isPlaying = playing,
                isPaused = paused,
                message = when {
                    !playing -> "Stopped"
                    paused -> "Paused"
                    else -> "Playing…"
                },
            )
        }
    }

    fun setProgress(current: Int, total: Int) {
        _snapshot.update { it.copy(progressCurrent = current, progressTotal = total) }
    }

    fun setLastSaved(macro: SavedMacro) {
        _snapshot.update {
            it.copy(
                mode = MacroOverlayMode.PLAYBACK,
                lastSavedMacro = macro,
                steps = macro.steps,
                stepCount = macro.steps.size,
                playbackConfig = it.playbackConfig.copy(
                    macroId = macro.id,
                    macroName = macro.name,
                    steps = macro.steps,
                ),
                progressTotal = macro.steps.size,
                progressCurrent = 0,
                isPlaying = false,
                isPaused = false,
                message = "Macro saved — press Play.",
                recordingStartedAtMs = 0L,
            )
        }
    }

    fun reset() {
        pendingSteps.clear()
        lastStepAtMs = 0L
        _snapshot.value = MacroSessionSnapshot()
    }
}
