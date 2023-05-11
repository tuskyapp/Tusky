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

package app.tusky.mklanguages

import com.github.h0tk3y.betterParse.grammar.parseToEnd
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@Execution(ExecutionMode.CONCURRENT)
internal class ValuesParserTest {
    @Nested
    @Execution(ExecutionMode.CONCURRENT)
    inner class ParseLocale {
        inner class Params(val filename: String, val expected: Locale?)

        private val parser = ValuesParser()

        private fun getParams(): Stream<Params> {
            return Stream.of(
                Params("values", null),
                Params("values-en", Locale(lang = "en")),
                Params("values-en-rGB", Locale(lang = "en", region = "GB")),
                Params("values-b+tzm+Tfng", Locale(lang = "tzm", script = "Tfng")),
                Params("values-mcc001", null),
                Params("values-land", null)
            )
        }

        @ParameterizedTest
        @MethodSource("getParams")
        fun `returns the expected locale`(params: Params) {
            assertEquals(params.expected, parser.parseToEnd(params.filename).locale)
        }
    }
}
