package tech.bigfig.roma.util

import android.text.Spanned

/**
 * Abstracting away Android-specific things.
 */
interface HtmlConverter {
    fun fromHtml(html: String): Spanned

    fun toHtml(text: Spanned): String
}

internal class HtmlConverterImpl : HtmlConverter {
    override fun fromHtml(html: String): Spanned {
        return HtmlUtils.fromHtml(html)
    }

    override fun toHtml(text: Spanned): String {
        return HtmlUtils.toHtml(text)
    }
}