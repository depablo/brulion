package pl.brulion.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import pl.brulion.data.db.AnnouncementEntity
import pl.brulion.data.db.BrulionDatabase
import pl.brulion.data.db.HiddenAnnouncementEntity
import pl.brulion.data.db.ImportantAnnouncementEntity
import pl.brulion.librus.nowSeconds

/**
 * Announcements without the ones the user doesn't want: dismissed one by one, or mentioning a
 * muted word. Librus sends a school complex's notices to everyone, high school ones included.
 */
class Announcements(private val db: BrulionDatabase, private val settings: Settings) {

    /** [mutedBy] is the muted word an announcement matched; null for one hidden by hand. */
    data class Hidden(val announcement: AnnouncementEntity, val mutedBy: String?)

    /** [visible] lists the important ones first; [important] holds the ids marked as important. */
    data class Split(val visible: List<AnnouncementEntity>, val hidden: List<Hidden>, val important: Set<String> = emptySet())

    fun observe(profileId: Long): Flow<Split> = combine(
        db.announcementDao().observeAll(profileId),
        db.hiddenAnnouncementDao().observeIds(),
        db.importantAnnouncementDao().observeIds(),
        settings.mutedWords,
    ) { all, hiddenIds, importantIds, words -> split(all, hiddenIds.toSet(), words, importantIds.toSet()) }

    suspend fun visible(profileId: Long): List<AnnouncementEntity> = observe(profileId).first().visible

    /** Ids of everything hidden, for lists that only know the id (the feed). */
    fun observeHiddenIds(): Flow<Set<String>> = combine(
        db.announcementDao().observeEveryProfile(),
        db.hiddenAnnouncementDao().observeIds(),
        settings.mutedWords,
    ) { all, hiddenIds, words -> hiddenIds.toSet() + split(all, emptySet(), words).hidden.map { it.announcement.id } }

    suspend fun hide(id: String) = db.hiddenAnnouncementDao().hide(HiddenAnnouncementEntity(id, nowSeconds()))

    suspend fun show(id: String) = db.hiddenAnnouncementDao().show(id)

    suspend fun markImportant(id: String) = db.importantAnnouncementDao().mark(ImportantAnnouncementEntity(id, nowSeconds()))

    suspend fun unmarkImportant(id: String) = db.importantAnnouncementDao().unmark(id)

    companion object {
        internal fun split(
            all: List<AnnouncementEntity>,
            hiddenIds: Set<String>,
            words: List<String>,
            importantIds: Set<String> = emptySet(),
        ): Split {
            val visible = mutableListOf<AnnouncementEntity>()
            val hidden = mutableListOf<Hidden>()
            for (announcement in all) {
                val word = mutedWord(announcement, words)
                when {
                    announcement.id in hiddenIds -> hidden += Hidden(announcement, null)
                    word != null -> hidden += Hidden(announcement, word)
                    else -> visible += announcement
                }
            }
            // Important ones go first; sortedBy is stable, so the date order inside each group stays.
            val pinned = visible.sortedBy { if (it.id in importantIds) 0 else 1 }
            return Split(pinned, hidden, importantIds.intersect(visible.map { it.id }.toSet()))
        }

        /** Whole words only, ignoring case: "LO" doesn't match "LOT" or "kolo". */
        internal fun mutedWord(announcement: AnnouncementEntity, words: List<String>): String? {
            val text = announcement.subject + "\n" + announcement.content
            return words.firstOrNull { word ->
                Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(word) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(text)
            }
        }
    }
}
