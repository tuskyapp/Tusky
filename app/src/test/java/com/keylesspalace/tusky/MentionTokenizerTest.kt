/* Copyright 2018 Levi Bard
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

package com.keylesspalace.tusky

import com.keylesspalace.tusky.util.MentionTokenizer
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MentionTokenizerTest(private val text: CharSequence,
    private val expectedStartIndex: Int,
    private val expectedEndIndex: Int) {

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun data(): Iterable<Any> {
            return listOf(
                arrayOf("@mention", 1, 8),
                arrayOf("@ment10n", 1, 8),
                arrayOf("@ment10n_", 1, 9),
                arrayOf("@ment10n_n", 1, 10),
                arrayOf("@ment10n_9", 1, 10),
                arrayOf(" @mention", 2, 9),
                arrayOf(" @ment10n", 2, 9),
                arrayOf(" @ment10n_", 2, 10),
                arrayOf(" @ment10n_ @", 12, 12),
                arrayOf(" @ment10n_ @ment20n", 12, 19),
                arrayOf(" @ment10n_ @ment20n_", 12, 20),
                arrayOf(" @ment10n_ @ment20n_n", 12, 21),
                arrayOf(" @ment10n_ @ment20n_9", 12, 21),
                arrayOf("mention", 7, 7),
                arrayOf("ment10n", 7, 7),
                arrayOf("mentio_", 7, 7)
            )
        }
    }

    private val tokenizer = MentionTokenizer()

    @Test
    fun tokenIndices_matchExpectations() {
        Assert.assertEquals(expectedStartIndex, tokenizer.findTokenStart(text, text.length))
        Assert.assertEquals(expectedEndIndex, tokenizer.findTokenEnd(text, text.length))
    }
}