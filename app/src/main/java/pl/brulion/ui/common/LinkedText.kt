package pl.brulion.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import pl.brulion.R

/** Bare web addresses in teachers' texts: http(s)://… or www.… */
private val UrlPattern = Regex("""(?:https?://|www\.)[^\s<>"]+""", RegexOption.IGNORE_CASE)

/** Punctuation that ends a sentence rather than the address. */
private const val TrailingPunctuation = ".,;:!?)]}'\""

/**
 * Returns [text] with every bare web address turned into a clickable link.
 * Ranges that already carry a link (e.g. from HTML `<a href>`) are left alone.
 */
fun linkify(text: AnnotatedString, styles: TextLinkStyles): AnnotatedString {
    val existing = text.getLinkAnnotations(0, text.length)
    val matches = UrlPattern.findAll(text.text).mapNotNull { match ->
        var end = match.range.last + 1
        while (end > match.range.first && text.text[end - 1] in TrailingPunctuation) {
            // Keep a closing parenthesis that has its opening one inside the address (Wikipedia style).
            if (text.text[end - 1] == ')' && text.text.substring(match.range.first, end - 1).contains('(')) break
            end--
        }
        val start = match.range.first
        if (end <= start) return@mapNotNull null
        if (existing.any { it.start < end && start < it.end }) return@mapNotNull null
        start until end
    }.toList()
    if (matches.isEmpty()) return text
    return buildAnnotatedString {
        append(text)
        for (range in matches) {
            val raw = text.text.substring(range.first, range.last + 1)
            val url = if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw
            addLink(LinkAnnotation.Url(url, styles), range.first, range.last + 1)
        }
    }
}

/** Link look shared by all screens. */
@Composable
fun linkStyles(): TextLinkStyles {
    val color = MaterialTheme.colorScheme.primary
    return remember(color) { TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline)) }
}

/** [text] with clickable web addresses, remembered across recompositions. */
@Composable
fun rememberLinkedText(text: String): AnnotatedString {
    val styles = linkStyles()
    return remember(text, styles) { linkify(AnnotatedString(text), styles) }
}

/**
 * Puts [text] on the clipboard and confirms it. Always toasts: some ROMs (e.g. LineageOS)
 * lack the system clipboard preview that Android 13+ normally shows.
 */
fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), text))
    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
}
