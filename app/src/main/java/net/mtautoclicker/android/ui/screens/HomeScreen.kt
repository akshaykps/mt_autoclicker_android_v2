package net.mtautoclicker.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mtautoclicker.android.engine.AutomationHub
import net.mtautoclicker.android.engine.formatDuration
import net.mtautoclicker.android.ui.components.AccentBlue
import net.mtautoclicker.android.ui.components.AccentEmerald
import net.mtautoclicker.android.ui.components.AccentRose
import net.mtautoclicker.android.ui.components.AccentViolet
import net.mtautoclicker.android.ui.components.FeatureCard
import net.mtautoclicker.android.ui.components.HeroHeader
import net.mtautoclicker.android.ui.components.MetricCard
import net.mtautoclicker.android.ui.components.SectionLabel
import net.mtautoclicker.android.ui.components.mtSafeEdges
import net.mtautoclicker.android.ui.theme.MtBorder
import net.mtautoclicker.android.ui.theme.MtCard
import net.mtautoclicker.android.ui.theme.MtDeep
import net.mtautoclicker.android.ui.theme.MtHi
import net.mtautoclicker.android.ui.theme.MtMid

enum class AppRoute {
    HOME,
    SINGLE_TARGET,
    MULTI_TARGET,
    MACRO_RECORDER,
    FULL_PAGE_SCREENSHOT,
    AUTO_REFRESH,
    PRESETS,
    SETTINGS,
    PERMISSIONS,
}

@Composable
fun HomeScreen(
    version: String,
    onNavigate: (AppRoute) -> Unit,
) {
    val stats by AutomationHub.sessionStats.collectAsState()

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
            FeatureCard(
                title = "Macro Recorder",
                subtitle = "Record taps, holds & swipes — then play them back",
                accent = AccentRose,
                icon = Icons.Rounded.FiberManualRecord,
                onClick = { onNavigate(AppRoute.MACRO_RECORDER) },
            )
            FeatureCard(
                title = "Full Page Screenshot",
                subtitle = "Any app — WhatsApp, Instagram, Chrome & more",
                accent = Color(0xFF06B6D4),
                icon = Icons.Rounded.PhotoCamera,
                onClick = { onNavigate(AppRoute.FULL_PAGE_SCREENSHOT) },
            )
            FeatureCard(
                title = "Auto Refresh",
                subtitle = "Timed pull-to-refresh for browsers, feeds & apps",
                accent = Color(0xFFF59E0B),
                icon = Icons.Rounded.Refresh,
                onClick = { onNavigate(AppRoute.AUTO_REFRESH) },
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
        }
    }
}
