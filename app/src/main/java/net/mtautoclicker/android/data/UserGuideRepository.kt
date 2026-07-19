package net.mtautoclicker.android.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.mtautoclicker.android.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

@Serializable
data class UserGuideImage(
    val url: String = "",
    val caption: String = "",
    val alt_text: String = "",
    val order: Int = 0,
)

@Serializable
data class UserGuideSection(
    val id: String,
    val title: String,
    val order: Int = 0,
    val body: String = "",
    val tags: List<String> = emptyList(),
    val related_route: String = "",
    val images: List<UserGuideImage> = emptyList(),
    val updated_at: String = "",
)

@Serializable
data class UserGuideDocument(
    val slug: String = "user-guide",
    val title: String = "MT Auto Clicker User Guide",
    val summary: String = "",
    val version: Int = 1,
    val updated_at: String = "",
    val sections: List<UserGuideSection> = emptyList(),
    val source: String = "bundled",
)

@Serializable
private data class UserGuideApiResponse(
    val success: Boolean = false,
    val guide: UserGuideDocument? = null,
)

/**
 * Offline-first guide source:
 * 1) the complete USER_GUIDE.md bundled at build time,
 * 2) the most recent validated Django response cached in app-private storage,
 * 3) a fresh published Django version when internet is available.
 */
class UserGuideRepository(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val cacheFile = File(context.filesDir, "user_guide_cache.json")
    private val cacheMetaFile = File(context.filesDir, "user_guide_cache_meta.txt")

    suspend fun loadLocal(): UserGuideDocument = withContext(Dispatchers.IO) {
        val bundled = parseBundledGuide()
        val cached = readCache()?.let { sanitizeDocument(it.copy(source = "cached")) }
        if (cached != null && cached.sections.isNotEmpty() && cached.version >= bundled.version) {
            cached
        } else {
            bundled
        }
    }

    private fun sanitizeDocument(document: UserGuideDocument): UserGuideDocument =
        document.copy(
            sections = document.sections
                .filter { it.id.isNotBlank() && it.title.isNotBlank() }
                .map { it.copy(body = cleanSectionBody(it.body), title = displayTitle(it.title)) }
                .filterNot { isSkipSection(it) }
                .sortedBy { it.order },
        )

    suspend fun refresh(force: Boolean = false): UserGuideDocument? = withContext(Dispatchers.IO) {
        if (!force && isCacheFresh()) {
            return@withContext readCache()?.let { sanitizeDocument(it.copy(source = "cached")) }
        }
        if (!hasValidatedInternet()) return@withContext null
        val response = getJson(
            "https://mtautoclicker.net/api/android/user-guide/" +
                "?app_version=${BuildConfig.VERSION_NAME}",
        ) ?: return@withContext null
        val document = runCatching {
            json.decodeFromString<UserGuideApiResponse>(response).guide
        }.onFailure {
            Log.w(TAG, "Could not decode remote user guide", it)
        }.getOrNull()
        if (document == null || document.sections.isEmpty()) return@withContext null
        val normalized = sanitizeDocument(document.copy(source = "remote"))
        if (normalized.sections.isEmpty()) return@withContext null
        runCatching {
            cacheFile.writeText(json.encodeToString(normalized))
            cacheMetaFile.writeText(System.currentTimeMillis().toString())
        }.onFailure {
            Log.w(TAG, "Could not cache remote user guide", it)
        }
        normalized
    }

    private fun isCacheFresh(): Boolean {
        if (!cacheFile.exists() || !cacheMetaFile.exists()) return false
        val cachedAt = cacheMetaFile.readText().trim().toLongOrNull() ?: return false
        return System.currentTimeMillis() - cachedAt < CACHE_TTL_MS
    }

    private fun readCache(): UserGuideDocument? = runCatching {
        if (!cacheFile.exists()) return@runCatching null
        json.decodeFromString<UserGuideDocument>(cacheFile.readText())
    }.onFailure {
        Log.w(TAG, "Could not read cached user guide", it)
    }.getOrNull()

    private fun parseBundledGuide(): UserGuideDocument {
        val markdown = context.assets.open("user_guide.md")
            .bufferedReader()
            .use { it.readText() }
        val lines = markdown.lines()
        val documentTitle = lines.firstOrNull { it.startsWith("# ") }
            ?.removePrefix("# ")
            ?.trim()
            .orEmpty()
            .ifBlank { "MT Auto Clicker User Guide" }
        val sections = mutableListOf<UserGuideSection>()
        val firstSectionIndex = lines.indexOfFirst { it.startsWith("## ") }
        val preamble = lines
            .drop(1)
            .take(if (firstSectionIndex > 1) firstSectionIndex - 1 else 0)
            .joinToString("\n")
            .trim()
        if (preamble.isNotBlank()) {
            sections += UserGuideSection(
                id = "welcome_and_safety",
                title = "Welcome & Safety",
                order = 0,
                body = preamble,
                tags = listOf("welcome", "safety", "introduction"),
            )
        }
        var currentTitle: String? = null
        val currentBody = mutableListOf<String>()

        fun flush() {
            val title = currentTitle ?: return
            // Skip the markdown table-of-contents chapter — the app builds an interactive TOC.
            if (title.equals("Contents", ignoreCase = true)) {
                currentBody.clear()
                return
            }
            val body = cleanSectionBody(currentBody.joinToString("\n"))
            if (body.isBlank() && isTocOnlyBody(currentBody.joinToString("\n"))) {
                currentBody.clear()
                return
            }
            val cleanTitle = displayTitle(title)
            sections += UserGuideSection(
                id = slugify(cleanTitle),
                title = cleanTitle,
                order = sections.size,
                body = body,
                tags = buildTags(cleanTitle, body),
                related_route = relatedRoute(cleanTitle),
            )
            currentBody.clear()
        }

        lines.forEach { line ->
            if (line.startsWith("## ")) {
                flush()
                currentTitle = line.removePrefix("## ").trim()
            } else if (currentTitle != null) {
                currentBody += line
            }
        }
        flush()

        return UserGuideDocument(
            title = documentTitle,
            summary = "Complete offline help for setup, features, presets, troubleshooting, privacy and safety.",
            version = 1,
            sections = sections.filterNot { isSkipSection(it) },
            source = "bundled",
        )
    }

    /** Drop markdown TOC link lines and normalize blank runs. */
    private fun cleanSectionBody(raw: String): String {
        val cleaned = raw.lineSequence()
            .filterNot { isMarkdownTocLinkLine(it) }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        return cleaned
    }

    private fun isMarkdownTocLinkLine(line: String): Boolean =
        line.trim().matches(Regex("""^\d+\.\s+\[[^\]]+]\(#[^)]+\)$"""))

    private fun isTocOnlyBody(raw: String): Boolean {
        val meaningful = raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "---" }
        return meaningful.any() && meaningful.all { isMarkdownTocLinkLine(it) }
    }

    private fun isSkipSection(section: UserGuideSection): Boolean =
        section.title.equals("Contents", ignoreCase = true) ||
            isTocOnlyBody(section.body)

    /** Prefer "What MT Auto Clicker Does" over "1. What MT Auto Clicker Does". */
    private fun displayTitle(title: String): String =
        title.replace(Regex("""^\d+\.\s+"""), "").trim().ifBlank { title }

    private fun buildTags(title: String, body: String): List<String> {
        val headings = body.lineSequence()
            .filter { it.startsWith("### ") }
            .map { it.removePrefix("### ").trim() }
            .toList()
        return (listOf(title) + headings)
            .flatMap { it.lowercase(Locale.US).split(Regex("[^a-z0-9]+")) }
            .filter { it.length > 2 }
            .distinct()
            .take(40)
    }

    private fun slugify(value: String): String =
        value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(100)

    private fun relatedRoute(title: String): String = when {
        title.contains("Single Target", true) -> "single_target"
        title.contains("Multi Target", true) -> "multi_target"
        title.contains("Macro", true) -> "macro_recorder"
        title.contains("Screenshot", true) -> "full_page_screenshot"
        title.contains("Auto Refresh", true) -> "auto_refresh"
        title.contains("Preset", true) -> "presets"
        title.contains("Notification", true) -> "notifications"
        title.contains("Feedback", true) -> "feedback"
        title.contains("Settings", true) || title.contains("Backup", true) -> "settings"
        title.contains("Permission", true) || title.contains("Setup", true) -> "permissions"
        else -> ""
    }

    private fun getJson(url: String): String? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
        }
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()
            ?.readText()
        connection.disconnect()
        body.takeIf { code in 200..299 }
    }.onFailure {
        Log.w(TAG, "User guide refresh failed", it)
    }.getOrNull()

    private fun hasValidatedInternet(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    companion object {
        private const val TAG = "MtUserGuide"
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L // 6 hours
    }
}
