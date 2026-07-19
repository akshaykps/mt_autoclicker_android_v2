package net.mtautoclicker.android.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.mtautoclicker.android.MtApplication
import net.mtautoclicker.android.data.AutoRefreshConfig
import net.mtautoclicker.android.data.FeatureKind
import net.mtautoclicker.android.data.MacroPlaybackConfig
import net.mtautoclicker.android.data.MtPreset
import net.mtautoclicker.android.data.MultiTargetConfig
import net.mtautoclicker.android.data.SavedMacro
import net.mtautoclicker.android.data.SingleTargetConfig
import net.mtautoclicker.android.engine.AutomationLauncher
import net.mtautoclicker.android.engine.LaunchResult
import net.mtautoclicker.android.ui.components.PageScaffold
import net.mtautoclicker.android.ui.theme.MtBlue
import net.mtautoclicker.android.ui.theme.MtBorder
import net.mtautoclicker.android.ui.theme.MtCard
import net.mtautoclicker.android.ui.theme.MtEmerald
import net.mtautoclicker.android.ui.theme.MtHi
import net.mtautoclicker.android.ui.theme.MtMid
import net.mtautoclicker.android.ui.theme.MtRow

private enum class PresetFilter { ALL, SINGLE, MULTI, MACRO, SCREENSHOT, REFRESH }

@Composable
fun PresetsScreen(onBack: () -> Unit, onNeedsPermissions: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presets by MtApplication.instance.presetRepository.savedPresets.collectAsState(initial = emptyList())
    val json = remember { Json { ignoreUnknownKeys = true } }
    var filter by remember { mutableStateOf(PresetFilter.ALL) }

    val filtered = remember(presets, filter) {
        when (filter) {
            PresetFilter.ALL -> presets
            PresetFilter.SINGLE -> presets.filter { it.feature == FeatureKind.SINGLE_TARGET }
            PresetFilter.MULTI -> presets.filter { it.feature == FeatureKind.MULTI_TARGET }
            PresetFilter.MACRO -> presets.filter { it.feature == FeatureKind.MACRO_RECORDER }
            PresetFilter.SCREENSHOT -> presets.filter { it.feature == FeatureKind.FULL_PAGE_SCREENSHOT }
            PresetFilter.REFRESH -> presets.filter { it.feature == FeatureKind.AUTO_REFRESH }
        }
    }

    PageScaffold(
        title = "Presets",
        onBack = onBack,
        showKeyboardHide = false,
        contentSpacing = 10.dp,
        horizontalPadding = 14.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MtCard)
                .border(1.dp, MtEmerald.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MtEmerald.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Bookmark, null, tint = MtEmerald, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Saved presets", color = MtHi, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "${presets.size} saved · save from a feature’s Recent tab",
                    color = MtMid,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                PresetFilter.ALL to "All",
                PresetFilter.SINGLE to "Single",
                PresetFilter.MULTI to "Multi",
                PresetFilter.MACRO to "Macro",
                PresetFilter.SCREENSHOT to "Shot",
                PresetFilter.REFRESH to "Refresh",
            ).forEach { (value, label) ->
                FilterChip(
                    label = label,
                    selected = filter == value,
                    count = when (value) {
                        PresetFilter.ALL -> presets.size
                        PresetFilter.SINGLE -> presets.count { it.feature == FeatureKind.SINGLE_TARGET }
                        PresetFilter.MULTI -> presets.count { it.feature == FeatureKind.MULTI_TARGET }
                        PresetFilter.MACRO -> presets.count { it.feature == FeatureKind.MACRO_RECORDER }
                        PresetFilter.SCREENSHOT -> presets.count { it.feature == FeatureKind.FULL_PAGE_SCREENSHOT }
                        PresetFilter.REFRESH -> presets.count { it.feature == FeatureKind.AUTO_REFRESH }
                    },
                ) { filter = value }
            }
        }

        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, MtBorder, RoundedCornerShape(14.dp))
                    .background(MtRow)
                    .padding(vertical = 28.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Rounded.Bookmark,
                    null,
                    tint = MtMid.copy(alpha = 0.4f),
                    modifier = Modifier.size(36.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("No presets here", color = MtHi, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Run a feature → Recent → Save to keep it here.",
                    color = MtMid,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MtCard)
                    .border(1.dp, MtBorder, RoundedCornerShape(16.dp)),
            ) {
                filtered.forEachIndexed { index, preset ->
                    CompactPresetRow(
                        preset = preset,
                        showDivider = index < filtered.lastIndex,
                        onRun = {
                            runCatching {
                                when (preset.feature) {
                                    FeatureKind.SINGLE_TARGET -> {
                                        val config = json.decodeFromString<SingleTargetConfig>(preset.configJson)
                                        AutomationLauncher.armSingle(config, preset.targets)
                                        when (AutomationLauncher.startFloatBar(context)) {
                                            LaunchResult.Ok -> Toast.makeText(context, "Loaded ${preset.name}", Toast.LENGTH_SHORT).show()
                                            is LaunchResult.NeedsPermissions -> onNeedsPermissions()
                                        }
                                    }
                                    FeatureKind.MULTI_TARGET -> {
                                        val config = json.decodeFromString<MultiTargetConfig>(preset.configJson)
                                        AutomationLauncher.armMulti(config, preset.targets)
                                        when (AutomationLauncher.startFloatBar(context)) {
                                            LaunchResult.Ok -> Toast.makeText(context, "Loaded ${preset.name}", Toast.LENGTH_SHORT).show()
                                            is LaunchResult.NeedsPermissions -> onNeedsPermissions()
                                        }
                                    }
                                    FeatureKind.MACRO_RECORDER -> {
                                        val cfg = json.decodeFromString<MacroPlaybackConfig>(preset.configJson)
                                        val macro = SavedMacro(
                                            id = cfg.macroId ?: preset.id,
                                            name = cfg.macroName.ifBlank { preset.name },
                                            createdAt = preset.createdAt,
                                            steps = cfg.steps,
                                            metadata = net.mtautoclicker.android.data.MacroMetadata(
                                                durationMs = cfg.steps.sumOf { it.delayMs + it.durationMs },
                                                actionCount = cfg.steps.size,
                                            ),
                                        )
                                        when (AutomationLauncher.startMacroPlayback(context, macro, cfg)) {
                                            LaunchResult.Ok -> Toast.makeText(context, "Loaded ${preset.name}", Toast.LENGTH_SHORT).show()
                                            is LaunchResult.NeedsPermissions -> onNeedsPermissions()
                                        }
                                    }
                                    FeatureKind.FULL_PAGE_SCREENSHOT -> {
                                        val cfg = json.decodeFromString<net.mtautoclicker.android.data.FullPageScreenshotConfig>(preset.configJson)
                                        val pkg = cfg.resolvedPackage()
                                        if (pkg.isBlank()) {
                                            Toast.makeText(context, "Preset has no app", Toast.LENGTH_SHORT).show()
                                        } else {
                                            when (AutomationLauncher.startFullPageScreenshot(context, pkg)) {
                                                LaunchResult.Ok -> Toast.makeText(context, "Loaded ${preset.name}", Toast.LENGTH_SHORT).show()
                                                is LaunchResult.NeedsPermissions -> onNeedsPermissions()
                                            }
                                        }
                                    }
                                    FeatureKind.AUTO_REFRESH -> {
                                        val cfg = json.decodeFromString<AutoRefreshConfig>(preset.configJson)
                                        when (AutomationLauncher.startAutoRefresh(context, cfg)) {
                                            LaunchResult.Ok -> Toast.makeText(context, "Loaded ${preset.name}", Toast.LENGTH_SHORT).show()
                                            is LaunchResult.NeedsPermissions -> onNeedsPermissions()
                                        }
                                    }
                                }
                            }
                        },
                        onDelete = {
                            scope.launch { MtApplication.instance.presetRepository.deletePreset(preset.id) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    count: Int,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "filterScale",
    )
    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MtBlue else MtRow)
            .border(1.dp, if (selected) MtBlue else MtBorder, RoundedCornerShape(999.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else MtMid,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
        )
        if (count > 0) {
            Text(
                text = count.toString(),
                color = if (selected) Color.White.copy(alpha = 0.9f) else MtBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) Color.White.copy(alpha = 0.22f) else MtBlue.copy(alpha = 0.12f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun CompactPresetRow(
    preset: MtPreset,
    showDivider: Boolean,
    onRun: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = featureAccent(preset.feature)
    val icon = featureIcon(preset.feature)
    val featureLabel = featureLabel(preset.feature)
    val targetCount = preset.targets.size
    val playInteraction = remember { MutableInteractionSource() }
    val playPressed by playInteraction.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (playPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 520f),
        label = "playScale",
    )

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRun)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    preset.name,
                    color = MtHi,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MetaChip(featureLabel, accent)
                    if (targetCount > 0) {
                        MetaChip(
                            "$targetCount tgt",
                            MtMid,
                        )
                    }
                    Text(
                        preset.createdAt.take(10),
                        color = MtMid.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .graphicsLayer { scaleX = playScale; scaleY = playScale }
                    .clip(RoundedCornerShape(10.dp))
                    .background(MtEmerald.copy(alpha = 0.14f))
                    .border(1.dp, MtEmerald.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = playInteraction,
                        indication = null,
                        onClick = onRun,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Load & start",
                    tint = MtEmerald,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFEF4444).copy(alpha = 0.1f))
                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 58.dp, end = 10.dp)
                    .height(1.dp)
                    .background(MtBorder.copy(alpha = 0.75f)),
            )
        }
    }
}

@Composable
private fun MetaChip(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun featureAccent(feature: FeatureKind): Color = when (feature) {
    FeatureKind.MULTI_TARGET -> Color(0xFF7C3AED)
    FeatureKind.MACRO_RECORDER -> Color(0xFFF43F5E)
    FeatureKind.FULL_PAGE_SCREENSHOT -> Color(0xFF0EA5E9)
    FeatureKind.AUTO_REFRESH -> Color(0xFFF59E0B)
    FeatureKind.SINGLE_TARGET -> Color(0xFF2563EB)
}

private fun featureIcon(feature: FeatureKind): ImageVector = when (feature) {
    FeatureKind.MULTI_TARGET -> Icons.Rounded.GridView
    FeatureKind.MACRO_RECORDER -> Icons.Rounded.FiberManualRecord
    FeatureKind.FULL_PAGE_SCREENSHOT -> Icons.Rounded.PhotoCamera
    FeatureKind.AUTO_REFRESH -> Icons.Rounded.Refresh
    FeatureKind.SINGLE_TARGET -> Icons.Rounded.AdsClick
}

private fun featureLabel(feature: FeatureKind): String = when (feature) {
    FeatureKind.MULTI_TARGET -> "Multi"
    FeatureKind.MACRO_RECORDER -> "Macro"
    FeatureKind.FULL_PAGE_SCREENSHOT -> "Shot"
    FeatureKind.AUTO_REFRESH -> "Refresh"
    FeatureKind.SINGLE_TARGET -> "Single"
}
