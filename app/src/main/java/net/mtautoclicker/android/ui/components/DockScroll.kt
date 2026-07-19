package net.mtautoclicker.android.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Reports vertical scroll offsets from dock-hosted screens so the floating
 * dock can expand at rest and shrink while the user scrolls.
 */
fun interface DockScrollReporter {
    fun onScroll(offsetPx: Int)
}

val LocalDockScrollReporter = staticCompositionLocalOf<DockScrollReporter> {
    DockScrollReporter { }
}
