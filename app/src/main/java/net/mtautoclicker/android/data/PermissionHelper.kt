package net.mtautoclicker.android.data

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import net.mtautoclicker.android.service.MtAccessibilityService

object PermissionHelper {
    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun isAccessibilityEnabled(context: Context): Boolean {
        if (MtAccessibilityService.isEnabled()) return true
        val enabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1
        if (!enabled) return false
        val services = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val expected = "${context.packageName}/${MtAccessibilityService::class.java.name}"
        return services.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.areNotificationsEnabled()
    }

    fun missingPermissions(context: Context): List<PermissionKind> {
        val missing = mutableListOf<PermissionKind>()
        if (!canDrawOverlays(context)) missing += PermissionKind.OVERLAY
        if (!isAccessibilityEnabled(context)) missing += PermissionKind.ACCESSIBILITY
        return missing
    }
}

enum class PermissionKind {
    OVERLAY,
    ACCESSIBILITY,
}
