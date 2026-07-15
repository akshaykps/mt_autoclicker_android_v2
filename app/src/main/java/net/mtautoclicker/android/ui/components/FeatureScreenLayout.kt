package net.mtautoclicker.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mtautoclicker.android.data.MtPreset
import net.mtautoclicker.android.ui.theme.LocalMtPalette
import net.mtautoclicker.android.ui.theme.MtBlue
import net.mtautoclicker.android.ui.theme.MtBorder
import net.mtautoclicker.android.ui.theme.MtCard
import net.mtautoclicker.android.ui.theme.MtDeep
import net.mtautoclicker.android.ui.theme.MtEmerald
import net.mtautoclicker.android.ui.theme.MtHi
import net.mtautoclicker.android.ui.theme.MtMid
import net.mtautoclicker.android.ui.theme.MtPurple
import net.mtautoclicker.android.ui.theme.MtRow

enum class FeatureTab { SETUP, PRESETS }

data class FeatureStepUi(
    val icon: ImageVector,
    val text: String,
)

@Composable
fun FeatureTabBar(
    selected: FeatureTab,
    setupGradient: List<Color>,
    presetsGradient: List<Color> = listOf(Color(0xFF8B5CF6), Color(0xFF7C3AED)),
    onSelect: (FeatureTab) -> Unit,
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
        FeatureTabChip(
            label = "Setup",
            icon = Icons.Rounded.Tune,
            selected = selected == FeatureTab.SETUP,
            gradient = setupGradient,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(FeatureTab.SETUP) },
        )
        FeatureTabChip(
            label = "Presets",
            icon = Icons.Rounded.PlayArrow,
            selected = selected == FeatureTab.PRESETS,
            gradient = presetsGradient,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(FeatureTab.PRESETS) },
        )
    }
}

@Composable
private fun FeatureTabChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    gradient: List<Color>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier
                        .background(Brush.horizontalGradient(gradient))
                        .shadow(6.dp, shape, clip = false)
                } else {
                    Modifier.background(Color.Transparent)
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color.White else MtMid,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = if (selected) Color.White else MtMid,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

@Composable
fun ExtensionStyleFeatureHero(
    title: String,
    description: String,
    icon: ImageVector,
    accentGradient: List<Color>,
    runSummary: String,
    steps: List<FeatureStepUi>,
    onStart: () -> Unit,
    onEditSummary: () -> Unit,
    starting: Boolean = false,
) {
    var stepsOpen by remember { mutableStateOf(true) }
    val cardShape = RoundedCornerShape(18.dp)
    val dark = LocalMtPalette.current.isDark
    val summaryBg = if (dark) Color(0xFF0EA5E9).copy(alpha = 0.12f) else Color(0xFF0369A1).copy(alpha = 0.10f)
    val summaryBorder = if (dark) Color(0xFF0EA5E9).copy(alpha = 0.40f) else Color(0xFF0284C7).copy(alpha = 0.45f)
    val summaryText = if (dark) Color(0xFFE0F2FE) else Color(0xFF0C4A6E)
    val summaryEditBg = if (dark) MtCard else Color.White
    val summaryEditTint = if (dark) Color(0xFF7DD3FC) else Color(0xFF0369A1)
    val summaryEditBorder = if (dark) Color(0xFF38BDF8).copy(alpha = 0.45f) else Color(0xFF0284C7).copy(alpha = 0.50f)
    val howBg = if (dark) MtDeep.copy(alpha = 0.65f) else MtRow

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, cardShape, clip = false)
            .clip(cardShape)
            .background(MtCard)
            .border(1.dp, MtBorder.copy(alpha = 0.9f), cardShape),
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accentGradient.first().copy(alpha = 0.14f),
                            MtCard,
                        ),
                    ),
                )
                .padding(18.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(accentGradient)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = MtHi, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(description, color = MtMid, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 0.dp, color = Color.Transparent)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Start
            Button(
                onClick = onStart,
                enabled = !starting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(accentGradient)),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            if (starting) "Starting…" else "Start",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }

            // Summary pill (theme-aware contrast for light + dark)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(summaryBg)
                    .border(1.dp, summaryBorder, RoundedCornerShape(12.dp))
                    .clickable(onClick = onEditSummary)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            ) {
                Text(
                    text = "Starting with $runSummary",
                    color = summaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 22.dp),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(summaryEditBg)
                        .border(1.dp, summaryEditBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit settings", tint = summaryEditTint, modifier = Modifier.size(13.dp))
                }
            }

            // How it works
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(howBg)
                    .border(1.dp, MtBorder.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { stepsOpen = !stepsOpen },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "HOW IT WORKS",
                        color = MtMid,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                    )
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        null,
                        tint = MtMid,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(if (stepsOpen) 180f else 0f),
                    )
                }
                AnimatedVisibility(visible = stepsOpen) {
                    Column(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        steps.forEachIndexed { index, step ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(MtRow)
                                        .border(1.dp, MtBorder, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("${index + 1}", color = MtMid, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Icon(step.icon, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                                Text(step.text, color = MtMid, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeaturePresetsPanel(
    featureLabel: String,
    presets: List<MtPreset>,
    accent: Color,
    onGoToSetup: () -> Unit,
    onLoad: (MtPreset) -> Unit,
    onDelete: (MtPreset) -> Unit,
    onRun: (MtPreset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column {
                Text("Saved presets", color = MtHi, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "${presets.size} preset${if (presets.size == 1) "" else "s"} for this feature",
                    color = MtMid,
                    fontSize = 12.sp,
                )
            }
        }

        if (presets.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MtBorder.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    .padding(vertical = 36.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    null,
                    tint = MtMid.copy(alpha = 0.35f),
                    modifier = Modifier.size(48.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("No presets yet", color = MtHi, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                        "Use Multi Target, place markers, and Play — your last run is saved here automatically. Or tap Save on the float bar.",
                        color = MtMid,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onGoToSetup,
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Rounded.Tune, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Go to Setup", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            presets.forEach { preset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MtCard)
                        .border(1.dp, MtBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(MtEmerald, Color(0xFF16A34A))))
                            .clickable { onRun(preset) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(preset.name, color = MtHi, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(preset.createdAt.take(10), color = MtMid, fontSize = 10.sp)
                    }
                    Icon(
                        Icons.Rounded.Settings,
                        null,
                        tint = MtBlue,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MtBlue.copy(alpha = 0.12f))
                            .clickable { onLoad(preset) }
                            .padding(8.dp),
                    )
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Delete preset",
                        tint = Color(0xFFF87171),
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                            .clickable { onDelete(preset) }
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

/** Shared page wrapper matching extension feature pages. */
@Composable
fun FeaturePageScaffold(
    title: String,
    onBack: () -> Unit,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    PageScaffold(title = title, onBack = onBack, scrollState = scrollState, content = content)
}

val SingleTargetGradient = listOf(Color(0xFF06B6D4), Color(0xFF2563EB))
val MultiTargetGradient = listOf(Color(0xFF10B981), Color(0xFF059669))
