package net.mtautoclicker.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.mtautoclicker.android.MtApplication
import net.mtautoclicker.android.data.FeatureKind
import net.mtautoclicker.android.data.MultiTargetConfig
import net.mtautoclicker.android.data.PresetRepository
import net.mtautoclicker.android.engine.AutomationLauncher
import net.mtautoclicker.android.engine.LaunchResult
import net.mtautoclicker.android.engine.formatInterval
import net.mtautoclicker.android.engine.formatStopSummary
import net.mtautoclicker.android.ui.components.ExtensionStyleFeatureHero
import net.mtautoclicker.android.ui.components.FeaturePageScaffold
import net.mtautoclicker.android.ui.components.FeatureRecentPanel
import net.mtautoclicker.android.ui.components.FeatureStepUi
import net.mtautoclicker.android.ui.components.FeatureTab
import net.mtautoclicker.android.ui.components.FeatureTabBar
import net.mtautoclicker.android.ui.components.MultiTargetGradient
import net.mtautoclicker.android.ui.components.SettingsCard
import net.mtautoclicker.android.ui.theme.MtEmerald
import net.mtautoclicker.android.ui.theme.MtHi
import net.mtautoclicker.android.ui.theme.MtMid

@Composable
fun MultiTargetScreen(onBack: () -> Unit, onNeedsPermissions: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(MultiTargetConfig()) }
    var tab by remember { mutableStateOf(FeatureTab.SETUP) }
    var status by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    val json = remember { Json { encodeDefaults = true } }
    val allPresets by MtApplication.instance.presetRepository.presets.collectAsState(initial = emptyList())
    val recents = remember(allPresets) {
        allPresets.filter { it.feature == FeatureKind.MULTI_TARGET && PresetRepository.isRecent(it) }
    }
    val scrollState = rememberScrollState()
    var settingsY by remember { mutableIntStateOf(0) }

    val runSummary = listOf(
        formatInterval(config.interval),
        if (config.parallel) "parallel" else "sequence",
        formatStopSummary(config.stop),
    ).joinToString(" · ")

    fun startClicking() {
        starting = true
        AutomationLauncher.armMulti(config)
        when (AutomationLauncher.startFloatBar(context)) {
            LaunchResult.Ok -> {
                status = "Float bar opened. Tap + for each target, then Play."
                scope.launch {
                    MtApplication.instance.trackingService.trackEvent("multi_target_start")
                }
            }
            is LaunchResult.NeedsPermissions -> onNeedsPermissions()
        }
        starting = false
    }

    FeaturePageScaffold(title = "Multi Target Clicking", onBack = onBack, scrollState = scrollState) {
        FeatureTabBar(
            selected = tab,
            setupGradient = MultiTargetGradient,
            onSelect = { tab = it },
        )

        if (tab == FeatureTab.SETUP) {
            ExtensionStyleFeatureHero(
                title = "Multi Target",
                description = "Place numbered targets and click them in sequence or parallel from the floating bar.",
                icon = Icons.Rounded.GridView,
                accentGradient = MultiTargetGradient,
                runSummary = runSummary,
                steps = listOf(
                    FeatureStepUi(Icons.Rounded.PlayArrow, "Press Start — float bar opens"),
                    FeatureStepUi(Icons.Rounded.Add, "Tap + to place each numbered target"),
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
                title = "Click mode",
                modifier = Modifier.onGloballyPositioned { settingsY = it.positionInParent().y.toInt() },
            ) {
                MouseButtonDropdown(config.mouseButton) { config = config.copy(mouseButton = it) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Parallel (all targets each cycle)", color = MtHi)
                    Switch(checked = config.parallel, onCheckedChange = { config = config.copy(parallel = it) })
                }
            }

            IntervalStopForm(
                interval = config.interval,
                stop = config.stop,
                onIntervalChange = { config = config.copy(interval = it) },
                onStopChange = { config = config.copy(stop = it) },
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text("Tip: after you Play, a Recent entry is saved automatically. Save any one to keep it under Presets, or tap Save on the float bar.", color = MtMid)
        } else {
            FeatureRecentPanel(
                featureLabel = "Multi Target",
                recents = recents,
                accent = MultiTargetGradient.first(),
                onGoToSetup = { tab = FeatureTab.SETUP },
                onLoad = { preset ->
                    runCatching {
                        config = json.decodeFromString<MultiTargetConfig>(preset.configJson)
                        tab = FeatureTab.SETUP
                        Toast.makeText(context, "Loaded ${preset.name}", Toast.LENGTH_SHORT).show()
                    }
                },
                onDelete = { preset ->
                    scope.launch { MtApplication.instance.presetRepository.deletePreset(preset.id) }
                },
                onRun = { preset ->
                    runCatching {
                        val loaded = json.decodeFromString<MultiTargetConfig>(preset.configJson)
                        config = loaded
                        AutomationLauncher.armMulti(loaded, preset.targets)
                        when (AutomationLauncher.startFloatBar(context)) {
                            LaunchResult.Ok -> Unit
                            is LaunchResult.NeedsPermissions -> onNeedsPermissions()
                        }
                    }
                },
                onSave = { preset, name ->
                    scope.launch {
                        MtApplication.instance.presetRepository.promoteRecentToSaved(preset.id, name)
                        Toast.makeText(context, "Saved to Presets", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }
}
