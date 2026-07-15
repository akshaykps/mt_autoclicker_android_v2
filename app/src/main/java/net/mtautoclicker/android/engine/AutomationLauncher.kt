package net.mtautoclicker.android.engine

import android.app.Activity
import android.content.Context
import android.content.Intent
import net.mtautoclicker.android.data.AutomationPlan
import net.mtautoclicker.android.data.AutomationRunState
import net.mtautoclicker.android.data.ClickTarget
import net.mtautoclicker.android.data.FeatureKind
import net.mtautoclicker.android.data.MacroPlaybackConfig
import net.mtautoclicker.android.data.MultiTargetConfig
import net.mtautoclicker.android.data.PermissionHelper
import net.mtautoclicker.android.data.SavedMacro
import net.mtautoclicker.android.data.SingleTargetConfig
import net.mtautoclicker.android.service.ClickAutomationService
import net.mtautoclicker.android.service.FloatingOverlayService
import net.mtautoclicker.android.service.MacroOverlayService
import net.mtautoclicker.android.service.MacroPlaybackService

sealed class LaunchResult {
    data object Ok : LaunchResult()
    data class NeedsPermissions(val missing: List<net.mtautoclicker.android.data.PermissionKind>) : LaunchResult()
}

object AutomationLauncher {
    fun armSingle(config: SingleTargetConfig, targets: List<ClickTarget> = emptyList()) {
        AutomationHub.arm(AutomationPlan.Single(config, targets))
    }

    fun armMulti(config: MultiTargetConfig, targets: List<ClickTarget> = emptyList()) {
        AutomationHub.arm(AutomationPlan.Multi(config, targets))
    }

    fun startFloatBar(context: Context, minimizeApp: Boolean = true): LaunchResult {
        val missing = PermissionHelper.missingPermissions(context)
        if (missing.isNotEmpty()) return LaunchResult.NeedsPermissions(missing)
        dismissMacroSession(context)
        FloatingOverlayService.show(context)
        AutomationHub.setRunState(
            AutomationRunState.ARMED,
            "Float bar ready — open another app, tap +, place target, then Play.",
        )
        if (minimizeApp) {
            minimizeToBackground(context)
        }
        return LaunchResult.Ok
    }

    fun startMacroRecord(context: Context, minimizeApp: Boolean = true): LaunchResult {
        val missing = PermissionHelper.missingPermissions(context)
        if (missing.isNotEmpty()) return LaunchResult.NeedsPermissions(missing)
        dismissClickSession(context)
        MacroHub.armRecordReady()
        MacroOverlayService.show(context)
        if (minimizeApp) {
            minimizeToBackground(context)
        }
        return LaunchResult.Ok
    }

    fun startMacroPlayback(
        context: Context,
        macro: SavedMacro,
        config: MacroPlaybackConfig = MacroPlaybackConfig(),
        minimizeApp: Boolean = true,
    ): LaunchResult {
        val missing = PermissionHelper.missingPermissions(context)
        if (missing.isNotEmpty()) return LaunchResult.NeedsPermissions(missing)
        dismissClickSession(context)
        MacroHub.armPlayback(macro, config)
        MacroOverlayService.show(context)
        if (minimizeApp) {
            minimizeToBackground(context)
        }
        return LaunchResult.Ok
    }

    /** Send user out of MT Auto Clicker so targets are placed on other apps. */
    fun minimizeToBackground(context: Context) {
        when (context) {
            is Activity -> context.moveTaskToBack(true)
            else -> {
                val home = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(home)
            }
        }
    }

    fun killAll(context: Context) {
        dismissClickSession(context)
        dismissMacroSession(context)
        AutomationHub.stopAll()
        MacroHub.reset()
    }

    private fun dismissClickSession(context: Context) {
        ClickAutomationService.stop(context)
        FloatingOverlayService.dismiss(context)
    }

    private fun dismissMacroSession(context: Context) {
        MacroPlaybackService.stop(context)
        MacroOverlayService.dismiss(context)
    }

    fun featureKind(plan: AutomationPlan?): FeatureKind? = plan?.feature
}
