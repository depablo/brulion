package pl.brulion.ui.timetable

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.provider.MediaStore
import android.widget.Toast
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.brulion.R
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.TimetableLessonEntity
import pl.brulion.data.school.regularPlan
import pl.brulion.ui.common.appContainer
import pl.brulion.ui.common.formatShort
import pl.brulion.ui.common.formatWeekday
import java.time.LocalDate

/** One student's lessons for the printed week. */
internal class PrintedStudent(val name: String, val lessons: List<TimetableLessonEntity>)

/**
 * The printable plan as HTML: one page per student, days across and lesson numbers down.
 * Changes are marked in the cells; a regular plan has none, because [regularPlan] removed them.
 */
internal fun planHtml(title: String, monday: LocalDate, students: List<PrintedStudent>): String {
    val sections = students.joinToString("\n") { studentSection(it, monday) }
    return """<!doctype html><html><head><meta charset="utf-8"><style>
        body { font-family: sans-serif; font-size: 11px; color: #111; margin: 0; }
        h1 { font-size: 15px; margin: 0 0 8px 0; }
        h2 { font-size: 13px; margin: 0 0 6px 0; }
        table { border-collapse: collapse; width: 100%; table-layout: fixed; }
        th, td { border: 1px solid #888; padding: 3px; vertical-align: top; }
        thead th { background: #e6e6e6; }
        tbody th { width: 60px; background: #f4f4f4; font-weight: normal; }
        .l { margin-bottom: 4px; }
        .off { text-decoration: line-through; color: #555; }
        .chg { font-style: italic; }
        section + section { page-break-before: always; }
        </style></head><body><h1>${esc(title)}</h1>$sections</body></html>"""
}

private fun studentSection(student: PrintedStudent, monday: LocalDate): String {
    val grid = weekGrid(student.lessons)
    val days = weekDays(monday, student.lessons)
    val head = days.joinToString("") { "<th>${esc(it.formatWeekday())} ${it.dayOfMonth}</th>" }
    val rows = grid.lessonNumbers.joinToString("") { lessonNo ->
        val time = esc(grid.startTime(lessonNo) ?: lessonNo.toString())
        val cells = days.joinToString("") { day ->
            "<td>${grid.at(day, lessonNo).joinToString("") { printedLesson(it) }}</td>"
        }
        "<tr><th>$time</th>$cells</tr>"
    }
    return "<section><h2>${esc(student.name)}</h2>" +
        "<table><thead><tr><th></th>$head</tr></thead><tbody>$rows</tbody></table></section>"
}

private fun printedLesson(lesson: TimetableLessonEntity): String {
    val (css, mark) = when (lesson.state) {
        LessonState.NORMAL -> "" to null
        LessonState.CANCELLED -> "off" to "odwołana"
        LessonState.MOVED_AWAY -> "off" to "przeniesiona"
        LessonState.SUBSTITUTION -> "chg" to "zastępstwo"
        LessonState.MOVED_HERE -> "chg" to "przeniesiona"
    }
    val details = listOfNotNull(
        lesson.teacherName?.takeIf { it.isNotBlank() },
        lesson.classroom?.takeIf { it.isNotBlank() }?.let { "sala $it" },
    ).joinToString(" · ")
    return buildString {
        append("<div class=\"l $css\"><b>${esc(lesson.subjectName ?: "–")}</b>")
        if (details.isNotEmpty()) append("<br>${esc(details)}")
        if (mark != null) append("<br><i>${esc(mark)}</i>")
        append("</div>")
    }
}

private fun esc(text: String): String = buildString {
    for (c in text) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }
}

/**
 * Renders [html] in a WebView and hands its document adapter to [onReady] once the page has loaded.
 * The WebView is held here until then, so it isn't collected while it loads.
 */
@SuppressLint("StaticFieldLeak")
private var pendingPlan: WebView? = null

private fun renderPlan(context: Context, jobName: String, html: String, onReady: (PrintDocumentAdapter) -> Unit) {
    val webView = WebView(context)
    pendingPlan = webView
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            val target = view ?: return
            pendingPlan = null
            onReady(target.createPrintDocumentAdapter(jobName))
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

/** Opens the system print dialog; there the user picks a printer, or "Save as PDF" in the same dialog. Main thread only. */
internal fun printPlan(context: Context, jobName: String, html: String) {
    renderPlan(context, jobName, html) { adapter ->
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
            .setMinMargins(PrintAttributes.Margins(400, 400, 400, 400))
            .build()
        printManager.print(jobName, adapter, attributes)
    }
}

/**
 * Writes the plan as a PDF into the Downloads folder. Returns false, and removes the half-written file,
 * when the file can't be created. Call off the main thread.
 */
internal fun savePlanPdf(context: Context, fileName: String, title: String, monday: LocalDate, students: List<PrintedStudent>): Boolean {
    val resolver = context.contentResolver
    val pending = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pending) ?: return false
    return try {
        val stream = resolver.openOutputStream(uri) ?: error("no output stream for $uri")
        stream.use { writePlanPdf(title, monday, students, it) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        true
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        false
    }
}

/**
 * Asks which students and which plan to print or save. "Regular" leaves out the week's substitutions
 * and moves; "with changes" shows the week as it is in the app.
 */
@Composable
fun PrintPlanDialog(monday: LocalDate, currentProfileId: Long, onDismiss: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val profiles by container.accounts.allProfiles.collectAsStateWithLifecycle(emptyList())
    var chosen by remember { mutableStateOf(setOf(currentProfileId)) }
    var regular by remember { mutableStateOf(false) }
    val sunday = monday.plusDays(6)
    val title = stringResource(R.string.print_plan_title) + " · " +
        "${monday.formatShort()} – ${sunday.formatShort()}" +
        if (regular) " · " + stringResource(R.string.print_regular_suffix) else ""
    val pdfName = "plan-lekcji-$monday${if (regular) "-staly" else ""}.pdf"

    // The work runs in the app scope: this dialog leaves the screen as soon as the user picks an action.
    fun build(then: suspend (List<PrintedStudent>) -> Unit) {
        val picked = profiles.filter { it.id in chosen }
        container.appScope.launch {
            val students = withContext(Dispatchers.IO) {
                picked.map { profile ->
                    val lessons = container.database.timetableDao().observeRange(profile.id, monday, sunday).first()
                    PrintedStudent(profile.studentName, if (regular) regularPlan(lessons) else lessons)
                }
            }
            then(students)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.print_plan_title)) },
        text = {
            Column {
                Text(stringResource(R.string.print_students), style = MaterialTheme.typography.labelLarge)
                profiles.forEach { profile ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { chosen = if (profile.id in chosen) chosen - profile.id else chosen + profile.id }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = profile.id in chosen, onCheckedChange = null)
                        Text(profile.studentName)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.print_kind), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().clickable { regular = true }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = regular, onClick = null)
                    Text(stringResource(R.string.print_regular))
                }
                Row(Modifier.fillMaxWidth().clickable { regular = false }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !regular, onClick = null)
                    Text(stringResource(R.string.print_with_changes))
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                TextButton(
                    enabled = chosen.isNotEmpty(),
                    onClick = {
                        build { students ->
                            val saved = withContext(Dispatchers.IO) { savePlanPdf(context, pdfName, title, monday, students) }
                            val message = if (saved) R.string.print_saved else R.string.print_save_failed
                            Toast.makeText(context, context.getString(message, pdfName), Toast.LENGTH_LONG).show()
                        }
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.print_save_pdf)) }
                TextButton(
                    enabled = chosen.isNotEmpty(),
                    onClick = {
                        build { students -> printPlan(context, title, planHtml(title, monday, students)) }
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.print_action)) }
            }
        },
    )
}
