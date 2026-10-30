package pl.brulion.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import pl.brulion.R

/** "Zgłoś" next to an error. */
@Composable
fun ReportButton(reason: String) {
    val diagnostics = appContainer().diagnostics
    TextButton(onClick = { diagnostics.send(reason) }) { Text(stringResource(R.string.report)) }
}

/** After a crash, offers to send the saved report once. */
@Composable
fun CrashReportDialog() {
    val diagnostics = appContainer().diagnostics
    var visible by remember { mutableStateOf(diagnostics.hasCrashReport()) }
    if (!visible) return
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.report_crash_title)) },
        text = { Text(stringResource(R.string.report_crash_text)) },
        confirmButton = {
            TextButton(onClick = {
                visible = false
                diagnostics.send("Awaria aplikacji")
            }) { Text(stringResource(R.string.report_send)) }
        },
        dismissButton = {
            TextButton(onClick = {
                visible = false
                diagnostics.dismissCrashReport()
            }) { Text(stringResource(R.string.report_skip)) }
        },
    )
}
