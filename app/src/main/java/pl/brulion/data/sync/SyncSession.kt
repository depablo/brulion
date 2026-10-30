package pl.brulion.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonObject
import pl.brulion.data.AccountRepository
import pl.brulion.librus.LibrusApi
import pl.brulion.librus.array
import pl.brulion.librus.long
import pl.brulion.librus.normalizeSpaces
import pl.brulion.librus.obj
import pl.brulion.librus.objects
import pl.brulion.librus.string

/**
 * One sync run of one profile. Each endpoint is fetched at most once, so sections can share
 * dictionaries (subjects, teachers...) without coordinating who loads them. [fetch] does the
 * actual request; tests pass recorded responses.
 */
class SyncSession(
    private val scope: CoroutineScope,
    private val fetch: suspend (endpoint: String) -> JsonObject,
) {
    constructor(scope: CoroutineScope, profileId: Long, accounts: AccountRepository, api: LibrusApi) :
        this(scope, { endpoint -> accounts.withApi(profileId) { token -> api.get(token, endpoint) } })

    private val cache = mutableMapOf<String, Deferred<JsonObject>>()
    private val cacheLock = Mutex()
    // Be gentle with Librus: a handful of requests in flight is plenty.
    private val network = Semaphore(4)

    suspend fun get(endpoint: String): JsonObject {
        val deferred = cacheLock.withLock {
            cache.getOrPut(endpoint) {
                scope.async {
                    network.withPermit { fetch(endpoint) }
                }
            }
        }
        return deferred.await()
    }

    /** Maps "Id" -> object for a list endpoint, e.g. get("Subjects") -> Subjects[]. */
    suspend fun byId(endpoint: String, listKey: String): Map<Long, JsonObject> =
        get(endpoint).array(listKey)?.objects().orEmpty()
            .mapNotNull { item -> item.long("Id")?.let { it to item } }
            .toMap()

    suspend fun subjects(): Map<Long, String> =
        byId("Subjects", "Subjects").mapValues { it.value.string("Name").orEmpty().normalizeSpaces() }

    suspend fun teachers(): Map<Long, String> = byId("Users", "Users").mapValues { (_, user) ->
        listOfNotNull(user.string("FirstName"), user.string("LastName"))
            .joinToString(" ") { it.normalizeSpaces() }
            .trim()
    }

    /** Lesson id -> (subject id, teacher id); attendance only references lessons. */
    suspend fun lessons(): Map<Long, Pair<Long?, Long?>> = byId("Lessons", "Lessons").mapValues { (_, lesson) ->
        lesson.obj("Subject")?.long("Id") to lesson.obj("Teacher")?.long("Id")
    }

    suspend fun classrooms(): Map<Long, String> = byId("Classrooms", "Classrooms").mapValues { (_, room) ->
        val name = room.string("Name").orEmpty().normalizeSpaces()
        val symbol = room.string("Symbol").orEmpty().normalizeSpaces()
        when {
            symbol.isEmpty() -> name
            name.isEmpty() || name.contains(symbol, ignoreCase = true) -> name.ifEmpty { symbol }
            else -> "$symbol $name"
        }
    }

    /** Lesson number -> start time from the school's bell schedule. */
    suspend fun lessonStartTimes(): Map<Int, String> {
        val ranges = get("Schools").obj("School")?.array("LessonsRange")?.objects().orEmpty()
        return ranges.mapIndexedNotNull { index, range ->
            range.string("From")?.take(5)?.let { index to it }
        }.toMap()
    }
}
