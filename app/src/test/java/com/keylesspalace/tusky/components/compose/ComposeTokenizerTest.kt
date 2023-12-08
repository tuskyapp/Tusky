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

package com.keylesspalace.tusky.components.compose

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ComposeTokenizerTest(
    private val text: CharSequence,
    private val expectedStartIndex: Int,
    private val expectedEndIndex: Int
) {

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
                arrayOf(" @ment10n-", 1, 10),
                arrayOf(" @ment10n- @", 11, 12),
                arrayOf(" @ment10n- @ment20n", 11, 19),
                arrayOf(" @ment10n- @ment20n-", 11, 20),
                arrayOf(" @ment10n- @ment20n-n", 11, 21),
                arrayOf(" @ment10n- @ment20n-9", 11, 21),
                arrayOf("@ment10n@l0calhost", 0, 18),
                arrayOf(" @ment10n@l0calhost", 1, 19),
                arrayOf(" @ment10n_@l0calhost", 1, 20),
                arrayOf(" @ment10n-@l0calhost", 1, 20),
                arrayOf(" @ment10n_@l0calhost @ment20n@husky", 21, 35),
                arrayOf(" @ment10n_@l0calhost @ment20n_@husky", 21, 36),
                arrayOf(" @ment10n-@l0calhost @ment20n-@husky", 21, 36),
                arrayOf(" @m@localhost", 1, 13),
                arrayOf(" @m@localhost @a@localhost", 14, 26),
                arrayOf("@m@", 0, 3),
                arrayOf(" @m@ @a@asdf", 5, 12),
                arrayOf(" @m@ @a@", 5, 8),
                arrayOf(" @m@ @a@a", 5, 9),
                arrayOf(" @m@a @a@m", 6, 10),
                arrayOf("@m@m@", 5, 5),
                arrayOf("#tusky@husky", 12, 12),
                arrayOf(":tusky@husky", 12, 12),
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
