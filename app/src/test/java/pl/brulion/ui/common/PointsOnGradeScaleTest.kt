package pl.brulion.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PointsOnGradeScaleTest {

    @Test
    fun fullMarkIsTheTop() {
        assertEquals(6.0, pointsOnGradeScale(20.0, 20.0)!!, 0.001)
    }

    @Test
    fun sharesMapToTheScale() {
        assertEquals(1.0, pointsOnGradeScale(0.0, 20.0)!!, 0.001)
        assertEquals(3.5, pointsOnGradeScale(10.0, 20.0)!!, 0.001)
    }

    @Test
    fun pointsAboveMaximumAreCapped() {
        assertEquals(6.0, pointsOnGradeScale(25.0, 20.0)!!, 0.001)
    }

    @Test
    fun unknownMaximumHasNoValue() {
        assertNull(pointsOnGradeScale(5.0, null))
        assertNull(pointsOnGradeScale(5.0, 0.0))
        assertNull(pointsOnGradeScale(null, 20.0))
    }
}
