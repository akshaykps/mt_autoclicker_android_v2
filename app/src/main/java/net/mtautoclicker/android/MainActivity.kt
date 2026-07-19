package net.mtautoclicker.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import net.mtautoclicker.android.data.PermissionHelper
import net.mtautoclicker.android.data.ThemePreference
import net.mtautoclicker.android.engine.AutomationLauncher
import net.mtautoclicker.android.ui.components.DockScrollReporter
import net.mtautoclicker.android.ui.components.LocalDockScrollReporter
import net.mtautoclicker.android.ui.screens.AnimatedSplashScreen
import net.mtautoclicker.android.ui.screens.AppRoute
import net.mtautoclicker.android.ui.screens.AutoRefreshScreen
import net.mtautoclicker.android.ui.screens.DockRoutes
import net.mtautoclicker.android.ui.screens.FeedbackScreen
import net.mtautoclicker.android.ui.screens.FullPageScreenshotScreen
import net.mtautoclicker.android.ui.screens.GrowthOverlayHost
import net.mtautoclicker.android.ui.screens.HomeScreen
import net.mtautoclicker.android.ui.screens.MacroRecorderScreen
import net.mtautoclicker.android.ui.screens.MtFloatingDock
import net.mtautoclicker.android.ui.screens.MultiTargetScreen
import net.mtautoclicker.android.ui.screens.NotificationsScreen
import net.mtautoclicker.android.ui.screens.PermissionsScreen
import net.mtautoclicker.android.ui.screens.PresetsScreen
import net.mtautoclicker.android.ui.screens.SettingsScreen
import net.mtautoclicker.android.ui.screens.SingleTargetScreen
import net.mtautoclicker.android.ui.screens.UserGuideScreen
import net.mtautoclicker.android.ui.theme.MtDeep
import net.mtautoclicker.android.ui.theme.MtTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    @Volatile
    private var composeReady = false

    private var requestedRoute by mutableStateOf<AppRoute?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !composeReady }
        requestedRoute = routeFromIntent(intent)

        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            composeReady = true
            val themePreference by MtApplication.instance.settingsRepository.themePreference
                .collectAsState(initial = ThemePreference.SYSTEM)
            MtTheme(themePreference = themePreference) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MtDeep),
                ) {
                    MtAppRoot(
                        version = BuildConfig.VERSION_NAME,
                        activityContext = this@MainActivity,
                        requestedRoute = requestedRoute,
                        onRequestedRouteConsumed = { requestedRoute = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedRoute = routeFromIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        window.decorView.postDelayed({
            if (!isFinishing) {
                AutomationLauncher.onMainAppForeground(this)
            }
        }, 350)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        window.decorView.postDelayed({
            if (!isFinishing && hasWindowFocus()) {
                AutomationLauncher.onMainAppForeground(this)
            }
        }, 450)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun routeFromIntent(intent: Intent?): AppRoute? {
        val name = intent?.getStringExtra(EXTRA_APP_ROUTE) ?: return null
        return AppRoute.entries.firstOrNull { it.name == name }
    }

    companion object {
        private const val EXTRA_APP_ROUTE = "net.mtautoclicker.android.extra.APP_ROUTE"

        fun routeIntent(context: Context, route: AppRoute): Intent =
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_APP_ROUTE, route.name)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
    }
}

private fun appRouteFromGuideKey(key: String): AppRoute? = when (key.lowercase()) {
    "home" -> AppRoute.HOME
    "settings" -> AppRoute.SETTINGS
    "permissions" -> AppRoute.PERMISSIONS
    "feedback" -> AppRoute.FEEDBACK
    "notifications", "inbox" -> AppRoute.NOTIFICATIONS
    "presets" -> AppRoute.PRESETS
    "single_target", "single-target" -> AppRoute.SINGLE_TARGET
    "multi_target", "multi-target" -> AppRoute.MULTI_TARGET
    "macro_recorder", "macro-recorder" -> AppRoute.MACRO_RECORDER
    "auto_refresh", "auto-refresh" -> AppRoute.AUTO_REFRESH
    "full_page_screenshot", "full-page-screenshot" -> AppRoute.FULL_PAGE_SCREENSHOT
    else -> null
}

@Composable
private fun MtAppRoot(
    version: String,
    activityContext: Context,
    requestedRoute: AppRoute?,
    onRequestedRouteConsumed: () -> Unit,
) {
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var route by rememberSaveable { mutableStateOf(AppRoute.HOME) }
    var pendingTourKind by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingGuideSection by rememberSaveable { mutableStateOf<String?>(null) }
    var guideReturnRoute by rememberSaveable { mutableStateOf(AppRoute.HOME) }
    var unreadNotifications by remember { mutableIntStateOf(0) }
    val dockScroll = remember { DockScrollController() }
    val showDock = route in DockRoutes

    LaunchedEffect(requestedRoute) {
        requestedRoute?.let {
            route = it
            showSplash = false
            onRequestedRouteConsumed()
        }
    }

    LaunchedEffect(route) {
        dockScroll.reset()
    }

    LaunchedEffect(showSplash, route) {
        if (showSplash) return@LaunchedEffect
        unreadNotifications = MtApplication.instance.androidCms
            .fetchInboxNotifications()
            .unread_count
    }

    if (showSplash) {
        AnimatedSplashScreen(onFinished = { showSplash = false })
        return
    }

    CompositionLocalProvider(LocalDockScrollReporter provides dockScroll.reporter) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (showDock) 72.dp else 0.dp),
            ) {
                when (route) {
                    AppRoute.HOME -> HomeScreen(
                        version = version,
                        onNavigate = {
                            if (it == AppRoute.USER_GUIDE) {
                                guideReturnRoute = AppRoute.HOME
                                pendingGuideSection = null
                            }
                            route = it
                        },
                    )
                    AppRoute.SINGLE_TARGET -> SingleTargetScreen(
                        onBack = { route = AppRoute.HOME },
                        onNeedsPermissions = { route = AppRoute.PERMISSIONS },
                    )
                    AppRoute.MULTI_TARGET -> MultiTargetScreen(
                        onBack = { route = AppRoute.HOME },
                        onNeedsPermissions = { route = AppRoute.PERMISSIONS },
                    )
                    AppRoute.MACRO_RECORDER -> MacroRecorderScreen(
                        onBack = { route = AppRoute.HOME },
                        onNeedsPermissions = { route = AppRoute.PERMISSIONS },
                    )
                    AppRoute.FULL_PAGE_SCREENSHOT -> FullPageScreenshotScreen(
                        onBack = { route = AppRoute.HOME },
                        onNeedsPermissions = { route = AppRoute.PERMISSIONS },
                    )
                    AppRoute.AUTO_REFRESH -> AutoRefreshScreen(
                        onBack = { route = AppRoute.HOME },
                        onNeedsPermissions = { route = AppRoute.PERMISSIONS },
                    )
                    AppRoute.PRESETS -> PresetsScreen(
                        onBack = { route = AppRoute.HOME },
                        onNeedsPermissions = { route = AppRoute.PERMISSIONS },
                    )
                    AppRoute.SETTINGS -> SettingsScreen(
                        onBack = { route = AppRoute.HOME },
                        onOpenUserGuide = {
                            guideReturnRoute = AppRoute.SETTINGS
                            pendingGuideSection = null
                            route = AppRoute.USER_GUIDE
                        },
                        onStartTour = { kind ->
                            pendingTourKind = kind
                            route = AppRoute.HOME
                        },
                    )
                    AppRoute.PERMISSIONS -> PermissionsScreen(onBack = { route = AppRoute.HOME })
                    AppRoute.FEEDBACK -> FeedbackScreen(onBack = { route = AppRoute.HOME })
                    AppRoute.NOTIFICATIONS -> NotificationsScreen(
                        onBack = { route = AppRoute.HOME },
                        onDeepLink = { deepLink ->
                            val normalized = deepLink.lowercase()
                            if (normalized == "tutorial" || normalized == "user_guide" || normalized == "help") {
                                guideReturnRoute = AppRoute.NOTIFICATIONS
                                pendingGuideSection = null
                                route = AppRoute.USER_GUIDE
                            } else if (normalized.startsWith("tutorial:")) {
                                guideReturnRoute = AppRoute.NOTIFICATIONS
                                pendingGuideSection = normalized.substringAfter(':').takeIf { it.isNotBlank() }
                                route = AppRoute.USER_GUIDE
                            } else {
                                route = when (normalized) {
                                "settings" -> AppRoute.SETTINGS
                                "permissions" -> AppRoute.PERMISSIONS
                                "feedback" -> AppRoute.FEEDBACK
                                "presets" -> AppRoute.PRESETS
                                "single_target", "single-target" -> AppRoute.SINGLE_TARGET
                                "multi_target", "multi-target" -> AppRoute.MULTI_TARGET
                                "macro_recorder", "macro-recorder" -> AppRoute.MACRO_RECORDER
                                "auto_refresh", "auto-refresh" -> AppRoute.AUTO_REFRESH
                                "full_page_screenshot", "full-page-screenshot" -> AppRoute.FULL_PAGE_SCREENSHOT
                                else -> AppRoute.NOTIFICATIONS
                                }
                            }
                        },
                    )
                    AppRoute.USER_GUIDE -> UserGuideScreen(
                        onBack = { route = guideReturnRoute },
                        initialSectionId = pendingGuideSection,
                        onOpenRoute = { routeKey ->
                            route = appRouteFromGuideKey(routeKey) ?: AppRoute.USER_GUIDE
                        },
                    )
                }
            }

            if (showDock) {
                MtFloatingDock(
                    currentRoute = route,
                    unreadNotifications = unreadNotifications,
                    collapsed = dockScroll.collapsed,
                    onNavigate = { route = it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            GrowthOverlayHost(
                enabled = true,
                routeIsHome = route == AppRoute.HOME,
                pendingTourKind = pendingTourKind,
                onPendingTourConsumed = { pendingTourKind = null },
                onOpenPermissions = {
                    route = if (PermissionHelper.missingPermissions(activityContext).isEmpty()) {
                        AppRoute.HOME
                    } else {
                        AppRoute.PERMISSIONS
                    }
                },
                onOpenFeedback = { route = AppRoute.FEEDBACK },
            )
        }
    }
}

private class DockScrollController {
    var collapsed by mutableStateOf(false)
        private set
    private var lastOffset = 0

    val reporter = DockScrollReporter { offsetPx ->
        val delta = offsetPx - lastOffset
        lastOffset = offsetPx
        collapsed = when {
            offsetPx <= 8 -> false
            delta > 4 -> true
            delta < -4 -> false
            else -> collapsed
        }
    }

    fun reset() {
        collapsed = false
        lastOffset = 0
    }
}
