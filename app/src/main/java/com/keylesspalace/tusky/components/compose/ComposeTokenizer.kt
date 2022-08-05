/* Copyright 2017 Andrew Dawson
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

package com.keylesspalace.tusky.components.compose

import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.widget.MultiAutoCompleteTextView

class ComposeTokenizer : MultiAutoCompleteTextView.Tokenizer {

    private fun isMentionOrHashtagAllowedCharacter(character: Char): Boolean {
        return Character.isLetterOrDigit(character) || character == '_' || // simple usernames
            character == '-' || // extended usernames
            character == '.' // domain dot
    }

    override fun findTokenStart(text: CharSequence, cursor: Int): Int {
        if (cursor == 0) {
            return cursor
        }
        var i = cursor
        var character = text[i - 1]

        // go up to first illegal character or character we're looking for (@, # or :)
        while (i > 0 && !(character == '@' || character == '#' || character == ':')) {
            if (!isMentionOrHashtagAllowedCharacter(character)) {
                return cursor
            }

            i--
            character = if (i == 0) ' ' else text[i - 1]
        }

        // maybe caught domain name? try search username
        if (i > 2 && character == '@') {
            var j = i - 1
            var character2 = text[i - 2]

            // again go up to first illegal character or tag "@"
            while (j > 0 && character2 != '@') {
                if (!isMentionOrHashtagAllowedCharacter(character2)) {
                    break
                }

                j--
                character2 = if (j == 0) ' ' else text[j - 1]
            }

            // found mention symbol, override cursor
            if (character2 == '@') {
                i = j
                character = character2
            }
        }

        if (i < 1 ||
            (character != '@' && character != '#' && character != ':') ||
            i > 1 && !Character.isWhitespace(text[i - 2])
        ) {
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
            val s = SpannableString("$text ")
            TextUtils.copySpansFrom(text, 0, text.length, Object::class.java, s, 0)
            s
        } else {
            "$text "
        }
    }
}
