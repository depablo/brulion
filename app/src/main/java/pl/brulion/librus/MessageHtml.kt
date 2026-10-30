package pl.brulion.librus

/**
 * Librus message bodies are plain text, but automatic notices mix in HTML
 * such as `</br>`, `<b>` and `<a href>`.
 */
object MessageHtml {
    private val Tag = Regex("</?(a|b|i|u|br|p|div|span|strong|em|ul|ol|li|font|h[1-6])\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val LineBreak = Regex("<\\s*/?\\s*br\\s*/?\\s*>", RegexOption.IGNORE_CASE)
    private val BlockEnd = Regex("</\\s*(p|div|li|h[1-6])\\s*>", RegexOption.IGNORE_CASE)
    private val AnyTag = Regex("<[^>]+>")
    private val Entities = mapOf("&nbsp;" to " ", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'")

    private val EnvelopeContent = Regex("<Content>\\s*<!\\[CDATA\\[(.*?)]]>\\s*</Content>", RegexOption.DOT_MATCHES_ALL)
    private val EnvelopeLeftovers = Regex("<!\\[CDATA\\[|]]>|</?(Message|Content)>")

    /**
     * Some bodies come wrapped in `<Message><Content><![CDATA[…]]></Content>…</Message>`;
     * returns just the content.
     */
    fun unwrap(text: String): String {
        if (!text.contains("<![CDATA[") && !text.contains("<Message>")) return text
        val content = EnvelopeContent.find(text)?.groupValues?.get(1) ?: EnvelopeLeftovers.replace(text, "")
        return content.trim()
    }

    fun isHtml(text: String): Boolean = Tag.containsMatchIn(text)

    /**
     * Turns the body into HTML that `Html.fromHtml` reads well. `</br>` is
     * normalized to `<br>`; source newlines only count when the text has no
     * line-break tags of its own, otherwise every line would be doubled.
     */
    fun toHtml(text: String): String {
        val withBreaks = LineBreak.replace(text, "<br>")
        val hasBreaks = withBreaks.contains("<br>") || BlockEnd.containsMatchIn(withBreaks)
        return if (hasBreaks) withBreaks.replace("\n", " ") else withBreaks.replace("\n", "<br>")
    }

    /** Plain text without tags, for previews and quotes. */
    fun toPlainText(raw: String): String {
        val text = unwrap(raw)
        if (!isHtml(text)) return text
        val html = toHtml(text)
        val plain = BlockEnd.replace(LineBreak.replace(html, "\n"), "\n")
            .let { AnyTag.replace(it, "") }
            .let { Entities.entries.fold(it) { acc, (entity, char) -> acc.replace(entity, char, ignoreCase = true) } }
            .replace("&amp;", "&", ignoreCase = true)
        return plain.lines().joinToString("\n") { it.trim() }.replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
