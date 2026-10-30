package pl.brulion.data.feed

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.brulion.data.db.FeedEntryEntity
import pl.brulion.data.db.FeedKind

class InstantNotifierTest {
    private val now = 1_000_000L

    private fun entry(id: Long, kind: FeedKind, title: String, profileId: Long = 1, ageSeconds: Long = 60, refKey: String = "r$id") =
        FeedEntryEntity(
            id = id, profileId = profileId, childKey = "c1", kind = kind, refKey = refKey,
            occurredAt = now - ageSeconds, title = title, detail = null,
        )

    @Test
    fun onlyPickedRecentAndVisible() {
        val entries = listOf(
            entry(1, FeedKind.GRADE, "Nowa ocena: Matematyka – 5"),
            entry(2, FeedKind.ATTENDANCE, "Nieobecność"),
            entry(3, FeedKind.GRADE, "Nowa ocena: Historia – 3", ageSeconds = 10 * 24 * 60 * 60),
            entry(4, FeedKind.ANNOUNCEMENT, "Ogłoszenie: Próbna matura", refKey = "A1"),
            entry(5, FeedKind.ANNOUNCEMENT, "Ogłoszenie: Zebranie", refKey = "A2"),
        )
        val picked = InstantNotifier.pickNew(entries, setOf(FeedKind.GRADE, FeedKind.ANNOUNCEMENT), setOf("A1"), now)
        assertEquals(listOf(1L, 5L), picked.map { it.id })
    }

    @Test
    fun sameMessageOnBothProfilesOnce() {
        val entries = listOf(
            entry(1, FeedKind.MESSAGE, "Wiadomość od Jan Nowak", profileId = 1, refKey = "1:9"),
            entry(2, FeedKind.MESSAGE, "Wiadomość od Jan Nowak", profileId = 2, refKey = "2:9"),
        )
        assertEquals(1, InstantNotifier.pickNew(entries, setOf(FeedKind.MESSAGE), emptySet(), now).size)
    }
}
