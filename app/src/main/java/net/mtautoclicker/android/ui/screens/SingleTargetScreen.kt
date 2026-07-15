package net.mtautoclicker.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.mtautoclicker.android.MtApplication
import net.mtautoclicker.android.data.FeatureKind
import net.mtautoclicker.android.data.SingleTargetConfig
import net.mtautoclicker.android.engine.AutomationLauncher
import net.mtautoclicker.android.engine.LaunchResult
import net.mtautoclicker.android.engine.formatInterval
import net.mtautoclicker.android.engine.formatStopSummary
import net.mtautoclicker.android.ui.components.ExtensionStyleFeatureHero
import net.mtautoclicker.android.ui.components.FeaturePageScaffold
import net.mtautoclicker.android.ui.components.FeaturePresetsPanel
import net.mtautoclicker.android.ui.components.FeatureStepUi
import net.mtautoclicker.android.ui.components.FeatureTab
import net.mtautoclicker.android.ui.components.FeatureTabBar
import net.mtautoclicker.android.ui.components.SettingsCard
import net.mtautoclicker.android.ui.components.SingleTargetGradient
import net.mtautoclicker.android.ui.theme.MtEmerald
import net.mtautoclicker.android.ui.theme.MtMid

@Composable
fun SingleTargetScreen(onBack: () -> Unit, onNeedsPermissions: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(SingleTargetConfig()) }
    var tab by remember { mutableStateOf(FeatureTab.SETUP) }
    var status by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    val json = remember { Json { encodeDefaults = true } }
    val allPresets by MtApplication.instance.presetRepository.presets.collectAsState(initial = emptyList())
    val presets = remember(allPresets) { allPresets.filter { it.feature == FeatureKind.SINGLE_TARGET } }
    val scrollState = rememberScrollState()
    var settingsY by remember { mutableIntStateOf(0) }

    val runSummary = listOf(
        formatInterval(config.interval),
        "${config.mouseButton.name.lowercase()} click",
        config.targetMode.name.lowercase(),
        formatStopSummary(config.stop),
    ).joinToString(" · ")

    fun startClicking() {
        starting = true
        AutomationLauncher.armSingle(config)
        when (AutomationLauncher.startFloatBar(context)) {
            LaunchResult.Ok -> {
                status = "Float bar opened. Tap + to place target, then Play."
                scope.launch {
                    MtApplication.instance.trackingService.trackEvent("single_target_start")
                }
            }
            is LaunchResult.NeedsPermissions -> onNeedsPermissions()
        }
        starting = false
    }

    FeaturePageScaffold(title = "Single Target Clicking", onBack = onBack, scrollState = scrollState) {
        FeatureTabBar(
            selected = tab,
            setupGradient = SingleTargetGradient,
            onSelect = { tab = it },
        )

        if (tab == FeatureTab.SETUP) {
            ExtensionStyleFeatureHero(
                title = "Single Target",
                description = "Click one position repeatedly. Configure timing below, then place your target and play from the floating bar.",
                icon = Icons.Rounded.AdsClick,
                accentGradient = SingleTargetGradient,
                runSummary = runSummary,
                steps = listOf(
                    FeatureStepUi(Icons.Rounded.PlayArrow, "Press Start — float bar opens"),
                    FeatureStepUi(Icons.Rounded.Add, "Tap + to place your click target marker"),
                    FeatureStepUi(Icons.Rounded.AdsClick, "Hit Play on the bar to begin clicking"),
                ),
                onStart = { startClicking() },
                onEditSummary = {
                    scope.launch { scrollState.animateScrollTo(settingsY.coerceAtLeast(0)) }
                },
                starting = starting,
            )

            status?.let { Text(it, color = MtEmerald) }

            SettingsCard(
                title = "Target settings",
                modifier = Modifier.onGloballyPositioned { settingsY = it.positionInParent().y.toInt() },
            ) {
                TargetModeDropdown(config.targetMode) { config = config.copy(targetMode = it) }
                MouseButtonDropdown(config.mouseButton) { config = config.copy(mouseButton = it) }
            }

            IntervalStopForm(
                interval = config.interval,
                stop = config.stop,
                onIntervalChange = { config = config.copy(interval = it) },
                onStopChange = { config = config.copy(stop = it) },
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text("Tip: tap the summary pill to jump to settings. After you Play, a Recent preset is saved automatically.", color = MtMid)
        } else {
            FeaturePresetsPanel(
                featureLabel = "Single Target",
                presets = presets,
                accent = SingleTargetGradient.first(),
                onGoToSetup = { tab = FeatureTab.SETUP },
                onLoad = { preset ->
                    runCatching {
                        config = json.decodeFromString<SingleTargetConfig>(preset.configJson)
                        tab = FeatureTab.SETUP
                        Toast.makeText(context, "Loaded ${preset.name}", Toast.LENGTH_SHORT).show()
                    }
                },
                onDelete = { preset ->
                    scope.launch { MtApplication.instance.presetRepository.deletePreset(preset.id) }
                },
                onRun = { preset ->
                    runCatching {
                        val loaded = json.decodeFromString<SingleTargetConfig>(preset.configJson)
                        config = loaded
                        AutomationLauncher.armSingle(loaded, preset.targets)
                        when (AutomationLauncher.startFloatBar(context)) {
                            LaunchResult.Ok -> Unit
                            is LaunchResult.NeedsPermissions -> onNeedsPermissions()
                        }
                    }
                },
            )
        }
    }
}
