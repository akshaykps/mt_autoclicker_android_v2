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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mtautoclicker.android.R
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class SplashRipple(
    val id: Int,
    val x: Float,
    val y: Float,
    val bornAt: Long,
)

private data class SplashSpark(
    val id: Int,
    val x: Float,
    val y: Float,
    val angleDeg: Float,
    val bornAt: Long,
)

private val SplashBg = Color(0xFF060B27)
private val SplashPurple = Color(0xFF8B5CF6)
private val SplashBlue = Color(0xFF3B82F6)
private val SplashGrid = Color(0xFF1E2D5A)

/**
 * Interactive launch splash: target grid, pulsing logo, auto-click cursor,
 * tap-anywhere ripples, and a short arming sequence before entering the app.
 */
@Composable
fun AnimatedSplashScreen(
    onFinished: () -> Unit,
) {
    val ripples = remember { mutableStateListOf<SplashRipple>() }
    val sparks = remember { mutableStateListOf<SplashSpark>() }
    var nextId by remember { mutableIntStateOf(1) }
    var statusIndex by remember { mutableIntStateOf(0) }
    var exiting by remember { mutableStateOf(false) }
    var logoPulseBoost by remember { mutableFloatStateOf(0f) }

    val logoScale = remember { Animatable(0.72f) }
    val logoAlpha = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(1f) }
    val cursorProgress = remember { Animatable(0f) }
    val exitScale = remember { Animatable(1f) }

    val infinite = rememberInfiniteTransition(label = "splash")
    val gridPulse by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "gridPulse",
    )
    val glowPulse by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowPulse",
    )
    val orbit by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbit",
    )

    val statuses = remember {
        listOf(
            "Calibrating targets…",
            "Arming auto clicker…",
            "Syncing click rhythm…",
            "Ready",
        )
    }

    fun spawnClick(xFrac: Float, yFrac: Float) {
        val id = nextId++
        val now = System.currentTimeMillis()
        ripples += SplashRipple(id, xFrac, yFrac, now)
        for (i in 0 until 3) {
            sparks += SplashSpark(
                id = nextId++,
                x = xFrac,
                y = yFrac,
                angleDeg = -40f + i * 28f,
                bornAt = now,
            )
        }
        logoPulseBoost = 1f
    }

    fun finish() {
        if (exiting) return
        exiting = true
    }

    LaunchedEffect(Unit) {
        launch {
            logoAlpha.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
        launch {
            logoScale.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
        }
        // Cursor travels to logo center and "clicks"
        delay(380)
        cursorProgress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        spawnClick(0.5f, 0.42f)
        delay(180)
        spawnClick(0.5f, 0.42f)

        // Auto click rhythm while arming
        repeat(3) { i ->
            delay(420L + i * 40L)
            if (!exiting) {
                val jx = 0.48f + Random.nextFloat() * 0.04f
                val jy = 0.40f + Random.nextFloat() * 0.05f
                spawnClick(jx, jy)
            }
        }
    }

    LaunchedEffect(Unit) {
        for (i in statuses.indices) {
            statusIndex = i
            delay(if (i == statuses.lastIndex) 520 else 700)
            if (exiting) break
        }
        if (!exiting) finish()
    }

    LaunchedEffect(exiting) {
        if (!exiting) return@LaunchedEffect
        launch { contentAlpha.animateTo(0f, tween(380)) }
        launch { exitScale.animateTo(1.08f, tween(380)) }
        delay(400)
        onFinished()
    }

    LaunchedEffect(logoPulseBoost) {
        if (logoPulseBoost <= 0f) return@LaunchedEffect
        delay(220)
        logoPulseBoost = 0f
    }

    // Prune old particles
    LaunchedEffect(Unit) {
        while (true) {
            delay(80)
            val now = System.currentTimeMillis()
            ripples.removeAll { now - it.bornAt > 900 }
            sparks.removeAll { now - it.bornAt > 520 }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF04071A),
                        SplashBg,
                        Color(0xFF0A1030),
                    ),
                ),
            )
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val x = (offset.x / size.width).coerceIn(0f, 1f)
                    val y = (offset.y / size.height).coerceIn(0f, 1f)
                    spawnClick(x, y)
                    // Double-tap-ish: after Ready, any tap finishes faster
                    if (statusIndex >= statuses.lastIndex - 1) {
                        finish()
                    }
                }
            }
            .alpha(contentAlpha.value)
            .scale(exitScale.value),
        contentAlignment = Alignment.Center,
    ) {
        // Target grid + orbiting markers + ripples
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val step = minOf(w, h) / 8.5f

            // Soft vignette grid of crosshairs
            var gy = step
            while (gy < h) {
                var gx = step
                while (gx < w) {
                    val alpha = 0.12f + 0.18f * gridPulse *
                        (0.55f + 0.45f * sin((gx + gy) * 0.01f + orbit * 0.02f).toFloat())
                    drawCrosshair(
                        center = Offset(gx, gy),
                        radius = 10f,
                        color = SplashGrid.copy(alpha = alpha),
                    )
                    gx += step
                }
                gy += step
            }

            // Orbiting click targets around logo
            val cx = w * 0.5f
            val cy = h * 0.42f
            val orbitR = minOf(w, h) * 0.22f
            for (i in 0 until 6) {
                val ang = Math.toRadians((orbit + i * 60.0))
                val tx = cx + cos(ang).toFloat() * orbitR
                val ty = cy + sin(ang).toFloat() * orbitR
                val pulse = 0.55f + 0.45f * glowPulse
                drawCircle(
                    color = SplashBlue.copy(alpha = 0.18f * pulse),
                    radius = 18f + 6f * glowPulse,
                    center = Offset(tx, ty),
                )
                drawCircle(
                    color = SplashPurple.copy(alpha = 0.85f * pulse),
                    radius = 5.5f,
                    center = Offset(tx, ty),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f),
                    radius = 2f,
                    center = Offset(tx, ty),
                )
            }

            // User / auto click ripples
            val now = System.currentTimeMillis()
            ripples.forEach { r ->
                val t = ((now - r.bornAt) / 900f).coerceIn(0f, 1f)
                val radius = 18f + t * minOf(w, h) * 0.18f
                val a = (1f - t)
                drawCircle(
                    color = SplashPurple.copy(alpha = 0.35f * a),
                    radius = radius,
                    center = Offset(r.x * w, r.y * h),
                    style = Stroke(width = 3.5f * (1f - t * 0.6f)),
                )
                drawCircle(
                    color = SplashBlue.copy(alpha = 0.25f * a),
                    radius = radius * 0.62f,
                    center = Offset(r.x * w, r.y * h),
                    style = Stroke(width = 2f),
                )
            }

            sparks.forEach { s ->
                val t = ((now - s.bornAt) / 520f).coerceIn(0f, 1f)
                val len = 10f + t * 28f
                val ox = s.x * w
                val oy = s.y * h
                val rad = Math.toRadians(s.angleDeg.toDouble())
                val dx = cos(rad).toFloat()
                val dy = sin(rad).toFloat()
                drawLine(
                    color = Color.White.copy(alpha = (1f - t) * 0.95f),
                    start = Offset(ox + dx * 8f, oy + dy * 8f),
                    end = Offset(ox + dx * len, oy + dy * len),
                    strokeWidth = 3f * (1f - t * 0.5f),
                    cap = StrokeCap.Round,
                )
            }

            // Animated cursor arrow traveling to center
            val cProg = cursorProgress.value
            if (cProg < 0.98f || !exiting) {
                val start = Offset(w * 0.78f, h * 0.18f)
                val end = Offset(cx + 8f, cy - 6f)
                val pos = Offset(
                    start.x + (end.x - start.x) * cProg,
                    start.y + (end.y - start.y) * cProg,
                )
                drawCursorArrow(pos, Color.White, SplashPurple)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp),
        ) {
            val boost = 1f + logoPulseBoost * 0.06f
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(168.dp)
                    .scale(logoScale.value * boost)
                    .alpha(logoAlpha.value),
            ) {
                // Soft brand glow behind logo
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                SplashPurple.copy(alpha = 0.42f * glowPulse),
                                SplashBlue.copy(alpha = 0.12f * glowPulse),
                                Color.Transparent,
                            ),
                        ),
                        radius = size.minDimension * 0.55f,
                    )
                }
                Image(
                    painter = painterResource(R.drawable.ic_splash_logo),
                    contentDescription = "MT Auto Clicker",
                    modifier = Modifier.size(132.dp),
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "MT Auto Clicker",
                color = Color(0xFFF1F5F9),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tap anywhere to click · tap again when ready to skip",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = statuses[statusIndex.coerceIn(statuses.indices)],
                color = SplashPurple.copy(alpha = 0.95f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCrosshair(
    center: Offset,
    radius: Float,
    color: Color,
) {
    drawCircle(color = color, radius = radius, center = center, style = Stroke(width = 1.4f))
    drawLine(
        color = color,
        start = Offset(center.x - radius * 1.35f, center.y),
        end = Offset(center.x + radius * 1.35f, center.y),
        strokeWidth = 1.2f,
    )
    drawLine(
        color = color,
        start = Offset(center.x, center.y - radius * 1.35f),
        end = Offset(center.x, center.y + radius * 1.35f),
        strokeWidth = 1.2f,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCursorArrow(
    tip: Offset,
    outline: Color,
    fill: Color,
) {
    rotate(degrees = -18f, pivot = tip) {
        val path = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x + 18f, tip.y + 42f)
            lineTo(tip.x + 8f, tip.y + 38f)
            lineTo(tip.x + 22f, tip.y + 68f)
            lineTo(tip.x + 14f, tip.y + 72f)
            lineTo(tip.x + 0f, tip.y + 40f)
            lineTo(tip.x - 10f, tip.y + 48f)
            close()
        }
        drawPath(path, color = outline)
        val inset = Path().apply {
            moveTo(tip.x, tip.y + 4f)
            lineTo(tip.x + 14f, tip.y + 38f)
            lineTo(tip.x + 6f, tip.y + 35f)
            lineTo(tip.x + 18f, tip.y + 62f)
            lineTo(tip.x + 12f, tip.y + 64f)
            lineTo(tip.x + 0f, tip.y + 38f)
            lineTo(tip.x - 7f, tip.y + 44f)
            close()
        }
        drawPath(inset, color = fill)
    }
}
