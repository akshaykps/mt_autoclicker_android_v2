package net.mtautoclicker.android.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import net.mtautoclicker.android.R
import net.mtautoclicker.android.ui.theme.MtBlue
import net.mtautoclicker.android.ui.theme.MtHi
import net.mtautoclicker.android.ui.theme.MtMid

/**
 * Google Play prominent disclosure for a non-accessibility-tool app.
 *
 * This must be shown immediately before opening Android Accessibility settings.
 * Dismissing or navigating away never counts as consent.
 */
@Composable
fun AccessibilityDisclosureDialog(
    onDecline: () -> Unit,
    onContinue: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = {
            Text(
                text = stringResource(R.string.accessibility_disclosure_title),
                color = MtHi,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.accessibility_disclosure_body),
                color = MtMid,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(stringResource(R.string.accessibility_disclosure_decline), color = MtMid)
            }
        },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(
                    stringResource(R.string.accessibility_disclosure_accept),
                    color = MtBlue,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    )
}
