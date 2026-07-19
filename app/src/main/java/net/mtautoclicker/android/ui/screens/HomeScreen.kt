package net.mtautoclicker.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mtautoclicker.android.MtApplication
import net.mtautoclicker.android.engine.AutomationHub
import net.mtautoclicker.android.engine.formatDuration
import net.mtautoclicker.android.ui.components.AccentBlue
import net.mtautoclicker.android.ui.components.AccentEmerald
import net.mtautoclicker.android.ui.components.AccentRose
import net.mtautoclicker.android.ui.components.AccentViolet
import net.mtautoclicker.android.ui.components.FeatureCard
import net.mtautoclicker.android.ui.components.HeroHeader
import net.mtautoclicker.android.ui.components.LocalDockScrollReporter
import net.mtautoclicker.android.ui.components.MetricCard
import net.mtautoclicker.android.ui.components.SectionLabel
import net.mtautoclicker.android.ui.components.mtSafeEdges
import net.mtautoclicker.android.ui.theme.MtBorder
import net.mtautoclicker.android.ui.theme.MtCard
import net.mtautoclicker.android.ui.theme.MtDeep
import net.mtautoclicker.android.ui.theme.MtHi
import net.mtautoclicker.android.ui.theme.MtMid
import net.mtautoclicker.android.ui.theme.MtPurple

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
    FEEDBACK,
    NOTIFICATIONS,
}

/** Routes that show the floating workflow dock. */
val DockRoutes = setOf(
    AppRoute.HOME,
    AppRoute.PRESETS,
    AppRoute.FEEDBACK,
    AppRoute.NOTIFICATIONS,
    AppRoute.SETTINGS,
)

@Composable
fun HomeScreen(
    version: String,
    onNavigate: (AppRoute) -> Unit,
) {
    val stats by AutomationHub.sessionStats.collectAsState()
    var unreadNotifications by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    val dockScrollReporter = LocalDockScrollReporter.current

    LaunchedEffect(Unit) {
        unreadNotifications = MtApplication.instance.androidCms
            .fetchInboxNotifications()
            .unread_count
    }

    LaunchedEffect(scrollState.value) {
        dockScrollReporter.onScroll(scrollState.value)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MtDeep)
            .mtSafeEdges()
            .verticalScroll(scrollState),
    ) {
        HeroHeader(
            version = version,
            compact = true,
            notificationCount = unreadNotifications,
            onNotificationsClick = { onNavigate(AppRoute.NOTIFICATIONS) },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricCard(
                    label = "Clicks",
                    value = stats.totalClicks.toString(),
                    accent = AccentBlue,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "Runtime",
                    value = formatDuration(stats.totalRuntimeMs),
                    accent = AccentViolet,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "Runs",
                    value = stats.runCount.toString(),
                    accent = AccentEmerald,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }

            QuickStartStrip()

            SectionLabel("Automation")

            FeatureCard(
                title = "Single Target",
                subtitle = "Repeat click on one spot",
                accent = AccentBlue,
                icon = Icons.Rounded.AdsClick,
                compact = true,
                onClick = { onNavigate(AppRoute.SINGLE_TARGET) },
            )
            FeatureCard(
                title = "Multi Target",
                subtitle = "Sequence or all-at-once clicks",
                accent = AccentViolet,
                icon = Icons.Rounded.GridView,
                compact = true,
                onClick = { onNavigate(AppRoute.MULTI_TARGET) },
            )
            FeatureCard(
                title = "Macro Recorder",
                subtitle = "Record taps, holds & swipes",
                accent = AccentRose,
                icon = Icons.Rounded.FiberManualRecord,
                compact = true,
                onClick = { onNavigate(AppRoute.MACRO_RECORDER) },
            )
            FeatureCard(
                title = "Full Page Screenshot",
                subtitle = "WhatsApp, Instagram, Chrome…",
                accent = Color(0xFF06B6D4),
                icon = Icons.Rounded.PhotoCamera,
                compact = true,
                onClick = { onNavigate(AppRoute.FULL_PAGE_SCREENSHOT) },
            )
            FeatureCard(
                title = "Auto Refresh",
                subtitle = "Timed pull-to-refresh",
                accent = Color(0xFFF59E0B),
                icon = Icons.Rounded.Refresh,
                compact = true,
                onClick = { onNavigate(AppRoute.AUTO_REFRESH) },
            )
        }
    }
}

@Composable
private fun QuickStartStrip() {
    var selectedStep by remember { mutableIntStateOf(0) }
    val steps = listOf(
        QuickStep("Pick", "Choose a tool", Icons.Rounded.TouchApp, AccentBlue),
        QuickStep("Start", "Open feature", Icons.Rounded.PlayArrow, AccentViolet),
        QuickStep("Place", "Drop targets", Icons.Rounded.AddCircle, AccentEmerald),
        QuickStep("Play", "Run clicks", Icons.Rounded.AdsClick, AccentRose),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MtCard)
            .border(1.dp, MtBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Quick start", color = MtHi, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(
                steps[selectedStep].hint,
                color = MtMid,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            steps.forEachIndexed { index, step ->
                val selected = selectedStep == index
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (pressed) 0.92f else 1f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
                    label = "qsScale$index",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) step.accent.copy(alpha = 0.14f)
                            else Color.Transparent,
                        )
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) { selectedStep = index }
                        .padding(vertical = 6.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(step.accent.copy(alpha = if (selected) 0.22f else 0.12f))
                            .border(
                                1.dp,
                                step.accent.copy(alpha = if (selected) 0.55f else 0.25f),
                                RoundedCornerShape(9.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            step.icon,
                            contentDescription = step.label,
                            tint = step.accent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        step.label,
                        color = if (selected) step.accent else MtMid,
                        fontSize = 9.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private data class QuickStep(
    val label: String,
    val hint: String,
    val icon: ImageVector,
    val accent: Color,
)

@Composable
fun MtFloatingDock(
    currentRoute: AppRoute,
    unreadNotifications: Int,
    onNavigate: (AppRoute) -> Unit,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        DockItem(AppRoute.HOME, "Home", Icons.Rounded.Home, AccentBlue),
        DockItem(AppRoute.PRESETS, "Presets", Icons.Rounded.Bookmark, AccentEmerald),
        DockItem(AppRoute.FEEDBACK, "Feedback", Icons.AutoMirrored.Rounded.Chat, MtPurple),
        DockItem(AppRoute.NOTIFICATIONS, "Inbox", Icons.Rounded.Notifications, Color(0xFFD97706)),
        DockItem(AppRoute.SETTINGS, "Settings", Icons.Rounded.Settings, AccentBlue),
    )

    val collapseProgress by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "dockCollapse",
    )
    val horizontalPad by animateDpAsState(
        targetValue = if (collapsed) 32.dp else 12.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "dockHPad",
    )
    val verticalPad by animateDpAsState(
        targetValue = if (collapsed) 8.dp else 8.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "dockVPad",
    )
    val barRadius by animateDpAsState(
        targetValue = if (collapsed) 28.dp else 24.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "dockRadius",
    )
    val innerHPad by animateDpAsState(
        targetValue = if (collapsed) 8.dp else 4.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "dockInnerH",
    )
    val innerVPad by animateDpAsState(
        targetValue = if (collapsed) 4.dp else 6.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "dockInnerV",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = horizontalPad, vertical = verticalPad),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = (10f + 6f * collapseProgress).dp,
                    shape = RoundedCornerShape(barRadius),
                    clip = false,
                )
                .clip(RoundedCornerShape(barRadius))
                .background(MtCard.copy(alpha = 0.96f))
                .border(1.dp, MtBorder.copy(alpha = 0.9f), RoundedCornerShape(barRadius))
                .padding(horizontal = innerHPad, vertical = innerVPad),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val active = currentRoute == item.route
                DockIconButton(
                    item = item,
                    active = active,
                    collapsed = collapsed,
                    badgeCount = if (item.route == AppRoute.NOTIFICATIONS) unreadNotifications else 0,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(item.route) },
                )
            }
        }
    }
}

private data class DockItem(
    val route: AppRoute,
    val label: String,
    val icon: ImageVector,
    val accent: Color,
)

@Composable
private fun DockIconButton(
    item: DockItem,
    active: Boolean,
    collapsed: Boolean,
    badgeCount: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 520f),
        label = "dockPress",
    )
    val pillColor by animateColorAsState(
        targetValue = if (active) item.accent.copy(alpha = if (collapsed) 0.16f else 0.14f)
        else Color.Transparent,
        animationSpec = tween(160),
        label = "dockPill",
    )
    val iconTint by animateColorAsState(
        targetValue = if (active) item.accent else MtMid,
        animationSpec = tween(160),
        label = "dockTint",
    )
    val iconSize by animateDpAsState(
        targetValue = if (collapsed) 22.dp else 24.dp,
        animationSpec = tween(160),
        label = "dockIconSize",
    )
    val vPad by animateDpAsState(
        targetValue = if (collapsed) 8.dp else 6.dp,
        animationSpec = tween(160),
        label = "dockItemPad",
    )

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .background(pillColor)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = vPad),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = iconTint,
                modifier = Modifier.size(iconSize),
            )
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .size(if (collapsed) 8.dp else 9.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDC2626))
                        .border(1.dp, MtCard, CircleShape),
                )
            }
        }
        // Remove label from layout when collapsed so no empty name space remains.
        AnimatedVisibility(
            visible = !collapsed,
            enter = fadeIn(tween(120)) + expandVertically(expandFrom = Alignment.Top, animationSpec = tween(160)),
            exit = fadeOut(tween(80)) + shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(140)),
        ) {
            Text(
                text = item.label,
                color = iconTint,
                fontSize = 10.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
