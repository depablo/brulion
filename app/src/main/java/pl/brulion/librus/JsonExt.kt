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
fun JsonObject.string(key: String): String? = primitive(key)?.content
fun JsonObject.int(key: String): Int? = primitive(key)?.intOrNull
fun JsonObject.long(key: String): Long? = primitive(key)?.longOrNull
fun JsonObject.bool(key: String): Boolean? = primitive(key)?.booleanOrNull

fun JsonArray.objects(): List<JsonObject> = filterIsInstance<JsonObject>()

fun JsonObject.double(key: String): Double? = primitive(key)?.doubleOrNull
