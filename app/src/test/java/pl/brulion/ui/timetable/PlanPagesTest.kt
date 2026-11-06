package pl.brulion.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanPagesTest {

    @Test
    fun severalPlansFitOnOnePageAtFullSize() {
        val pages = planPages(listOf(200f, 200f, 200f), available = 700f, minScale = 0.55f, onePerPage = false)

        assertEquals(1, pages.size)
        assertEquals(listOf(0, 1, 2), pages.single().students)
        assertEquals(1f, pages.single().scale, 0.001f)
    }

    @Test
    fun tooManyPlansShareFewerPagesInsteadOfShrinkingTooMuch() {
        // One page would need a scale of 0.5, which is below the minimum: two pages at full size instead.
        val pages = planPages(listOf(300f, 300f, 300f, 300f), available = 600f, minScale = 0.55f, onePerPage = false)

        assertEquals(2, pages.size)
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), pages.map { it.students })
        assertTrue(pages.all { it.scale == 1f })
    }

    @Test
    fun largeLayoutPutsEachStudentOnItsOwnPage() {
        val pages = planPages(listOf(100f, 100f), available = 1000f, minScale = 0.5f, onePerPage = true)

        assertEquals(listOf(listOf(0), listOf(1)), pages.map { it.students })
    }

    @Test
    fun aTallPlanIsScaledDownToFitItsPage() {
        val page = planPages(listOf(800f), available = 400f, minScale = 0.5f, onePerPage = true).single()

        assertEquals(0.5f, page.scale, 0.001f)
    }
}
