package net.mtautoclicker.android.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.mtautoclicker.android.MtApplication
import net.mtautoclicker.android.data.PermissionHelper
import net.mtautoclicker.android.data.PermissionKind
import net.mtautoclicker.android.data.ThemePreference
import net.mtautoclicker.android.ui.components.MtPrimaryButton
import net.mtautoclicker.android.ui.components.PageScaffold
import net.mtautoclicker.android.ui.theme.MtBlue
import net.mtautoclicker.android.ui.theme.MtBorder
import net.mtautoclicker.android.ui.theme.MtCard
import net.mtautoclicker.android.ui.theme.MtEmerald
import net.mtautoclicker.android.ui.theme.MtHi
import net.mtautoclicker.android.ui.theme.MtMid
import net.mtautoclicker.android.ui.theme.MtRow

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val analyticsEnabled by MtApplication.instance.settingsRepository.analyticsEnabled.collectAsState(initial = true)
    val themePreference by MtApplication.instance.settingsRepository.themePreference
        .collectAsState(initial = ThemePreference.SYSTEM)
    val overlayOk = PermissionHelper.canDrawOverlays(context)
    val accessibilityOk = PermissionHelper.isAccessibilityEnabled(context)

    PageScaffold(title = "Settings", onBack = onBack) {
        SettingsSection(
            icon = Icons.Rounded.Palette,
            title = "Appearance",
            subtitle = "Same theme options as the Chrome extension",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeOption(
                    label = "System",
                    icon = Icons.Rounded.PhoneAndroid,
                    selected = themePreference == ThemePreference.SYSTEM,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            MtApplication.instance.settingsRepository.setThemePreference(ThemePreference.SYSTEM)
                        }
                    },
                )
                ThemeOption(
                    label = "Light",
                    icon = Icons.Rounded.LightMode,
                    selected = themePreference == ThemePreference.LIGHT,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            MtApplication.instance.settingsRepository.setThemePreference(ThemePreference.LIGHT)
                        }
                    },
                )
                ThemeOption(
                    label = "Dark",
                    icon = Icons.Rounded.DarkMode,
                    selected = themePreference == ThemePreference.DARK,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            MtApplication.instance.settingsRepository.setThemePreference(ThemePreference.DARK)
                        }
                    },
                )
            }
        }

        SettingsSection(
            icon = Icons.Rounded.Settings,
            title = "Permissions",
            subtitle = "Required for the float bar and clicks",
        ) {
            PermissionStatusRow(
                icon = Icons.Rounded.Layers,
                label = "Display over other apps",
                granted = overlayOk,
                actionLabel = "Open",
                onAction = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                },
            )
            PermissionStatusRow(
                icon = Icons.Rounded.Accessibility,
                label = "Accessibility service",
                granted = accessibilityOk,
                actionLabel = "Open",
                onAction = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
            )
        }

        SettingsSection(
            icon = Icons.Rounded.BarChart,
            title = "Analytics",
            subtitle = "Anonymous usage helps improve MT Auto Clicker",
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MtRow)
                    .border(1.dp, MtBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (analyticsEnabled) "Analytics on" else "Analytics off",
                        color = MtHi,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Text(
                        "Device ID + events sent to mtautoclicker.net",
                        color = MtMid,
                        fontSize = 11.sp,
                    )
                }
                Switch(
                    checked = analyticsEnabled,
                    onCheckedChange = {
                        scope.launch {
                            MtApplication.instance.settingsRepository.setAnalyticsEnabled(it)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MtBlue,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = MtBorder,
                    ),
                )
            }
        }

        SettingsSection(
            icon = Icons.Rounded.Info,
            title = "About",
            subtitle = "MT Auto Clicker for Chrome OS & Android",
        ) {
            AboutRow("Version", "1.0.0")
            AboutRow("Website", "mtautoclicker.net") {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://mtautoclicker.net")),
                )
            }
        }
    }
}

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val missing = PermissionHelper.missingPermissions(context)

    PageScaffold(title = "Permissions required", onBack = onBack) {
        Text(
            "MT Auto Clicker needs these permissions before automation can run on Chrome OS or Android.",
            color = MtMid,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        missing.forEach { kind ->
            when (kind) {
                PermissionKind.OVERLAY -> SettingsSection(
                    icon = Icons.Rounded.Layers,
                    title = "Display over other apps",
                    subtitle = "Shows the floating control bar while you automate",
                ) {
                    MtPrimaryButton(
                        text = "Grant overlay permission",
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        },
                    )
                }
                PermissionKind.ACCESSIBILITY -> SettingsSection(
                    icon = Icons.Rounded.Accessibility,
                    title = "Accessibility",
                    subtitle = "Performs taps at screen positions you choose",
                ) {
                    MtPrimaryButton(
                        text = "Open Accessibility settings",
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                    )
                }
            }
        }
        if (missing.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MtEmerald.copy(alpha = 0.12f))
                    .border(1.dp, MtEmerald.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.CheckCircle, null, tint = MtEmerald)
                Text("All permissions granted.", color = MtHi, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SettingsSection(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MtCard)
            .border(1.dp, MtBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MtBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = MtBlue, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(title, color = MtHi, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, color = MtMid, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
        content()
    }
}

@Composable
private fun ThemeOption(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MtBlue else MtRow)
            .border(1.dp, if (selected) MtBlue else MtBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) Color.White else MtMid,
            modifier = Modifier.size(20.dp),
        )
        Text(
            label,
            color = if (selected) Color.White else MtHi,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PermissionStatusRow(
    icon: ImageVector,
    label: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MtRow)
            .border(1.dp, MtBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (granted) MtEmerald.copy(alpha = 0.15f)
                    else Color(0xFFF59E0B).copy(alpha = 0.15f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (granted) Icons.Rounded.CheckCircle else icon,
                contentDescription = null,
                tint = if (granted) MtEmerald else Color(0xFFD97706),
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = MtHi, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                if (granted) "Granted" else "Not granted",
                color = if (granted) MtEmerald else MtMid,
                fontSize = 11.sp,
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MtBlue.copy(alpha = 0.12f))
                .clickable(onClick = onAction)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(actionLabel, color = MtBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Icon(Icons.Rounded.OpenInNew, null, tint = MtBlue, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MtRow)
            .border(1.dp, MtBorder, RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MtMid, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = MtHi, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (onClick != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Rounded.OpenInNew, null, tint = MtBlue, modifier = Modifier.size(14.dp))
            }
        }
    }
}
