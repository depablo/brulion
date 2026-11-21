package pl.brulion.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkifyTest {
    private val styles = TextLinkStyles()

    private fun urls(text: AnnotatedString): List<String> =
        linkify(text, styles).getLinkAnnotations(0, text.length).map { (it.item as LinkAnnotation.Url).url }

    private fun urls(text: String) = urls(AnnotatedString(text))

    @Test
    fun findsAddressInTopic() {
        val url = "https://www.prezydent.pl/storage/file/core_files/2022/11/2/d39a/Tekst%20hymnu.pdf"
        assertEquals(listOf(url), urls("Tekst hymnu państwowego\n$url"))
    }

    @Test
    fun dropsSentencePunctuation() {
        assertEquals(listOf("https://a.pl/x"), urls("Zobacz https://a.pl/x."))
        assertEquals(listOf("https://a.pl/x"), urls("(link: https://a.pl/x)"))
    }

    @Test
    fun keepsBalancedParenthesis() {
        assertEquals(listOf("https://pl.wikipedia.org/wiki/Hymn_(Polska)"), urls("https://pl.wikipedia.org/wiki/Hymn_(Polska)"))
    }

    @Test
    fun addsSchemeToWww() {
        assertEquals(listOf("https://www.librus.pl"), urls("na www.librus.pl, proszę"))
    }

    @Test
    fun leavesTextWithoutAddressesAlone() {
        assertEquals(emptyList<String>(), urls("this, that, these, those"))
    }

    @Test
    fun skipsExistingLinks() {
        val text = buildAnnotatedString {
            withLink(LinkAnnotation.Url("https://a.pl")) { append("https://a.pl") }
            append(" i https://b.pl")
        }
        assertEquals(listOf("https://a.pl", "https://b.pl"), urls(text))
    }
}
