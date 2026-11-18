package pl.brulion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.brulion.data.db.ProfileEntity

class ChildOrderTest {
    private fun profile(id: Long, login: String, name: String) =
        ProfileEntity(id = id, accountId = 1, synergiaLogin = login, studentName = name, isParent = false)

    private val anna = childKey(profile(1, "111", "Anna"))
    private val beata = childKey(profile(2, "222", "Beata"))
    private val celina = childKey(profile(3, "333", "Celina"))

    private val profiles = listOf(
        profile(1, "111", "Anna"),
        profile(2, "222", "Beata"),
        profile(3, "333", "Celina"),
    )

    @Test
    fun withoutPositionsSortsByName() {
        assertEquals(listOf("Anna", "Beata", "Celina"), orderProfiles(profiles, emptyMap()).map { it.studentName })
    }

    @Test
    fun storedPositionsWin() {
        val order = mapOf(celina to 0, anna to 1, beata to 2)
        assertEquals(listOf("Celina", "Anna", "Beata"), orderProfiles(profiles, order).map { it.studentName })
    }

    @Test
    fun childrenWithoutPositionComeLast() {
        val order = mapOf(beata to 0)
        assertEquals(listOf("Beata", "Anna", "Celina"), orderProfiles(profiles, order).map { it.studentName })
    }

    @Test
    fun parentAndStudentProfilesOfOneChildStayTogether() {
        // "111u" is the parent login of the same child as "111".
        val withParent = profiles + profile(4, "111u", "Anna")
        val order = mapOf(celina to 0, anna to 1)
        val names = orderProfiles(withParent, order).map { it.id }
        assertEquals(listOf(3L, 1L, 4L, 2L), names)
    }

    @Test
    fun moveDownSwapsWithNeighbour() {
        assertEquals(listOf("b", "a", "c"), moveChildInOrder(listOf("a", "b", "c"), 0, 1))
    }

    @Test
    fun moveUpSwapsWithNeighbour() {
        assertEquals(listOf("a", "c", "b"), moveChildInOrder(listOf("a", "b", "c"), 2, -1))
    }

    @Test
    fun cannotMoveOffTheEnds() {
        assertNull(moveChildInOrder(listOf("a", "b"), 0, -1))
        assertNull(moveChildInOrder(listOf("a", "b"), 1, 1))
        assertNull(moveChildInOrder(listOf("a"), -1, 1))
    }
}
