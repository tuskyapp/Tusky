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

package com.keylesspalace.tusky.core.text

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StringUtilsTest {
    @Test
    fun isLessThan() {
        val lessList = listOf(
            "abc" to "bcd",
            "ab" to "abc",
            "cb" to "abc",
            "1" to "2"
        )
        lessList.forEach { (l, r) -> assertTrue("$l < $r", l.isLessThan(r)) }
        val notLessList = lessList.map { (l, r) -> r to l } + listOf(
            "abc" to "abc"
        )
        notLessList.forEach { (l, r) -> assertFalse("not $l < $r", l.isLessThan(r)) }
    }

    @Test
    fun isLessThanOrEqual() {
        val lessList = listOf(
            "abc" to "bcd",
            "ab" to "abc",
            "cb" to "abc",
            "1" to "2",
            "abc" to "abc"
        )
        lessList.forEach { (l, r) -> assertTrue("$l < $r", l.isLessThanOrEqual(r)) }
        val notLessList = lessList.filterNot { (l, r) -> l == r }.map { (l, r) -> r to l }
        notLessList.forEach { (l, r) -> assertFalse("not $l < $r", l.isLessThanOrEqual(r)) }
    }
}
