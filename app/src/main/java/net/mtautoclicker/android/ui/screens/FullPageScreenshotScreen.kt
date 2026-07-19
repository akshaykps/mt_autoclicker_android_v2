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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
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
import net.mtautoclicker.android.data.FeatureKind
import net.mtautoclicker.android.data.FullPageScreenshotConfig
import net.mtautoclicker.android.data.LaunchableApp
import net.mtautoclicker.android.data.LaunchableAppHelper
import net.mtautoclicker.android.engine.AutomationLauncher
import net.mtautoclicker.android.engine.LaunchResult
import net.mtautoclicker.android.data.PresetRepository
import net.mtautoclicker.android.ui.components.ExtensionStyleFeatureHero
import net.mtautoclicker.android.ui.components.FeaturePageScaffold
import net.mtautoclicker.android.ui.components.FeatureRecentPanel
import net.mtautoclicker.android.ui.components.FeatureStepUi
import net.mtautoclicker.android.ui.components.FeatureTab
import net.mtautoclicker.android.ui.components.FeatureTabBar
import net.mtautoclicker.android.ui.components.FullPageScreenshotGradient
import net.mtautoclicker.android.ui.components.ScreenshotAnimatedIcon
import net.mtautoclicker.android.ui.components.SettingsCard
import net.mtautoclicker.android.ui.theme.MtBorder
import net.mtautoclicker.android.ui.theme.MtCard
import net.mtautoclicker.android.ui.theme.MtEmerald
import net.mtautoclicker.android.ui.theme.MtHi
import net.mtautoclicker.android.ui.theme.MtMid
import net.mtautoclicker.android.ui.theme.MtRow

private val AccentCyan = Color(0xFF06B6D4)

private enum class AppFilter { ALL, POPULAR, BROWSERS }

@Composable
fun FullPageScreenshotScreen(onBack: () -> Unit, onNeedsPermissions: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = MtApplication.instance.settingsRepository
    val json = remember { Json { encodeDefaults = true; ignoreUnknownKeys = true } }

    val preferred by settings.preferredAppPackage.collectAsState(initial = null)
    val rememberPref by settings.rememberAppChoice.collectAsState(initial = false)
    val allPresets by MtApplication.instance.presetRepository.presets.collectAsState(initial = emptyList())
    val recents = remember(allPresets) {
        allPresets.filter { it.feature == FeatureKind.FULL_PAGE_SCREENSHOT && PresetRepository.isRecent(it) }
    }

    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var loadingApps by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<String?>(null) }
    var rememberChoice by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(FeatureTab.SETUP) }
    var status by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppFilter.ALL) }

    LaunchedEffect(Unit) {
        loadingApps = true
        apps = withContext(Dispatchers.Default) {
            LaunchableAppHelper.listLaunchableApps(context)
        }
        loadingApps = false
    }

    LaunchedEffect(preferred, rememberPref, apps) {
        rememberChoice = rememberPref
        if (selected == null && apps.isNotEmpty()) {
            selected = when {
                rememberPref && preferred != null && apps.any { it.packageName == preferred } -> preferred
                else -> apps.firstOrNull { it.isPopular }?.packageName
                    ?: apps.firstOrNull()?.packageName
            }
        }
    }

    val filtered = remember(apps, query, filter) {
        val q = query.trim().lowercase()
        apps.asSequence()
            .filter { app ->
                when (filter) {
                    AppFilter.ALL -> true
                    AppFilter.POPULAR -> app.isPopular
                    AppFilter.BROWSERS -> app.isBrowser
                }
            }
            .filter { app ->
                q.isEmpty() ||
                    app.label.lowercase().contains(q) ||
                    app.packageName.lowercase().contains(q)
            }
            .toList()
    }

    val selectedLabel = apps.firstOrNull { it.packageName == selected }?.label ?: "No app"
    val runSummary = "$selectedLabel · scroll & stitch"

    fun startCapture(appPackage: String, remember: Boolean) {
        starting = true
        scope.launch {
            settings.setPreferredApp(appPackage, remember)
            val config = FullPageScreenshotConfig(
                appPackage = appPackage,
                rememberChoice = remember,
            )
            val appLabel = apps.firstOrNull { it.packageName == appPackage }?.label ?: appPackage
            MtApplication.instance.presetRepository.addRecentPreset(
                feature = FeatureKind.FULL_PAGE_SCREENSHOT,
                configJson = json.encodeToString(config),
                targets = emptyList(),
                displayName = "Recent · $appLabel",
            )
            when (AutomationLauncher.startFullPageScreenshot(context, appPackage)) {
                LaunchResult.Ok -> {
                    status = "Allow Entire screen → open a long scrollable view → tap Snapshot."
                    MtApplication.instance.trackingService.trackEvent("full_page_screenshot_start")
                }
                is LaunchResult.NeedsPermissions -> {
                    Toast.makeText(context, "Enable Overlay & Accessibility first", Toast.LENGTH_LONG).show()
                    onNeedsPermissions()
                }
            }
            starting = false
        }
    }

    FeaturePageScaffold(title = "Full Page Screenshot", onBack = onBack) {
        FeatureTabBar(
            selected = tab,
            setupGradient = FullPageScreenshotGradient,
            onSelect = { tab = it },
        )

        if (tab == FeatureTab.SETUP) {
            ExtensionStyleFeatureHero(
                title = "Full Page Screenshot",
                description = "Works on any app with a scrollable screen — WhatsApp chats, Instagram, browsers, and more. We capture the entire screen, scroll, and stitch.",
                icon = Icons.Rounded.PhotoCamera,
                accentGradient = FullPageScreenshotGradient,
                runSummary = runSummary,
                steps = listOf(
                    FeatureStepUi(Icons.Rounded.Apps, "Pick any app below"),
                    FeatureStepUi(Icons.Rounded.PlayArrow, "Start — allow Entire screen capture"),
                    FeatureStepUi(Icons.Rounded.TouchApp, "Open a long list/chat, tap Snapshot — wait for Done"),
                ),
                onStart = {
                    val pkg = selected
                    if (pkg.isNullOrBlank()) {
                        Toast.makeText(context, "Select an app first", Toast.LENGTH_SHORT).show()
                    } else {
                        startCapture(pkg, rememberChoice)
                    }
                },
                onEditSummary = { },
                starting = starting,
                animatedIcon = {
                    ScreenshotAnimatedIcon(accent = Color(0xFF06B6D4), size = 36.dp)
                },
            )

            status?.let { Text(it, color = MtEmerald) }

            SettingsCard(title = "Choose app (${filtered.size})") {
                // Search
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
                        cursorBrush = SolidColor(AccentCyan),
                        textStyle = TextStyle(color = MtHi, fontSize = 13.sp),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text("Search WhatsApp, Instagram…", color = MtMid, fontSize = 13.sp)
                            }
                            inner()
                        },
                    )
                }

                // Filter chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip("All", filter == AppFilter.ALL) { filter = AppFilter.ALL }
                    FilterChip("Popular", filter == AppFilter.POPULAR) { filter = AppFilter.POPULAR }
                    FilterChip("Browsers", filter == AppFilter.BROWSERS) { filter = AppFilter.BROWSERS }
                }

                when {
                    loadingApps -> Text("Loading apps…", color = MtMid, fontSize = 12.sp)
                    filtered.isEmpty() -> Text("No apps match your search", color = MtMid, fontSize = 12.sp)
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 68.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 4.dp),
                        ) {
                            items(filtered, key = { it.packageName }) { app ->
                                AppGridCell(
                                    app = app,
                                    selected = selected == app.packageName,
                                    onClick = { selected = app.packageName },
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
                            checkedColor = AccentCyan,
                            uncheckedColor = MtMid,
                        ),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Remember my choice", color = MtHi, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Skip re-picking next time", color = MtMid, fontSize = 11.sp)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0891B2).copy(alpha = 0.08f))
                    .border(1.dp, AccentCyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Works best on scrollable screens",
                        color = MtHi,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    "Chats, feeds, and long pages stitch well. Allow Entire screen when Android asks. Saves to Gallery → Pictures → MT Auto Clicker.",
                    color = MtMid,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }

            Text(
                "Tip: after you Start, a Recent entry is saved on the Recent tab. Save any one to keep it under Presets.",
                color = MtMid,
                fontSize = 12.sp,
            )
        } else {
            FeatureRecentPanel(
                featureLabel = "Full Page Screenshot",
                recents = recents,
                accent = FullPageScreenshotGradient.first(),
                onGoToSetup = { tab = FeatureTab.SETUP },
                onLoad = { preset ->
                    runCatching {
                        val loaded = json.decodeFromString<FullPageScreenshotConfig>(preset.configJson)
                        selected = loaded.resolvedPackage().takeIf { it.isNotBlank() } ?: selected
                        rememberChoice = loaded.rememberChoice
                        tab = FeatureTab.SETUP
                        Toast.makeText(context, "Loaded ${preset.name}", Toast.LENGTH_SHORT).show()
                    }
                },
                onDelete = { preset ->
                    scope.launch { MtApplication.instance.presetRepository.deletePreset(preset.id) }
                },
                onRun = { preset ->
                    runCatching {
                        val loaded = json.decodeFromString<FullPageScreenshotConfig>(preset.configJson)
                        val pkg = loaded.resolvedPackage()
                        if (pkg.isBlank()) {
                            Toast.makeText(context, "Preset has no app", Toast.LENGTH_SHORT).show()
                        } else {
                            selected = pkg
                            rememberChoice = loaded.rememberChoice
                            startCapture(pkg, loaded.rememberChoice)
                        }
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
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.White else MtMid,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) AccentCyan else MtRow)
            .border(1.dp, if (selected) AccentCyan else MtBorder, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun AppGridCell(
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
            .background(if (selected) AccentCyan.copy(alpha = 0.14f) else MtRow)
            .border(
                1.dp,
                if (selected) AccentCyan else MtBorder.copy(alpha = 0.7f),
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
                        .background(AccentCyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Apps, null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                }
            }
            if (selected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = AccentCyan,
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
