package net.mtautoclicker.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import net.mtautoclicker.android.MtApplication
import net.mtautoclicker.android.data.UserGuideDocument
import net.mtautoclicker.android.data.UserGuideSection
import net.mtautoclicker.android.ui.components.PageScaffold
import net.mtautoclicker.android.ui.theme.MtBlue
import net.mtautoclicker.android.ui.theme.MtBorder
import net.mtautoclicker.android.ui.theme.MtCard
import net.mtautoclicker.android.ui.theme.MtEmerald
import net.mtautoclicker.android.ui.theme.MtHi
import net.mtautoclicker.android.ui.theme.MtMid
import net.mtautoclicker.android.ui.theme.MtPurple
import net.mtautoclicker.android.ui.theme.MtRow

@Composable
fun UserGuideScreen(
    onBack: () -> Unit,
    initialSectionId: String? = null,
    onOpenRoute: (String) -> Unit,
) {
    var guide by remember { mutableStateOf<UserGuideDocument?>(null) }
    var selectedId by rememberSaveable(initialSectionId) {
        mutableStateOf(initialSectionId)
    }
    var query by rememberSaveable { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    var refreshedOnce by remember { mutableStateOf(false) }
    val repository = MtApplication.instance.userGuideRepository
    val scope = rememberCoroutineScope()

    suspend fun refreshGuide() {
        refreshing = true
        val remote = repository.refresh()
        if (remote != null) guide = remote
        refreshedOnce = true
        refreshing = false
    }

    LaunchedEffect(Unit) {
        guide = repository.loadLocal()
        refreshGuide()
    }

    val document = guide
    if (document == null) {
        PageScaffold(title = "User Guide", onBack = onBack) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MtBlue)
            }
        }
        return
    }

    val selectedIndex = document.sections.indexOfFirst { it.id == selectedId }
    val selected = document.sections.getOrNull(selectedIndex)
    if (selected != null) {
        GuideSectionDetail(
            section = selected,
            chapterNumber = selectedIndex + 1,
            chapterCount = document.sections.size,
            previous = document.sections.getOrNull(selectedIndex - 1),
            next = document.sections.getOrNull(selectedIndex + 1),
            version = document.version,
            source = document.source,
            onBack = { selectedId = null },
            onOpenSection = { selectedId = it },
            onOpenRoute = onOpenRoute,
        )
        return
    }

    val normalizedQuery = query.trim().lowercase()
    val filtered = document.sections.filter { section ->
        normalizedQuery.isBlank() ||
            section.title.contains(normalizedQuery, ignoreCase = true) ||
            section.body.contains(normalizedQuery, ignoreCase = true) ||
            section.tags.any { it.contains(normalizedQuery, ignoreCase = true) }
    }

    PageScaffold(title = "User Guide", onBack = onBack, contentSpacing = 12.dp) {
        GuideHero(
            document = document,
            refreshing = refreshing,
            refreshedOnce = refreshedOnce,
            onRefresh = {
                scope.launch { refreshGuide() }
            },
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text("Search setup, macros, presets…") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MtHi,
                unfocusedTextColor = MtHi,
                focusedBorderColor = MtBlue,
                unfocusedBorderColor = MtBorder,
                focusedContainerColor = MtCard,
                unfocusedContainerColor = MtCard,
                cursorColor = MtBlue,
                focusedLeadingIconColor = MtBlue,
                unfocusedLeadingIconColor = MtMid,
                focusedPlaceholderColor = MtMid,
                unfocusedPlaceholderColor = MtMid,
            ),
            shape = RoundedCornerShape(14.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            listOf("Setup", "Target", "Macro", "Screenshot", "Troubleshooting", "Privacy").forEach { topic ->
                val active = query.equals(topic, ignoreCase = true)
                AssistChip(
                    onClick = { query = if (active) "" else topic },
                    label = { Text(topic, fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (active) MtBlue.copy(alpha = .2f) else MtRow,
                        labelColor = if (active) MtBlue else MtMid,
                    ),
                    border = AssistChipDefaults.assistChipBorder(
                        enabled = true,
                        borderColor = if (active) MtBlue else MtBorder,
                    ),
                )
            }
        }

        Text(
            text = if (query.isBlank()) {
                "Table of contents · ${document.sections.size} chapters"
            } else {
                "${filtered.size} results for “$query”"
            },
            color = MtMid,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )

        if (normalizedQuery.isBlank()) {
            guideGroups(document.sections).forEach { group ->
                GuideGroupHeader(title = group.title, icon = group.icon, accent = group.accent)
                group.sections.forEach { section ->
                    GuideSectionCard(
                        section = section,
                        number = document.sections.indexOf(section) + 1,
                        query = normalizedQuery,
                        accent = group.accent,
                        onClick = { selectedId = section.id },
                    )
                }
            }
        } else {
            filtered.forEach { section ->
                GuideSectionCard(
                    section = section,
                    number = document.sections.indexOf(section) + 1,
                    query = normalizedQuery,
                    accent = MtBlue,
                    onClick = { selectedId = section.id },
                )
            }
        }

        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MtCard)
                    .border(1.dp, MtBorder, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = MtMid)
                Spacer(Modifier.height(8.dp))
                Text("No exact match", color = MtHi, fontWeight = FontWeight.Bold)
                Text(
                    "Try a shorter word such as “interval”, “permission”, or “preset”.",
                    color = MtMid,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

private data class GuideGroup(
    val title: String,
    val icon: ImageVector,
    val accent: Color,
    val sections: List<UserGuideSection>,
)

@Composable
private fun guideGroups(sections: List<UserGuideSection>): List<GuideGroup> {
    fun match(section: UserGuideSection, vararg keys: String) =
        keys.any { section.title.contains(it, ignoreCase = true) }

    val gettingStarted = sections.filter {
        match(it, "Welcome", "Requirements", "Setup", "Permission") ||
            match(it, "What MT Auto Clicker Does")
    }
    val tools = sections.filter {
        match(it, "Single Target", "Multi Target", "Macro", "Screenshot", "Auto Refresh", "Floatbar", "Common Options")
    }
    val library = sections.filter {
        match(it, "Preset", "Recent", "Notification", "Feedback", "Settings", "Backup", "Import", "Tour", "Review")
    }
    val help = sections.filter {
        match(it, "Example", "Troubleshoot", "Privacy", "Safety", "Quick Reference", "Support")
    }
    val used = (gettingStarted + tools + library + help).map { it.id }.toSet()
    val other = sections.filterNot { it.id in used }

    return listOf(
        GuideGroup("Getting started", Icons.Rounded.PlayArrow, MtBlue, gettingStarted),
        GuideGroup("Automation tools", Icons.Rounded.TouchApp, MtPurple, tools),
        GuideGroup("Library & account", Icons.Rounded.Settings, MtEmerald, library),
        GuideGroup("Help & safety", Icons.Rounded.Security, Color(0xFFD97706), help),
        GuideGroup("More", Icons.Rounded.Book, MtMid, other),
    ).filter { it.sections.isNotEmpty() }
}

@Composable
private fun GuideGroupHeader(title: String, icon: ImageVector, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = .16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        }
        Text(title, color = MtHi, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        Spacer(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MtBorder),
        )
    }
}

@Composable
private fun GuideHero(
    document: UserGuideDocument,
    refreshing: Boolean,
    refreshedOnce: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MtBlue.copy(alpha = .12f))
            .border(1.dp, MtBlue.copy(alpha = .3f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(13.dp))
                .background(MtBlue.copy(alpha = .2f))
                .padding(11.dp),
        ) {
            Icon(Icons.Rounded.Book, contentDescription = null, tint = MtBlue)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(document.title, color = MtHi, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Text(
                document.summary,
                color = MtMid,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (document.source == "bundled") Icons.Rounded.CloudOff else Icons.Rounded.CloudDone,
                    contentDescription = null,
                    tint = if (document.source == "bundled") MtMid else MtEmerald,
                    modifier = Modifier.padding(end = 5.dp).size(14.dp),
                )
                Text(
                    if (document.source == "bundled") "Available offline · v${document.version}"
                    else "Synced from server · v${document.version}",
                    color = if (document.source == "bundled") MtMid else MtEmerald,
                    fontSize = 11.sp,
                )
            }
        }
        IconButton(onClick = onRefresh, enabled = !refreshing) {
            if (refreshing) {
                CircularProgressIndicator(color = MtBlue, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = if (refreshedOnce) "Check again" else "Check for updates",
                    tint = MtBlue,
                )
            }
        }
    }
}

@Composable
private fun GuideSectionCard(
    section: UserGuideSection,
    number: Int,
    query: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val preview = remember(section.body, query) {
        section.body.lineSequence()
            .map { it.trim().trimStart('#', '-', '*', '>', ' ') }
            .map { stripMarkdownLinks(it).replace("**", "") }
            .firstOrNull { it.length > 25 && (query.isBlank() || it.contains(query, true)) }
            ?.take(140)
            .orEmpty()
    }
    val topicCount = remember(section.body) {
        section.body.lineSequence().count { it.startsWith("### ") }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MtCard)
            .border(1.dp, MtBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = .16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString().padStart(2, '0'),
                color = accent,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(section.title, color = MtHi, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (preview.isNotBlank()) {
                Text(
                    preview,
                    color = MtMid,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (topicCount > 0) {
                    MetaPill("$topicCount topics", accent)
                }
                if (section.images.isNotEmpty()) {
                    MetaPill(
                        "${section.images.size} shot${if (section.images.size == 1) "" else "s"}",
                        MtEmerald,
                    )
                }
                if (section.related_route.isNotBlank()) {
                    MetaPill("Try in app", MtPurple)
                }
            }
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = accent,
        )
    }
}

@Composable
private fun MetaPill(text: String, color: Color) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = .12f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun GuideSectionDetail(
    section: UserGuideSection,
    chapterNumber: Int,
    chapterCount: Int,
    previous: UserGuideSection?,
    next: UserGuideSection?,
    version: Int,
    source: String,
    onBack: () -> Unit,
    onOpenSection: (String) -> Unit,
    onOpenRoute: (String) -> Unit,
) {
    val outline = remember(section.body) {
        section.body.lineSequence()
            .filter { it.startsWith("### ") }
            .map { it.removePrefix("### ").trim() }
            .toList()
    }
    var focusedHeading by rememberSaveable(section.id) { mutableStateOf<String?>(null) }

    PageScaffold(title = section.title, onBack = onBack, contentSpacing = 12.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MtCard)
                .border(1.dp, MtBorder, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Chapter $chapterNumber of $chapterCount",
                    color = MtBlue,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MtEmerald,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        " Offline · v$version",
                        color = MtEmerald,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MtRow),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(chapterNumber.toFloat() / chapterCount.coerceAtLeast(1))
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MtBlue),
                )
            }
            Text(section.title, color = MtHi, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            if (source != "bundled") {
                Text("Updated from server", color = MtMid, fontSize = 11.sp)
            }
        }

        if (outline.isNotEmpty()) {
            Text("In this chapter", color = MtMid, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                outline.forEachIndexed { index, heading ->
                    val active = focusedHeading == heading
                    AssistChip(
                        onClick = {
                            focusedHeading = if (active) null else heading
                        },
                        label = {
                            Text(
                                "${index + 1}. $heading",
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (active) MtBlue.copy(alpha = .2f) else MtCard,
                            labelColor = if (active) MtBlue else MtHi,
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = if (active) MtBlue else MtBorder,
                        ),
                    )
                }
            }
            if (focusedHeading != null) {
                Text(
                    "Showing “$focusedHeading” — tap the chip again to show the full chapter.",
                    color = MtMid,
                    fontSize = 11.sp,
                )
            }
        }

        MarkdownLite(
            markdown = section.body,
            focusHeading = focusedHeading,
        )

        section.images.sortedBy { it.order }.forEach { image ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(MtCard)
                    .border(1.dp, MtBorder, RoundedCornerShape(15.dp)),
            ) {
                AsyncImage(
                    model = image.url,
                    contentDescription = image.alt_text.ifBlank { image.caption },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                        .background(MtRow),
                    contentScale = ContentScale.Fit,
                )
                if (image.caption.isNotBlank()) {
                    Text(
                        image.caption,
                        modifier = Modifier.padding(12.dp),
                        color = MtMid,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        if (section.related_route.isNotBlank()) {
            Button(
                onClick = { onOpenRoute(section.related_route) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MtBlue),
                shape = RoundedCornerShape(13.dp),
            ) {
                Icon(Icons.Rounded.TouchApp, contentDescription = null)
                Text(
                    "Open this feature",
                    modifier = Modifier.padding(start = 8.dp),
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = { previous?.let { onOpenSection(it.id) } },
                enabled = previous != null,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp),
                border = BorderStroke(1.dp, MtBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MtHi),
            ) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null)
                Text(
                    shortLabel(previous?.title, "Previous"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                )
            }
            Button(
                onClick = { next?.let { onOpenSection(it.id) } },
                enabled = next != null,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MtBlue),
                shape = RoundedCornerShape(13.dp),
            ) {
                Text(
                    shortLabel(next?.title, "Next"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                )
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            }
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(1.dp, MtBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MtMid),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Back to contents", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun MarkdownLite(
    markdown: String,
    focusHeading: String? = null,
) {
    val blocks = remember(markdown, focusHeading) {
        parseGuideBlocks(markdown, focusHeading)
    }
    blocks.forEach { block ->
        when (block) {
            is GuideBlock.SpacerBlock -> Spacer(Modifier.height(block.heightDp.dp))
            is GuideBlock.Heading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MtBlue.copy(alpha = .1f))
                        .border(1.dp, MtBlue.copy(alpha = .22f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MtBlue),
                    )
                    Text(
                        block.text,
                        color = MtHi,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (block.level <= 3) 15.sp else 13.sp,
                    )
                }
            }
            is GuideBlock.Callout -> {
                val accent = when (block.kind) {
                    CalloutKind.Warning -> Color(0xFFD97706)
                    CalloutKind.Tip -> MtEmerald
                    CalloutKind.Info -> MtBlue
                }
                val icon = when (block.kind) {
                    CalloutKind.Warning -> Icons.Rounded.Warning
                    CalloutKind.Tip -> Icons.Rounded.Lightbulb
                    CalloutKind.Info -> Icons.Rounded.Info
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = .1f))
                        .border(1.dp, accent.copy(alpha = .28f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Text(
                        inlineMarkdown(block.text),
                        color = MtHi,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            is GuideBlock.Code -> Text(
                block.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MtRow)
                    .border(1.dp, MtBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = MtHi,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            is GuideBlock.Divider -> Spacer(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .height(1.dp)
                    .background(MtBorder),
            )
            is GuideBlock.Bullet -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MtCard)
                    .border(1.dp, MtBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MtBlue),
                )
                Text(
                    inlineMarkdown(block.text),
                    color = MtMid,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.weight(1f),
                )
            }
            is GuideBlock.Step -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MtCard)
                    .border(1.dp, MtBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MtBlue.copy(alpha = .16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        block.number.toString(),
                        color = MtBlue,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    inlineMarkdown(block.text),
                    color = MtHi,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.weight(1f),
                )
            }
            is GuideBlock.Paragraph -> Text(
                inlineMarkdown(block.text),
                color = MtMid,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

private sealed class GuideBlock {
    data class SpacerBlock(val heightDp: Int) : GuideBlock()
    data class Heading(val text: String, val level: Int) : GuideBlock()
    data class Callout(val text: String, val kind: CalloutKind) : GuideBlock()
    data class Code(val text: String) : GuideBlock()
    data object Divider : GuideBlock()
    data class Bullet(val text: String) : GuideBlock()
    data class Step(val number: Int, val text: String) : GuideBlock()
    data class Paragraph(val text: String) : GuideBlock()
}

private enum class CalloutKind { Info, Tip, Warning }

private fun parseGuideBlocks(markdown: String, focusHeading: String?): List<GuideBlock> {
    val source = if (focusHeading.isNullOrBlank()) {
        markdown
    } else {
        extractHeadingSection(markdown, focusHeading) ?: markdown
    }
    val blocks = mutableListOf<GuideBlock>()
    var inCode = false
    source.lines().forEach { raw ->
        val line = raw.trimEnd()
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                inCode = !inCode
            }
            trimmed.isBlank() -> blocks += GuideBlock.SpacerBlock(4)
            inCode -> blocks += GuideBlock.Code(line)
            trimmed == "---" -> blocks += GuideBlock.Divider
            trimmed.startsWith("### ") -> blocks += GuideBlock.Heading(trimmed.removePrefix("### "), 3)
            trimmed.startsWith("#### ") -> blocks += GuideBlock.Heading(trimmed.removePrefix("#### "), 4)
            trimmed.startsWith("> ") -> {
                val text = stripMarkdownLinks(trimmed.removePrefix("> "))
                val kind = when {
                    text.contains("Important", true) || text.contains("Warning", true) ||
                        text.contains("Do not", true) -> CalloutKind.Warning
                    text.contains("Tip", true) || text.contains("Hint", true) -> CalloutKind.Tip
                    else -> CalloutKind.Info
                }
                blocks += GuideBlock.Callout(text, kind)
            }
            trimmed.matches(Regex("""^[-*]\s+.*""")) -> {
                blocks += GuideBlock.Bullet(
                    stripMarkdownLinks(trimmed.replaceFirst(Regex("""^[-*]\s+"""), "")),
                )
            }
            trimmed.matches(Regex("""^\d+\.\s+.*""")) -> {
                val number = trimmed.substringBefore('.').trim().toIntOrNull() ?: 1
                val text = stripMarkdownLinks(trimmed.replaceFirst(Regex("""^\d+\.\s+"""), ""))
                // Skip leftover TOC-style empty anchors
                if (text.isNotBlank()) {
                    blocks += GuideBlock.Step(number, text)
                }
            }
            else -> blocks += GuideBlock.Paragraph(stripMarkdownLinks(trimmed))
        }
    }
    return blocks
}

private fun extractHeadingSection(markdown: String, heading: String): String? {
    val lines = markdown.lines()
    val start = lines.indexOfFirst {
        it.trim() == "### $heading" || it.trim().removePrefix("### ").trim() == heading
    }
    if (start < 0) return null
    val end = lines.drop(start + 1).indexOfFirst { it.trim().startsWith("### ") }
        .let { if (it < 0) lines.size else start + 1 + it }
    return lines.subList(start, end).joinToString("\n")
}

private fun shortLabel(title: String?, fallback: String): String {
    val value = title ?: return fallback
    return if (value.length > 14) value.take(14) + "…" else value
}

private fun stripMarkdownLinks(value: String): String =
    value
        .replace(Regex("""\[([^\]]+)]\([^)]+\)"""), "$1")
        .replace(Regex("""\[([^\]]+)]"""), "$1")

@Composable
private fun inlineMarkdown(value: String) = buildAnnotatedString {
    var cursor = 0
    val bold = Regex("""\*\*(.+?)\*\*""")
    bold.findAll(value).forEach { match ->
        append(value.substring(cursor, match.range.first))
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MtHi))
        append(match.groupValues[1])
        pop()
        cursor = match.range.last + 1
    }
    append(value.substring(cursor))
}
