package pl.brulion.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.brulion.data.db.GradeEntity
import pl.brulion.data.db.GradeType

class GradeParserTest {
    private val responses = mapOf(
        "Subjects" to json("""{"Subjects":[{"Id":1,"Name":"Matematyka"}]}"""),
        "Users" to json("""{"Users":[{"Id":10,"FirstName":"Anna","LastName":"Kowalska"}]}"""),
        "Grades/Categories" to json(
            """{"Categories":[
                {"Id":100,"Name":"Sprawdzian","Weight":3,"CountToTheAverage":true,"Color":{"Id":2}},
                {"Id":101,"Name":"Aktywność","Weight":1,"CountToTheAverage":false}
            ]}"""
        ),
        "Grades/Comments" to json("""{"Comments":[{"Id":500,"Text":"Poprawa  w terminie"}]}"""),
        "Grades" to json(
            """{"Grades":[
                {"Id":1,"Semester":1,"Subject":{"Id":1},"Grade":"2","Category":{"Id":100},"AddedBy":{"Id":10},"AddDate":"2026-10-01 10:00:00","IsConstituent":true},
                {"Id":2,"Semester":1,"Subject":{"Id":1},"Grade":"4+","Category":{"Id":100},"AddDate":"2026-10-08 10:00:00","IsConstituent":true,
                 "Improvement":{"Id":1},"Comments":[{"Id":500}]},
                {"Id":3,"Semester":1,"Subject":{"Id":1},"Grade":"+","Category":{"Id":101},"IsConstituent":true},
                {"Id":4,"Semester":1,"Subject":{"Id":1},"Grade":"np","Category":{"Id":100},"IsConstituent":true},
                {"Id":5,"Semester":1,"Subject":{"Id":1},"Grade":"4","IsSemesterProposition":true},
                {"Id":6,"Semester":2,"Subject":{"Id":1},"Grade":"5","IsFinal":"1"},
                {"Id":7,"Subject":{"Id":1},"Grade":"3"}
            ]}"""
        ),
    )

    private val grades: Map<Long, GradeEntity> = withSession(responses) { GradeParser(it, profileId = 1).normal() }.associateBy { it.id }

    @Test
    fun weightsFollowTheCategory() {
        assertEquals(3.0, grades.getValue(1).weight, 0.0)
        // Category that doesn't count, and "np" in one that does: shown, but no weight.
        assertEquals(0.0, grades.getValue(3).weight, 0.0)
        assertEquals(0.0, grades.getValue(4).weight, 0.0)
        assertEquals(null, grades.getValue(4).numericValue)
    }

    @Test
    fun improvementIsLinkedOnTheOldGrade() {
        assertEquals(2L, grades.getValue(1).improvedById)
        val better = grades.getValue(2)
        assertEquals(4.5, better.numericValue!!, 0.0)
        assertEquals("Poprawa w terminie", better.comment)
        assertEquals("Sprawdzian", better.categoryName)
    }

    @Test
    fun typesAndNames() {
        assertEquals(GradeType.REGULAR, grades.getValue(1).type)
        assertEquals(GradeType.SEMESTER_PROPOSED, grades.getValue(5).type)
        assertEquals(GradeType.YEAR_FINAL, grades.getValue(6).type)
        assertEquals("Matematyka" to "Anna Kowalska", grades.getValue(1).subjectName to grades.getValue(1).teacherName)
        // Without a semester the grade can't be placed and is skipped.
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L, 6L), grades.keys)
    }
}
