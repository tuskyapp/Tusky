/* Copyright 2022 Tusky Contributors
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

@file:JvmName("StatusParsingHelper")

package com.keylesspalace.tusky.util

import android.text.Editable
import android.text.Html.TagHandler
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.TypefaceSpan
import androidx.core.text.parseAsHtml
import org.xml.sax.XMLReader

/**
 * parse a String containing html from the Mastodon api to Spanned
 */
@JvmOverloads
fun String.parseAsMastodonHtml(tagHandler: TagHandler? = tuskyTagHandler): Spanned {
    return this.replace("<br> ", "<br>&nbsp;")
        .replace("<br /> ", "<br />&nbsp;")
        .replace("<br/> ", "<br/>&nbsp;")
        .replace("  ", "&nbsp;&nbsp;")
        .parseAsHtml(tagHandler = tagHandler)
        /* Html.fromHtml returns trailing whitespace if the html ends in a </p> tag, which
         * most status contents do, so it should be trimmed. */
        .trimTrailingWhitespace()
}

val tuskyTagHandler = TuskyTagHandler()

open class TuskyTagHandler : TagHandler {

    class Code

    override fun handleTag(opening: Boolean, tag: String, output: Editable, xmlReader: XMLReader) {
        when (tag) {
            "code" -> {
                if (opening) {
                    start(output as SpannableStringBuilder, Code())
                } else {
                    end(
                        output as SpannableStringBuilder,
                        Code::class.java,
                        TypefaceSpan("monospace")
                    )
                }
            }
        }
    }

    /** @return the last span in [text] of type [kind], or null if that kind is not in text */
    protected fun <T> getLast(text: Spanned, kind: Class<T>): Any? {
        val spans = text.getSpans(0, text.length, kind)
        return spans?.get(spans.size - 1)
    }

    /**
     * Mark the start of a span of [text] with [mark] so it can be discovered later by [end].
     */
    protected fun start(text: SpannableStringBuilder, mark: Any) {
        val len = text.length
        text.setSpan(mark, len, len, Spannable.SPAN_MARK_MARK)
    }

    /**
     * Set a [span] over the [text] most from the point recently marked with [mark] to the end
     * of the text.
     */
    protected fun <T> end(text: SpannableStringBuilder, mark: Class<T>, span: Any) {
        val len = text.length
        val obj = getLast(text, mark)
        val where = text.getSpanStart(obj)
        text.removeSpan(obj)
        if (where != len) {
            text.setSpan(span, where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
