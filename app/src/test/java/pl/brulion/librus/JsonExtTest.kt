package pl.brulion.librus

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonExtTest {
    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun emptyListIsNotAnError() {
        assertEquals(emptyList<JsonObject>(), parse("""{"Grades": []}""").requiredArray("Grades").objects())
    }

    @Test
    fun missingListIsAnError() {
        val error = assertThrows(LibrusException::class.java) { parse("""{"Other": []}""").requiredArray("Grades") }
        assertEquals(LibrusException.Reason.FORM_CHANGED, error.reason)
    }

    @Test
    fun listOfWrongTypeIsAnError() {
        assertThrows(LibrusException::class.java) { parse("""{"Grades": null}""").requiredArray("Grades") }
    }
}
