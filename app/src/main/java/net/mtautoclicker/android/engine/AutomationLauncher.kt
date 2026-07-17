package net.mtautoclicker.android.engine

import android.app.Activity
import android.content.Context
import android.content.Intent
import net.mtautoclicker.android.ScreenCapturePermissionActivity
import net.mtautoclicker.android.data.AutoRefreshConfig
import net.mtautoclicker.android.data.AutomationPlan
import net.mtautoclicker.android.data.AutomationRunState
import net.mtautoclicker.android.data.ClickTarget
import net.mtautoclicker.android.data.FeatureKind
import net.mtautoclicker.android.data.LaunchableAppHelper
import net.mtautoclicker.android.data.MacroPlaybackConfig
import net.mtautoclicker.android.data.MultiTargetConfig
import net.mtautoclicker.android.data.PermissionHelper
import net.mtautoclicker.android.data.SavedMacro
import net.mtautoclicker.android.data.SingleTargetConfig
import net.mtautoclicker.android.service.AutoRefreshService
import net.mtautoclicker.android.service.ClickAutomationService
import net.mtautoclicker.android.service.FloatingOverlayService
import net.mtautoclicker.android.service.FullPageCaptureService
import net.mtautoclicker.android.service.MacroOverlayService
import net.mtautoclicker.android.service.MacroPlaybackService
import net.mtautoclicker.android.service.RefreshOverlayService
import net.mtautoclicker.android.service.ScreenshotOverlayService

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
        dismissScreenshotSession(context)
        dismissRefreshSession(context)
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
        dismissScreenshotSession(context)
        dismissRefreshSession(context)
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
        dismissScreenshotSession(context)
        dismissRefreshSession(context)
        MacroHub.armPlayback(macro, config)
        MacroOverlayService.show(context)
        if (minimizeApp) {
            minimizeToBackground(context)
        }
        return LaunchResult.Ok
    }

    fun startFullPageScreenshot(context: Context, browserPackage: String): LaunchResult {
        val missing = PermissionHelper.missingPermissions(context)
        if (missing.isNotEmpty()) return LaunchResult.NeedsPermissions(missing)
        dismissClickSession(context)
        dismissMacroSession(context)
        dismissRefreshSession(context)
        ScreenCapturePermissionActivity.start(context, browserPackage)
        return LaunchResult.Ok
    }

    fun startAutoRefresh(context: Context, config: AutoRefreshConfig, minimizeApp: Boolean = true): LaunchResult {
        val missing = PermissionHelper.missingPermissions(context)
        if (missing.isNotEmpty()) return LaunchResult.NeedsPermissions(missing)
        dismissClickSession(context)
        dismissMacroSession(context)
        dismissScreenshotSession(context)
        AutoRefreshHub.arm(config)
        RefreshOverlayService.show(context)
        if (config.appPackage.isNotBlank()) {
            LaunchableAppHelper.launchApp(context, config.appPackage)
        }
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
        dismissScreenshotSession(context)
        dismissRefreshSession(context)
        AutomationHub.stopAll()
        MacroHub.reset()
        FullPageCaptureHub.reset()
        AutoRefreshHub.reset()
    }

    private fun dismissClickSession(context: Context) {
        ClickAutomationService.stop(context)
        FloatingOverlayService.dismiss(context)
    }

    private fun dismissMacroSession(context: Context) {
        MacroPlaybackService.stop(context)
        MacroOverlayService.dismiss(context)
    }

    private fun dismissScreenshotSession(context: Context) {
        ScreenshotOverlayService.dismiss(context)
        FullPageCaptureService.stop(context)
    }

    private fun dismissRefreshSession(context: Context) {
        RefreshOverlayService.dismiss(context)
        AutoRefreshService.stop(context)
    }

    /** Call when the main app UI is visible so float bars don't sit on top of MT screens. */
    fun onMainAppForeground(context: Context) {
        if (FullPageCaptureHub.shouldAutoDismissOnMainApp()) {
            dismissScreenshotSession(context)
        }
        if (AutoRefreshHub.shouldAutoDismissOnMainApp()) {
            dismissRefreshSession(context)
        }
    }

    fun featureKind(plan: AutomationPlan?): FeatureKind? = plan?.feature
}
