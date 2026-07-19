package net.mtautoclicker.android.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mtautoclicker.android.R
import net.mtautoclicker.android.data.PermissionHelper
import net.mtautoclicker.android.ui.theme.LocalMtPalette
import kotlin.math.sin

private data class SplashStep(
    val label: String,
    val progressTo: Float,
    val durationMs: Int,
)

/**
 * Theme-aware launch splash matching the light/dark marketing art.
 * Edge-to-edge art (bezels trimmed), animated progress with real permission checks.
 */
@Composable
fun AnimatedSplashScreen(
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val isDark = LocalMtPalette.current.isDark

    val bg = if (isDark) Color(0xFF05050C) else Color(0xFFF7F5FF)
    val accent = if (isDark) Color(0xFFA855F7) else Color(0xFF8B5CF6)
    val accentSoft = if (isDark) Color(0xFF7C3AED) else Color(0xFFA78BFA)
    val track = if (isDark) Color(0xFF2A2438) else Color(0xFFE8E4F2)
    val statusColor = if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED)
    val artRes = if (isDark) R.drawable.splash_art_dark else R.drawable.splash_art_light

    val overlayOk = remember { PermissionHelper.canDrawOverlays(context) }
    val accessibilityOk = remember { PermissionHelper.isAccessibilityEnabled(context) }

    val steps = remember(overlayOk, accessibilityOk) {
        listOf(
            SplashStep(
                label = if (overlayOk) "Overlay permission ready"
                else "Checking overlay permission",
                progressTo = 0.28f,
                durationMs = 700,
            ),
            SplashStep(
                label = if (accessibilityOk) "Accessibility permission ready"
                else "Checking Accessibility permission",
                progressTo = 0.58f,
                durationMs = 850,
            ),
            SplashStep(
                label = "Automating",
                progressTo = 0.86f,
                durationMs = 700,
            ),
            SplashStep(
                label = "Ready",
                progressTo = 1f,
                durationMs = 520,
            ),
        )
    }

    var exiting by remember { mutableStateOf(false) }
    var stepIndex by remember { mutableIntStateOf(0) }
    var statusDots by remember { mutableIntStateOf(0) }

    val artAlpha = remember { Animatable(0f) }
    val artScale = remember { Animatable(1.04f) }
    val footerAlpha = remember { Animatable(0f) }
    val progress = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(1f) }
    val exitScale = remember { Animatable(1f) }

    val infinite = rememberInfiniteTransition(label = "splashTheme")
    val ripplePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ripple",
    )
    val handBob by infinite.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "handBob",
    )
    val glowPulse by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )
    val clickPulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "clickPulse",
    )

    fun finish() {
        if (!exiting) exiting = true
    }

    LaunchedEffect(Unit) {
        launch { artAlpha.animateTo(1f, tween(480, easing = FastOutSlowInEasing)) }
        // Stay slightly overscaled so edges never letterbox during bob/glow.
        launch { artScale.animateTo(1.02f, tween(820, easing = FastOutSlowInEasing)) }
        delay(220)
        launch { footerAlpha.animateTo(1f, tween(380)) }

        steps.forEachIndexed { index, step ->
            stepIndex = index
            progress.animateTo(
                step.progressTo,
                tween(step.durationMs, easing = FastOutSlowInEasing),
            )
            delay(90)
        }
        delay(180)
        finish()
    }

    LaunchedEffect(Unit) {
        while (!exiting) {
            delay(360)
            statusDots = (statusDots + 1) % 4
        }
    }

    LaunchedEffect(exiting) {
        if (!exiting) return@LaunchedEffect
        launch { contentAlpha.animateTo(0f, tween(320)) }
        launch { exitScale.animateTo(1.04f, tween(320)) }
        delay(340)
        onFinished()
    }

    val current = steps[stepIndex.coerceIn(steps.indices)]
    val showAnimatedDots = current.label != "Ready" &&
        !current.label.endsWith("ready")
    val dotsSuffix = if (!showAnimatedDots) "" else when (statusDots) {
        0 -> ""
        1 -> "."
        2 -> ".."
        else -> "..."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .alpha(contentAlpha.value)
            .scale(exitScale.value),
    ) {
        // Full-bleed art — Crop + slight overscale removes side letterboxing.
        Image(
            painter = painterResource(artRes),
            contentDescription = "MT Auto Clicker",
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .offset(y = handBob.dp)
                .scale(artScale.value)
                .alpha(artAlpha.value),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, bg.copy(alpha = 0.55f), bg),
                    ),
                ),
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(artAlpha.value * 0.95f),
        ) {
            val cx = size.width * 0.58f
            val cy = size.height * 0.70f
            for (i in 0 until 3) {
                val local = ((ripplePhase + i / 3f) % 1f)
                val radius = 18f + local * size.minDimension * 0.11f
                val a = (1f - local) * 0.45f * glowPulse
                drawCircle(
                    color = accent.copy(alpha = a),
                    radius = radius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3.2f * (1f - local * 0.55f)),
                )
            }
            drawCircle(
                color = accent.copy(alpha = 0.22f * glowPulse),
                radius = 14f * clickPulse,
                center = Offset(cx, cy),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.55f * glowPulse),
                radius = 4.5f,
                center = Offset(cx, cy),
            )

            val logoCx = size.width * 0.5f
            val logoCy = size.height * 0.18f
            val breath = 0.85f + 0.15f * sin(ripplePhase * Math.PI * 2).toFloat()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.26f * breath),
                        accentSoft.copy(alpha = 0.08f * breath),
                        Color.Transparent,
                    ),
                    center = Offset(logoCx, logoCy),
                    radius = size.minDimension * 0.28f,
                ),
                radius = size.minDimension * 0.28f,
                center = Offset(logoCx, logoCy),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 36.dp, vertical = 20.dp)
                .alpha(footerAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = current.label + dotsSuffix,
                color = statusColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            ) {
                val h = size.height
                val radius = CornerRadius(h / 2f, h / 2f)
                drawRoundRect(
                    color = track,
                    size = size,
                    cornerRadius = radius,
                )
                val fillW = size.width * progress.value
                if (fillW > 0f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(accentSoft, accent, accent.copy(alpha = 0.85f)),
                        ),
                        size = Size(fillW, h),
                        cornerRadius = radius,
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.55f * glowPulse),
                        radius = h * 0.55f,
                        center = Offset(fillW.coerceAtLeast(h), h / 2f),
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(steps.size) { index ->
                    val selected = index == stepIndex
                    Box(
                        modifier = Modifier
                            .size(if (selected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (selected) accent else track),
                    )
                }
            }
        }
    }
}
