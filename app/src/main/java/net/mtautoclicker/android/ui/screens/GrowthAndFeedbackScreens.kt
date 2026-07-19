package net.mtautoclicker.android.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mtautoclicker.android.MtApplication
import net.mtautoclicker.android.data.AndroidOverlayDto
import net.mtautoclicker.android.data.AndroidTourDto
import net.mtautoclicker.android.data.AndroidTourStepDto
import net.mtautoclicker.android.data.PermissionHelper
import net.mtautoclicker.android.engine.AutomationHub
import net.mtautoclicker.android.ui.components.MtPrimaryButton
import net.mtautoclicker.android.ui.components.PageScaffold
import net.mtautoclicker.android.ui.theme.MtBlue
import net.mtautoclicker.android.ui.theme.MtBorder
import net.mtautoclicker.android.ui.theme.MtCard
import net.mtautoclicker.android.ui.theme.MtEmerald
import net.mtautoclicker.android.ui.theme.MtHi
import net.mtautoclicker.android.ui.theme.MtMid
import net.mtautoclicker.android.ui.theme.MtPurple
import net.mtautoclicker.android.ui.theme.MtRow

/* ─── Feedback (Mac-parity form, Android features) ───────────────────────── */

private data class FeedbackFeature(
    val id: String,
    val title: String,
    val accent: Color,
    val icon: ImageVector,
)

private data class FeedbackTypeOption(
    val value: String,
    val label: String,
    val description: String,
    val accent: Color,
    val icon: ImageVector,
)

private val androidFeedbackFeatures = listOf(
    FeedbackFeature("single-target", "Single Target", Color(0xFF2563EB), Icons.Rounded.AdsClick),
    FeedbackFeature("multi-target", "Multi Target", Color(0xFF7C3AED), Icons.Rounded.TouchApp),
    FeedbackFeature("macro-recorder", "Macro Recorder", Color(0xFFDB2777), Icons.Rounded.FiberManualRecord),
    FeedbackFeature("full-page-screenshot", "Full Page Screenshot", Color(0xFF0D9488), Icons.Rounded.Screenshot),
    FeedbackFeature("auto-refresh", "Auto Refresh", Color(0xFFD97706), Icons.Rounded.Refresh),
    FeedbackFeature("presets", "Presets", Color(0xFF4F46E5), Icons.Rounded.FolderOpen),
    FeedbackFeature("float-bar", "Float bar & overlays", Color(0xFF0891B2), Icons.Rounded.Layers),
    FeedbackFeature("settings", "Settings & Permissions", Color(0xFF059669), Icons.Rounded.Settings),
)

private val feedbackTypeOptions = listOf(
    FeedbackTypeOption("feature", "Feature", "Thoughts on existing tools", Color(0xFF2563EB), Icons.Rounded.AutoAwesome),
    FeedbackTypeOption("suggestion", "Suggestion", "New ideas or improvements", Color(0xFF7C3AED), Icons.Rounded.Lightbulb),
    FeedbackTypeOption("bug", "Bug", "Something broken or wrong", Color(0xFFDC2626), Icons.Rounded.BugReport),
    FeedbackTypeOption("other", "Other", "General notes or questions", Color(0xFF0D9488), Icons.AutoMirrored.Rounded.Chat),
)

private fun sanitizeFeedbackMessage(input: String): String {
    var sanitized = input
        .replace(Regex("<[^>]*>"), "")
        .replace(Regex("(?i)<script\\b[^<]*(?:(?!</script>)<[^<]*)*</script>"), "")
        .replace(Regex("(?i)on\\w+\\s*=\\s*[\"'][^\"']*[\"']"), "")
        .replace(Regex("(?i)javascript:"), "")
        .replace(Regex("(?i)data:"), "")
        .replace("\u0000", "")
    return sanitized.trim()
}

@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var feedbackType by remember { mutableStateOf("feature") }
    var message by remember { mutableStateOf("") }
    var selectedFeatures by remember { mutableStateOf(setOf<String>()) }
    var suggestionType by remember { mutableStateOf("new") } // new | existing
    var bugType by remember { mutableStateOf("feature") } // feature | other
    var submitAttempted by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var errorBanner by remember { mutableStateOf<String?>(null) }

    val maxChars = 500
    val minChars = 10
    val remaining = maxChars - message.length
    val typeAccent = feedbackTypeOptions.firstOrNull { it.value == feedbackType }?.accent ?: MtPurple

    fun resetSubSelections() {
        selectedFeatures = emptySet()
        suggestionType = "new"
        bugType = "feature"
        submitAttempted = false
        errorBanner = null
    }

    fun toggleFeature(id: String) {
        selectedFeatures = if (id in selectedFeatures) selectedFeatures - id else selectedFeatures + id
    }

    fun needsFeaturePick(): Boolean = when (feedbackType) {
        "feature" -> true
        "suggestion" -> suggestionType == "existing"
        "bug" -> bugType == "feature"
        else -> false
    }

    fun validate(): String? {
        if (needsFeaturePick() && selectedFeatures.isEmpty()) {
            return when (feedbackType) {
                "feature" -> "Select at least one feature"
                "suggestion" -> "Select at least one existing feature"
                else -> "Select at least one feature with the bug"
            }
        }
        val clean = sanitizeFeedbackMessage(message)
        if (clean.length < minChars) return "Please write at least $minChars characters"
        return null
    }

    fun buildPayloadMessage(clean: String): String {
        val typeLabel = feedbackTypeOptions.firstOrNull { it.value == feedbackType }?.label ?: feedbackType
        val parts = mutableListOf("Feedback Type: $typeLabel")
        if (feedbackType == "suggestion") {
            parts += "Suggestion Type: ${if (suggestionType == "new") "New Feature Suggestion" else "Existing Feature Improvement"}"
        }
        if (feedbackType == "bug") {
            parts += "Bug Type: ${if (bugType == "feature") "Feature Bug" else "Other Bug"}"
        }
        if (selectedFeatures.isNotEmpty()) {
            val names = selectedFeatures.map { id ->
                androidFeedbackFeatures.firstOrNull { it.id == id }?.title ?: id
            }.joinToString(", ")
            parts += "Related Features: $names"
        }
        parts += "\nMessage:\n$clean"
        return parts.joinToString("\n")
    }

    PageScaffold(
        title = "Feedback",
        onBack = onBack,
        showKeyboardHide = true,
        contentSpacing = 10.dp,
        horizontalPadding = 14.dp,
    ) {
        if (sent) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(listOf(MtEmerald.copy(alpha = 0.2f), MtCard)),
                    )
                    .border(1.dp, MtEmerald.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MtEmerald.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = MtEmerald, modifier = Modifier.size(32.dp))
                }
                Text("Thank you!", color = MtHi, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Your feedback helps improve MT Auto Clicker for Android & Chrome OS.",
                    color = MtMid,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                )
                MtPrimaryButton(text = "Back to Home", onClick = onBack, containerColor = MtEmerald)
            }
            return@PageScaffold
        }

        // Compact header — accent follows selected feedback type
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MtCard)
                .border(1.dp, typeAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(typeAccent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = typeAccent, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Share feedback", color = MtHi, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Bugs, ideas & feature notes — we read every one", color = MtMid, fontSize = 11.sp)
            }
            // Mini type color legend
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                feedbackTypeOptions.forEach { opt ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (feedbackType == opt.value) opt.accent
                                else opt.accent.copy(alpha = 0.35f),
                            ),
                    )
                }
            }
        }

        errorBanner?.let { err ->
            Text(
                err,
                color = Color(0xFFDC2626),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFDC2626).copy(alpha = 0.1f))
                    .border(1.dp, Color(0xFFDC2626).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
            )
        }

        // Feedback type — each type has its own accent
        FeedbackCard(title = "Feedback type", accent = typeAccent) {
            val rows = feedbackTypeOptions.chunked(2)
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { opt ->
                        val selected = feedbackType == opt.value
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) opt.accent.copy(alpha = 0.12f) else MtRow)
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) opt.accent else opt.accent.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable {
                                    feedbackType = opt.value
                                    resetSubSelections()
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(opt.accent.copy(alpha = if (selected) 0.22f else 0.14f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(opt.icon, null, tint = opt.accent, modifier = Modifier.size(16.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(opt.label, color = MtHi, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(opt.description, color = MtMid, fontSize = 10.sp, lineHeight = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // Feature feedback → pick features
        if (feedbackType == "feature") {
            FeaturePickerCard(
                title = "Select features",
                subtitle = "One or more features this is about",
                required = true,
                showError = submitAttempted && selectedFeatures.isEmpty(),
                selected = selectedFeatures,
                onToggle = ::toggleFeature,
            )
        }

        // Suggestion subtypes
        if (feedbackType == "suggestion") {
            FeedbackCard(title = "Suggestion type", accent = Color(0xFF8B5CF6)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SubTypeChip(
                        label = "New feature",
                        hint = "Brand-new idea",
                        selected = suggestionType == "new",
                        accent = Color(0xFF8B5CF6),
                        modifier = Modifier.weight(1f),
                    ) {
                        suggestionType = "new"
                        selectedFeatures = emptySet()
                    }
                    SubTypeChip(
                        label = "Existing",
                        hint = "Improve a tool",
                        selected = suggestionType == "existing",
                        accent = Color(0xFF6366F1),
                        modifier = Modifier.weight(1f),
                    ) {
                        suggestionType = "existing"
                        selectedFeatures = emptySet()
                    }
                }
                if (suggestionType == "existing") {
                    Spacer(modifier = Modifier.height(4.dp))
                    FeaturePickerInline(
                        required = true,
                        showError = submitAttempted && selectedFeatures.isEmpty(),
                        selected = selectedFeatures,
                        onToggle = ::toggleFeature,
                    )
                }
            }
        }

        // Bug subtypes
        if (feedbackType == "bug") {
            FeedbackCard(title = "Bug type", accent = Color(0xFFEF4444)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SubTypeChip(
                        label = "Feature bug",
                        hint = "Tied to a tool",
                        selected = bugType == "feature",
                        accent = Color(0xFFEF4444),
                        modifier = Modifier.weight(1f),
                    ) {
                        bugType = "feature"
                        selectedFeatures = emptySet()
                    }
                    SubTypeChip(
                        label = "Something else",
                        hint = "App-wide / other",
                        selected = bugType == "other",
                        accent = Color(0xFFF97316),
                        modifier = Modifier.weight(1f),
                    ) {
                        bugType = "other"
                        selectedFeatures = emptySet()
                    }
                }
                if (bugType == "feature") {
                    Spacer(modifier = Modifier.height(4.dp))
                    FeaturePickerInline(
                        required = true,
                        showError = submitAttempted && selectedFeatures.isEmpty(),
                        selected = selectedFeatures,
                        onToggle = ::toggleFeature,
                    )
                }
            }
        }

        // Message
        FeedbackCard(title = "Your message", accent = typeAccent) {
            OutlinedTextField(
                value = message,
                onValueChange = { if (it.length <= maxChars) message = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                placeholder = {
                    Text(
                        when (feedbackType) {
                            "bug" -> "What went wrong? Steps to reproduce help a lot…"
                            "suggestion" -> "Describe your idea clearly…"
                            "feature" -> "What do you like or want improved?"
                            else -> "Share your thoughts…"
                        },
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = typeAccent,
                    unfocusedBorderColor = MtBorder,
                    focusedTextColor = MtHi,
                    unfocusedTextColor = MtHi,
                    cursorColor = typeAccent,
                    focusedPlaceholderColor = MtMid.copy(alpha = 0.55f),
                    unfocusedPlaceholderColor = MtMid.copy(alpha = 0.45f),
                ),
                shape = RoundedCornerShape(12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Min $minChars characters", color = MtMid, fontSize = 10.sp)
                Text(
                    "$remaining left",
                    color = when {
                        remaining < 20 -> Color(0xFFEF4444)
                        remaining < 50 -> Color(0xFFF59E0B)
                        else -> MtMid
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Security note
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MtBlue.copy(alpha = 0.08f))
                .border(1.dp, MtBlue.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Rounded.Security, null, tint = MtBlue, modifier = Modifier.size(16.dp))
            Column {
                Text("Your feedback is secure", color = MtHi, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                Text(
                    "Input is sanitized before send. No passwords or personal data needed.",
                    color = MtMid,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }

        MtPrimaryButton(
            text = if (sending) "Sending…" else "Send feedback",
            enabled = !sending,
            containerColor = typeAccent,
            onClick = {
                submitAttempted = true
                val err = validate()
                if (err != null) {
                    errorBanner = err
                    return@MtPrimaryButton
                }
                errorBanner = null
                val clean = sanitizeFeedbackMessage(message)
                val trackingType = when (feedbackType) {
                    "bug" -> "bug"
                    "feature" -> "feature"
                    "suggestion" -> "suggestion"
                    "other" -> "other"
                    else -> "general"
                }
                val featureNames = selectedFeatures.map { id ->
                    androidFeedbackFeatures.firstOrNull { it.id == id }?.title ?: id
                }
                sending = true
                scope.launch {
                    val ok = MtApplication.instance.androidCms.submitFeedback(
                        feedbackType = trackingType,
                        message = buildPayloadMessage(clean),
                        metadata = buildMap {
                            put("original_feedback_type", feedbackType)
                            put("platform_ui", "android")
                            if (feedbackType == "suggestion") put("suggestion_type", suggestionType)
                            if (feedbackType == "bug") put("bug_type", bugType)
                            if (selectedFeatures.isNotEmpty()) {
                                put("selected_features", selectedFeatures.joinToString(","))
                                put("feature_names", featureNames.joinToString(", "))
                            }
                        },
                    )
                    sending = false
                    if (ok) {
                        sent = true
                    } else {
                        errorBanner = "Could not send — check connection and try again"
                        Toast.makeText(context, "Send failed", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
}

@Composable
private fun FeedbackCard(
    title: String,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MtCard)
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Text(title, color = MtHi, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        content()
    }
}

@Composable
private fun SubTypeChip(
    label: String,
    hint: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.12f) else MtRow)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accent else accent.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(accent),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = MtHi, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(hint, color = MtMid, fontSize = 10.sp)
        }
    }
}

@Composable
private fun FeaturePickerCard(
    title: String,
    subtitle: String,
    required: Boolean,
    showError: Boolean,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    FeedbackCard(title = if (required) "$title *" else title, accent = Color(0xFF2563EB)) {
        Text(subtitle, color = MtMid, fontSize = 10.sp)
        FeaturePickerInline(required = required, showError = showError, selected = selected, onToggle = onToggle)
    }
}

@Composable
private fun FeaturePickerInline(
    required: Boolean,
    showError: Boolean,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        androidFeedbackFeatures.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { feature ->
                    val on = feature.id in selected
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) feature.accent.copy(alpha = 0.12f) else MtRow)
                            .border(
                                width = if (on) 1.5.dp else 1.dp,
                                color = if (on) feature.accent else feature.accent.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable { onToggle(feature.id) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(feature.accent.copy(alpha = if (on) 0.22f else 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                feature.icon,
                                null,
                                tint = feature.accent,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                        Text(
                            feature.title,
                            color = MtHi,
                            fontSize = 11.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 2,
                            lineHeight = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (on) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                null,
                                tint = feature.accent,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
        if (showError && required) {
            Text("Please select at least one feature", color = Color(0xFFDC2626), fontSize = 11.sp)
        }
    }
}

/* ─── What's New (modal) ─────────────────────────────────────────────────── */

@Composable
fun WhatsNewDialog(
    card: AndroidOverlayDto,
    onDismiss: () -> Unit,
    onCta: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "wn")
    val glow by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "glow",
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF12183A), MtCard),
                    ),
                )
                .border(1.dp, MtPurple.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("WHAT'S NEW", color = MtPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, "Close", tint = MtMid, modifier = Modifier.size(18.dp))
                }
            }

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .scale(0.92f + 0.08f * glow)
                        .clip(CircleShape)
                        .background(MtPurple.copy(alpha = 0.18f * glow)),
                )
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(listOf(MtPurple, MtBlue)),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }

            Text(card.title, color = MtHi, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
            Text(
                card.body,
                color = MtMid,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )

            MtPrimaryButton(
                text = card.cta_text.ifBlank { "Got it" },
                onClick = onCta,
                containerColor = MtPurple,
            )
            TextButton(onClick = onDismiss) {
                Text("Close", color = MtMid, fontSize = 13.sp)
            }
        }
    }
}

/* ─── Notification ───────────────────────────────────────────────────────── */

@Composable
fun NotificationPopupDialog(
    card: AndroidOverlayDto,
    onDismiss: () -> Unit,
    onCta: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp))
                .background(MtCard)
                .border(1.dp, MtBlue.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MtBlue.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.NotificationsActive, null, tint = MtBlue, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Notification", color = MtBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(card.title, color = MtHi, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, null, tint = MtMid, modifier = Modifier.size(16.dp))
                }
            }
            Text(card.body, color = MtMid, fontSize = 13.sp, lineHeight = 18.sp)
            MtPrimaryButton(
                text = card.cta_text.ifBlank { "OK" },
                onClick = if (card.cta_text.isNotBlank()) onCta else onDismiss,
            )
        }
    }
}

/* ─── Review ─────────────────────────────────────────────────────────────── */

@Composable
fun ReviewPromptHost(idleOnHome: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stats by AutomationHub.sessionStats.collectAsState()
    var open by remember { mutableStateOf(false) }
    var storeUrl by remember { mutableStateOf("") }

    LaunchedEffect(idleOnHome, stats.runCount) {
        if (!idleOnHome) {
            open = false
            return@LaunchedEffect
        }
        delay(2800)
        val cms = MtApplication.instance.androidCms
        if (cms.shouldShowReview(stats.runCount)) {
            val cfg = cms.fetchReviewConfig()
            storeUrl = cfg.storeUrl
            cms.markReviewShown()
            open = true
        }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Enjoying MT Auto Clicker?", color = MtHi) },
            text = {
                Text(
                    "A quick Play Store rating helps other Android & Chromebook users find us.",
                    color = MtMid,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    open = false
                    scope.launch {
                        MtApplication.instance.androidCms.markReviewSubmitted()
                        MtApplication.instance.androidCms.submitFeedback(
                            feedbackType = "store_review",
                            message = "",
                            rating = 5,
                            metadata = mapOf("action" to "opened_store"),
                        )
                    }
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(storeUrl)))
                    }
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Star, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                        Text(" Rate on Play", color = MtBlue, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    open = false
                    scope.launch {
                        MtApplication.instance.androidCms.submitFeedback(
                            feedbackType = "store_review",
                            message = "",
                            metadata = mapOf("action" to "dismissed"),
                        )
                    }
                }) { Text("Not now", color = MtMid) }
            },
            containerColor = MtCard,
        )
    }
}

/* ─── Interactive tour ───────────────────────────────────────────────────── */

private fun tourStepIcon(step: AndroidTourStepDto, kind: String, index: Int): ImageVector {
    return when (step.action_key) {
        "open_overlay_settings" -> Icons.Rounded.Layers
        "open_accessibility_settings" -> Icons.Rounded.AccessibilityNew
        "open_permissions" -> Icons.Rounded.Security
        "finish" -> Icons.Rounded.PlayArrow
        else -> when {
            kind == "onboarding" && index == 0 -> Icons.Rounded.WavingHand
            kind == "onboarding" && index == 1 -> Icons.Rounded.Security
            else -> Icons.Rounded.TouchApp
        }
    }
}

private fun tourStepAccent(index: Int): Color = when (index % 3) {
    0 -> Color(0xFF8B5CF6)
    1 -> Color(0xFF3B82F6)
    else -> Color(0xFF10B981)
}

@Composable
fun AppTourDialog(
    tour: AndroidTourDto,
    onFinished: () -> Unit,
    onOpenPermissions: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var index by remember { mutableIntStateOf(0) }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val step = tour.steps.getOrNull(index) ?: return
    val accent = tourStepAccent(index)
    val isLast = index >= tour.steps.lastIndex

    fun markCompleteAndClose(action: String) {
        scope.launch {
            val cms = MtApplication.instance.androidCms
            cms.reportTourAction(tour.kind, action, step.id)
            when (tour.kind) {
                "onboarding" -> {
                    cms.setOnboardingDone(true)
                    // If permissions already granted by the end of welcome, don't queue permissions tour.
                    if (PermissionHelper.missingPermissions(context).isEmpty()) {
                        cms.setPermissionsTourDone(true)
                    }
                }
                "permissions" -> cms.setPermissionsTourDone(true)
            }
            onFinished()
        }
    }

    fun goNext() {
        scope.launch {
            MtApplication.instance.androidCms.reportTourAction(tour.kind, "cta", step.id)
        }
        val action = step.action_key
        val missing = PermissionHelper.missingPermissions(context)
        // Never dump the user onto Permissions when everything is already granted —
        // especially on replay / last-step "Get started".
        when {
            action == "open_overlay_settings" -> context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
            action == "open_accessibility_settings" ->
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            action == "open_permissions" && !isLast && missing.isNotEmpty() ->
                onOpenPermissions?.invoke()
        }
        if (isLast || action == "finish") {
            markCompleteAndClose("completed")
        } else {
            index++
        }
    }

    fun goBack() {
        if (index > 0) index--
    }

    LaunchedEffect(Unit) {
        MtApplication.instance.androidCms.reportTourAction(tour.kind, "started")
    }

    Dialog(
        onDismissRequest = { markCompleteAndClose("skipped") },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.78f)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0E1538), MtCard),
                    ),
                )
                .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(26.dp))
                .pointerInput(index) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                dragAccum < -80f -> goNext()
                                dragAccum > 80f -> goBack()
                            }
                            dragAccum = 0f
                        },
                        onHorizontalDrag = { _, amount ->
                            dragAccum += amount
                        },
                    )
                }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tour.title.uppercase(),
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { markCompleteAndClose("skipped") }) {
                    Text("Skip", color = MtMid, fontSize = 13.sp)
                }
            }

            // Progress dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tour.steps.forEachIndexed { i, _ ->
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (i <= index) accent else MtBorder),
                    )
                }
            }

            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    (slideInHorizontally { it / 3 } + fadeIn())
                        .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
                },
                label = "tourStep",
                modifier = Modifier.weight(1f),
            ) { stepIndex ->
                val s = tour.steps.getOrNull(stepIndex) ?: return@AnimatedContent
                val stepAccent = tourStepAccent(stepIndex)
                val stepIcon = tourStepIcon(s, tour.kind, stepIndex)
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(stepAccent.copy(alpha = 0.12f)),
                        )
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(stepAccent, stepAccent.copy(alpha = 0.65f)),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(stepIcon, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        s.title,
                        color = MtHi,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        s.body,
                        color = MtMid,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Swipe or use buttons · ${stepIndex + 1} of ${tour.steps.size}",
                        color = MtMid.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (index > 0) {
                    IconButton(
                        onClick = { goBack() },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MtRow)
                            .border(1.dp, MtBorder, RoundedCornerShape(14.dp)),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = MtHi)
                    }
                }
                MtPrimaryButton(
                    text = step.cta_text.ifBlank {
                        when {
                            isLast -> "Get started"
                            step.action_key.startsWith("open_") -> "Open & continue"
                            else -> "Next"
                        }
                    },
                    onClick = { goNext() },
                    modifier = Modifier.weight(1f),
                    containerColor = accent,
                )
                if (!isLast) {
                    IconButton(
                        onClick = { goNext() },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(accent.copy(alpha = 0.2f))
                            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Next", tint = accent)
                    }
                }
            }
        }
    }
}

/* ─── Host: load once per process, survive navigation ────────────────────── */

@Composable
fun GrowthOverlayHost(
    enabled: Boolean,
    routeIsHome: Boolean,
    pendingTourKind: String?,
    onPendingTourConsumed: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenFeedback: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var bootstrapped by remember { mutableStateOf(false) }
    var whatsNew by remember { mutableStateOf<AndroidOverlayDto?>(null) }
    var notification by remember { mutableStateOf<AndroidOverlayDto?>(null) }
    var activeTour by remember { mutableStateOf<AndroidTourDto?>(null) }

    LaunchedEffect(enabled) {
        if (!enabled || bootstrapped) return@LaunchedEffect
        bootstrapped = true
        val cms = MtApplication.instance.androidCms
        // Permission-aware: never re-show tours when overlay + accessibility are already granted.
        activeTour = cms.resolveLaunchTour(context)
        if (activeTour == null) {
            whatsNew = cms.fetchWhatsNew()
            notification = cms.fetchNotifications().firstOrNull()
        }
    }

    LaunchedEffect(pendingTourKind) {
        val kind = pendingTourKind ?: return@LaunchedEffect
        activeTour = MtApplication.instance.androidCms.fetchTour(kind)
        onPendingTourConsumed()
    }

    // After tour ends, allow What's New once (same session only if not already dismissed).
    LaunchedEffect(activeTour) {
        if (activeTour != null || !bootstrapped) return@LaunchedEffect
        if (whatsNew == null) {
            whatsNew = MtApplication.instance.androidCms.fetchWhatsNew()
        }
        if (notification == null) {
            notification = MtApplication.instance.androidCms.fetchNotifications().firstOrNull()
        }
    }

    if (!enabled) return

    if (routeIsHome) {
        MacStyleGrowthPrompts(
            idle = activeTour == null && whatsNew == null && notification == null,
            onOpenFeedback = onOpenFeedback,
        )
    }

    whatsNew?.let { card ->
        WhatsNewDialog(
            card = card,
            onDismiss = {
                scope.launch {
                    // "Remind later" still marks seen so it won't spam every launch.
                    MtApplication.instance.androidCms.dismissWhatsNew(card)
                    MtApplication.instance.androidCms.reportOverlayAction(card.id, "dismissed")
                }
                whatsNew = null
            },
            onCta = {
                scope.launch {
                    MtApplication.instance.androidCms.reportOverlayAction(card.id, "cta")
                    MtApplication.instance.androidCms.dismissWhatsNew(card)
                }
                val url = card.cta_url.ifBlank { card.deep_link }
                if (url.isNotBlank()) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
                whatsNew = null
            },
        )
    }

    notification?.let { card ->
        NotificationPopupDialog(
            card = card,
            onDismiss = {
                scope.launch {
                    MtApplication.instance.androidCms.dismissNotification(card)
                    MtApplication.instance.androidCms.reportOverlayAction(card.id, "dismissed")
                }
                notification = null
            },
            onCta = {
                scope.launch {
                    MtApplication.instance.androidCms.dismissNotification(card)
                    MtApplication.instance.androidCms.reportOverlayAction(card.id, "cta")
                }
                val url = card.cta_url.ifBlank { card.deep_link }
                if (url.isNotBlank()) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
                notification = null
            },
        )
    }

    activeTour?.let { tour ->
        AppTourDialog(
            tour = tour,
            onFinished = { activeTour = null },
            onOpenPermissions = onOpenPermissions,
        )
    }
}
