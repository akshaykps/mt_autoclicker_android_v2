package net.mtautoclicker.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import net.mtautoclicker.android.data.LaunchableAppHelper
import net.mtautoclicker.android.engine.FullPageCaptureHub
import net.mtautoclicker.android.service.FullPageCaptureService
import net.mtautoclicker.android.service.ScreenshotOverlayService

/**
 * Transparent activity that requests MediaProjection consent, then starts
 * the full-page capture session + float bar and launches the chosen app.
 */
class ScreenCapturePermissionActivity : ComponentActivity() {

    private val appPackage: String by lazy {
        intent.getStringExtra(EXTRA_APP_PACKAGE)
            ?: intent.getStringExtra(EXTRA_BROWSER_PACKAGE).orEmpty()
    }

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                FullPageCaptureHub.setProjectionConsent(result.resultCode, result.data!!)
                FullPageCaptureHub.armReady(appPackage)
                FullPageCaptureService.start(this)
                ScreenshotOverlayService.show(this)
                if (appPackage.isNotBlank()) {
                    LaunchableAppHelper.launchApp(this, appPackage)
                }
                Toast.makeText(
                    this,
                    "Open a long scrollable screen, then tap Snapshot",
                    Toast.LENGTH_LONG,
                ).show()
            } else {
                Toast.makeText(this, "Screen capture permission is required", Toast.LENGTH_LONG).show()
                FullPageCaptureHub.setError("Screen capture denied")
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (appPackage.isBlank()) {
            Toast.makeText(this, "No app selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // Prefer entire-screen capture so "single app" doesn't confuse users / truncate capture.
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mgr.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            mgr.createScreenCaptureIntent()
        }
        projectionLauncher.launch(intent)
    }

    companion object {
        const val EXTRA_APP_PACKAGE = "app_package"
        const val EXTRA_BROWSER_PACKAGE = "browser_package"

        fun start(context: Context, appPackage: String) {
            context.startActivity(
                Intent(context, ScreenCapturePermissionActivity::class.java).apply {
                    putExtra(EXTRA_APP_PACKAGE, appPackage)
                    putExtra(EXTRA_BROWSER_PACKAGE, appPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }
}
