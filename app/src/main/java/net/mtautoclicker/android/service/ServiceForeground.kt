package net.mtautoclicker.android.service

import android.app.Notification
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build

/**
 * Starts a special-use foreground service safely across API levels.
 *
 * [ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE] exists only on Android 14+.
 * Passing it on Android 10–13 (common on Xiaomi Redmi 9 etc.) crashes the process
 * when Play is pressed on the float bar.
 */
internal fun Service.startSpecialUseForeground(id: Int, notification: Notification) {
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
            startForeground(
                id,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        }
        else -> {
            @Suppress("DEPRECATION")
            startForeground(id, notification)
        }
    }
}
