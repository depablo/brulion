/*
 * Part of Brulion, GNU GPL v3. Based on Librus support in Szkolny.eu
 * (https://github.com/szkolny-eu/szkolny-android), (c) Kuba Szczodrzyński and contributors, GPL v3.
 */
package pl.brulion.librus

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

/** e-Usprawiedliwienia: the Librus API 3.0 `Justifications` endpoint, as the Librus web app calls it. */
class LibrusJustifications(private val api: LibrusApi) {

    /**
     * [lessons] are lesson numbers from the timetable (1, 2, ...), not lesson ids. Leave them
     * empty to justify whole days between [dateFrom] and [dateTo].
     */
    class Request(
        val dateFrom: LocalDate,
        val dateTo: LocalDate,
        val lessons: List<Int>,
        val message: String,
        val notifyTeachers: Boolean,
    ) {
        fun toJson(): JsonObject = buildJsonObject {
            put("messageFromParent", message)
            put("lessons", JsonArray(lessons.map { JsonPrimitive(it) }))
            put("dateFrom", dateFrom.toString())
            put("dateTo", dateTo.toString())
            put("sendNotify", notifyTeachers)
        }
    }

    /** Sends the justification and returns the id Librus gave it, if it sent one. */
    suspend fun send(token: String, request: Request): String? =
        api.post(token, "Justifications", request.toJson()).string("Id")
}
