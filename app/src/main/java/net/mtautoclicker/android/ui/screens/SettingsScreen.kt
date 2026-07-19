package net.mtautoclicker.android.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tour
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mtautoclicker.android.MtApplication
import net.mtautoclicker.android.data.AppBackup
import net.mtautoclicker.android.data.PermissionHelper
import net.mtautoclicker.android.data.PermissionKind
import net.mtautoclicker.android.data.SettingsRepository
import net.mtautoclicker.android.data.ThemePreference
import net.mtautoclicker.android.ui.components.MtPrimaryButton
import net.mtautoclicker.android.ui.components.PageScaffold
import net.mtautoclicker.android.ui.theme.MtBlue
import net.mtautoclicker.android.ui.theme.MtBorder
import net.mtautoclicker.android.ui.theme.MtCard
import net.mtautoclicker.android.ui.theme.MtEmerald
import net.mtautoclicker.android.ui.theme.MtHi
import net.mtautoclicker.android.ui.theme.MtMid
import net.mtautoclicker.android.ui.theme.MtPurple
import net.mtautoclicker.android.ui.theme.MtRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private data class StatusBanner(val message: String, val success: Boolean)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onStartTour: (String) -> Unit = {},
    onOpenUserGuide: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val soundMuted by MtApplication.instance.settingsRepository.notificationSoundMuted.collectAsState(initial = false)
    val hapticsEnabled by MtApplication.instance.settingsRepository.hapticsEnabled.collectAsState(initial = true)
    val themePreference by MtApplication.instance.settingsRepository.themePreference
        .collectAsState(initial = ThemePreference.SYSTEM)
    val markerScale by MtApplication.instance.settingsRepository.targetMarkerScalePercent
        .collectAsState(initial = SettingsRepository.DEFAULT_MARKER_SCALE)
    val floatBarScale by MtApplication.instance.settingsRepository.floatBarScalePercent
        .collectAsState(initial = SettingsRepository.DEFAULT_FLOAT_BAR_SCALE)
    var deviceId by remember { mutableStateOf("…") }
    var storageLabel by remember { mutableStateOf("Loading…") }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<StatusBanner?>(null) }
    val overlayOk = PermissionHelper.canDrawOverlays(context)
    val accessibilityOk = PermissionHelper.isAccessibilityEnabled(context)

    fun showStatus(message: String, success: Boolean = true) {
        status = StatusBanner(message, success)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) {
            showStatus("Export cancelled", success = false)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            runCatching {
                val json = AppBackup.exportJson(
                    presets = MtApplication.instance.presetRepository,
                    macros = MtApplication.instance.macroRepository,
                    settings = MtApplication.instance.settingsRepository,
                )
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: error("Could not open save location")
                showStatus("Backup saved successfully")
            }.onFailure {
                showStatus("Failed to save — try again", success = false)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                    .orEmpty()
                val msg = AppBackup.importJson(
                    raw = text,
                    presets = MtApplication.instance.presetRepository,
                    macros = MtApplication.instance.macroRepository,
                    settings = MtApplication.instance.settingsRepository,
                )
                showStatus(msg)
                refreshStorage(onResult = { storageLabel = it })
            }.onFailure {
                showStatus("Import failed — check the file", success = false)
            }
        }
    }

    LaunchedEffect(Unit) {
        deviceId = MtApplication.instance.settingsRepository.getOrCreateDeviceId()
        refreshStorage(onResult = { storageLabel = it })
    }

    LaunchedEffect(status) {
        if (status != null) {
            delay(2800)
            status = null
        }
    }

    PageScaffold(
        title = "Settings",
        onBack = onBack,
        showKeyboardHide = false,
        contentSpacing = 10.dp,
        horizontalPadding = 14.dp,
    ) {
        status?.let { StatusToast(it) }

        // Compact community chip
        CompactChip(
            icon = Icons.Rounded.Groups,
            label = "Community Support",
            onClick = { openUrl(context, SettingsRepository.COMMUNITY_URL) },
        )

        SettingsGroup(label = "DEVICE", accent = Color(0xFF3B82F6)) {
            CompactDeviceId(deviceId) {
                clipboard.setText(AnnotatedString(deviceId))
                showStatus("Device ID copied")
            }
        }

        SettingsGroup(label = "APPEARANCE", accent = Color(0xFF8B5CF6)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CompactThemeChip("System", Icons.Rounded.PhoneAndroid, themePreference == ThemePreference.SYSTEM, Modifier.weight(1f)) {
                    scope.launch { MtApplication.instance.settingsRepository.setThemePreference(ThemePreference.SYSTEM) }
                }
                CompactThemeChip("Light", Icons.Rounded.LightMode, themePreference == ThemePreference.LIGHT, Modifier.weight(1f)) {
                    scope.launch { MtApplication.instance.settingsRepository.setThemePreference(ThemePreference.LIGHT) }
                }
                CompactThemeChip("Dark", Icons.Rounded.DarkMode, themePreference == ThemePreference.DARK, Modifier.weight(1f)) {
                    scope.launch { MtApplication.instance.settingsRepository.setThemePreference(ThemePreference.DARK) }
                }
            }
            GroupDivider()
            CompactMarkerRow(
                markerScale = markerScale,
                onDecrease = {
                    scope.launch {
                        MtApplication.instance.settingsRepository.setTargetMarkerScalePercent(
                            markerScale - SettingsRepository.MARKER_SCALE_STEP,
                        )
                    }
                },
                onIncrease = {
                    scope.launch {
                        MtApplication.instance.settingsRepository.setTargetMarkerScalePercent(
                            markerScale + SettingsRepository.MARKER_SCALE_STEP,
                        )
                    }
                },
            )
            GroupDivider()
            CompactFloatBarRow(
                floatBarScale = floatBarScale,
                onDecrease = {
                    scope.launch {
                        MtApplication.instance.settingsRepository.setFloatBarScalePercent(
                            floatBarScale - SettingsRepository.FLOAT_BAR_SCALE_STEP,
                        )
                    }
                },
                onIncrease = {
                    scope.launch {
                        MtApplication.instance.settingsRepository.setFloatBarScalePercent(
                            floatBarScale + SettingsRepository.FLOAT_BAR_SCALE_STEP,
                        )
                    }
                },
            )
        }

        SettingsGroup(label = "PREFERENCES", accent = Color(0xFF8B5CF6)) {
            CompactToggle(
                icon = Icons.Rounded.Notifications,
                iconTint = Color(0xFFF59E0B),
                title = "Notification sound",
                checked = !soundMuted,
                onCheckedChange = {
                    scope.launch { MtApplication.instance.settingsRepository.setNotificationSoundMuted(!it) }
                },
            )
            GroupDivider()
            CompactToggle(
                icon = Icons.Rounded.Vibration,
                iconTint = Color(0xFFEC4899),
                title = "Haptics",
                checked = hapticsEnabled,
                onCheckedChange = {
                    scope.launch { MtApplication.instance.settingsRepository.setHapticsEnabled(it) }
                },
            )
        }

        SettingsGroup(label = "PERMISSIONS", accent = Color(0xFF10B981)) {
            CompactLinkRow(
                icon = Icons.Rounded.Layers,
                iconTint = Color(0xFF3B82F6),
                title = "Overlay",
                trailing = if (overlayOk) "On" else "Off",
                trailingColor = if (overlayOk) MtEmerald else Color(0xFFD97706),
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                },
            )
            GroupDivider()
            CompactLinkRow(
                icon = Icons.Rounded.Accessibility,
                iconTint = Color(0xFF8B5CF6),
                title = "Accessibility",
                trailing = if (accessibilityOk) "On" else "Off",
                trailingColor = if (accessibilityOk) MtEmerald else Color(0xFFD97706),
                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            )
            GroupDivider()
            CompactLinkRow(
                icon = Icons.Rounded.Tour,
                iconTint = Color(0xFF14B8A6),
                title = "Permissions tour",
                trailing = null,
                onClick = { onStartTour("permissions") },
            )
        }

        SettingsGroup(label = "DATA · $storageLabel", accent = Color(0xFFEA580C)) {
            // Icon grid — no full-width stacked buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconAction(
                    icon = Icons.Rounded.Upload,
                    label = "Export",
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.weight(1f),
                ) {
                    val stamp = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    exportLauncher.launch("mt-autoclicker-backup-$stamp.json")
                }
                IconAction(
                    icon = Icons.Rounded.Download,
                    label = "Import",
                    tint = MtPurple,
                    modifier = Modifier.weight(1f),
                ) { importLauncher.launch("application/json") }
                IconAction(
                    icon = Icons.Rounded.History,
                    label = "Clear",
                    tint = Color(0xFFCA8A04),
                    modifier = Modifier.weight(1f),
                ) { confirmAction = "clear_history" }
                IconAction(
                    icon = Icons.Rounded.DeleteForever,
                    label = "Presets",
                    tint = Color(0xFFEA580C),
                    modifier = Modifier.weight(1f),
                ) { confirmAction = "delete_presets" }
                IconAction(
                    icon = Icons.Rounded.RestartAlt,
                    label = "Reset",
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.weight(1f),
                ) { confirmAction = "factory_reset" }
            }
        }

        SettingsGroup(label = "ABOUT", accent = Color(0xFF6366F1)) {
            CompactLinkRow(
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                iconTint = Color(0xFF2563EB),
                title = "User guide & tutorials",
                trailing = "Offline",
                onClick = onOpenUserGuide,
            )
            GroupDivider()
            CompactLinkRow(
                icon = Icons.Rounded.Info,
                iconTint = Color(0xFF6366F1),
                title = "Version",
                trailing = "1.0.0",
                showChevron = false,
                onClick = null,
            )
            GroupDivider()
            CompactLinkRow(
                icon = Icons.Rounded.Language,
                iconTint = Color(0xFF2563EB),
                title = "Website",
                trailing = SettingsRepository.WEBSITE_LABEL,
                onClick = { openUrl(context, SettingsRepository.WEBSITE_URL) },
            )
            GroupDivider()
            CompactLinkRow(
                icon = Icons.Rounded.Tour,
                iconTint = Color(0xFF14B8A6),
                title = "Replay tour",
                trailing = null,
                onClick = {
                    scope.launch { MtApplication.instance.androidCms.setOnboardingDone(false) }
                    onStartTour("onboarding")
                },
            )
            GroupDivider()
            CompactLinkRow(
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                iconTint = Color(0xFFEC4899),
                title = "Powered by WebTreta",
                trailing = null,
                onClick = { openUrl(context, SettingsRepository.WEBTRETA_URL) },
            )
        }
    }

    confirmAction?.let { action ->
        val (title, body) = when (action) {
            "clear_history" -> "Clear history?" to "Removes recent-run presets. Saved presets stay."
            "delete_presets" -> "Delete all presets?" to "Deletes every saved and recent preset. Macros are kept."
            else -> "Factory reset?" to "Deletes presets, macros, and settings. The app will restart fresh."
        }
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(title, color = MtHi) },
            text = { Text(body, color = MtMid, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    val a = action
                    confirmAction = null
                    scope.launch {
                        when (a) {
                            "clear_history" -> {
                                MtApplication.instance.presetRepository.clearRecentHistory()
                                showStatus("History cleared")
                            }
                            "delete_presets" -> {
                                MtApplication.instance.presetRepository.deleteAllPresets()
                                showStatus("All presets deleted")
                            }
                            "factory_reset" -> {
                                MtApplication.instance.presetRepository.deleteAllPresets()
                                MtApplication.instance.macroRepository.deleteAllMacros()
                                MtApplication.instance.settingsRepository.factoryResetSettings()
                                showStatus("Factory reset done — reopen the app")
                            }
                        }
                        refreshStorage(onResult = { storageLabel = it })
                        deviceId = MtApplication.instance.settingsRepository.getOrCreateDeviceId()
                    }
                }) {
                    Text(
                        "Confirm",
                        color = if (action == "factory_reset") Color(0xFFDC2626) else Color(0xFFEA580C),
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) {
                    Text("Cancel", color = MtMid)
                }
            },
            containerColor = MtCard,
        )
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private suspend fun refreshStorage(onResult: (String) -> Unit) {
    val (saved, recent) = MtApplication.instance.presetRepository.storageSummary()
    val macros = MtApplication.instance.macroRepository.macroCount()
    onResult("$saved saved · $recent recent · $macros macros")
}

@Composable
private fun StatusToast(banner: StatusBanner) {
    val tint = if (banner.success) MtEmerald else Color(0xFFEA580C)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (banner.success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(banner.message, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CompactChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF3B82F6).copy(alpha = 0.18f), Color(0xFF8B5CF6).copy(alpha = 0.16f)),
                ),
            )
            .border(1.dp, MtBlue.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true),
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TinyIcon(icon, Color(0xFF3B82F6))
        Text(label, color = MtHi, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = MtBlue, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun SettingsGroup(
    label: String,
    accent: Color = MtBlue,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Text(
                label,
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MtCard)
                .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(14.dp)),
            content = content,
        )
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 44.dp),
        thickness = 0.5.dp,
        color = MtBorder.copy(alpha = 0.7f),
    )
}

@Composable
private fun CompactDeviceId(deviceId: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true),
                onClick = onCopy,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TinyIcon(Icons.Rounded.PhoneAndroid, MtBlue)
        Column(modifier = Modifier.weight(1f)) {
            Text("Device ID", color = MtHi, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                deviceId,
                color = MtMid,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MtBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ContentCopy, "Copy", tint = MtBlue, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun CompactThemeChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(if (selected) MtBlue else MtRow, tween(160), label = "tbg")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, if (selected) MtBlue else MtBorder, RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true),
                onClick = onClick,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (selected) Color.White else MtMid, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            label,
            color = if (selected) Color.White else MtHi,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun CompactMarkerRow(
    markerScale: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    // Preview uses real scale (no hard clip) so 200% is fully visible.
    val outer = (22 * markerScale / 100f).coerceIn(12f, 44f).dp
    val inner = (8 * markerScale / 100f).coerceIn(5f, 16f).dp
    val previewBox = 48.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TinyIcon(Icons.Rounded.AdsClick, MtBlue)
        Column(modifier = Modifier.weight(1f)) {
            Text("Marker", color = MtHi, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("Target ring size", color = MtMid, fontSize = 10.sp)
        }
        Box(
            modifier = Modifier.size(previewBox),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(outer)
                    .clip(CircleShape)
                    .border(1.5.dp, MtBlue, CircleShape)
                    .background(MtBlue.copy(alpha = 0.12f)),
            )
            Box(
                modifier = Modifier
                    .size(inner)
                    .clip(CircleShape)
                    .background(MtBlue),
            )
        }
        StepChip(Icons.Rounded.Remove, markerScale > SettingsRepository.MIN_MARKER_SCALE, onDecrease)
        Text(
            "$markerScale%",
            color = MtMid,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.Center,
        )
        StepChip(Icons.Rounded.Add, markerScale < SettingsRepository.MAX_MARKER_SCALE, onIncrease)
    }
}

@Composable
private fun CompactFloatBarRow(
    floatBarScale: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    val btn = (14 * floatBarScale / 100f).coerceIn(10f, 28f).dp
    val pillW = (22 * floatBarScale / 100f).coerceIn(16f, 40f).dp
    val pillH = (40 * floatBarScale / 100f).coerceIn(28f, 72f).dp
    val previewH = 56.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TinyIcon(Icons.Rounded.Layers, Color(0xFF8B5CF6))
        Column(modifier = Modifier.weight(1f)) {
            Text("Float bar", color = MtHi, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("Single & Multi height", color = MtMid, fontSize = 10.sp)
        }
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(previewH),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(pillW)
                    .height(pillH)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF60A5FA).copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                    .padding(vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                Box(
                    modifier = Modifier
                        .size(btn)
                        .clip(CircleShape)
                        .background(MtBlue.copy(alpha = 0.45f)),
                )
                Box(
                    modifier = Modifier
                        .size(btn)
                        .clip(CircleShape)
                        .background(MtEmerald.copy(alpha = 0.55f)),
                )
                Box(
                    modifier = Modifier
                        .size((btn.value * 0.75f).dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.55f)),
                )
            }
        }
        StepChip(Icons.Rounded.Remove, floatBarScale > SettingsRepository.MIN_FLOAT_BAR_SCALE, onDecrease)
        Text(
            "$floatBarScale%",
            color = MtMid,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.Center,
        )
        StepChip(Icons.Rounded.Add, floatBarScale < SettingsRepository.MAX_FLOAT_BAR_SCALE, onIncrease)
    }
}

@Composable
private fun StepChip(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) MtRow else MtRow.copy(alpha = 0.4f))
            .border(1.dp, MtBorder, RoundedCornerShape(8.dp))
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(bounded = true),
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) MtHi else MtMid, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun CompactToggle(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconTint: Color = MtBlue,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TinyIcon(icon, iconTint)
        Text(title, color = MtHi, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.height(28.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = iconTint,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = MtBorder,
            ),
        )
    }
}

@Composable
private fun CompactLinkRow(
    icon: ImageVector?,
    title: String,
    trailing: String?,
    trailingColor: Color = MtMid,
    showChevron: Boolean = true,
    iconTint: Color = MtBlue,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(bounded = true),
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            TinyIcon(icon, iconTint)
        } else {
            Spacer(modifier = Modifier.size(28.dp))
        }
        Text(title, color = MtHi, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, color = trailingColor, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showChevron && onClick != null) {
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = iconTint, modifier = Modifier.size(13.dp))
        }
    }
}

@Composable
private fun TinyIcon(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun IconAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true),
                onClick = onClick,
            )
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(18.dp))
        }
        Text(label, color = MtHi, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val overlayOk = remember(refreshTick) { PermissionHelper.canDrawOverlays(context) }
    val accessibilityOk = remember(refreshTick) { PermissionHelper.isAccessibilityEnabled(context) }
    val grantedCount = listOf(overlayOk, accessibilityOk).count { it }
    val allGranted = grantedCount == 2
    val progress = grantedCount / 2f

    PageScaffold(
        title = "Permissions",
        onBack = onBack,
        showKeyboardHide = false,
        contentSpacing = 12.dp,
        horizontalPadding = 14.dp,
    ) {
        // Hero status card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        if (allGranted) {
                            listOf(MtEmerald.copy(alpha = 0.22f), MtCard)
                        } else {
                            listOf(Color(0xFFF59E0B).copy(alpha = 0.18f), MtCard)
                        },
                    ),
                )
                .border(
                    1.dp,
                    if (allGranted) MtEmerald.copy(alpha = 0.4f) else Color(0xFFF59E0B).copy(alpha = 0.4f),
                    RoundedCornerShape(20.dp),
                )
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (allGranted) MtEmerald.copy(alpha = 0.2f)
                        else Color(0xFFF59E0B).copy(alpha = 0.2f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (allGranted) Icons.Rounded.Verified else Icons.Rounded.Security,
                    null,
                    tint = if (allGranted) MtEmerald else Color(0xFFD97706),
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                if (allGranted) "You're all set" else "Almost ready",
                color = MtHi,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Text(
                if (allGranted) {
                    "Overlay and Accessibility are enabled. You can automate from Home."
                } else {
                    "Enable both permissions below so the float bar and taps can work."
                },
                color = MtMid,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = if (allGranted) MtEmerald else Color(0xFFF59E0B),
                trackColor = MtBorder,
            )
            Text(
                "$grantedCount of 2 enabled",
                color = if (allGranted) MtEmerald else Color(0xFFD97706),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
        }

        PermissionActionCard(
            icon = Icons.Rounded.Layers,
            accent = Color(0xFF3B82F6),
            title = "Display over other apps",
            body = "Shows the floating control bar and target markers on top of other apps.",
            granted = overlayOk,
            actionLabel = if (overlayOk) "Manage" else "Enable overlay",
            onAction = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            },
        )

        PermissionActionCard(
            icon = Icons.Rounded.Accessibility,
            accent = Color(0xFF8B5CF6),
            title = "Accessibility service",
            body = "Performs taps and gestures where you place targets. We never read passwords.",
            granted = accessibilityOk,
            actionLabel = if (accessibilityOk) "Manage" else "Open Accessibility",
            onAction = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
        )

        if (allGranted) {
            MtPrimaryButton(
                text = "Continue to Home",
                onClick = onBack,
                containerColor = MtEmerald,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF59E0B).copy(alpha = 0.1f))
                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Rounded.WarningAmber, null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                Text(
                    "After enabling a permission in system settings, return here — status updates automatically.",
                    color = MtMid,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun PermissionActionCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    body: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MtCard)
            .border(
                1.dp,
                if (granted) MtEmerald.copy(alpha = 0.35f) else accent.copy(alpha = 0.35f),
                RoundedCornerShape(18.dp),
            )
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
                    .background(
                        Brush.linearGradient(
                            listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.1f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = MtHi, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(body, color = MtMid, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        if (granted) MtEmerald.copy(alpha = 0.15f)
                        else Color(0xFFF59E0B).copy(alpha = 0.15f),
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                        null,
                        tint = if (granted) MtEmerald else Color(0xFFD97706),
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        if (granted) "On" else "Off",
                        color = if (granted) MtEmerald else Color(0xFFD97706),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        MtPrimaryButton(
            text = actionLabel,
            onClick = onAction,
            containerColor = if (granted) accent.copy(alpha = 0.75f) else accent,
        )
    }
}
