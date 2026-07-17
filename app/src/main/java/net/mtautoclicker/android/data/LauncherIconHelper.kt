package net.mtautoclicker.android.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * OxygenOS / ColorOS often force the **launcher** icon into the status bar
 * and ignore [android.app.Notification.Builder.setSmallIcon].
 * While macro recording, swap to a recording-styled launcher alias so the
 * status-bar chip can show a camera tile instead of the hand logo.
 */
object LauncherIconHelper {

    private const val DEFAULT = "net.mtautoclicker.android.LauncherDefault"
    private const val RECORDING = "net.mtautoclicker.android.LauncherRecording"

    fun setRecordingIcon(context: Context, recording: Boolean) {
        val pm = context.applicationContext.packageManager
        val flags = PackageManager.DONT_KILL_APP
        runCatching {
            if (recording) {
                setState(pm, context, DEFAULT, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, flags)
                setState(pm, context, RECORDING, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, flags)
            } else {
                setState(pm, context, RECORDING, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, flags)
                setState(pm, context, DEFAULT, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, flags)
            }
        }
    }

    private fun setState(
        pm: PackageManager,
        context: Context,
        className: String,
        newState: Int,
        flags: Int,
    ) {
        val component = ComponentName(context.packageName, className)
        val current = pm.getComponentEnabledSetting(component)
        if (current == newState) return
        pm.setComponentEnabledSetting(component, newState, flags)
    }
}
