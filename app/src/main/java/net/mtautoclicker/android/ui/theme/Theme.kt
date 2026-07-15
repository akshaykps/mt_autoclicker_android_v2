package net.mtautoclicker.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import net.mtautoclicker.android.data.ThemePreference

data class MtPalette(
    val deep: Color,
    val card: Color,
    val row: Color,
    val border: Color,
    val blue: Color,
    val blueDim: Color,
    val purple: Color,
    val hi: Color,
    val mid: Color,
    val lo: Color,
    val emerald: Color,
    val isDark: Boolean,
)

val MtPaletteDark = MtPalette(
    deep = Color(0xFF060B27),
    card = Color(0xFF0D1333),
    row = Color(0xFF0A1028),
    border = Color(0xFF1E2D5A),
    blue = Color(0xFF3B82F6),
    blueDim = Color(0xFF2563EB),
    purple = Color(0xFF8B5CF6),
    hi = Color(0xFFF1F5F9),
    mid = Color(0xFF94A3B8),
    lo = Color(0xFF475569),
    emerald = Color(0xFF10B981),
    isDark = true,
)

val MtPaletteLight = MtPalette(
    deep = Color(0xFFF8FAFC),
    card = Color(0xFFFFFFFF),
    row = Color(0xFFF1F5F9),
    border = Color(0xFFCBD5E1),
    blue = Color(0xFF2563EB),
    blueDim = Color(0xFF1D4ED8),
    purple = Color(0xFF7C3AED),
    hi = Color(0xFF0F172A),
    mid = Color(0xFF334155),
    lo = Color(0xFF475569),
    emerald = Color(0xFF059669),
    isDark = false,
)

val LocalMtPalette = staticCompositionLocalOf { MtPaletteDark }

val MtDeep: Color
    @Composable @ReadOnlyComposable get() = LocalMtPalette.current.deep
val MtCard: Color
    @Composable @ReadOnlyComposable get() = LocalMtPalette.current.card
val MtRow: Color
    @Composable @ReadOnlyComposable get() = LocalMtPalette.current.row
val MtBorder: Color
    @Composable @ReadOnlyComposable get() = LocalMtPalette.current.border
val MtBlue: Color
    @Composable @ReadOnlyComposable get() = LocalMtPalette.current.blue
val MtBlueDim: Color
    @Composable @ReadOnlyComposable get() = LocalMtPalette.current.blueDim
val MtPurple: Color
    @Composable @ReadOnlyComposable get() = LocalMtPalette.current.purple
val MtHi: Color
    @Composable @ReadOnlyComposable get() = LocalMtPalette.current.hi
val MtMid: Color
    @Composable @ReadOnlyComposable get() = LocalMtPalette.current.mid
val MtLo: Color
    @Composable @ReadOnlyComposable get() = LocalMtPalette.current.lo
val MtEmerald: Color
    @Composable @ReadOnlyComposable get() = LocalMtPalette.current.emerald

private val MtDarkColors = darkColorScheme(
    primary = MtPaletteDark.blue,
    onPrimary = Color.White,
    secondary = MtPaletteDark.purple,
    background = MtPaletteDark.deep,
    surface = MtPaletteDark.card,
    onBackground = MtPaletteDark.hi,
    onSurface = MtPaletteDark.hi,
    outline = MtPaletteDark.border,
)

private val MtLightColors = lightColorScheme(
    primary = MtPaletteLight.blue,
    onPrimary = Color.White,
    secondary = MtPaletteLight.purple,
    background = MtPaletteLight.deep,
    surface = MtPaletteLight.card,
    onBackground = MtPaletteLight.hi,
    onSurface = MtPaletteLight.hi,
    outline = MtPaletteLight.border,
)

@Composable
fun MtTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = if (dark) MtPaletteDark else MtPaletteLight
    CompositionLocalProvider(LocalMtPalette provides palette) {
        MaterialTheme(
            colorScheme = if (dark) MtDarkColors else MtLightColors,
            content = content,
        )
    }
}
