/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.components.compose

import com.keylesspalace.tusky.FakeSpannable
import com.keylesspalace.tusky.finders
import com.keylesspalace.tusky.util.highlightSpans
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class StatusLengthTest(
    private val text: String,
    private val expectedLength: Int
) {
    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        @JvmStatic
        fun data(): Iterable<Any> {
            return listOf(
                arrayOf("", 0),
                arrayOf(" ", 1),
                arrayOf("123", 3),
                arrayOf("🫣", 1),
                // "@user@server" should be treated as "@user"
                arrayOf("123 @example@example.org", 12),
                // URLs are always treated as 23 even if they are shorter
                arrayOf("123 http://example.org", 27),
                // URLs over 23 chars are treated as 23 chars
                arrayOf("123 http://urlthatislongerthan23characters.example.org", 27),
                // Short hashtags are treated as is
                arrayOf("123 #basictag", 13),
                // Long hashtags are *also* treated as is (not treated as 23, like URLs)
                arrayOf("123 #atagthatislongerthan23characters", 37),
                // urls can have balanced parenthesis, otherwise they are ignored https://github.com/tuskyapp/Tusky/issues/4425
                arrayOf("(https://en.wikipedia.org/wiki/Beethoven_(horse))", 25),
                // protocols can have any case https://github.com/tuskyapp/Tusky/issues/4641
                arrayOf("Http://example.org", 23),
                arrayOf("HTTPS://example.org", 23),
                arrayOf("HTTPS://EXAMPLE.ORG", 23)
            )
        }
    }

    @Test
    fun statusLength_matchesExpectations() {
        val spannedText = FakeSpannable(text)
        spannedText.highlightSpans(0, finders)

        assertEquals(
            expectedLength,
            ComposeActivity.statusLength(spannedText, null, 23)
        )
    }

    @Test
    fun statusLength_withCwText_matchesExpectations() {
        val spannedText = FakeSpannable(text)
        spannedText.highlightSpans(0, finders)

        val cwText = FakeSpannable(
            "a @example@example.org #hashtagmention and http://example.org URL"
        )
        assertEquals(
            expectedLength + cwText.length,
            ComposeActivity.statusLength(spannedText, cwText, 23)
        )
    }
}
