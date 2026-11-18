package pl.brulion.data

import pl.brulion.data.db.ProfileEntity

/**
 * Sorts profiles by the user's child order, then by name. Children without a stored position
 * come after the ordered ones, by name.
 */
fun orderProfiles(profiles: List<ProfileEntity>, positions: Map<String, Int>): List<ProfileEntity> =
    profiles.sortedWith(
        compareBy<ProfileEntity> { positions[childKey(it)] ?: Int.MAX_VALUE }
            .thenBy { it.studentName },
    )

/**
 * Moves the child at [index] by [delta] places in [childKeys] (already in display order).
 * Returns the new order, or null when the move would leave the list.
 */
fun moveChildInOrder(childKeys: List<String>, index: Int, delta: Int): List<String>? {
    val target = index + delta
    if (index !in childKeys.indices || target !in childKeys.indices) return null
    val order = childKeys.toMutableList()
    val moved = order.removeAt(index)
    order.add(target, moved)
    return order
}
