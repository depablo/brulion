package pl.brulion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.brulion.data.db.AnnouncementEntity

class AnnouncementsTest {
    private fun notice(id: String, subject: String, content: String = "") = AnnouncementEntity(
        profileId = 1, id = id, subject = subject, content = content, authorName = null,
        startDate = null, endDate = null, read = false, addedAt = 0, firstSeenAt = 0,
    )

    @Test
    fun wholeWordsIgnoringCase() {
        val words = listOf("LO", "matura")
        assertEquals("LO", Announcements.mutedWord(notice("1", "Dzień kolorowy", "dla uczniów lo i SP"), words))
        assertEquals("matura", Announcements.mutedWord(notice("2", "Próbna MATURA"), words))
        assertNull(Announcements.mutedWord(notice("3", "Lot do Krakowa", "koło naukowe, maturalny"), words))
    }

    @Test
    fun handHiddenAndMutedGoApart() {
        val all = listOf(notice("1", "Olimpiada"), notice("2", "Próbna matura"), notice("3", "Zebranie"))
        val split = Announcements.split(all, hiddenIds = setOf("1"), words = listOf("matura"))
        assertEquals(listOf("3"), split.visible.map { it.id })
        assertEquals(listOf("1" to null, "2" to "matura"), split.hidden.map { it.announcement.id to it.mutedBy })
    }

    @Test
    fun importantPinnedToTopKeepingDateOrder() {
        val all = listOf(notice("1", "Zebranie"), notice("2", "Wycieczka"), notice("3", "Egzamin"))
        val split = Announcements.split(all, hiddenIds = emptySet(), words = emptyList(), importantIds = setOf("3"))
        assertEquals(listOf("3", "1", "2"), split.visible.map { it.id })
        assertEquals(setOf("3"), split.important)
    }

    @Test
    fun hiddenImportantIsNotListedAsVisibleImportant() {
        val all = listOf(notice("1", "Zebranie"), notice("2", "Wycieczka"))
        val split = Announcements.split(all, hiddenIds = setOf("2"), words = emptyList(), importantIds = setOf("2"))
        assertEquals(listOf("1"), split.visible.map { it.id })
        assertEquals(emptySet<String>(), split.important)
    }
}
