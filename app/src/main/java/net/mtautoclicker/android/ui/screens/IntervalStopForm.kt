package net.mtautoclicker.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.mtautoclicker.android.data.IntervalConfig
import net.mtautoclicker.android.data.IntervalUnit
import net.mtautoclicker.android.data.StopCondition
import net.mtautoclicker.android.data.StopType
import net.mtautoclicker.android.data.TargetMode
import net.mtautoclicker.android.engine.MIN_CLICK_INTERVAL_MS
import net.mtautoclicker.android.ui.components.MtNumberField
import net.mtautoclicker.android.ui.components.SettingsCard
import net.mtautoclicker.android.ui.theme.MtBlue
import net.mtautoclicker.android.ui.theme.MtBorder
import net.mtautoclicker.android.ui.theme.MtHi
import net.mtautoclicker.android.ui.theme.MtMid

@Composable
private fun MtDropdownColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MtBlue,
    unfocusedBorderColor = MtBorder,
    focusedTextColor = MtHi,
    unfocusedTextColor = MtHi,
    focusedLabelColor = MtMid,
    unfocusedLabelColor = MtMid,
    cursorColor = MtBlue,
    focusedTrailingIconColor = MtMid,
    unfocusedTrailingIconColor = MtMid,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntervalStopForm(
    interval: IntervalConfig,
    stop: StopCondition,
    onIntervalChange: (IntervalConfig) -> Unit,
    onStopChange: (StopCondition) -> Unit,
) {
    SettingsCard(title = "Timing") {
        val intervalMin = if (interval.unit == IntervalUnit.MS) MIN_CLICK_INTERVAL_MS.toLong() else 1L
        MtNumberField(
            value = interval.value,
            onCommit = { n ->
                onIntervalChange(interval.copy(value = n.toDouble().coerceAtLeast(intervalMin.toDouble())))
            },
            label = "Interval (min ${MIN_CLICK_INTERVAL_MS} ms)",
            min = intervalMin,
        )
        Text(
            "Fastest reliable rate on Android is about ${MIN_CLICK_INTERVAL_MS} ms (~${1000 / MIN_CLICK_INTERVAL_MS} CPS). Device may run slightly slower under load.",
            color = MtMid,
        )
        UnitDropdown(interval.unit) { onIntervalChange(interval.copy(unit = it)) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Variable interval", color = MtHi)
            Switch(checked = interval.variable, onCheckedChange = { onIntervalChange(interval.copy(variable = it)) })
        }
        if (interval.variable) {
            MtNumberField(
                value = interval.minValue ?: interval.value,
                onCommit = { n -> onIntervalChange(interval.copy(minValue = n.toDouble())) },
                label = "Min",
                min = intervalMin,
            )
            MtNumberField(
                value = interval.maxValue ?: interval.value,
                onCommit = { n -> onIntervalChange(interval.copy(maxValue = n.toDouble())) },
                label = "Max",
                min = intervalMin,
            )
        }
        MtNumberField(
            value = interval.startDelayMs,
            onCommit = { n -> onIntervalChange(interval.copy(startDelayMs = n)) },
            label = "Start delay (ms)",
            min = 0,
        )
        MtNumberField(
            value = interval.randomOffsetPx,
            onCommit = { n -> onIntervalChange(interval.copy(randomOffsetPx = n.toInt())) },
            label = "Random offset (px)",
            min = 0,
        )
    }

    SettingsCard(title = "Stop condition") {
        StopTypeDropdown(stop.type) { onStopChange(stop.copy(type = it)) }
        when (stop.type) {
            StopType.CYCLES -> MtNumberField(
                value = stop.cycles ?: 1,
                onCommit = { n -> onStopChange(stop.copy(cycles = n.toInt().coerceAtLeast(1))) },
                label = "Number of cycles",
                min = 1,
            )
            StopType.DURATION -> MtNumberField(
                value = ((stop.durationMs ?: 0L) / 1000L).coerceAtLeast(0L),
                onCommit = { n -> onStopChange(stop.copy(durationMs = n.coerceAtLeast(0) * 1000)) },
                label = "Duration (seconds)",
                min = 0,
            )
            StopType.NEVER -> Text("Runs until you stop manually.", color = MtMid)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(selected: IntervalUnit, onSelect: (IntervalUnit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name.lowercase(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Unit") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = MtDropdownColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            IntervalUnit.entries.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit.name.lowercase()) },
                    onClick = {
                        onSelect(unit)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopTypeDropdown(selected: StopType, onSelect: (StopType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name.lowercase(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Stop type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = MtDropdownColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StopType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.name.lowercase()) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetModeDropdown(selected: TargetMode, onSelect: (TargetMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name.lowercase(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Target type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = MtDropdownColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TargetMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.name.lowercase()) },
                    onClick = {
                        onSelect(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}
