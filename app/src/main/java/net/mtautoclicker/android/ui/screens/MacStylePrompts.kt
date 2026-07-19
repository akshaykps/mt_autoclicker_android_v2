package net.mtautoclicker.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mtautoclicker.android.MtApplication
import net.mtautoclicker.android.data.SettingsRepository

private val ReviewCardTop = Color(0xFF1A1A4E)
private val ReviewCardMid = Color(0xFF1E1E5A)
private val ReviewCardBot = Color(0xFF16163D)
private val NudgeTop = Color(0xFF0F2440)
private val StarGold = Color(0xFFFACC15)
private val StarMuted = Color(0xFF94A3B8)
private val StarEmpty = Color(0xFF475569)

/**
 * Mac-parity review card (bottom sheet style) and feedback nudge.
 */
@Composable
fun MacStyleGrowthPrompts(
    idle: Boolean,
    onOpenFeedback: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = MtApplication.instance.settingsRepository
    val cms = MtApplication.instance.androidCms

    var phase by remember { mutableStateOf("idle") } // idle | review | thankyou | nudge
    var selectedStar by remember { mutableIntStateOf(0) }

    // Review gate (Mac: 5 feature uses / remind / soft re-show).
    LaunchedEffect(idle) {
        if (!idle) return@LaunchedEffect
        while (true) {
            delay(2500)
            if (phase != "idle") continue
            if (settings.shouldShowReviewPrompt()) {
                phase = "review"
            } else if (settings.shouldShowFeedbackNudge()) {
                delay(30_000)
                if (phase == "idle") {
                    settings.markFeedbackNudgeShown()
                    phase = "nudge"
                }
            }
        }
    }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    fun submitReview(rating: Int?, action: String) {
        scope.launch {
            cms.submitFeedback(
                feedbackType = "store_review",
                message = "User $action on Play Store — ${rating ?: 0} star(s).",
                rating = rating?.takeIf { it > 0 },
                metadata = mapOf(
                    "action" to action,
                    "store" to "Play Store",
                    "platform" to settings.osPlatformLabel(),
                ),
            )
        }
    }

    fun openStore(rating: Int) {
        phase = "thankyou"
        submitReview(rating, "opened_store")
        scope.launch {
            settings.setReviewCompleted()
            delay(1500)
            openUrl(SettingsRepository.PLAY_STORE_URL)
            delay(700)
            phase = "idle"
            delay(30_000)
            settings.markFeedbackNudgeShown()
            phase = "nudge"
        }
    }

    if (phase == "nudge") {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            FeedbackNudgeCard(
                onLater = { phase = "idle" },
                onOpen = {
                    phase = "idle"
                    onOpenFeedback()
                },
                onClose = { phase = "idle" },
            )
        }
    }

    if (phase == "review" || phase == "thankyou") {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            ReviewRatingCard(
                phase = phase,
                selectedStar = selectedStar,
                onStar = { star ->
                    selectedStar = star
                    if (star >= 4) openStore(star)
                },
                onLater = {
                    submitReview(selectedStar.takeIf { it > 0 }, "later")
                    scope.launch { settings.setReviewRemindLater() }
                    phase = "idle"
                    selectedStar = 0
                },
                onDontAsk = {
                    submitReview(selectedStar.takeIf { it > 0 }, "dismissed")
                    scope.launch { settings.setReviewDontAskAgain() }
                    phase = "idle"
                    selectedStar = 0
                },
                onRateStore = { openStore(selectedStar.coerceAtLeast(1)) },
            )
        }
    }
}

@Composable
private fun FeedbackNudgeCard(
    onLater: () -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(300.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(listOf(NudgeTop, ReviewCardTop, ReviewCardBot)),
            )
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF4ADE80), Color(0xFF10B981), Color(0xFF14B8A6)),
                    ),
                ),
        )
        Box {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF0D9488))),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("💬", fontSize = 16.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Having an issue or suggestion?", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            "Let us know through the Feedback form — we read every message.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Maybe later",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .clickable(onClick = onLater)
                            .padding(vertical = 10.dp),
                    )
                    Text(
                        "Open Feedback",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(listOf(Color(0xFF059669), Color(0xFF0D9488))),
                            )
                            .clickable(onClick = onOpen)
                            .padding(vertical = 10.dp),
                    )
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp),
            ) {
                Icon(Icons.Rounded.Close, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ReviewRatingCard(
    phase: String,
    selectedStar: Int,
    onStar: (Int) -> Unit,
    onLater: () -> Unit,
    onDontAsk: () -> Unit,
    onRateStore: () -> Unit,
) {
    val appear by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(350),
        label = "reviewAppear",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(0.96f + 0.04f * appear)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(listOf(ReviewCardTop, ReviewCardMid, ReviewCardBot)),
            )
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF6366F1), Color(0xFFA855F7), Color(0xFFEC4899)),
                    ),
                ),
        )
        Box {
            Column(modifier = Modifier.padding(18.dp)) {
                if (phase == "thankyou") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("🎉", fontSize = 28.sp)
                        Text("Thank you for your review!", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Your feedback helps us grow.", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        Text("Taking you to Play Store shortly…", color = Color(0xFF64748B), fontSize = 11.sp)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(end = 24.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF9333EA))),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Bolt, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Column {
                            Text("Enjoying MT Auto Clicker?", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Tell us what you think!", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        (1..5).forEach { star ->
                            val filled = star <= selectedStar
                            val tint = when {
                                filled && selectedStar >= 4 -> StarGold
                                filled -> StarMuted
                                else -> StarEmpty
                            }
                            Icon(
                                imageVector = if (filled) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = "$star stars",
                                tint = tint,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { onStar(star) }
                                    .padding(2.dp),
                            )
                        }
                    }

                    Text(
                        text = when {
                            selectedStar >= 4 -> "✨ Great! Rate us on Play Store"
                            selectedStar > 0 -> "💬 Thanks for your honest feedback!"
                            else -> "Tap a star to rate"
                        },
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    )

                    if (selectedStar in 1..3) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "★ Rate on Play Store",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.horizontalGradient(listOf(Color(0xFF4F46E5), Color(0xFF9333EA))),
                                    )
                                    .clickable(onClick = onRateStore)
                                    .padding(vertical = 11.dp),
                            )
                            Text(
                                "Remind me later",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onLater)
                                    .padding(vertical = 8.dp),
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Later",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .clickable(onClick = onLater)
                                    .padding(vertical = 11.dp),
                            )
                            Text(
                                "★ Play Store",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.horizontalGradient(listOf(Color(0xFF4F46E5), Color(0xFF9333EA))),
                                    )
                                    .clickable { onRateStore() }
                                    .padding(vertical = 11.dp),
                            )
                        }
                    }
                }
            }
            if (phase == "review") {
                IconButton(
                    onClick = onDontAsk,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp),
                ) {
                    Icon(Icons.Rounded.Close, "Don't ask again", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
