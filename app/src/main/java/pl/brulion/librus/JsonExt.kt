package pl.brulion.librus

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

private fun JsonObject.primitive(key: String): JsonPrimitive? =
    (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

/**
 * The list a section is built from. Librus sends an empty list as `[]`, so a missing key means
 * the response changed (or was cut short), and must not read as "nothing here".
 */
fun JsonObject.requiredArray(key: String): JsonArray =
    array(key) ?: throw LibrusException(LibrusException.Reason.FORM_CHANGED, "response has no $key list")
fun JsonObject.string(key: String): String? = primitive(key)?.content
fun JsonObject.int(key: String): Int? = primitive(key)?.intOrNull
fun JsonObject.long(key: String): Long? = primitive(key)?.longOrNull
fun JsonObject.bool(key: String): Boolean? = primitive(key)?.booleanOrNull

fun JsonArray.objects(): List<JsonObject> = filterIsInstance<JsonObject>()

fun JsonObject.double(key: String): Double? = primitive(key)?.doubleOrNull
