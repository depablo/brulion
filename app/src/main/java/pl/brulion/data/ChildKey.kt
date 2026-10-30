package pl.brulion.data

import pl.brulion.data.db.BrulionDatabase
import pl.brulion.data.db.ProfileEntity
import java.security.MessageDigest

/**
 * Identifies a child across their parent and student profiles: the student's Synergia login is
 * the parent's with a trailing "u". Hashed, as it ends up in preference keys; unlike the name,
 * it is never empty and doesn't change with a surname.
 */
fun childKey(profile: ProfileEntity): String {
    val base = profile.synergiaLogin.trim().lowercase().removeSuffix("u")
    val digest = MessageDigest.getInstance("SHA-256").digest(base.toByteArray())
    return "c" + digest.take(8).joinToString("") { "%02x".format(it) }
}

/** Earlier builds keyed children by their lowercased name; moves the feed and summary state over. */
suspend fun migrateChildKeys(db: BrulionDatabase, settings: Settings, profiles: List<ProfileEntity>) {
    for (profile in profiles) {
        val legacy = profile.studentName.trim().lowercase()
        val key = childKey(profile)
        db.feedDao().moveChild(legacy, key)
        settings.moveLastSummaryAt(legacy, key)
    }
}
