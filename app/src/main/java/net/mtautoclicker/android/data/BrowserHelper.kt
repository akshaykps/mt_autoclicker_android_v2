package net.mtautoclicker.android.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isPopular: Boolean = false,
    val isBrowser: Boolean = false,
)

/**
 * Lists user-facing launchable apps for Full Page Screenshot target picking.
 */
object LaunchableAppHelper {

    /** Shown near the top of the picker when installed. */
    private val popularPackages = linkedSetOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.instagram.android",
        "com.facebook.orca", // Messenger
        "com.facebook.mlite",
        "com.facebook.katana",
        "com.twitter.android",
        "com.zhiliaoapp.musically", // TikTok
        "com.ss.android.ugc.trill",
        "com.snapchat.android",
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "com.discord",
        "com.spotify.music",
        "com.netflix.mediaclient",
        "com.google.android.youtube",
        "com.reddit.frontpage",
        "com.linkedin.android",
        "com.pinterest",
        "com.amazon.mShop.android.shopping",
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.opera.browser",
        "com.google.android.gm",
        "com.google.android.apps.maps",
        "com.google.android.apps.photos",
        "com.google.android.apps.messaging",
    )

    private val browserPackages = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.focus",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.brave.browser",
        "com.sec.android.app.sbrowser",
        "com.vivaldi.browser",
        "com.duckduckgo.mobile.android",
        "com.kiwibrowser.browser",
        "com.UCMobile.intl",
        "mark.via.gp",
    )

    fun listLaunchableApps(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val self = context.packageName
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)

        val byPackage = LinkedHashMap<String, LaunchableApp>()
        for (resolve in activities) {
            val pkg = resolve.activityInfo?.packageName ?: continue
            if (pkg == self) continue
            if (byPackage.containsKey(pkg)) continue
            val label = resolve.loadLabel(pm)?.toString()?.ifBlank { pkg } ?: pkg
            val icon = runCatching { resolve.loadIcon(pm) }.getOrNull()
                ?: runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
            byPackage[pkg] = LaunchableApp(
                packageName = pkg,
                label = label,
                icon = icon,
                isPopular = pkg in popularPackages,
                isBrowser = pkg in browserPackages,
            )
        }

        return byPackage.values.sortedWith(
            compareByDescending<LaunchableApp> { it.isPopular }
                .thenByDescending { it.isBrowser }
                .thenBy { it.label.lowercase() },
        )
    }

    fun launchApp(context: Context, packageName: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return false
        return runCatching {
            context.startActivity(launch)
            true
        }.getOrDefault(false)
    }
}

/** @deprecated Prefer [LaunchableAppHelper] — kept for older call sites. */
@Deprecated("Use LaunchableAppHelper", ReplaceWith("LaunchableAppHelper"))
object BrowserHelper {
    fun listInstalledBrowsers(context: Context): List<InstalledBrowser> =
        LaunchableAppHelper.listLaunchableApps(context)
            .filter { it.isBrowser }
            .map { InstalledBrowser(it.packageName, it.label, it.icon) }

    fun launchBrowser(context: Context, packageName: String): Boolean =
        LaunchableAppHelper.launchApp(context, packageName)
}

data class InstalledBrowser(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)
