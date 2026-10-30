package pl.brulion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import pl.brulion.data.db.ProfileEntity

class ChildKeyTest {
    private fun profile(login: String, name: String = "") = ProfileEntity(
        accountId = 1, synergiaLogin = login, studentName = name, isParent = !login.endsWith("u"),
        apiAccessToken = null, apiTokenExpiresAt = 0,
    )

    @Test
    fun parentAndStudentAccountsAreOneChild() {
        assertEquals(childKey(profile("1234567", "Anna Nowak")), childKey(profile("1234567u", "anna  NOWAK")))
    }

    @Test
    fun namesDontMatter() {
        assertNotEquals(childKey(profile("1234567")), childKey(profile("7654321")))
        assertEquals(childKey(profile("1234567", "Anna Nowak")), childKey(profile("1234567", "Anna Kowalska")))
    }
}
