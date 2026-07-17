package net.mtautoclicker.android.engine

import android.os.SystemClock
import kotlinx.coroutines.delay
import net.mtautoclicker.android.data.IntervalConfig
import net.mtautoclicker.android.data.IntervalUnit
import net.mtautoclicker.android.data.StopCondition
import net.mtautoclicker.android.data.StopType
import kotlin.random.Random

/** Lowest interval we advertise for Accessibility gesture clicks. */
const val MIN_CLICK_INTERVAL_MS = 10L

fun intervalToMs(interval: IntervalConfig): Long {
    val value = interval.value
    return when (interval.unit) {
        IntervalUnit.MS -> value.toLong()
        IntervalUnit.S -> (value * 1000).toLong()
        IntervalUnit.MIN -> (value * 60_000).toLong()
    }
}

fun resolveIntervalMs(interval: IntervalConfig): Long {
    if (interval.variable && interval.minValue != null && interval.maxValue != null) {
        val min = intervalToMs(interval.copy(value = interval.minValue))
        val max = intervalToMs(interval.copy(value = interval.maxValue))
        return (min + Random.nextLong((max - min).coerceAtLeast(0L) + 1))
            .coerceAtLeast(MIN_CLICK_INTERVAL_MS)
    }
    return intervalToMs(interval).coerceAtLeast(MIN_CLICK_INTERVAL_MS)
}

/**
 * Sleep until an absolute [SystemClock.elapsedRealtime] deadline.
 * Uses [delay] for longer waits, then a short spin for ~10ms-class intervals
 * (same idea as the Chrome extension sleepUntil).
 */
suspend fun delayUntilElapsedRealtime(deadlineElapsedMs: Long) {
    while (true) {
        val remaining = deadlineElapsedMs - SystemClock.elapsedRealtime()
        if (remaining <= 0L) return
        if (remaining > 3L) {
            delay(remaining - 2L)
            continue
        }
        while (SystemClock.elapsedRealtime() < deadlineElapsedMs) {
            // busy-wait last few ms for tighter CPS
        }
        return
    }
}

fun shouldStop(stop: StopCondition, cycles: Int, elapsedMs: Long): Boolean {
    if (stop.type == StopType.CYCLES && stop.cycles != null && cycles >= stop.cycles) return true
    if (stop.type == StopType.DURATION && stop.durationMs != null && elapsedMs >= stop.durationMs) return true
    return false
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    val h = m / 60
    return when {
        h > 0 -> "${h}h ${m % 60}m"
        m > 0 -> "${m}m ${s % 60}s"
        else -> "${s}s"
    }
}

fun formatInterval(interval: IntervalConfig): String {
    val suffix = when (interval.unit) {
        IntervalUnit.MS -> "ms"
        IntervalUnit.S -> "s"
        IntervalUnit.MIN -> "min"
    }
    val v = interval.value
    val text = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
    return "$text$suffix"
}

fun formatStopSummary(stop: StopCondition, unitLabel: String = "clicks"): String {
    return when (stop.type) {
        StopType.CYCLES -> "${stop.cycles ?: 0} $unitLabel"
        StopType.DURATION -> {
            val sec = ((stop.durationMs ?: 0) / 1000).toInt()
            if (sec >= 60) "${sec / 60} min" else "${sec}s"
        }
        StopType.NEVER -> "no stop"
    }
}
