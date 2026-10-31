package pl.brulion.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.brulion.data.db.ProfileEntity

class OnePerChildTest {
    private fun profile(id: Long, login: String, name: String, parent: Boolean) =
        ProfileEntity(id = id, accountId = 1, synergiaLogin = login, studentName = name, isParent = parent)

    private val profiles = listOf(
        profile(1, "123", "Antonina Nowak", parent = true),
        profile(3, "123u", "Antonina Nowak", parent = false),
        profile(2, "456", "Łucja Nowak", parent = true),
    )

    @Test
    fun eachChildOnce() {
        assertEquals(listOf(1L, 2L), onePerChild(profiles, current = null).map { it.id })
    }

    @Test
    fun currentProfileIsKept() {
        assertEquals(listOf(3L, 2L), onePerChild(profiles, current = 3).map { it.id })
    }
}
