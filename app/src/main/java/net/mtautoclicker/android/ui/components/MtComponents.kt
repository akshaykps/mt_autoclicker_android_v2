package net.mtautoclicker.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mtautoclicker.android.ui.theme.MtBlue
import net.mtautoclicker.android.ui.theme.MtBorder
import net.mtautoclicker.android.ui.theme.MtCard
import net.mtautoclicker.android.ui.theme.MtDeep
import net.mtautoclicker.android.ui.theme.MtEmerald
import net.mtautoclicker.android.ui.theme.MtHi
import net.mtautoclicker.android.ui.theme.MtMid
import net.mtautoclicker.android.ui.theme.MtPurple
import net.mtautoclicker.android.ui.theme.MtRow

/** Safe area padding for punch-hole / status bar / gesture nav (OnePlus etc.). */
fun Modifier.mtSafeEdges(): Modifier =
    this
        .statusBarsPadding()
        .navigationBarsPadding()

@Composable
fun PageScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MtDeep)
            .mtSafeEdges()
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp)
            .padding(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MtCard)
                        .border(1.dp, MtBorder, CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MtHi,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = title,
                color = MtHi,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        content()
    }
}

@Composable
fun FeatureHeroCard(
    description: String,
    runSummary: String,
    startLabel: String = "Start",
    onStart: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(22.dp), clip = false)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF1A2A6C).copy(alpha = 0.55f),
                        MtBlue.copy(alpha = 0.28f),
                        MtPurple.copy(alpha = 0.18f),
                        MtCard,
                    ),
                ),
            )
            .border(1.dp, MtBlue.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MtBlue.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.AdsClick, contentDescription = null, tint = MtBlue, modifier = Modifier.size(22.dp))
            }
            Text("Quick start", color = MtHi, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Text(description, color = MtHi.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 18.sp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StepLine(1, "Press Start — app goes to background")
            StepLine(2, "Open the app you want to click")
            StepLine(3, "Tap + on the float bar to place a target")
            StepLine(4, "Hit Play on the float bar to begin")
        }
        Text(runSummary, color = MtMid, fontSize = 11.sp)
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MtBlue),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(6.dp))
            Text(startLabel.removePrefix("▶ ").removePrefix("▶"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
        }
    }
}

@Composable
private fun StepLine(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MtBlue.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", color = MtBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Text(text, color = MtMid, fontSize = 12.sp)
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MtCard)
            .border(1.dp, MtBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Text(text = label, color = MtMid, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = value, color = accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
fun FeatureCard(
    title: String,
    subtitle: String,
    accent: Color,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp))
            .background(MtCard)
            .border(1.dp, MtBorder.copy(alpha = 0.85f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.12f)),
                    ),
                )
                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MtHi, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = MtMid, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MtMid,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MtRow)
            .border(1.dp, MtBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = MtHi, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        content()
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = MtMid,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 2.dp),
    )
}

@Composable
fun MtPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
) {
    val resolvedColor = containerColor ?: MtBlue
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = resolvedColor),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MtTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MtBlue,
            unfocusedBorderColor = MtBorder,
            focusedTextColor = MtHi,
            unfocusedTextColor = MtHi,
            focusedLabelColor = MtMid,
            unfocusedLabelColor = MtMid,
            cursorColor = MtBlue,
        ),
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
fun HeroHeader(version: String, onSettingsClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(MtBlue.copy(alpha = 0.18f), Color.Transparent),
                ),
            )
            .padding(horizontal = 18.dp)
            .padding(top = 12.dp, bottom = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(listOf(MtPurple, MtBlue)),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AdsClick, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("MT Auto Clicker", color = MtHi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "v$version",
                            color = MtBlue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(MtBlue.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Text("Click less. Do more.", color = MtMid, fontSize = 13.sp)
                }
                if (onSettingsClick != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MtCard)
                            .border(1.dp, MtBorder, CircleShape)
                            .clickable(onClick = onSettingsClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Settings, null, tint = MtMid, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

val AccentBlue = Color(0xFF3B82F6)
val AccentViolet = Color(0xFF8B5CF6)
val AccentEmerald = Color(0xFF10B981)
val AccentRose = Color(0xFFF43F5E)
