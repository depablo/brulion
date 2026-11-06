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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import pl.brulion.librus.schoolToday
import pl.brulion.ui.common.formatWeekday
import pl.brulion.widget.onePerChild
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One student's lessons for the printed week. */
internal class PrintedStudent(val name: String, val lessons: List<TimetableLessonEntity>)

/**
 * The printable plan as HTML: days across and lesson numbers down. [compact] puts all students on one
 * page with smaller text; otherwise each student gets a page. Changes are marked in the cells; a
 * regular plan has none, because [regularPlan] removed them.
 */
internal fun planHtml(title: String, monday: LocalDate, students: List<PrintedStudent>, compact: Boolean, printedOn: LocalDate): String {
    val sections = students.joinToString("\n") { studentSection(it, monday) }
    val size = if (compact) "8px" else "11px"
    val breaks = if (compact) "" else "section + section { page-break-before: always; }"
    return """<!doctype html><html><head><meta charset="utf-8"><style>
        body { font-family: sans-serif; font-size: $size; color: #111; margin: 0; }
        h1 { font-size: 1.4em; margin: 0 0 6px 0; }
        h2 { font-size: 1.2em; margin: 8px 0 4px 0; }
        table { border-collapse: collapse; width: 100%; table-layout: fixed; }
        th, td { border: 1px solid #888; padding: 2px; vertical-align: top; }
        thead th { background: #e6e6e6; }
        tbody th { width: 48px; background: #f4f4f4; font-weight: normal; }
        .l { margin-bottom: 3px; }
        .off { text-decoration: line-through; color: #555; }
        .chg { font-style: italic; }
        footer { margin-top: 6px; color: #555; font-size: 0.9em; }
        $breaks
        </style></head><body><h1>${esc(title)}</h1>$sections
        <footer>Wydrukowano: ${esc(printedOn.format(PrintedOnFormat))}</footer></body></html>"""
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


private val PrintedOnFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

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

/** Opens the system print dialog, where the user picks a printer or "Save as PDF". Main thread only. */
internal fun printPlan(context: Context, jobName: String, html: String, size: PlanSize) {
    renderPlan(context, jobName, html) { adapter ->
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val mediaSize = if (size == PlanSize.LARGE) PrintAttributes.MediaSize.ISO_A4.asLandscape()
        else PrintAttributes.MediaSize.ISO_A4.asPortrait()
        val attributes = PrintAttributes.Builder()
            .setMediaSize(mediaSize)
            .setMinMargins(PrintAttributes.Margins(400, 400, 400, 400))
            .build()
        printManager.print(jobName, adapter, attributes)
    }
}

/**
 * Writes the plan as a PDF into the Downloads folder. Returns false, and removes the half-written file,
 * when the file can't be created. Call off the main thread.
 */
internal fun savePlanPdf(
    context: Context,
    fileName: String,
    title: String,
    monday: LocalDate,
    students: List<PrintedStudent>,
    size: PlanSize,
    printedOn: LocalDate,
): Boolean {
    val resolver = context.contentResolver
    val pending = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pending) ?: return false
    return try {
        val stream = resolver.openOutputStream(uri) ?: error("no output stream for $uri")
        stream.use { writePlanPdf(title, monday, students, size, printedOn, it) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        true
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        false
    }
}

/**
 * Asks which students and which plan to print or save. The regular plan is the default: it leaves out
 * the week's substitutions and moves, and its title has no date range, only the printing date.
 */
@Composable
fun PrintPlanDialog(monday: LocalDate, currentProfileId: Long, onDismiss: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val accountProfiles by container.accounts.allProfiles.collectAsStateWithLifecycle(emptyList())
    // A child seen through a parent and a student account is one timetable: listed once.
    val profiles = remember(accountProfiles, currentProfileId) { onePerChild(accountProfiles, currentProfileId) }
    var chosen by remember { mutableStateOf(setOf(currentProfileId)) }
    var regular by remember { mutableStateOf(true) }
    var size by remember { mutableStateOf(PlanSize.SMALL) }
    val sunday = monday.plusDays(6)
    val printedOn = remember { schoolToday() }
    val title = stringResource(R.string.plan_document_title) + " · " + if (regular) {
        stringResource(R.string.print_regular_suffix)
    } else {
        "${monday.formatShort()} – ${sunday.formatShort()}"
    }
    val fileName = if (regular) "plan-stały-$printedOn.pdf" else "plan-lekcji-$monday.pdf"

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
            Column(Modifier.verticalScroll(rememberScrollState())) {
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
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.print_size), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().clickable { size = PlanSize.SMALL }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = size == PlanSize.SMALL, onClick = null)
                    Text(stringResource(R.string.print_size_small))
                }
                Row(Modifier.fillMaxWidth().clickable { size = PlanSize.LARGE }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = size == PlanSize.LARGE, onClick = null)
                    Text(stringResource(R.string.print_size_large))
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                TextButton(
                    enabled = chosen.isNotEmpty(),
                    onClick = {
                        val chosenSize = size
                        build { students ->
                            val saved = withContext(Dispatchers.IO) {
                                savePlanPdf(context, fileName, title, monday, students, chosenSize, printedOn)
                            }
                            val message = if (saved) R.string.print_saved else R.string.print_save_failed
                            Toast.makeText(context, context.getString(message, fileName), Toast.LENGTH_LONG).show()
                        }
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.print_save_pdf)) }
                TextButton(
                    enabled = chosen.isNotEmpty(),
                    onClick = {
                        val chosenSize = size
                        build { students ->
                            printPlan(
                                context,
                                title,
                                planHtml(title, monday, students, compact = chosenSize == PlanSize.SMALL, printedOn = printedOn),
                                chosenSize,
                            )
                        }
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.print_action)) }
            }
        },
    )
}
