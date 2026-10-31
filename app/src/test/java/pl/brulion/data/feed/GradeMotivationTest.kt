package pl.brulion.data.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.brulion.data.db.FeedEntryEntity
import pl.brulion.data.db.FeedKind

class GradeMotivationTest {
    private fun entry(title: String, kind: FeedKind = FeedKind.GRADE) = FeedEntryEntity(
        profileId = 1, childKey = "c1", kind = kind, refKey = "NORMAL:7", occurredAt = 0, title = title, detail = null,
    )

    @Test
    fun gradeFromTitle() {
        assertEquals(5, GradeMotivation.gradeOf("Nowa ocena: Matematyka – 5+"))
        assertEquals(2, GradeMotivation.gradeOf("Ocena roczna: Język polski – 2-"))
        assertEquals(1, GradeMotivation.gradeOf("Nowa ocena: Wiedza o społeczeństwie – historia – 1"))
        assertNull(GradeMotivation.gradeOf("Nowa ocena: Fizyka – 12/15"))
        assertNull(GradeMotivation.gradeOf("Nowa ocena: Fizyka – np"))
        assertNull(GradeMotivation.gradeOf("Nowa ocena: Fizyka – +"))
        assertNull(GradeMotivation.gradeOf("Nowa ocena: Fizyka – 0"))
    }

    @Test
    fun audiencesGetTheirOwnTexts() {
        val parent = GradeMotivation.forEntry(entry("Nowa ocena: Matematyka – 5"), parent = true)
        val student = GradeMotivation.forEntry(entry("Nowa ocena: Matematyka – 5"), parent = false)
        assertTrue(parent in GradeMotivation.Parent.getValue(5))
        assertTrue(student in GradeMotivation.Student.getValue(5))
        assertNotNull(parent)
    }

    @Test
    fun onlyForGrades() {
        assertNull(GradeMotivation.forEntry(entry("Nowa ocena: Matematyka – 5", FeedKind.NOTE), parent = true))
    }

    @Test
    fun tenDistinctTextsPerGradeAndAudience() {
        for (texts in listOf(GradeMotivation.Student, GradeMotivation.Parent)) {
            assertEquals((1..6).toSet(), texts.keys)
            texts.values.forEach { assertEquals(10, it.toSet().size) }
        }
    }

    @Test
    fun noPronounsForTheReaderAndNoGenderedForms() {
        val forbidden = Regex(
            "(?U)\\b(ty|ci|cię|ciebie|tobie|tobą|twój|twoja|twoje|twojego|twojej|twoim|twoich|pan|pani|" +
                "syn|syna|córka|córki|on|ona|jego|jej)\\b|łeś|łaś|łem|łam|\\(a\\)",
            RegexOption.IGNORE_CASE,
        )
        listOf("Twoja praca", "Brawo, zdobyłeś piątkę", "Gratulacje dla Ciebie").forEach { assertNotNull(it, forbidden.find(it)) }
        val all = (GradeMotivation.Student.values + GradeMotivation.Parent.values).flatten()
        all.forEach { assertNull(it, forbidden.find(it)) }
    }
}
