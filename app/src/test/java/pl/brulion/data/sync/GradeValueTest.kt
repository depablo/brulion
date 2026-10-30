package pl.brulion.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradeValueTest {
    @Test
    fun plainAndModifiedGrades() {
        assertEquals(5.0, gradeNumericValue("5")!!, 0.0)
        assertEquals(5.5, gradeNumericValue("5+")!!, 0.0)
        assertEquals(4.75, gradeNumericValue("5-")!!, 0.0)
        assertEquals(0.75, gradeNumericValue("1-")!!, 0.0)
        assertEquals(6.5, gradeNumericValue(" 6+ ")!!, 0.0)
    }

    @Test
    fun nonNumericGrades() {
        assertNull(gradeNumericValue("0"))
        assertNull(gradeNumericValue("np"))
        assertNull(gradeNumericValue("+"))
        assertNull(gradeNumericValue("-"))
        assertNull(gradeNumericValue("7"))
        assertNull(gradeNumericValue(""))
    }
}
