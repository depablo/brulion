package pl.brulion.data.school

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.brulion.data.db.GradeEntity
import pl.brulion.data.db.GradeKind
import pl.brulion.data.db.GradeType

class GradeStatsTest {
    private var nextId = 1L

    private fun grade(
        subject: String,
        value: Double?,
        weight: Double = 1.0,
        type: GradeType = GradeType.REGULAR,
        semester: Int = 1,
        kind: GradeKind = GradeKind.NORMAL,
    ) = GradeEntity(
        profileId = 1, kind = kind, id = nextId++, type = type, semester = semester, subjectId = 1,
        subjectName = subject, value = value?.toString() ?: "np", numericValue = value, maxValue = null,
        weight = weight, categoryName = "", color = 0, comment = null, teacherName = null,
        addedAt = nextId, improvedById = null, firstSeenAt = 0,
    )

    @Test
    fun weightedAverageSkipsZeroWeightAndNonNumeric() {
        val grades = listOf(grade("M", 5.0, 3.0), grade("M", 3.0, 1.0), grade("M", 1.0, 0.0), grade("M", null))
        assertEquals(4.5, weightedAverage(grades)!!, 1e-9)
    }

    @Test
    fun weightedAverageIgnoresPointAndSummaryGrades() {
        val grades = listOf(
            grade("M", 4.0),
            grade("M", 6.0, kind = GradeKind.POINT),
            grade("M", 2.0, type = GradeType.SEMESTER_FINAL),
        )
        assertEquals(4.0, weightedAverage(grades)!!, 1e-9)
        assertNull(weightedAverage(listOf(grade("M", null))))
    }

    @Test
    fun nextGradeMovesTheAverage() {
        // 5 (w3) + 3 (w1) = 4.5; a 6 with weight 2 gives (15 + 3 + 12) / 6 = 5.0.
        val grades = listOf(grade("M", 5.0, 3.0), grade("M", 3.0, 1.0), grade("M", 1.0, 0.0))
        assertEquals(5.0, averageWith(grades, 6.0, 2.0)!!, 1e-9)
        assertEquals(listOf(1.0, 3.0), usedWeights(grades))
        assertEquals(2.0, averageWith(emptyList(), 2.0, 1.0)!!, 1e-9)
    }

    @Test
    fun groupsBySubjectAndSemester() {
        val grades = listOf(
            grade("Matematyka", 5.0),
            grade("biologia", 3.0),
            grade("Matematyka", 4.0, type = GradeType.SEMESTER_PROPOSED),
            grade("Matematyka", 2.0, semester = 2),
        )
        val subjects = subjectGrades(grades, 1)
        assertEquals(listOf("biologia", "Matematyka"), subjects.map { it.subjectName })
        assertEquals(1, subjects[1].grades.size)
        assertEquals(GradeType.SEMESTER_PROPOSED, subjects[1].summaryGrades.single().type)
        assertEquals(4.0, overallAverage(subjects)!!, 1e-9)
        assertEquals(2, currentSemester(grades))
    }
}
