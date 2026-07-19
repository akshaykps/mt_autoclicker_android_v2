package net.mtautoclicker.android

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.mtautoclicker.android.data.AndroidCmsRepository
import net.mtautoclicker.android.data.MacroRepository
import net.mtautoclicker.android.data.PresetRepository
import net.mtautoclicker.android.data.SettingsRepository
import net.mtautoclicker.android.data.TrackingService
import net.mtautoclicker.android.data.UserGuideRepository
import net.mtautoclicker.android.data.telemetry.TelemetryQueue

class MtApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var presetRepository: PresetRepository
        private set

    lateinit var macroRepository: MacroRepository
        private set

    lateinit var trackingService: TrackingService
        private set

    lateinit var androidCms: AndroidCmsRepository
        private set

    lateinit var userGuideRepository: UserGuideRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsRepository = SettingsRepository(this)
        presetRepository = PresetRepository(this)
        macroRepository = MacroRepository(this)
        trackingService = TrackingService(this, settingsRepository)
        androidCms = AndroidCmsRepository(this, settingsRepository)
        userGuideRepository = UserGuideRepository(this)
        TelemetryQueue.get(this).scheduleDrain()
        appScope.launch {
            trackingService.startSession()
            trackingService.trackInstallIfNeeded()
            androidCms.trackAndroidEvent("app_open")
        }
    }

    companion object {
        lateinit var instance: MtApplication
            private set
    }
}
