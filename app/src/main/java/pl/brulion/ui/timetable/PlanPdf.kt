package pl.brulion.ui.timetable

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.TimetableLessonEntity
import pl.brulion.ui.common.formatShort
import pl.brulion.ui.common.formatWeekday
import java.io.OutputStream
import java.time.LocalDate

// A4 landscape in PDF points; a plan is one page per student, and a long one continues on the next page.
private const val PAGE_WIDTH = 842
private const val PAGE_HEIGHT = 595
private const val MARGIN = 28f
private const val TIME_WIDTH = 56f
private const val HEADER_HEIGHT = 22f
private const val MIN_ROW_HEIGHT = 30f
private const val PAD = 4f

/**
 * Writes the plan as a PDF file. The table is drawn directly, so the file doesn't depend on a printer
 * or the print dialog. Changes are written as text ("odwołana", "zastępstwo"), so the plan reads the
 * same in black and white.
 */
internal fun writePlanPdf(title: String, monday: LocalDate, students: List<PrintedStudent>, out: OutputStream) {
    val document = PdfDocument()
    val writer = PlanPdfWriter(document, title, monday)
    students.forEach { writer.student(it) }
    if (students.isEmpty()) writer.empty()
    writer.finish()
    document.writeTo(out)
    document.close()
}

private class PlanPdfWriter(
    private val document: PdfDocument,
    private val title: String,
    private val monday: LocalDate,
) {
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.BLACK }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        color = Color.BLACK
    }
    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f
        typeface = Typeface.DEFAULT_BOLD
        color = Color.BLACK
    }
    private val linePaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.8f; color = Color.GRAY }
    private val headerPaint = Paint().apply { color = Color.rgb(230, 230, 230) }

    private var pageNumber = 0
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var y = 0f

    fun student(student: PrintedStudent) {
        val lessons = student.lessons
        val grid = weekGrid(lessons)
        val days = weekDays(monday, lessons)
        val dayWidth = (PAGE_WIDTH - 2 * MARGIN - TIME_WIDTH) / days.size
        newPage()
        drawTitle("$title · ${student.name}")
        drawHeader(days, dayWidth)
        for (lessonNo in grid.lessonNumbers) {
            val cells = days.map { cellText(grid.at(it, lessonNo)) }
            val layouts = cells.map { layout(it, dayWidth) }
            val rowHeight = maxOf(MIN_ROW_HEIGHT, (layouts.maxOfOrNull { it.height } ?: 0) + 2 * PAD)
            if (y + rowHeight > PAGE_HEIGHT - MARGIN) {
                newPage()
                drawHeader(days, dayWidth)
            }
            drawRow(grid.startTime(lessonNo) ?: lessonNo.toString(), layouts, dayWidth, rowHeight)
        }
    }

    /** Only when there is nothing to print at all, so the file still says why. */
    fun empty() {
        newPage()
        drawTitle(title)
        canvas?.drawText(EMPTY_TEXT, MARGIN, y + 20f, textPaint)
    }

    fun finish() {
        page?.let { document.finishPage(it) }
        page = null
        canvas = null
    }

    private fun newPage() {
        page?.let { document.finishPage(it) }
        pageNumber++
        val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        val newPage = document.startPage(info)
        page = newPage
        canvas = newPage.canvas
        y = MARGIN
    }

    private fun drawTitle(text: String) {
        val c = canvas ?: return
        c.drawText(text, MARGIN, y + 14f, titlePaint)
        y += 24f
    }

    private fun drawHeader(days: List<LocalDate>, dayWidth: Float) {
        val c = canvas ?: return
        c.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + HEADER_HEIGHT, headerPaint)
        c.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + HEADER_HEIGHT, linePaint)
        days.forEachIndexed { index, day ->
            val x = MARGIN + TIME_WIDTH + index * dayWidth
            c.drawText("${day.formatWeekday()} ${day.formatShort()}", x + PAD, y + 15f, dayPaint)
            c.drawLine(x, y, x, y + HEADER_HEIGHT, linePaint)
        }
        y += HEADER_HEIGHT
    }

    private fun drawRow(time: String, layouts: List<StaticLayout>, dayWidth: Float, rowHeight: Float) {
        val c = canvas ?: return
        c.drawRect(MARGIN, y, MARGIN + TIME_WIDTH, y + rowHeight, linePaint)
        c.drawText(time, MARGIN + PAD, y + 14f, textPaint)
        layouts.forEachIndexed { index, layout ->
            val left = MARGIN + TIME_WIDTH + index * dayWidth
            c.drawRect(left, y, left + dayWidth, y + rowHeight, linePaint)
            c.save()
            c.translate(left + PAD, y + PAD)
            layout.draw(c)
            c.restore()
        }
        y += rowHeight
    }

    private fun layout(text: CharSequence, dayWidth: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, textPaint, (dayWidth - 2 * PAD).toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()

    /** One lesson per paragraph: subject in bold, teacher and room, then the change, if any. */
    private fun cellText(lessons: List<TimetableLessonEntity>): CharSequence {
        val text = SpannableStringBuilder()
        lessons.forEachIndexed { index, lesson ->
            if (index > 0) text.append("\n")
            val subjectStart = text.length
            text.append(lesson.subjectName ?: "–")
            text.setSpan(StyleSpan(Typeface.BOLD), subjectStart, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (lesson.state == LessonState.CANCELLED || lesson.state == LessonState.MOVED_AWAY) {
                text.setSpan(StrikethroughSpan(), subjectStart, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val details = listOfNotNull(
                lesson.teacherName?.takeIf { it.isNotBlank() },
                lesson.classroom?.takeIf { it.isNotBlank() }?.let { "sala $it" },
            ).joinToString(" · ")
            if (details.isNotEmpty()) text.append("\n").append(details)
            changeLabel(lesson.state)?.let { label ->
                val start = text.length
                text.append("\n").append(label)
                text.setSpan(StyleSpan(Typeface.ITALIC), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return text
    }

    private fun changeLabel(state: LessonState): String? = when (state) {
        LessonState.NORMAL -> null
        LessonState.CANCELLED -> "odwołana"
        LessonState.MOVED_AWAY, LessonState.MOVED_HERE -> "przeniesiona"
        LessonState.SUBSTITUTION -> "zastępstwo"
    }

    private companion object {
        const val EMPTY_TEXT = "Brak lekcji w tym tygodniu"
    }
}
