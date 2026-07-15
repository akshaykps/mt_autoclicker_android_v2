package net.mtautoclicker.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mtautoclicker.android.data.AutomationRunState
import net.mtautoclicker.android.engine.AutomationHub
import net.mtautoclicker.android.engine.formatDuration
import net.mtautoclicker.android.ui.components.AccentBlue
import net.mtautoclicker.android.ui.components.AccentEmerald
import net.mtautoclicker.android.ui.components.AccentViolet
import net.mtautoclicker.android.ui.components.FeatureCard
import net.mtautoclicker.android.ui.components.HeroHeader
import net.mtautoclicker.android.ui.components.MetricCard
import net.mtautoclicker.android.ui.components.MtPrimaryButton
import net.mtautoclicker.android.ui.components.SectionLabel
import net.mtautoclicker.android.ui.components.mtSafeEdges
import net.mtautoclicker.android.ui.theme.MtBorder
import net.mtautoclicker.android.ui.theme.MtCard
import net.mtautoclicker.android.ui.theme.MtDeep
import net.mtautoclicker.android.ui.theme.MtEmerald
import net.mtautoclicker.android.ui.theme.MtHi
import net.mtautoclicker.android.ui.theme.MtMid
import net.mtautoclicker.android.ui.theme.MtRow

enum class AppRoute {
    HOME,
    SINGLE_TARGET,
    MULTI_TARGET,
    PRESETS,
    SETTINGS,
    PERMISSIONS,
}

@Composable
fun HomeScreen(
    version: String,
    onNavigate: (AppRoute) -> Unit,
    onKillAll: () -> Unit,
) {
    val stats by AutomationHub.sessionStats.collectAsState()
    val snapshot by AutomationHub.snapshot.collectAsState()
    val running = snapshot.runState == AutomationRunState.RUNNING ||
        snapshot.runState == AutomationRunState.PAUSED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MtDeep)
            .mtSafeEdges()
            .verticalScroll(rememberScrollState()),
    ) {
        HeroHeader(
            version = version,
            onSettingsClick = { onNavigate(AppRoute.SETTINGS) },
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Session metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricCard(
                    label = "Clicks",
                    value = stats.totalClicks.toString(),
                    accent = AccentBlue,
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "Runtime",
                    value = formatDuration(stats.totalRuntimeMs),
                    accent = AccentViolet,
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "Runs",
                    value = stats.runCount.toString(),
                    accent = AccentEmerald,
                    modifier = Modifier.weight(1f),
                )
            }

            // Live status chip
            StatusBanner(
                running = running,
                message = snapshot.message ?: if (running) "Automation active" else "Ready to start",
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MtCard)
                    .border(1.dp, MtBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Quick start", color = MtHi, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "Pick a tool, press Start on the feature page, place targets from the float bar, then Play.",
                    color = MtMid,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            SectionLabel("Automation")
            FeatureCard(
                title = "Single Target",
                subtitle = "Repeat click on one spot — best for games & buttons",
                accent = AccentBlue,
                icon = Icons.Rounded.AdsClick,
                onClick = { onNavigate(AppRoute.SINGLE_TARGET) },
            )
            FeatureCard(
                title = "Multi Target",
                subtitle = "Click several points in sequence or all at once",
                accent = AccentViolet,
                icon = Icons.Rounded.GridView,
                onClick = { onNavigate(AppRoute.MULTI_TARGET) },
            )

            SectionLabel("Workflow")
            FeatureCard(
                title = "Presets",
                subtitle = "Recent runs & saved setups in one tap",
                accent = AccentEmerald,
                icon = Icons.Rounded.Bookmark,
                onClick = { onNavigate(AppRoute.PRESETS) },
            )
            FeatureCard(
                title = "Settings & Permissions",
                subtitle = "Theme, overlay, accessibility, privacy",
                accent = AccentBlue,
                icon = Icons.Rounded.Settings,
                onClick = { onNavigate(AppRoute.SETTINGS) },
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Emergency stop card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFDC2626).copy(alpha = 0.08f))
                    .border(1.dp, Color(0xFFDC2626).copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.StopCircle,
                        contentDescription = null,
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Emergency stop", color = MtHi, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(
                    "Stops all clicking and closes the float bar immediately.",
                    color = MtMid,
                    fontSize = 12.sp,
                )
                MtPrimaryButton(
                    text = "Stop everything now",
                    onClick = onKillAll,
                    containerColor = Color(0xFFDC2626),
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(running: Boolean, message: String) {
    val accent = if (running) MtEmerald else MtMid
    val bg = if (running) MtEmerald.copy(alpha = 0.12f) else MtRow
    val border = if (running) MtEmerald.copy(alpha = 0.40f) else MtBorder
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (running) "Active" else "Idle",
                color = MtHi,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
            Text(message, color = MtMid, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}
