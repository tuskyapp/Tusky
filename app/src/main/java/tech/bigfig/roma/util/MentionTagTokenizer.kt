/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma.util

import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.widget.MultiAutoCompleteTextView

class MentionTagTokenizer : MultiAutoCompleteTextView.Tokenizer {
    override fun findTokenStart(text: CharSequence, cursor: Int): Int {
        if (cursor == 0) {
            return cursor
        }
        var i = cursor
        var character = text[i - 1]
        while (i > 0 && character != '@' && character != '#') {
            // See SpanUtils.MENTION_REGEX
            if (!Character.isLetterOrDigit(character) && character != '_') {
                return cursor
            }
            i--
            character = if (i == 0) ' ' else text[i - 1]
        }
        if (i < 1
                || (character != '@' && character != '#')
                || i > 1 && !Character.isWhitespace(text[i - 2])) {
            return cursor
        }
        return i - 1
    }

    override fun findTokenEnd(text: CharSequence, cursor: Int): Int {
        var i = cursor
        val length = text.length
        while (i < length) {
            if (text[i] == ' ') {
                return i
            } else {
                i++
            }
        }
        return length
    }

    override fun terminateToken(text: CharSequence): CharSequence {
        var i = text.length
        while (i > 0 && text[i - 1] == ' ') {
            i--
        }
        return if (i > 0 && text[i - 1] == ' ') {
            text
        } else if (text is Spanned) {
            val s = SpannableString(text.toString() + " ")
            TextUtils.copySpansFrom(text, 0, text.length, Object::class.java, s, 0)
            s
        } else {
            text.toString() + " "
        }
    }
}
