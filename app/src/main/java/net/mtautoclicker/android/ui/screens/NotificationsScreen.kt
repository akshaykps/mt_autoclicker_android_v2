package net.mtautoclicker.android.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    suspend fun refresh() {
        loading = true
        val response = MtApplication.instance.androidCms.fetchInboxNotifications()
        if (response.success) {
            items = response.notifications
            error = null
        } else {
            error = "Could not refresh notifications"
        }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    val visibleItems = if (unreadOnly) items.filterNot { it.is_read } else items
    val unreadCount = items.count { !it.is_read }

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
                    if (unreadCount == 0) "You're all caught up"
                    else "$unreadCount unread ${if (unreadCount == 1) "message" else "messages"}",
                    color = if (unreadCount == 0) MtEmerald else MtMid,
                    fontSize = 11.sp,
                )
            }
            SmallInboxAction(Icons.Rounded.Refresh, "Refresh", loading) {
                scope.launch { refresh() }
            }
            if (unreadCount > 0) {
                SmallInboxAction(Icons.Rounded.MarkEmailRead, "Mark all read", false) {
                    scope.launch {
                        items.filterNot { it.is_read }.forEach {
                            MtApplication.instance.androidCms.markInboxNotification(it.id, "read")
                        }
                        refresh()
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
        } else if (visibleItems.isEmpty()) {
            EmptyInbox(unreadOnly = unreadOnly)
        } else {
            visibleItems.forEach { item ->
                NotificationInboxCard(
                    item = item,
                    onClick = {
                        scope.launch {
                            if (!item.is_read) {
                                MtApplication.instance.androidCms.markInboxNotification(item.id, "read")
                                items = items.map {
                                    if (it.id == item.id) it.copy(is_read = true) else it
                                }
                            }
                        }
                    },
                    onAction = {
                        when {
                            item.deep_link.isNotBlank() -> onDeepLink(item.deep_link)
                            item.cta_url.isNotBlank() -> runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.cta_url)))
                            }
                        }
                    },
                )
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
private fun NotificationInboxCard(
    item: AndroidNotificationDto,
    onClick: () -> Unit,
    onAction: () -> Unit,
) {
    val style = notificationStyle(item.category)
    val hasAction = item.deep_link.isNotBlank() || item.cta_url.isNotBlank()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (item.is_read) MtCard else style.color.copy(alpha = 0.08f))
            .border(
                1.dp,
                if (item.is_read) MtBorder else style.color.copy(alpha = 0.42f),
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(style.color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(style.icon, null, tint = style.color, modifier = Modifier.size(19.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        style.label,
                        color = style.color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (!item.is_read) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(style.color),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    item.title,
                    color = MtHi,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(item.body, color = MtMid, fontSize = 12.sp, lineHeight = 17.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatPublishedAt(item.published_at),
                color = MtMid.copy(alpha = 0.75f),
                fontSize = 10.sp,
                modifier = Modifier.weight(1f),
            )
            if (hasAction) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(style.color.copy(alpha = 0.12f))
                        .clickable {
                            onClick()
                            onAction()
                        }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        item.cta_text.ifBlank {
                            if (item.is_feedback_reply) "View feedback" else "Open"
                        },
                        color = style.color,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        null,
                        tint = style.color,
                        modifier = Modifier.size(13.dp),
                    )
                }
            } else if (item.is_read) {
                Icon(Icons.Rounded.CheckCircle, "Read", tint = MtEmerald, modifier = Modifier.size(14.dp))
            }
        }
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
