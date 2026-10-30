package pl.brulion.data.sync

import pl.brulion.data.db.SeenRow

/**
 * Decides when a record was first seen. Known records keep their stamp; new ones get
 * [newStamp], which is 0 on a section's first sync so a whole year of data isn't "new".
 * A [stamp] with backfill marks a record that was missed rather than just received.
 */
internal class Stamper(seen: List<SeenRow>, private val newStamp: Long) {
    private val known = seen.associate { it.key to it.firstSeenAt }
    var newCount = 0
        private set

    fun stamp(key: String, backfill: Boolean = false): Long =
        known[key] ?: (if (backfill) 0L else newStamp).also { if (it > 0) newCount++ }
}
