package net.mtautoclicker.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.mtautoclicker.android.MtApplication
import net.mtautoclicker.android.data.FeatureKind
import net.mtautoclicker.android.data.MacroPlaybackConfig
import net.mtautoclicker.android.data.PresetRepository
import net.mtautoclicker.android.data.SavedMacro
import net.mtautoclicker.android.engine.AutomationLauncher
import net.mtautoclicker.android.engine.LaunchResult
import net.mtautoclicker.android.engine.formatDuration
import net.mtautoclicker.android.ui.components.FeaturePageScaffold
import net.mtautoclicker.android.ui.components.FeatureStepUi
import net.mtautoclicker.android.ui.components.MtPrimaryButton
import net.mtautoclicker.android.ui.theme.MtBorder
import net.mtautoclicker.android.ui.theme.MtCard
import net.mtautoclicker.android.ui.theme.MtDeep
import net.mtautoclicker.android.ui.theme.MtHi
import net.mtautoclicker.android.ui.theme.MtMid
import net.mtautoclicker.android.ui.theme.MtPurple
import net.mtautoclicker.android.ui.theme.MtRow
import java.time.Instant
import java.time.temporal.ChronoUnit

private enum class MacroTab { RECORD, PLAYBACK }

private val RecordGradient = listOf(Color(0xFFF43F5E), Color(0xFFDC2626))
private val PlaybackGradient = listOf(Color(0xFF8B5CF6), Color(0xFF7C3AED))

private val RECORDED_ITEMS = listOf(
    "Taps (single click)",
    "Long presses (holds)",
    "Swipes and drag paths",
    "Back / Home / Recents — edge/bottom gestures, 3-button bar, or notification actions",
    "Notification shade — swipe down from the top edge",
    "Delays between each action",
)

private val NOT_RECORDED_ITEMS = listOf(
    "Keyboard typing (except captured text fields)",
    "Touches outside the tinted capture guide (if any OS edge is reserved)",
    "Notifications and most OS dialogs (except shade open via top swipe)",
    "Multi-finger / pinch gestures",
)

private val DEVICE_LIMITATIONS = listOf(
    "Playback injects gestures via Accessibility — your finger does not move.",
    "Some apps ignore injected (non-trusted) gestures.",
    "Keep Accessibility enabled for record and playback.",
    "After updating the app, toggle Accessibility off/on so nav-key capture is enabled.",
    "While recording, colored edge bands show capture hot-zones; cyan rings mark taps/swipes.",
    "Gesture nav: left/right edge → Back · bottom swipe up → Home · hold → Recents · top swipe down → Notifications.",
    "On some OnePlus / OxygenOS builds the status-bar chip may keep the app icon even while the home icon shows REC — use the red REC notification card.",
    "If Home/Recents keys are blocked by the OEM, use Back/Home/Recents on the recording notification.",
    "Macros are stored on this device only.",
)

@Composable
fun MacroRecorderScreen(onBack: () -> Unit, onNeedsPermissions: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val json = remember { Json { encodeDefaults = true } }
    var tab by remember { mutableStateOf(MacroTab.RECORD) }
    var starting by remember { mutableStateOf(false) }
    var limitsOpen by remember { mutableStateOf(false) }
    var recordedOpen by remember { mutableStateOf(false) }
    var notRecordedOpen by remember { mutableStateOf(false) }
    var deviceLimitsOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SavedMacro?>(null) }
    var saveTarget by remember { mutableStateOf<SavedMacro?>(null) }
    var presetName by remember { mutableStateOf("") }

    val macros by MtApplication.instance.macroRepository.macros.collectAsState(initial = emptyList())
    val sorted = remember(macros) {
        macros.sortedByDescending { it.createdAt }
    }
    val recent = sorted.take(3)

    fun startRecord() {
        starting = true
        when (AutomationLauncher.startMacroRecord(context)) {
            LaunchResult.Ok -> {
                scope.launch {
                    MtApplication.instance.trackingService.trackEvent("macro_record_start")
                }
            }
            is LaunchResult.NeedsPermissions -> onNeedsPermissions()
        }
        starting = false
    }

    fun playMacro(macro: SavedMacro) {
        when (AutomationLauncher.startMacroPlayback(context, macro)) {
            LaunchResult.Ok -> {
                scope.launch {
                    MtApplication.instance.trackingService.trackEvent("macro_playback_start")
                }
            }
            is LaunchResult.NeedsPermissions -> onNeedsPermissions()
        }
    }

    FeaturePageScaffold(title = "Macro Recorder", onBack = onBack) {
        MacroTabBar(selected = tab, macroCount = sorted.size, onSelect = { tab = it })

        when (tab) {
            MacroTab.RECORD -> {
                RecordHeroCard(starting = starting, onStart = { startRecord() })

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Rounded.Movie, null, tint = MtPurple, modifier = Modifier.size(16.dp))
                            Text("Recent recordings", color = MtHi, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        if (sorted.size > 3) {
                            Text(
                                "View all ${sorted.size}",
                                color = MtPurple,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { tab = MacroTab.PLAYBACK },
                            )
                        }
                    }

                    if (recent.isEmpty()) {
                        EmptyMacrosCard(
                            title = "No recordings yet",
                            subtitle = "Your last 3 macros will show up here after you record.",
                        )
                    } else {
                        recent.forEach { macro ->
                            MacroRow(
                                macro = macro,
                                compact = true,
                                onPlay = { playMacro(it) },
                                onSave = {
                                    saveTarget = it
                                    presetName = PresetRepository.defaultSavedName(FeatureKind.MACRO_RECORDER)
                                    scope.launch {
                                        presetName = MtApplication.instance.presetRepository
                                            .nextDefaultSavedName(FeatureKind.MACRO_RECORDER)
                                    }
                                },
                                onDelete = { deleteTarget = it },
                            )
                        }
                        if (sorted.isNotEmpty() && sorted.size <= 3) {
                            Text(
                                "Open playback library →",
                                color = MtPurple,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { tab = MacroTab.PLAYBACK }
                                    .padding(vertical = 8.dp),
                            )
                        }
                    }
                }

                LimitsCard(
                    open = limitsOpen,
                    onToggle = { limitsOpen = !limitsOpen },
                    recordedOpen = recordedOpen,
                    onRecordedToggle = { recordedOpen = !recordedOpen },
                    notRecordedOpen = notRecordedOpen,
                    onNotRecordedToggle = { notRecordedOpen = !notRecordedOpen },
                    deviceLimitsOpen = deviceLimitsOpen,
                    onDeviceLimitsToggle = { deviceLimitsOpen = !deviceLimitsOpen },
                )
            }

            MacroTab.PLAYBACK -> {
                PlaybackHeader(count = sorted.size)

                if (sorted.isEmpty()) {
                    EmptyMacrosCard(
                        title = "No macro recordings yet",
                        subtitle = "Record a macro on the Record tab first.",
                    )
                    Text(
                        "Go to Record",
                        color = Color(0xFFF43F5E),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF43F5E).copy(alpha = 0.12f))
                            .clickable { tab = MacroTab.RECORD }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                } else {
                    sorted.forEach { macro ->
                        MacroRow(
                            macro = macro,
                            onPlay = { playMacro(it) },
                            onSave = {
                                saveTarget = it
                                presetName = PresetRepository.defaultSavedName(FeatureKind.MACRO_RECORDER)
                                scope.launch {
                                    presetName = MtApplication.instance.presetRepository
                                        .nextDefaultSavedName(FeatureKind.MACRO_RECORDER)
                                }
                            },
                            onDelete = { deleteTarget = it },
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF59E0B).copy(alpha = 0.08f))
                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Rounded.WarningAmber,
                        null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        "Playback injects gestures via Accessibility. Some apps may ignore non-trusted input.",
                        color = Color(0xFFFCD34D).copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this macro?") },
            text = { Text("“${target.name}” will be removed from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            MtApplication.instance.macroRepository.deleteMacro(target.id)
                            deleteTarget = null
                        }
                    },
                ) { Text("Delete", color = Color(0xFFEF4444)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }

    saveTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { saveTarget = null },
            title = { Text("Save as preset") },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    singleLine = true,
                    label = { Text("Preset name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MtHi,
                        unfocusedTextColor = MtHi,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val name = presetName.trim().ifBlank {
                                MtApplication.instance.presetRepository.nextDefaultSavedName(FeatureKind.MACRO_RECORDER)
                            }
                            val cfg = MacroPlaybackConfig(
                                macroId = target.id,
                                macroName = name,
                                steps = target.steps,
                            )
                            MtApplication.instance.presetRepository.savePreset(
                                name = name,
                                feature = FeatureKind.MACRO_RECORDER,
                                configJson = json.encodeToString(cfg),
                                targets = emptyList(),
                            )
                            Toast.makeText(context, "Preset saved: $name", Toast.LENGTH_SHORT).show()
                            saveTarget = null
                        }
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { saveTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MacroTabBar(
    selected: MacroTab,
    macroCount: Int,
    onSelect: (MacroTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MtRow)
            .border(1.dp, MtBorder, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MacroTabChip(
            label = "Record",
            icon = Icons.Rounded.FiberManualRecord,
            selected = selected == MacroTab.RECORD,
            gradient = RecordGradient,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(MacroTab.RECORD) },
        )
        MacroTabChip(
            label = "Playback",
            icon = Icons.Rounded.PlayArrow,
            selected = selected == MacroTab.PLAYBACK,
            gradient = PlaybackGradient,
            badge = if (macroCount > 0) macroCount.toString() else null,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(MacroTab.PLAYBACK) },
        )
    }
}

@Composable
private fun MacroTabChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    gradient: List<Color>,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .then(
                if (selected) Modifier.background(Brush.horizontalGradient(gradient))
                else Modifier.background(Color.Transparent),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) Color.White else MtMid, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = if (selected) Color.White else MtMid,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
        if (badge != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                badge,
                color = if (selected) Color.White else MtMid,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.25f) else MtBorder)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun RecordHeroCard(starting: Boolean, onStart: () -> Unit) {
    val cardShape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, cardShape, clip = false)
            .clip(cardShape)
            .background(MtCard)
            .border(1.dp, MtBorder.copy(alpha = 0.9f), cardShape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(RecordGradient.first().copy(alpha = 0.14f), MtCard),
                    ),
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(RecordGradient)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.RadioButtonChecked, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Record on your device", color = MtHi, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Capture taps, holds, and swipes. Adjust speed and repeats from the floating panel when you play back.",
                        color = MtMid,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeatureChip("Taps", Color(0xFF38BDF8))
                FeatureChip("Holds", Color(0xFFA78BFA))
                FeatureChip("Swipes", Color(0xFF34D399))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = MtBorder.copy(alpha = 0.7f), shape = RoundedCornerShape(0.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MtPrimaryButton(
                text = if (starting) "Opening…" else "Start Record",
                onClick = onStart,
                enabled = !starting,
                containerColor = Color(0xFFE11D48),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MtDeep.copy(alpha = 0.65f))
                    .border(1.dp, MtBorder.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "HOW IT WORKS",
                    color = MtMid,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                )
                listOf(
                    FeatureStepUi(Icons.Rounded.TouchApp, "Confirm Start recording on the popup"),
                    FeatureStepUi(Icons.Rounded.FiberManualRecord, "Use your phone — watch REC in the notification"),
                    FeatureStepUi(Icons.Rounded.Movie, "Pull shade → Stop, then Play from notification"),
                ).forEachIndexed { index, step ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MtBorder.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${index + 1}", color = MtMid, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Icon(step.icon, null, tint = Color(0xFF60A5FA), modifier = Modifier.size(14.dp))
                        Text(step.text, color = MtMid, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureChip(label: String, accent: Color) {
    Text(
        label,
        color = accent,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun PlaybackHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF8B5CF6).copy(alpha = 0.14f), Color(0xFF7C3AED).copy(alpha = 0.06f)),
                ),
            )
            .border(1.dp, MtBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(PlaybackGradient)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Column {
            Text("All recordings", color = MtHi, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                "$count macro${if (count == 1) "" else "s"} saved on this device",
                color = MtMid,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun EmptyMacrosCard(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MtBorder, RoundedCornerShape(14.dp))
            .background(MtCard.copy(alpha = 0.5f))
            .padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.PlayArrow,
            null,
            tint = MtMid.copy(alpha = 0.45f),
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(title, color = MtMid, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = MtMid.copy(alpha = 0.8f), fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun MacroRow(
    macro: SavedMacro,
    onPlay: (SavedMacro) -> Unit,
    onSave: (SavedMacro) -> Unit,
    onDelete: (SavedMacro) -> Unit,
    compact: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MtCard)
            .border(1.dp, MtBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = if (compact) 10.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 34.dp else 38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669))))
                .clickable { onPlay(macro) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                macro.name,
                color = MtHi,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compact) 12.sp else 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, null, tint = MtPurple, modifier = Modifier.size(11.dp))
                Text(
                    "${formatDuration(macro.metadata.durationMs)} · ${macro.metadata.actionCount} actions · ${formatMacroAge(macro.createdAt)}",
                    color = MtMid,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            Icons.Rounded.Bookmark,
            "Save as preset",
            tint = Color(0xFFFBBF24),
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF59E0B).copy(alpha = 0.12f))
                .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .clickable { onSave(macro) }
                .padding(8.dp),
        )
        Icon(
            Icons.Rounded.Delete,
            "Delete",
            tint = Color(0xFFF87171),
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .clickable { onDelete(macro) }
                .padding(8.dp),
        )
    }
}

@Composable
private fun LimitsCard(
    open: Boolean,
    onToggle: () -> Unit,
    recordedOpen: Boolean,
    onRecordedToggle: () -> Unit,
    notRecordedOpen: Boolean,
    onNotRecordedToggle: () -> Unit,
    deviceLimitsOpen: Boolean,
    onDeviceLimitsToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MtCard)
            .border(1.dp, MtBorder, RoundedCornerShape(14.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0EA5E9).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Info, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                }
                Text("Recording limits & notes", color = MtHi, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Text(
                if (open) "Hide" else "Show",
                color = MtMid,
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MtDeep)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        if (open) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = MtBorder, shape = RoundedCornerShape(0.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CollapsibleNotes(
                    title = "What is recorded",
                    tone = Color(0xFF34D399),
                    items = RECORDED_ITEMS,
                    open = recordedOpen,
                    onToggle = onRecordedToggle,
                )
                CollapsibleNotes(
                    title = "What is not recorded",
                    tone = Color(0xFFF87171),
                    items = NOT_RECORDED_ITEMS,
                    open = notRecordedOpen,
                    onToggle = onNotRecordedToggle,
                )
                CollapsibleNotes(
                    title = "Device limitations",
                    tone = Color(0xFFFBBF24),
                    items = DEVICE_LIMITATIONS,
                    open = deviceLimitsOpen,
                    onToggle = onDeviceLimitsToggle,
                )
            }
        }
    }
}

@Composable
private fun CollapsibleNotes(
    title: String,
    tone: Color,
    items: List<String>,
    open: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tone.copy(alpha = 0.06f))
            .border(1.dp, tone.copy(alpha = 0.22f), RoundedCornerShape(10.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = tone, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                tint = tone.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp),
            )
        }
        if (open) {
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items.forEach { item ->
                    Text("• $item", color = MtMid, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }
    }
}

private fun formatMacroAge(iso: String): String {
    return runCatching {
        val then = Instant.parse(iso)
        val days = ChronoUnit.DAYS.between(then, Instant.now())
        when {
            days <= 0 -> "today"
            days == 1L -> "1 day ago"
            days < 30 -> "$days days ago"
            else -> "${days / 30} mo ago"
        }
    }.getOrDefault("saved")
}
