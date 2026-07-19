package net.mtautoclicker.android.service

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import net.mtautoclicker.android.MainActivity
import net.mtautoclicker.android.R
import net.mtautoclicker.android.ui.screens.AppRoute

internal fun Context.dp(v: Int): Int =
    (v * resources.displayMetrics.density).toInt()

internal fun Context.floatbarPillBg(accent: Int): GradientDrawable =
    GradientDrawable().apply {
        setColor(0xF20B1220.toInt())
        cornerRadius = dp(26).toFloat()
        setStroke(dp(1), accent and 0x00FFFFFF or 0x55000000)
    }

internal fun Context.floatbarCircleBg(color: Int, stroke: Int = 0x66FFFFFF): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(dp(1), stroke)
    }

internal fun Context.floatbarDragHandle(widthPx: Int): TextView =
    TextView(this).apply {
        text = "⠿"
        gravity = Gravity.CENTER
        setTextColor(0xFF94A3B8.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        includeFontPadding = false
        setPadding(0, dp(1), 0, dp(4))
        layoutParams = LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

internal fun Context.floatbarStatusLabel(
    text: String,
    widthPx: Int,
    color: Int = 0xFFE2E8F0.toInt(),
): TextView =
    TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        setPadding(0, 0, 0, dp(6))
        layoutParams = LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.WRAP_CONTENT)
        maxLines = 1
    }

/** Shared MT logo — opens feature screen, then runs optional dismiss cleanup. */
internal fun Context.mtFeatureButton(
    sizePx: Int,
    route: AppRoute,
    onAfterNavigate: (() -> Unit)? = null,
): ImageView {
    val inset = dp(3)
    return ImageView(this).apply {
        setImageResource(R.drawable.ic_app_logo)
        scaleType = ImageView.ScaleType.CENTER_CROP
        setPadding(inset, inset, inset, inset)
        background = floatbarCircleBg(0xFF0F172A.toInt(), 0x8060A5FA.toInt())
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(6)
        }
        elevation = 0f
        contentDescription = "Open feature in MT Auto Clicker"
        isClickable = true
        isFocusable = true
        setOnClickListener {
            startActivity(MainActivity.routeIntent(this@mtFeatureButton, route))
            onAfterNavigate?.invoke()
        }
    }
}

internal fun Context.floatbarActionButton(
    iconRes: Int,
    sizePx: Int,
    fillColor: Int,
    onClick: () -> Unit,
): ImageButton =
    ImageButton(this).apply {
        setImageResource(iconRes)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = floatbarCircleBg(fillColor)
        layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(6)
        }
        elevation = 0f
        isClickable = true
        setOnClickListener { onClick() }
    }

internal fun Context.floatbarCloseButton(
    sizePx: Int,
    onClick: () -> Unit,
): ImageButton =
    floatbarActionButton(
        iconRes = R.drawable.ic_close,
        sizePx = sizePx,
        fillColor = 0xFF334155.toInt(),
        onClick = onClick,
    ).also {
        (it.layoutParams as LinearLayout.LayoutParams).bottomMargin = 0
    }

internal fun Context.floatbarSpeedChip(
    label: String,
    sizePx: Int,
    active: Boolean,
    onClick: () -> Unit,
): TextView =
    TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(if (active) Color.WHITE else 0xFFCBD5E1.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        includeFontPadding = false
        background = floatbarCircleBg(
            color = if (active) 0xFF3B82F6.toInt() else 0xFF1E293B.toInt(),
            stroke = if (active) 0x66FFFFFF.toInt() else 0xFF475569.toInt(),
        )
        layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(5)
        }
        setOnClickListener { onClick() }
    }
