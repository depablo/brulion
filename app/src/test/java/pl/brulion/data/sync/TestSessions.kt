package pl.brulion.data.sync

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.IOException

internal fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

internal fun resourceJson(path: String): JsonObject = json(object {}.javaClass.getResource(path)!!.readText())

/**
 * Runs [block] with a session answering from [responses]; endpoints in [failing] fail like an
 * HTTP 500. Supervised as in SyncRepository, so a failed endpoint doesn't cancel the others.
 */
internal fun <T> withSession(
    responses: Map<String, JsonObject>,
    failing: Set<String> = emptySet(),
    block: suspend (SyncSession) -> T,
): T = runBlocking {
    supervisorScope {
        block(SyncSession(this) { endpoint ->
            if (endpoint in failing) throw IOException("HTTP 500")
            responses[endpoint] ?: error("unexpected request: $endpoint")
        })
    }
}
