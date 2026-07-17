package net.mtautoclicker.android.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.mtautoclicker.android.MtApplication
import net.mtautoclicker.android.data.AutoRefreshConfig
import net.mtautoclicker.android.data.FeatureKind
import net.mtautoclicker.android.data.LaunchableApp
import net.mtautoclicker.android.data.LaunchableAppHelper
import net.mtautoclicker.android.engine.AutomationLauncher
import net.mtautoclicker.android.engine.LaunchResult
import net.mtautoclicker.android.engine.formatInterval
import net.mtautoclicker.android.engine.formatStopSummary
import net.mtautoclicker.android.ui.components.AutoRefreshGradient
import net.mtautoclicker.android.ui.components.ExtensionStyleFeatureHero
import net.mtautoclicker.android.ui.components.FeaturePageScaffold
import net.mtautoclicker.android.data.PresetRepository
import net.mtautoclicker.android.ui.components.FeatureRecentPanel
import net.mtautoclicker.android.ui.components.FeatureStepUi
import net.mtautoclicker.android.ui.components.FeatureTab
import net.mtautoclicker.android.ui.components.FeatureTabBar
import net.mtautoclicker.android.ui.components.SettingsCard
import net.mtautoclicker.android.ui.theme.MtBorder
import net.mtautoclicker.android.ui.theme.MtCard
import net.mtautoclicker.android.ui.theme.MtEmerald
import net.mtautoclicker.android.ui.theme.MtHi
import net.mtautoclicker.android.ui.theme.MtMid
import net.mtautoclicker.android.ui.theme.MtRow

private val AccentAmber = Color(0xFFF59E0B)

@Composable
fun AutoRefreshScreen(onBack: () -> Unit, onNeedsPermissions: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = MtApplication.instance.settingsRepository
    val json = remember { Json { encodeDefaults = true; ignoreUnknownKeys = true } }

    var config by remember { mutableStateOf(AutoRefreshConfig()) }
    var tab by remember { mutableStateOf(FeatureTab.SETUP) }
    var status by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    var rememberChoice by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var loadingApps by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()
    var settingsY by remember { mutableIntStateOf(0) }

    val preferred by settings.preferredRefreshAppPackage.collectAsState(initial = null)
    val rememberPref by settings.rememberRefreshAppChoice.collectAsState(initial = false)
    val allPresets by MtApplication.instance.presetRepository.presets.collectAsState(initial = emptyList())
    val recents = remember(allPresets) {
        allPresets.filter { it.feature == FeatureKind.AUTO_REFRESH && PresetRepository.isRecent(it) }
    }

    LaunchedEffect(Unit) {
        loadingApps = true
        apps = withContext(Dispatchers.Default) { LaunchableAppHelper.listLaunchableApps(context) }
        loadingApps = false
    }

    LaunchedEffect(preferred, rememberPref, apps) {
        rememberChoice = rememberPref
        if (config.appPackage.isBlank() && apps.isNotEmpty()) {
            val pkg = when {
                rememberPref && preferred != null && apps.any { it.packageName == preferred } -> preferred
                else -> apps.firstOrNull { it.isPopular || it.isBrowser }?.packageName
                    ?: apps.first().packageName
            }
            if (!pkg.isNullOrBlank()) {
                config = config.copy(appPackage = pkg, rememberChoice = rememberChoice)
            }
        }
    }

    val selectedLabel = apps.firstOrNull { it.packageName == config.appPackage }?.label ?: "No app"
    val runSummary = listOf(
        formatInterval(config.interval),
        formatStopSummary(config.stop, "refreshes"),
        selectedLabel,
    ).joinToString(" · ")

    val filtered = remember(apps, query) {
        val q = query.trim().lowercase()
        apps.filter {
            q.isEmpty() ||
                it.label.lowercase().contains(q) ||
                it.packageName.lowercase().contains(q)
        }
    }

    fun startRefresh() {
        val pkg = config.appPackage
        if (pkg.isBlank()) {
            Toast.makeText(context, "Select an app first", Toast.LENGTH_SHORT).show()
            return
        }
        starting = true
        scope.launch {
            val cfg = config.copy(rememberChoice = rememberChoice)
            settings.setPreferredRefreshApp(pkg, rememberChoice)
            MtApplication.instance.presetRepository.addRecentPreset(
                feature = FeatureKind.AUTO_REFRESH,
                configJson = json.encodeToString(cfg),
                targets = emptyList(),
                displayName = "Recent · $selectedLabel · ${formatInterval(cfg.interval)}",
            )
            when (AutomationLauncher.startAutoRefresh(context, cfg)) {
                LaunchResult.Ok -> {
                    status = "App opened. Tap Refresh on the float bar to start."
                    MtApplication.instance.trackingService.trackEvent("auto_refresh_start")
                }
                is LaunchResult.NeedsPermissions -> {
                    Toast.makeText(context, "Enable Overlay & Accessibility first", Toast.LENGTH_LONG).show()
                    onNeedsPermissions()
                }
            }
            starting = false
        }
    }

    FeaturePageScaffold(title = "Auto Refresh", onBack = onBack, scrollState = scrollState) {
        FeatureTabBar(
            selected = tab,
            setupGradient = AutoRefreshGradient,
            onSelect = { tab = it },
        )

        if (tab == FeatureTab.SETUP) {
            ExtensionStyleFeatureHero(
                title = "Auto Refresh",
                description = "Pull-to-refresh any app on a timer — browsers, feeds, games lobbies, and more. Set interval & stop rules, pick an app, then tap Refresh on the float bar.",
                icon = Icons.Rounded.Refresh,
                accentGradient = AutoRefreshGradient,
                runSummary = runSummary,
                steps = listOf(
                    FeatureStepUi(Icons.Rounded.Timer, "Set refresh interval & stop condition"),
                    FeatureStepUi(Icons.Rounded.Apps, "Pick the app to refresh"),
                    FeatureStepUi(Icons.Rounded.PlayArrow, "Start — then tap Refresh on the float bar"),
                ),
                onStart = { startRefresh() },
                onEditSummary = {
                    scope.launch { scrollState.animateScrollTo(settingsY.coerceAtLeast(0)) }
                },
                starting = starting,
            )

            status?.let { Text(it, color = MtEmerald) }

            IntervalStopForm(
                interval = config.interval,
                stop = config.stop,
                onIntervalChange = { config = config.copy(interval = it) },
                onStopChange = { config = config.copy(stop = it) },
            )

            SettingsCard(
                title = "Choose app (${filtered.size})",
                modifier = Modifier.onGloballyPositioned { settingsY = it.positionInParent().y.toInt() },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MtRow)
                        .border(1.dp, MtBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, tint = MtMid, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        cursorBrush = SolidColor(AccentAmber),
                        textStyle = TextStyle(color = MtHi, fontSize = 13.sp),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text("Search Chrome, Instagram…", color = MtMid, fontSize = 13.sp)
                            }
                            inner()
                        },
                    )
                }

                when {
                    loadingApps -> Text("Loading apps…", color = MtMid, fontSize = 12.sp)
                    filtered.isEmpty() -> Text("No apps match", color = MtMid, fontSize = 12.sp)
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 68.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 4.dp),
                        ) {
                            items(filtered, key = { it.packageName }) { app ->
                                RefreshAppCell(
                                    app = app,
                                    selected = config.appPackage == app.packageName,
                                    onClick = {
                                        config = config.copy(appPackage = app.packageName)
                                    },
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MtCard)
                        .border(1.dp, MtBorder, RoundedCornerShape(14.dp))
                        .clickable { rememberChoice = !rememberChoice }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Checkbox(
                        checked = rememberChoice,
                        onCheckedChange = { rememberChoice = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = AccentAmber,
                            uncheckedColor = MtMid,
                        ),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Remember my choice", color = MtHi, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Skip re-picking next time", color = MtMid, fontSize = 11.sp)
                    }
                }
            }

            Text(
                "Tip: refresh uses a pull-down gesture (like pull-to-refresh). Works best on scrollable feeds & browser pages.",
                color = MtMid,
                fontSize = 12.sp,
            )
        } else {
            FeatureRecentPanel(
                featureLabel = "Auto Refresh",
                recents = recents,
                accent = AutoRefreshGradient.first(),
                onGoToSetup = { tab = FeatureTab.SETUP },
                onLoad = { preset ->
                    runCatching {
                        config = json.decodeFromString<AutoRefreshConfig>(preset.configJson)
                        rememberChoice = config.rememberChoice
                        tab = FeatureTab.SETUP
                        Toast.makeText(context, "Loaded ${preset.name}", Toast.LENGTH_SHORT).show()
                    }
                },
                onDelete = { preset ->
                    scope.launch { MtApplication.instance.presetRepository.deletePreset(preset.id) }
                },
                onRun = { preset ->
                    runCatching {
                        val loaded = json.decodeFromString<AutoRefreshConfig>(preset.configJson)
                        config = loaded
                        rememberChoice = loaded.rememberChoice
                        startRefresh()
                    }
                },
                onSave = { preset, name ->
                    scope.launch {
                        MtApplication.instance.presetRepository.promoteRecentToSaved(preset.id, name)
                        Toast.makeText(context, "Saved to Presets", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }
}

@Composable
private fun RefreshAppCell(
    app: LaunchableApp,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val iconBitmap = remember(app.packageName) {
        runCatching {
            app.icon?.toBitmap(96, 96, Bitmap.Config.ARGB_8888)?.asImageBitmap()
        }.getOrNull()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AccentAmber.copy(alpha = 0.14f) else MtRow)
            .border(
                1.dp,
                if (selected) AccentAmber else MtBorder.copy(alpha = 0.7f),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentAmber.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Apps, null, tint = AccentAmber, modifier = Modifier.size(20.dp))
                }
            }
            if (selected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    null,
                    tint = AccentAmber,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = app.label,
            color = MtHi,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 12.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
