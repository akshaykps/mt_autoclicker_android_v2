package net.mtautoclicker.android

import android.Manifest
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import net.mtautoclicker.android.data.ThemePreference
import net.mtautoclicker.android.engine.AutomationLauncher
import net.mtautoclicker.android.ui.screens.AppRoute
import net.mtautoclicker.android.ui.screens.AutoRefreshScreen
import net.mtautoclicker.android.ui.screens.FullPageScreenshotScreen
import net.mtautoclicker.android.ui.screens.HomeScreen
import net.mtautoclicker.android.ui.screens.MacroRecorderScreen
import net.mtautoclicker.android.ui.screens.MultiTargetScreen
import net.mtautoclicker.android.ui.screens.PermissionsScreen
import net.mtautoclicker.android.ui.screens.PresetsScreen
import net.mtautoclicker.android.ui.screens.SettingsScreen
import net.mtautoclicker.android.ui.screens.SingleTargetScreen
import net.mtautoclicker.android.ui.theme.MtDeep
import net.mtautoclicker.android.ui.theme.MtTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
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
                        onKillAll = { AutomationLauncher.killAll(this@MainActivity) },
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        // Dismiss screenshot float bar when user is actually looking at MT Auto Clicker.
        // Delayed so brief focus flickers after permission / browser launch don't cancel the session.
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
}

@Composable
private fun MtAppRoot(version: String, onKillAll: () -> Unit) {
    var route by rememberSaveable { mutableStateOf(AppRoute.HOME) }

    when (route) {
        AppRoute.HOME -> HomeScreen(
            version = version,
            onNavigate = { route = it },
            onKillAll = onKillAll,
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
        AppRoute.SETTINGS -> SettingsScreen(onBack = { route = AppRoute.HOME })
        AppRoute.PERMISSIONS -> PermissionsScreen(onBack = { route = AppRoute.HOME })
    }
}
