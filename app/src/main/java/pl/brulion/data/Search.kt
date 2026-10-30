package pl.brulion.data

import java.text.Normalizer

/** Lowercase without diacritics, so "zolc" finds "Żółć". */
fun searchKey(text: String): String =
    Normalizer.normalize(text.lowercase().replace('ł', 'l'), Normalizer.Form.NFD).replace(MARKS, "")

/** True when every word of [query] occurs in one of [fields]; a blank query matches everything. */
fun matchesQuery(query: String, vararg fields: String?): Boolean {
    val words = searchKey(query).split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return true
    val haystack = fields.filterNotNull().joinToString("\n") { searchKey(it) }
    return words.all { it in haystack }
}

private val MARKS = Regex("\\p{Mn}+")
