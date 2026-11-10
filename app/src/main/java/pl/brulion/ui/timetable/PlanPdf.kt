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
import pl.brulion.ui.common.formatWeekdayFull
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.min

/**
 * How the plans are laid out on paper: [LARGE] puts one student on a landscape page; [SMALL] fits
 * all of them on portrait A4 pages, scaling the tables down when there are several.
 */
enum class PlanSize { LARGE, SMALL }

private const val LARGE_WIDTH = 842   // A4 landscape, in PDF points
private const val LARGE_HEIGHT = 595
private const val SMALL_WIDTH = 595   // A4 portrait
private const val SMALL_HEIGHT = 842
private const val MARGIN = 28f
private const val FOOTER = 18f
private const val GAP = 14f
private const val TIME_WIDTH = 56f
private const val HEADER_HEIGHT = 22f
private const val TITLE_HEIGHT = 24f
private const val MIN_ROW_HEIGHT = 30f
private const val PAD = 4f

/** A small plan is never shrunk below this, so the text stays readable; more pages are used instead. */
private const val MIN_SMALL_SCALE = 0.55f

/** One sheet of paper: the students on it and the scale their plans are drawn at. */
internal class PlanPage(val students: List<Int>, val scale: Float)

/**
 * Splits the students over as few pages as possible, so that no plan is drawn below [minScale].
 * [onePerPage] puts each student on a page of their own. Heights are in the same units as [available].
 */
internal fun planPages(natural: List<Float>, available: Float, minScale: Float, onePerPage: Boolean): List<PlanPage> {
    val count = natural.size
    if (count == 0) return emptyList()
    if (onePerPage) {
        return natural.indices.map { PlanPage(listOf(it), min(1f, available / natural[it])) }
    }
    for (pages in 1..count) {
        val size = ceil(count.toDouble() / pages).toInt()
        val groups = (0 until count).chunked(size)
        val result = groups.map { group ->
            PlanPage(group, min(1f, available / group.sumOf { natural[it].toDouble() }.toFloat()))
        }
        if (pages == count || result.all { it.scale >= minScale }) return result
    }
    return emptyList()
}

/**
 * Writes the plan as a PDF file. The table is drawn directly, so the file doesn't depend on a printer
 * or the print dialog. Changes are written as text ("odwołana", "zastępstwo"), so the plan reads the
 * same in black and white. Each page ends with the date it was printed.
 */
internal fun writePlanPdf(
    title: String,
    monday: LocalDate,
    students: List<PrintedStudent>,
    size: PlanSize,
    printedOn: LocalDate,
    regular: Boolean,
    out: OutputStream,
) {
    val document = PdfDocument()
    val writer = PlanPdfWriter(document, title, monday, size, printedOn, regular)
    writer.write(students)
    writer.finish()
    document.writeTo(out)
    document.close()
}

private class PlanPdfWriter(
    private val document: PdfDocument,
    private val title: String,
    private val monday: LocalDate,
    private val size: PlanSize,
    private val printedOn: LocalDate,
    /** The regular plan shows only the weekday in the header; a plan with changes also shows the date. */
    private val regular: Boolean,
) {
    private val pageWidth = if (size == PlanSize.LARGE) LARGE_WIDTH else SMALL_WIDTH
    private val pageHeight = if (size == PlanSize.LARGE) LARGE_HEIGHT else SMALL_HEIGHT

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
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = Color.DKGRAY }
    private val linePaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.8f; color = Color.GRAY }
    private val headerPaint = Paint().apply { color = Color.rgb(230, 230, 230) }

    private var pageNumber = 0
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null

    fun write(students: List<PrintedStudent>) {
        if (students.isEmpty()) {
            newPage()
            canvas?.drawText(EMPTY_TEXT, MARGIN, MARGIN + 14f, titlePaint)
            return
        }
        val width = pageWidth - 2 * MARGIN
        val available = pageHeight - 2 * MARGIN - FOOTER
        val natural = students.map { blockHeight(it, width) }
        val minScale = if (size == PlanSize.LARGE) 0.5f else MIN_SMALL_SCALE
        for (planPage in planPages(natural, available, minScale, onePerPage = size == PlanSize.LARGE)) {
            newPage()
            var top = MARGIN
            for (index in planPage.students) {
                val scaledWidth = width / planPage.scale
                val height = blockHeight(students[index], scaledWidth) * planPage.scale
                val c = canvas ?: return
                c.save()
                c.translate(MARGIN, top)
                c.scale(planPage.scale, planPage.scale)
                drawBlock(c, students[index], scaledWidth)
                c.restore()
                top += height + GAP
            }
        }
    }

    fun finish() {
        closePage()
    }

    private fun newPage() {
        closePage()
        pageNumber++
        val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        val started = document.startPage(info)
        page = started
        canvas = started.canvas
    }

    /** The printing date goes at the bottom of every page, so a printed plan shows when it was made. */
    private fun closePage() {
        val current = page ?: return
        canvas?.drawText("Wydrukowano: ${printedOn.format(PrintedOnFormat)}", MARGIN, pageHeight - MARGIN / 2, footerPaint)
        document.finishPage(current)
        page = null
        canvas = null
    }

    /** Height of one student's block (title, header and rows) at [width], without drawing it. */
    private fun blockHeight(student: PrintedStudent, width: Float): Float = drawBlock(null, student, width)

    /**
     * Draws the title, day header and lesson rows of one student at the top left of the canvas (or only
     * measures them when [c] is null). Returns the height used.
     */
    private fun drawBlock(c: Canvas?, student: PrintedStudent, width: Float): Float {
        var y = 0f
        c?.drawText("$title · ${student.name}", 0f, y + 14f, titlePaint)
        y += TITLE_HEIGHT

        val lessons = student.lessons
        val grid = weekGrid(lessons)
        val days = weekDays(monday, lessons)
        val dayWidth = (width - TIME_WIDTH) / days.size
        c?.let {
            it.drawRect(0f, y, width, y + HEADER_HEIGHT, headerPaint)
            it.drawRect(0f, y, width, y + HEADER_HEIGHT, linePaint)
            days.forEachIndexed { index, day ->
                val x = TIME_WIDTH + index * dayWidth
                val label = if (regular) day.formatWeekdayFull() else "${day.formatWeekday()} ${day.formatShort()}"
                it.drawText(label, x + PAD, y + 15f, dayPaint)
                it.drawLine(x, y, x, y + HEADER_HEIGHT, linePaint)
            }
        }
        y += HEADER_HEIGHT

        for (lessonNo in grid.lessonNumbers) {
            val layouts = days.map { cellLayout(grid.at(it, lessonNo), dayWidth) }
            val rowHeight = maxOf(MIN_ROW_HEIGHT, (layouts.maxOfOrNull { it.height } ?: 0) + 2 * PAD)
            c?.let { drawRow(it, grid.startTime(lessonNo) ?: lessonNo.toString(), layouts, dayWidth, y, rowHeight) }
            y += rowHeight
        }
        return y
    }

    private fun drawRow(c: Canvas, time: String, layouts: List<StaticLayout>, dayWidth: Float, top: Float, rowHeight: Float) {
        c.drawRect(0f, top, TIME_WIDTH, top + rowHeight, linePaint)
        c.drawText(time, PAD, top + 14f, textPaint)
        layouts.forEachIndexed { index, layout ->
            val left = TIME_WIDTH + index * dayWidth
            c.drawRect(left, top, left + dayWidth, top + rowHeight, linePaint)
            c.save()
            c.translate(left + PAD, top + PAD)
            layout.draw(c)
            c.restore()
        }
    }

    private fun cellLayout(lessons: List<TimetableLessonEntity>, dayWidth: Float): StaticLayout {
        val text = cellText(lessons)
        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, (dayWidth - 2 * PAD).toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
    }

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
        val PrintedOnFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    }
}
