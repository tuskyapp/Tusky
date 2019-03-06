/* Copyright 2018 Levi Bard
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

package tech.bigfig.roma

import tech.bigfig.roma.util.ComposeTokenizer
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ComposeTokenizerTest(private val text: CharSequence,
                           private val expectedStartIndex: Int,
                           private val expectedEndIndex: Int) {

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun data(): Iterable<Any> {
            return listOf(
                    arrayOf("@mention", 0, 8),
                    arrayOf("@ment10n", 0, 8),
                    arrayOf("@ment10n_", 0, 9),
                    arrayOf("@ment10n_n", 0, 10),
                    arrayOf("@ment10n_9", 0, 10),
                    arrayOf(" @mention", 1, 9),
                    arrayOf(" @ment10n", 1, 9),
                    arrayOf(" @ment10n_", 1, 10),
                    arrayOf(" @ment10n_ @", 11, 12),
                    arrayOf(" @ment10n_ @ment20n", 11, 19),
                    arrayOf(" @ment10n_ @ment20n_", 11, 20),
                    arrayOf(" @ment10n_ @ment20n_n", 11, 21),
                    arrayOf(" @ment10n_ @ment20n_9", 11, 21),
                    arrayOf("mention", 7, 7),
                    arrayOf("ment10n", 7, 7),
                    arrayOf("mentio_", 7, 7),
                    arrayOf("#tusky", 0, 6),
                    arrayOf("#@tusky", 7, 7),
                    arrayOf("@#tusky", 7, 7),
                    arrayOf(" @#tusky", 8, 8),
                    arrayOf(":mastodon", 0, 9),
                    arrayOf(":@mastodon", 10, 10),
                    arrayOf("@:mastodon", 10, 10),
                    arrayOf(" @:mastodon", 11, 11),
                    arrayOf("#@:mastodon", 11, 11),
                    arrayOf(" #@:mastodon", 12, 12)
            )
        }
    }

    private val tokenizer = ComposeTokenizer()

    @Test
    fun tokenIndices_matchExpectations() {
        Assert.assertEquals(expectedStartIndex, tokenizer.findTokenStart(text, text.length))
        Assert.assertEquals(expectedEndIndex, tokenizer.findTokenEnd(text, text.length))
    }
}