package net.mtautoclicker.android.data

import android.content.Context
import android.provider.Settings
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
