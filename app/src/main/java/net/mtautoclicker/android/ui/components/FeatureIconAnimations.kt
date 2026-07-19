package net.mtautoclicker.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.mtautoclicker.android.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Looping mini-demos for Home feature cards — show what each tool does at a glance.
 */
@Composable
fun SingleTargetAnimatedIcon(
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val infinite = rememberInfiniteTransition(label = "singleTarget")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "singlePhase",
    )
    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val ringR = this.size.minDimension * 0.32f

        // Target ring
        drawCircle(
            color = accent.copy(alpha = 0.35f),
            radius = ringR,
            center = Offset(cx, cy),
            style = Stroke(width = this.size.minDimension * 0.06f),
        )
        drawCircle(
            color = accent,
            radius = this.size.minDimension * 0.08f,
            center = Offset(cx, cy),
        )

        // Expanding click ripple (0 → 0.55)
        val rippleT = (phase / 0.55f).coerceIn(0f, 1f)
        if (phase < 0.55f) {
            drawCircle(
                color = accent.copy(alpha = (1f - rippleT) * 0.55f),
                radius = ringR * (0.4f + rippleT * 1.4f),
                center = Offset(cx, cy),
                style = Stroke(width = this.size.minDimension * 0.045f),
            )
        }

        // Tap press scale on center (brief squash mid-cycle)
        val press = when {
            phase in 0.08f..0.18f -> 1f - ((phase - 0.08f) / 0.1f) * 0.35f
            phase in 0.18f..0.28f -> 0.65f + ((phase - 0.18f) / 0.1f) * 0.35f
            else -> 1f
        }
        drawCircle(
            color = accent,
            radius = this.size.minDimension * 0.08f * press,
            center = Offset(cx, cy),
        )

        // Cursor / finger tip approaching from bottom-right then tapping
        val tipProgress = when {
            phase < 0.35f -> phase / 0.35f
            phase < 0.5f -> 1f
            else -> 1f - ((phase - 0.5f) / 0.5f).coerceIn(0f, 1f) * 0.35f
        }
        val tipX = cx + this.size.width * 0.28f * (1f - tipProgress)
        val tipY = cy + this.size.height * 0.28f * (1f - tipProgress)
        drawCircle(
            color = accent.copy(alpha = 0.9f),
            radius = this.size.minDimension * 0.07f,
            center = Offset(tipX, tipY),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = this.size.minDimension * 0.03f,
            center = Offset(tipX, tipY),
        )
    }
}

@Composable
fun MultiTargetAnimatedIcon(
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val infinite = rememberInfiniteTransition(label = "multiTarget")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "multiPhase",
    )
    Canvas(modifier = modifier.size(size)) {
        val pad = this.size.minDimension * 0.18f
        val cell = (this.size.minDimension - pad * 2f) / 2f
        val positions = listOf(
            Offset(pad + cell * 0.5f, pad + cell * 0.5f),
            Offset(pad + cell * 1.5f, pad + cell * 0.5f),
            Offset(pad + cell * 0.5f, pad + cell * 1.5f),
            Offset(pad + cell * 1.5f, pad + cell * 1.5f),
        )
        val active = phase.toInt().coerceIn(0, 3)
        val localT = phase - active

        positions.forEachIndexed { index, center ->
            val isActive = index == active
            val isDone = index < active
            val baseAlpha = when {
                isActive -> 0.45f + localT * 0.55f
                isDone -> 0.85f
                else -> 0.28f
            }
            val radius = this.size.minDimension * if (isActive) 0.11f + localT * 0.04f else 0.1f
            drawRoundRect(
                color = accent.copy(alpha = baseAlpha * 0.25f),
                topLeft = Offset(center.x - cell * 0.38f, center.y - cell * 0.38f),
                size = Size(cell * 0.76f, cell * 0.76f),
                cornerRadius = CornerRadius(cell * 0.18f),
            )
            drawCircle(
                color = accent.copy(alpha = baseAlpha),
                radius = radius,
                center = center,
            )
            if (isActive && localT < 0.45f) {
                val rip = localT / 0.45f
                drawCircle(
                    color = accent.copy(alpha = (1f - rip) * 0.5f),
                    radius = radius * (1f + rip * 1.8f),
                    center = center,
                    style = Stroke(width = this.size.minDimension * 0.035f),
                )
            }
        }
    }
}

@Composable
fun MacroRecorderAnimatedIcon(
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val infinite = rememberInfiniteTransition(label = "macro")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "macroPhase",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "macroPulse",
    )
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val min = this.size.minDimension
        val badge = Offset(w * 0.22f, h * 0.22f)
        val recR = min * 0.11f * pulse
        val playColor = Color(0xFF22C55E)

        // Fixed task: tap #1 → swipe → tap #2 (recorded, then replayed identically)
        val tap1 = Offset(w * 0.28f, h * 0.68f)
        val mid = Offset(w * 0.55f, h * 0.78f)
        val tap2 = Offset(w * 0.78f, h * 0.42f)

        val isRecording = phase < 0.44f
        val isTransition = phase in 0.44f..0.56f
        val isPlayback = phase > 0.56f
        val actionColor = if (isPlayback) playColor else accent

        val local = when {
            isRecording -> (phase / 0.44f).coerceIn(0f, 1f)
            isPlayback -> ((phase - 0.56f) / 0.44f).coerceIn(0f, 1f)
            else -> 1f
        }

        // Ghost full path so the repeated task is obvious
        val ghost = Path().apply {
            moveTo(tap1.x, tap1.y)
            quadraticBezierTo(mid.x, mid.y, tap2.x, tap2.y)
        }
        drawPath(
            path = ghost,
            color = accent.copy(alpha = 0.16f),
            style = Stroke(width = min * 0.045f, cap = StrokeCap.Round),
        )

        // Target markers for the same two taps
        drawCircle(color = actionColor.copy(alpha = 0.22f), radius = min * 0.08f, center = tap1)
        drawCircle(color = actionColor.copy(alpha = 0.22f), radius = min * 0.08f, center = tap2)
        drawCircle(color = actionColor.copy(alpha = 0.7f), radius = min * 0.035f, center = tap1)
        drawCircle(color = actionColor.copy(alpha = 0.7f), radius = min * 0.035f, center = tap2)

        if (!isTransition) {
            // 0–0.18: press tap1, 0.18–0.78: swipe, 0.78–1.0: press tap2
            val tip = when {
                local < 0.18f -> tap1
                local < 0.78f -> {
                    val t = ((local - 0.18f) / 0.6f).coerceIn(0f, 1f)
                    // Draw completed trail behind the cursor
                    var prev = tap1
                    val segs = 16
                    val drawUntil = (t * segs).toInt()
                    for (i in 1..drawUntil) {
                        val cur = quadBezier(tap1, mid, tap2, i / segs.toFloat())
                        drawLine(
                            color = actionColor,
                            start = prev,
                            end = cur,
                            strokeWidth = min * 0.07f,
                            cap = StrokeCap.Round,
                        )
                        prev = cur
                    }
                    quadBezier(tap1, mid, tap2, t)
                }
                else -> {
                    // Full trail already complete
                    var prev = tap1
                    for (i in 1..16) {
                        val cur = quadBezier(tap1, mid, tap2, i / 16f)
                        drawLine(
                            color = actionColor,
                            start = prev,
                            end = cur,
                            strokeWidth = min * 0.07f,
                            cap = StrokeCap.Round,
                        )
                        prev = cur
                    }
                    tap2
                }
            }

            // Cursor
            drawCircle(color = actionColor, radius = min * 0.075f, center = tip)
            drawCircle(color = Color.White.copy(alpha = 0.92f), radius = min * 0.03f, center = tip)

            // Tap ripples at start/end of the same task
            if (local < 0.18f) {
                val rip = local / 0.18f
                drawCircle(
                    color = actionColor.copy(alpha = (1f - rip) * 0.55f),
                    radius = min * (0.06f + rip * 0.14f),
                    center = tap1,
                    style = Stroke(width = min * 0.035f),
                )
            } else if (local > 0.78f) {
                val rip = (local - 0.78f) / 0.22f
                drawCircle(
                    color = actionColor.copy(alpha = (1f - rip) * 0.55f),
                    radius = min * (0.06f + rip * 0.14f),
                    center = tap2,
                    style = Stroke(width = min * 0.035f),
                )
            }
        } else {
            // Keep finished recorded path visible while switching to Play
            var prev = tap1
            for (i in 1..16) {
                val cur = quadBezier(tap1, mid, tap2, i / 16f)
                drawLine(
                    color = accent.copy(alpha = 0.55f),
                    start = prev,
                    end = cur,
                    strokeWidth = min * 0.06f,
                    cap = StrokeCap.Round,
                )
                prev = cur
            }
        }

        // Mode badge: REC (red) → PLAY (green)
        val badgeColor = when {
            isPlayback -> playColor
            isTransition -> Color(0xFFF59E0B)
            else -> accent
        }
        drawCircle(color = badgeColor.copy(alpha = 0.25f), radius = recR * 1.55f, center = badge)
        drawCircle(color = badgeColor, radius = recR, center = badge)
        if (isPlayback || isTransition) {
            val play = Path().apply {
                moveTo(badge.x - recR * 0.28f, badge.y - recR * 0.5f)
                lineTo(badge.x + recR * 0.58f, badge.y)
                lineTo(badge.x - recR * 0.28f, badge.y + recR * 0.5f)
                close()
            }
            drawPath(play, Color.White)
        }
    }
}

@Composable
fun ScreenshotAnimatedIcon(
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val infinite = rememberInfiniteTransition(label = "screenshot")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shotPhase",
    )
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val frameL = w * 0.22f
        val frameT = h * 0.18f
        val frameW = w * 0.56f
        val frameH = h * 0.64f

        // Page frame
        drawRoundRect(
            color = accent.copy(alpha = 0.2f),
            topLeft = Offset(frameL, frameT),
            size = Size(frameW, frameH),
            cornerRadius = CornerRadius(w * 0.08f),
        )
        drawRoundRect(
            color = accent.copy(alpha = 0.75f),
            topLeft = Offset(frameL, frameT),
            size = Size(frameW, frameH),
            cornerRadius = CornerRadius(w * 0.08f),
            style = Stroke(width = w * 0.045f),
        )

        // Scroll scan line moving down (full-page capture)
        val scanY = frameT + frameH * (0.1f + phase * 0.75f)
        drawLine(
            color = accent.copy(alpha = 0.85f),
            start = Offset(frameL + frameW * 0.12f, scanY),
            end = Offset(frameL + frameW * 0.88f, scanY),
            strokeWidth = w * 0.04f,
            cap = StrokeCap.Round,
        )

        // Content lines above scan
        val lineCount = 3
        for (i in 0 until lineCount) {
            val y = frameT + frameH * (0.22f + i * 0.16f)
            if (y < scanY - h * 0.02f) {
                drawLine(
                    color = accent.copy(alpha = 0.35f),
                    start = Offset(frameL + frameW * 0.18f, y),
                    end = Offset(frameL + frameW * 0.82f, y),
                    strokeWidth = w * 0.03f,
                    cap = StrokeCap.Round,
                )
            }
        }

        // Shutter flash near mid-cycle
        if (phase in 0.42f..0.58f) {
            val flash = 1f - ((phase - 0.42f) / 0.16f - 0.5f).let { kotlin.math.abs(it) } * 2f
            drawCircle(
                color = Color.White.copy(alpha = flash.coerceIn(0f, 1f) * 0.55f),
                radius = w * 0.22f,
                center = Offset(w * 0.5f, h * 0.48f),
            )
        }

        // Tiny camera badge
        drawCircle(
            color = accent,
            radius = w * 0.09f,
            center = Offset(w * 0.72f, h * 0.28f),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = w * 0.04f,
            center = Offset(w * 0.72f, h * 0.28f),
        )
    }
}

@Composable
fun AutoRefreshAnimatedIcon(
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val infinite = rememberInfiniteTransition(label = "refresh")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "refreshSpin",
    )
    val pull by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "refreshPull",
    )
    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f - this.size.height * 0.04f
        val r = this.size.minDimension * 0.28f

        rotate(rotation, pivot = Offset(cx, cy)) {
            // Circular arrows
            drawArc(
                color = accent,
                startAngle = -40f,
                sweepAngle = 220f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = this.size.minDimension * 0.08f, cap = StrokeCap.Round),
            )
            // Arrow head
            val tipAngle = (-40f + 220f) * (PI.toFloat() / 180f)
            val tip = Offset(cx + r * cos(tipAngle), cy + r * sin(tipAngle))
            val back = Offset(
                tip.x - this.size.minDimension * 0.1f * cos(tipAngle - 0.9f),
                tip.y - this.size.minDimension * 0.1f * sin(tipAngle - 0.9f),
            )
            val side = Offset(
                tip.x - this.size.minDimension * 0.1f * cos(tipAngle + 0.9f),
                tip.y - this.size.minDimension * 0.1f * sin(tipAngle + 0.9f),
            )
            val arrow = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(back.x, back.y)
                lineTo(side.x, side.y)
                close()
            }
            drawPath(arrow, accent)
        }

        // Pull-to-refresh chevron under the spinner
        val pullY = this.size.height * (0.72f + 0.08f * sin(pull * PI.toFloat() * 2f))
        val chevron = Path().apply {
            moveTo(this@Canvas.size.width * 0.35f, pullY)
            lineTo(this@Canvas.size.width * 0.5f, pullY + this@Canvas.size.height * 0.08f)
            lineTo(this@Canvas.size.width * 0.65f, pullY)
        }
        drawPath(
            path = chevron,
            color = accent.copy(alpha = 0.7f),
            style = Stroke(width = this.size.minDimension * 0.06f, cap = StrokeCap.Round),
        )
    }
}

/** Original MT logo with a soft pulse + click ripple for headers. */
@Composable
fun BrandAnimatedIcon(
    accent: Color = Color(0xFF3B82F6),
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val infinite = rememberInfiniteTransition(label = "brandLogo")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "brandPhase",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "brandPulse",
    )
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val min = this.size.minDimension
            // Expanding ripples behind the logo
            listOf(0f, 0.33f, 0.66f).forEach { offset ->
                val t = ((phase + offset) % 1f)
                drawCircle(
                    color = accent.copy(alpha = (1f - t) * 0.35f),
                    radius = min * (0.28f + t * 0.42f),
                    center = Offset(cx, cy),
                    style = Stroke(width = min * 0.045f),
                )
            }
        }
        Image(
            painter = painterResource(R.drawable.ic_app_logo),
            contentDescription = "MT Auto Clicker",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size * 0.82f)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                }
                .clip(RoundedCornerShape(size * 0.22f)),
        )
    }
}

private fun quadBezier(p0: Offset, p1: Offset, p2: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(
        u * u * p0.x + 2f * u * t * p1.x + t * t * p2.x,
        u * u * p0.y + 2f * u * t * p1.y + t * t * p2.y,
    )
}
