package net.mtautoclicker.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mtautoclicker.android.R
import net.mtautoclicker.android.ui.theme.MtBlue
import net.mtautoclicker.android.ui.theme.MtBorder
import net.mtautoclicker.android.ui.theme.MtCard
import net.mtautoclicker.android.ui.theme.MtDeep
import net.mtautoclicker.android.ui.theme.MtEmerald
import net.mtautoclicker.android.ui.theme.MtHi
import net.mtautoclicker.android.ui.theme.MtMid
import net.mtautoclicker.android.ui.theme.MtPurple
import net.mtautoclicker.android.ui.theme.MtRow

/** Safe area padding for punch-hole / status bar / gesture nav (OnePlus etc.). */
fun Modifier.mtSafeEdges(): Modifier =
    this
        .statusBarsPadding()
        .navigationBarsPadding()

@Composable
fun PageScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
    /** Hide keyboard button — useful on forms; off for screens like Settings. */
    showKeyboardHide: Boolean = true,
    contentSpacing: androidx.compose.ui.unit.Dp = 14.dp,
    horizontalPadding: androidx.compose.ui.unit.Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dockScrollReporter = LocalDockScrollReporter.current

    // Scroll dismisses keyboard on Realme / OEMs that hide the system “close keyboard” key.
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    LaunchedEffect(scrollState.value) {
        dockScrollReporter.onScroll(scrollState.value)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MtDeep)
            .mtSafeEdges()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                focusManager.clearFocus(force = true)
                keyboard?.hide()
            }
            .verticalScroll(scrollState)
            .padding(horizontal = horizontalPadding)
            .padding(top = 10.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(contentSpacing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MtCard)
                .border(1.dp, MtBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MtRow)
                        .border(1.dp, MtBorder, CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MtHi,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MtCard)
                        .border(1.dp, MtBlue.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    BrandAnimatedIcon(accent = MtBlue, size = 36.dp)
                }
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = title,
                color = MtHi,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (showKeyboardHide) {
                IconButton(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        keyboard?.hide()
                    },
                ) {
                    Icon(
                        Icons.Rounded.KeyboardHide,
                        contentDescription = "Hide keyboard",
                        tint = MtMid,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        content()
    }
}

@Composable
fun FeatureHeroCard(
    description: String,
    runSummary: String,
    startLabel: String = "Start",
    onStart: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(22.dp), clip = false)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF1A2A6C).copy(alpha = 0.55f),
                        MtBlue.copy(alpha = 0.28f),
                        MtPurple.copy(alpha = 0.18f),
                        MtCard,
                    ),
                ),
            )
            .border(1.dp, MtBlue.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MtBlue.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.AdsClick, contentDescription = null, tint = MtBlue, modifier = Modifier.size(22.dp))
            }
            Text("Quick start", color = MtHi, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Text(description, color = MtHi.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 18.sp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StepLine(1, "Press Start — app goes to background")
            StepLine(2, "Open the app you want to click")
            StepLine(3, "Tap + on the float bar to place a target")
            StepLine(4, "Hit Play on the float bar to begin")
        }
        Text(runSummary, color = MtMid, fontSize = 11.sp)
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MtBlue),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(6.dp))
            Text(startLabel.removePrefix("▶ ").removePrefix("▶"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
        }
    }
}

@Composable
private fun StepLine(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MtBlue.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", color = MtBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Text(text, color = MtMid, fontSize = 12.sp)
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(if (compact) 12.dp else 16.dp))
            .background(MtCard)
            .border(1.dp, MtBorder, RoundedCornerShape(if (compact) 12.dp else 16.dp))
            .padding(
                horizontal = if (compact) 10.dp else 12.dp,
                vertical = if (compact) 8.dp else 14.dp,
            ),
    ) {
        Text(
            text = label,
            color = MtMid,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(if (compact) 3.dp else 6.dp))
        Text(
            text = value,
            color = accent,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 15.sp else 18.sp,
        )
    }
}

/** Single compact interactive strip for session stats on Home. */
@Composable
fun SessionMetricsStrip(
    clicks: String,
    runtime: String,
    runs: String,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(0) }
    val items = listOf(
        Triple("Clicks", clicks, AccentBlue),
        Triple("Runtime", runtime, AccentViolet),
        Triple("Runs", runs, AccentEmerald),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MtCard)
            .border(1.dp, MtBorder, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEachIndexed { index, (label, value, accent) ->
            val active = selected == index
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (pressed) 0.96f else 1f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 520f),
                label = "metricScale$index",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) accent.copy(alpha = 0.12f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (active) accent.copy(alpha = 0.45f) else Color.Transparent,
                        RoundedCornerShape(11.dp),
                    )
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                    ) { selected = index }
                    .padding(horizontal = 6.dp, vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    color = if (active) accent else MtMid,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    value,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun FeatureCard(
    title: String,
    subtitle: String,
    accent: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    compact: Boolean = false,
    /** Larger icon well for animated / demo icons. */
    largeIcon: Boolean = false,
    /** When set, replaces the static icon glyph. */
    animatedIcon: (@Composable BoxScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(if (compact) 14.dp else 18.dp)
    val iconWell = when {
        largeIcon -> if (compact) 46.dp else 64.dp
        compact -> 36.dp
        else -> 48.dp
    }
    val iconCorner = when {
        largeIcon -> if (compact) 12.dp else 16.dp
        compact -> 10.dp
        else -> 14.dp
    }
    val glyphSize = when {
        largeIcon -> if (compact) 30.dp else 32.dp
        compact -> 18.dp
        else -> 24.dp
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (compact) Modifier else Modifier.shadow(4.dp, shape, clip = false))
            .clip(shape)
            .background(MtCard)
            .border(1.dp, MtBorder.copy(alpha = 0.85f), shape)
            .clickable(onClick = onClick)
            .padding(if (compact) 8.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(iconWell)
                .clip(RoundedCornerShape(iconCorner))
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.12f)),
                    ),
                )
                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(iconCorner)),
            contentAlignment = Alignment.Center,
        ) {
            if (animatedIcon != null) {
                animatedIcon()
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(glyphSize),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = MtHi,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 13.sp else 15.sp,
                maxLines = 1,
            )
            if (!compact) Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                color = MtMid,
                fontSize = if (compact) 11.sp else 12.sp,
                lineHeight = if (compact) 13.sp else 16.sp,
                maxLines = if (compact) 1 else 2,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MtMid,
            modifier = Modifier.size(if (compact) 18.dp else 22.dp),
        )
    }
}

@Composable
fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MtRow)
            .border(1.dp, MtBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = MtHi, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        content()
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = MtMid,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 2.dp, top = 0.dp, bottom = 0.dp),
    )
}

@Composable
fun MtPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
) {
    val resolvedColor = containerColor ?: MtBlue
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = resolvedColor),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MtTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MtBlue,
            unfocusedBorderColor = MtBorder,
            focusedTextColor = MtHi,
            unfocusedTextColor = MtHi,
            focusedLabelColor = MtMid,
            unfocusedLabelColor = MtMid,
            cursorColor = MtBlue,
        ),
        shape = RoundedCornerShape(12.dp),
    )
}

/**
 * Number input: numeric keyboard, no trailing ".0", clears on focus so typing replaces the value.
 * Includes Done / hide-keyboard affordances for OEMs (e.g. Realme) that omit a system hide key.
 */
@Composable
fun MtNumberField(
    value: Number,
    onCommit: (Long) -> Unit,
    label: String,
    min: Long = 0,
    allowEmptyWhileTyping: Boolean = true,
) {
    val display = formatWholeNumber(value)
    var text by remember { mutableStateOf(display) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    fun dismissKeyboard() {
        if (text.isBlank()) {
            text = display
        } else {
            val n = (text.toLongOrNull() ?: min).coerceAtLeast(min)
            text = n.toString()
            onCommit(n)
        }
        focused = false
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }

    // Keep in sync when parent value changes and field is not being edited.
    LaunchedEffect(display, focused) {
        if (!focused) text = display
    }

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val filtered = raw.filter { it.isDigit() }
            text = filtered
            if (filtered.isNotEmpty()) {
                val n = filtered.toLongOrNull() ?: return@OutlinedTextField
                onCommit(n.coerceAtLeast(min))
            } else if (!allowEmptyWhileTyping) {
                onCommit(min)
            }
        },
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (state.isFocused) {
                    focused = true
                    text = ""
                } else if (focused) {
                    focused = false
                    if (text.isBlank()) {
                        text = display
                    } else {
                        val n = (text.toLongOrNull() ?: min).coerceAtLeast(min)
                        text = n.toString()
                        onCommit(n)
                    }
                    keyboard?.hide()
                }
            },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = { dismissKeyboard() },
        ),
        trailingIcon = {
            if (focused) {
                IconButton(onClick = { dismissKeyboard() }) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "Done — hide keyboard",
                        tint = MtBlue,
                    )
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MtBlue,
            unfocusedBorderColor = MtBorder,
            focusedTextColor = MtHi,
            unfocusedTextColor = MtHi,
            focusedLabelColor = MtMid,
            unfocusedLabelColor = MtMid,
            cursorColor = MtBlue,
        ),
        shape = RoundedCornerShape(12.dp),
    )
}

/** Show 100 instead of 100.0 */
fun formatWholeNumber(value: Number): String {
    val d = value.toDouble()
    return if (d == d.toLong().toDouble()) d.toLong().toString() else {
        // Trim trailing zeros for rare fractional intervals
        d.toString().trimEnd('0').trimEnd('.')
    }
}

@Composable
fun HeroHeader(
    @Suppress("UNUSED_PARAMETER") version: String = "",
    notificationCount: Int = 0,
    compact: Boolean = false,
    onNotificationsClick: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(MtBlue.copy(alpha = 0.16f), Color.Transparent),
                ),
            )
            .padding(horizontal = if (compact) 14.dp else 18.dp)
            .padding(top = if (compact) 10.dp else 14.dp, bottom = if (compact) 8.dp else 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MtCard)
                .border(1.dp, MtBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = if (compact) 12.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 52.dp else 56.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(MtCard)
                    .border(1.dp, MtBlue.copy(alpha = 0.35f), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                BrandAnimatedIcon(
                    accent = MtBlue,
                    size = if (compact) 46.dp else 50.dp,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "MT Auto Clicker",
                    color = MtHi,
                    fontSize = if (compact) 18.sp else 21.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Click less. Do more.",
                    color = MtMid,
                    fontSize = if (compact) 12.sp else 13.sp,
                )
            }
            if (onNotificationsClick != null) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 44.dp else 46.dp)
                        .clip(CircleShape)
                        .background(MtBlue.copy(alpha = 0.12f))
                        .border(1.dp, MtBlue.copy(alpha = 0.35f), CircleShape)
                        .clickable(onClick = onNotificationsClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Notifications,
                        contentDescription = "Notifications",
                        tint = MtBlue,
                        modifier = Modifier.size(if (compact) 20.dp else 22.dp),
                    )
                    if (notificationCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(if (compact) 10.dp else 16.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDC2626))
                                .border(1.5.dp, MtCard, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!compact) {
                                Text(
                                    if (notificationCount > 9) "9+" else notificationCount.toString(),
                                    color = Color.White,
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

val AccentBlue = Color(0xFF3B82F6)
val AccentViolet = Color(0xFF8B5CF6)
val AccentEmerald = Color(0xFF10B981)
val AccentRose = Color(0xFFF43F5E)
