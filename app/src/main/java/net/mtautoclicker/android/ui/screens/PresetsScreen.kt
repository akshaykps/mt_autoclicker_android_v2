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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.mtautoclicker.android.MtApplication
import net.mtautoclicker.android.data.FeatureKind
import net.mtautoclicker.android.data.MtPreset
import net.mtautoclicker.android.data.MultiTargetConfig
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
import net.mtautoclicker.android.ui.theme.MtPurple
import net.mtautoclicker.android.ui.theme.MtRow

private enum class PresetFilter { ALL, SINGLE, MULTI }

@Composable
fun PresetsScreen(onBack: () -> Unit, onNeedsPermissions: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presets by MtApplication.instance.presetRepository.presets.collectAsState(initial = emptyList())
    val json = remember { Json { ignoreUnknownKeys = true } }
    var filter by remember { mutableStateOf(PresetFilter.ALL) }

    val filtered = remember(presets, filter) {
        when (filter) {
            PresetFilter.ALL -> presets
            PresetFilter.SINGLE -> presets.filter { it.feature == FeatureKind.SINGLE_TARGET }
            PresetFilter.MULTI -> presets.filter { it.feature == FeatureKind.MULTI_TARGET }
        }
    }

    PageScaffold(title = "Presets", onBack = onBack) {
        // Header summary
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MtCard)
                .border(1.dp, MtBorder, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MtEmerald.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Bookmark, null, tint = MtEmerald, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Saved & recent", color = MtHi, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "${presets.size} preset${if (presets.size == 1) "" else "s"} ready to run",
                    color = MtMid,
                    fontSize = 12.sp,
                )
            }
        }

        // Filter chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip("All", filter == PresetFilter.ALL) { filter = PresetFilter.ALL }
            FilterChip("Single", filter == PresetFilter.SINGLE) { filter = PresetFilter.SINGLE }
            FilterChip("Multi", filter == PresetFilter.MULTI) { filter = PresetFilter.MULTI }
        }

        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, MtBorder, RoundedCornerShape(18.dp))
                    .background(MtRow)
                    .padding(vertical = 36.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Rounded.Bookmark,
                    null,
                    tint = MtMid.copy(alpha = 0.4f),
                    modifier = Modifier.size(48.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("No presets here", color = MtHi, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Run Single or Multi Target once — a Recent preset is saved automatically. Or tap Save on the float bar.",
                    color = MtMid,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp,
                )
            }
        } else {
            filtered.forEach { preset ->
                PresetListCard(
                    preset = preset,
                    onRun = {
                        runCatching {
                            when (preset.feature) {
                                FeatureKind.SINGLE_TARGET -> {
                                    val config = json.decodeFromString<SingleTargetConfig>(preset.configJson)
                                    AutomationLauncher.armSingle(config, preset.targets)
                                }
                                FeatureKind.MULTI_TARGET -> {
                                    val config = json.decodeFromString<MultiTargetConfig>(preset.configJson)
                                    AutomationLauncher.armMulti(config, preset.targets)
                                }
                            }
                            when (AutomationLauncher.startFloatBar(context)) {
                                LaunchResult.Ok -> Toast.makeText(context, "Loaded ${preset.name}", Toast.LENGTH_SHORT).show()
                                is LaunchResult.NeedsPermissions -> onNeedsPermissions()
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

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.White else MtMid,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MtBlue else MtRow)
            .border(1.dp, if (selected) MtBlue else MtBorder, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun PresetListCard(
    preset: MtPreset,
    onRun: () -> Unit,
    onDelete: () -> Unit,
) {
    val isMulti = preset.feature == FeatureKind.MULTI_TARGET
    val accent = if (isMulti) MtPurple else MtBlue
    val icon = if (isMulti) Icons.Rounded.GridView else Icons.Rounded.AdsClick
    val featureLabel = if (isMulti) "Multi Target" else "Single Target"
    val targetCount = preset.targets.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MtCard)
            .border(1.dp, MtBorder, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.16f))
                    .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(preset.name, color = MtHi, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge(featureLabel, accent)
                    if (targetCount > 0) {
                        Badge("$targetCount target${if (targetCount == 1) "" else "s"}", MtMid)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Created ${preset.createdAt.take(10)}", color = MtMid, fontSize = 11.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.horizontalGradient(listOf(MtEmerald, Color(0xFF16A34A))))
                    .clickable(onClick = onRun)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Load & start", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
