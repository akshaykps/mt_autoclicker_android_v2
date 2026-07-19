package net.mtautoclicker.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import net.mtautoclicker.android.MtApplication
import net.mtautoclicker.android.data.AndroidNotificationDto
import net.mtautoclicker.android.ui.components.PageScaffold
import net.mtautoclicker.android.ui.theme.MtBlue
import net.mtautoclicker.android.ui.theme.MtBorder
import net.mtautoclicker.android.ui.theme.MtCard
import net.mtautoclicker.android.ui.theme.MtEmerald
import net.mtautoclicker.android.ui.theme.MtHi
import net.mtautoclicker.android.ui.theme.MtMid
import net.mtautoclicker.android.ui.theme.MtPurple
import net.mtautoclicker.android.ui.theme.MtRow
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private data class NotificationStyle(
    val icon: ImageVector,
    val color: Color,
    val label: String,
)

private fun notificationStyle(category: String): NotificationStyle = when (category) {
    "update" -> NotificationStyle(Icons.Rounded.Campaign, Color(0xFF2563EB), "APP UPDATE")
    "whats_new" -> NotificationStyle(Icons.Rounded.AutoAwesome, Color(0xFF7C3AED), "WHAT'S NEW")
    "upcoming" -> NotificationStyle(Icons.Rounded.Schedule, Color(0xFFDB2777), "UPCOMING")
    "event" -> NotificationStyle(Icons.Rounded.Event, Color(0xFFD97706), "EVENT")
    "feedback_reply" -> NotificationStyle(Icons.Rounded.BugReport, Color(0xFF059669), "FEEDBACK REPLY")
    else -> NotificationStyle(Icons.Rounded.Notifications, Color(0xFF0891B2), "NOTICE")
}

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onDeepLink: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<AndroidNotificationDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var unreadOnly by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AndroidNotificationDto?>(null) }

    suspend fun refresh(force: Boolean = false) {
        loading = true
        val response = MtApplication.instance.androidCms.fetchInboxNotifications(force = force)
        if (response.success) {
            items = response.notifications
            error = null
        } else {
            error = "Could not refresh notifications"
        }
        loading = false
    }

    fun markLocalRead(id: Int) {
        items = items.map { if (it.id == id) it.copy(is_read = true) else it }
        selected = selected?.let { if (it.id == id) it.copy(is_read = true) else it }
    }

    fun removeLocal(id: Int) {
        items = items.filterNot { it.id == id }
        if (selected?.id == id) selected = null
    }

    LaunchedEffect(Unit) { refresh(force = false) }

    val unreadItems = remember(items) { items.filterNot { it.is_read } }
    val readItems = remember(items) { items.filter { it.is_read } }
    val visibleUnread = if (unreadOnly) unreadItems else unreadItems
    val visibleRead = if (unreadOnly) emptyList() else readItems
    val unreadCount = unreadItems.size
    val readCount = readItems.size

    selected?.let { item ->
        InboxDetailDialog(
            item = item,
            onDismiss = { selected = null },
            onAction = {
                when {
                    item.deep_link.isNotBlank() -> onDeepLink(item.deep_link)
                    item.cta_url.isNotBlank() -> runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.cta_url)))
                    }
                }
                selected = null
            },
            onDelete = {
                scope.launch {
                    MtApplication.instance.androidCms.markInboxNotification(item.id, "dismiss")
                    removeLocal(item.id)
                }
            },
        )
    }

    PageScaffold(
        title = "Notifications",
        onBack = onBack,
        showKeyboardHide = false,
        contentSpacing = 10.dp,
        horizontalPadding = 14.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MtCard)
                .border(1.dp, MtBlue.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MtBlue.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Inbox, null, tint = MtBlue, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Your inbox", color = MtHi, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    when {
                        unreadCount == 0 && readCount == 0 -> "You're all caught up"
                        unreadCount == 0 -> "$readCount read · tap trash to clear"
                        else -> "$unreadCount unread · $readCount read"
                    },
                    color = if (unreadCount == 0) MtEmerald else MtMid,
                    fontSize = 11.sp,
                )
            }
            SmallInboxAction(Icons.Rounded.Refresh, "Refresh", loading) {
                scope.launch { refresh(force = true) }
            }
            if (unreadCount > 0) {
                SmallInboxAction(Icons.Rounded.MarkEmailRead, "Mark all read", false) {
                    scope.launch {
                        unreadItems.forEach {
                            MtApplication.instance.androidCms.markInboxNotification(it.id, "read")
                        }
                        items = items.map { it.copy(is_read = true) }
                    }
                }
            }
            if (readCount > 0) {
                SmallInboxAction(Icons.Rounded.DeleteOutline, "Clear read", false) {
                    scope.launch {
                        readItems.forEach {
                            MtApplication.instance.androidCms.markInboxNotification(it.id, "dismiss")
                        }
                        items = items.filterNot { it.is_read }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InboxFilterChip(
                text = "All ${items.size}",
                selected = !unreadOnly,
                modifier = Modifier.weight(1f),
            ) { unreadOnly = false }
            InboxFilterChip(
                text = "Unread $unreadCount",
                selected = unreadOnly,
                modifier = Modifier.weight(1f),
            ) { unreadOnly = true }
        }

        error?.let {
            Text(
                it,
                color = Color(0xFFDC2626),
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFDC2626).copy(alpha = 0.1f))
                    .padding(10.dp),
            )
        }

        if (loading && items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MtBlue, modifier = Modifier.size(30.dp))
            }
        } else if (visibleUnread.isEmpty() && visibleRead.isEmpty()) {
            EmptyInbox(unreadOnly = unreadOnly)
        } else {
            if (visibleUnread.isNotEmpty()) {
                SectionHeader(
                    title = "Unread",
                    count = visibleUnread.size,
                    accent = Color(0xFFDC2626),
                )
                visibleUnread.forEach { item ->
                    UnreadShortCard(
                        item = item,
                        onClick = {
                            scope.launch {
                                if (!item.is_read) {
                                    MtApplication.instance.androidCms.markInboxNotification(item.id, "read")
                                    markLocalRead(item.id)
                                }
                                selected = item.copy(is_read = true)
                            }
                        },
                    )
                }
            }

            if (visibleRead.isNotEmpty()) {
                if (visibleUnread.isNotEmpty()) Spacer(modifier = Modifier.height(4.dp))
                SectionHeader(
                    title = "Read",
                    count = visibleRead.size,
                    accent = MtMid,
                )
                visibleRead.forEach { item ->
                    ReadShortCard(
                        item = item,
                        onClick = { selected = item },
                        onDelete = {
                            scope.launch {
                                MtApplication.instance.androidCms.markInboxNotification(item.id, "dismiss")
                                removeLocal(item.id)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title.uppercase(),
            color = accent,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(accent.copy(alpha = 0.12f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text("$count", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MtBorder),
        )
    }
}

@Composable
private fun UnreadShortCard(
    item: AndroidNotificationDto,
    onClick: () -> Unit,
) {
    val style = notificationStyle(item.category)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(style.color.copy(alpha = 0.14f), MtCard),
                ),
            )
            .border(1.dp, style.color.copy(alpha = 0.38f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(style.color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(style.icon, null, tint = style.color, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    style.label,
                    color = style.color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 0.4.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatRelativeTime(item.published_at),
                    color = MtMid.copy(alpha = 0.8f),
                    fontSize = 9.sp,
                )
            }
            Text(
                item.title,
                color = MtHi,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.body.isNotBlank()) {
                Text(
                    item.body,
                    color = MtMid,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(style.color),
        )
    }
}

@Composable
private fun ReadShortCard(
    item: AndroidNotificationDto,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val style = notificationStyle(item.category)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MtCard)
            .border(1.dp, MtBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MtRow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(style.icon, null, tint = MtMid, modifier = Modifier.size(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                color = MtHi.copy(alpha = 0.88f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatRelativeTime(item.published_at).ifBlank { style.label },
                color = MtMid.copy(alpha = 0.75f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Rounded.CheckCircle,
            "Read",
            tint = MtEmerald.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp),
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFFDC2626).copy(alpha = 0.08f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true),
                    onClick = onDelete,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.DeleteOutline,
                "Delete",
                tint = Color(0xFFDC2626),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun InboxDetailDialog(
    item: AndroidNotificationDto,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
    onDelete: () -> Unit,
) {
    val style = notificationStyle(item.category)
    val hasAction = item.deep_link.isNotBlank() || item.cta_url.isNotBlank()
    val pulse = rememberInfiniteTransition(label = "inboxDetail")
    val glow by pulse.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1100, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "glow",
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true),
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.94f, animationSpec = tween(200)),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.96f, animationSpec = tween(140)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(style.color.copy(alpha = 0.22f), MtCard, MtCard),
                        ),
                    )
                    .border(1.dp, style.color.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        style.label,
                        color = style.color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, "Close", tint = MtMid, modifier = Modifier.size(18.dp))
                    }
                }

                Box(contentAlignment = Alignment.Center, modifier = Modifier.height(84.dp)) {
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .scale(0.94f + 0.06f * glow)
                            .clip(CircleShape)
                            .background(style.color.copy(alpha = 0.16f * glow)),
                    )
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(listOf(style.color, style.color.copy(alpha = 0.72f))),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(style.icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }

                Text(
                    item.title,
                    color = MtHi,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MtRow.copy(alpha = 0.55f))
                        .border(1.dp, MtBorder, RoundedCornerShape(14.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                ) {
                    Text(
                        item.body.ifBlank { "No additional details." },
                        color = MtMid,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                    )
                }

                Text(
                    formatPublishedAt(item.published_at),
                    color = MtMid.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                )

                if (hasAction) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(style.color)
                            .clickable(onClick = onAction)
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                item.cta_text.ifBlank {
                                    if (item.is_feedback_reply) "View feedback" else "Open"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", color = Color(0xFFDC2626), fontSize = 13.sp)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = MtMid, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallInboxAction(
    icon: ImageVector,
    description: String,
    disabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MtRow)
            .border(1.dp, MtBorder, RoundedCornerShape(10.dp))
            .then(
                if (disabled) Modifier
                else Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true),
                    onClick = onClick,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (disabled) MtMid.copy(alpha = 0.45f) else MtBlue, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun InboxFilterChip(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MtBlue.copy(alpha = 0.13f) else MtCard)
            .border(1.dp, if (selected) MtBlue else MtBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) MtBlue else MtMid,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun EmptyInbox(unreadOnly: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MtPurple.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (unreadOnly) Icons.Rounded.CheckCircle else Icons.Rounded.Celebration,
                null,
                tint = if (unreadOnly) MtEmerald else MtPurple,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            if (unreadOnly) "No unread notifications" else "Your inbox is empty",
            color = MtHi,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        Text(
            "Updates, events, and feedback replies will appear here.",
            color = MtMid,
            fontSize = 11.sp,
        )
    }
}

private fun formatPublishedAt(value: String): String {
    if (value.isBlank()) return ""
    return runCatching {
        OffsetDateTime.parse(value).format(
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT),
        )
    }.getOrDefault(value.take(16).replace('T', ' '))
}

private fun formatRelativeTime(value: String): String {
    if (value.isBlank()) return ""
    return runCatching {
        val then = OffsetDateTime.parse(value)
        val now = OffsetDateTime.now()
        val minutes = java.time.Duration.between(then, now).toMinutes()
        when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m"
            minutes < 60 * 24 -> "${minutes / 60}h"
            minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d"
            else -> then.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }
    }.getOrDefault(value.take(10))
}
