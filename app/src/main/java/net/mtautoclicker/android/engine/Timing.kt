package net.mtautoclicker.android.engine

import net.mtautoclicker.android.data.IntervalConfig
import net.mtautoclicker.android.data.IntervalUnit
import net.mtautoclicker.android.data.StopCondition
import net.mtautoclicker.android.data.StopType
import kotlin.random.Random

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
        return min + Random.nextLong(max - min + 1)
    }
    return intervalToMs(interval)
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
    return "${interval.value.toInt()}$suffix"
}

fun formatStopSummary(stop: StopCondition): String {
    return when (stop.type) {
        StopType.CYCLES -> "${stop.cycles ?: 0} clicks"
        StopType.DURATION -> {
            val sec = ((stop.durationMs ?: 0) / 1000).toInt()
            if (sec >= 60) "${sec / 60} min" else "${sec}s"
        }
        StopType.NEVER -> "no stop"
    }
}
