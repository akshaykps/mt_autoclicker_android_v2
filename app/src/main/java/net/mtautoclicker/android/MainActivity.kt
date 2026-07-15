package net.mtautoclicker.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import net.mtautoclicker.android.data.ThemePreference
import net.mtautoclicker.android.engine.AutomationLauncher
import net.mtautoclicker.android.ui.screens.AppRoute
import net.mtautoclicker.android.ui.screens.HomeScreen
import net.mtautoclicker.android.ui.screens.MultiTargetScreen
import net.mtautoclicker.android.ui.screens.PermissionsScreen
import net.mtautoclicker.android.ui.screens.PresetsScreen
import net.mtautoclicker.android.ui.screens.SettingsScreen
import net.mtautoclicker.android.ui.screens.SingleTargetScreen
import net.mtautoclicker.android.ui.theme.MtDeep
import net.mtautoclicker.android.ui.theme.MtTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        AppRoute.PRESETS -> PresetsScreen(
            onBack = { route = AppRoute.HOME },
            onNeedsPermissions = { route = AppRoute.PERMISSIONS },
        )
        AppRoute.SETTINGS -> SettingsScreen(onBack = { route = AppRoute.HOME })
        AppRoute.PERMISSIONS -> PermissionsScreen(onBack = { route = AppRoute.HOME })
    }
}
