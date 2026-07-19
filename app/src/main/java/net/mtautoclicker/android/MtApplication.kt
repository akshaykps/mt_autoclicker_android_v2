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

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsRepository = SettingsRepository(this)
        presetRepository = PresetRepository(this)
        macroRepository = MacroRepository(this)
        trackingService = TrackingService(this, settingsRepository)
        androidCms = AndroidCmsRepository(this, settingsRepository)
        appScope.launch {
            trackingService.trackInstallIfNeeded()
            trackingService.startSession()
            androidCms.trackAndroidEvent("app_open")
        }
    }

    companion object {
        lateinit var instance: MtApplication
            private set
    }
}
