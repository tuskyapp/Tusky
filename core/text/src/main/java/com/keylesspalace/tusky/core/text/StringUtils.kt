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

@file:JvmName("StringUtils")

package com.keylesspalace.tusky.core.text

import android.text.Spanned
import java.util.Random

private const val POSSIBLE_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"

fun randomAlphanumericString(count: Int): String {
    val chars = CharArray(count)
    val random = Random()
    for (i in 0 until count) {
        chars[i] = POSSIBLE_CHARS[random.nextInt(POSSIBLE_CHARS.length)]
    }
    return String(chars)
}

/**
 * A < B (strictly) by length and then by content.
 * Examples:
 * "abc" < "bcd"
 * "ab"  < "abc"
 * "cb"  < "abc"
 * not: "ab" < "ab"
 * not: "abc" > "cb"
 */
fun String.isLessThan(other: String): Boolean {
    return when {
        this.length < other.length -> true
        this.length > other.length -> false
        else -> this < other
    }
}

/**
 * A <= B (strictly) by length and then by content.
 * Examples:
 * "abc" <= "bcd"
 * "ab"  <= "abc"
 * "cb"  <= "abc"
 * "ab"  <= "ab"
 * not: "abc" > "cb"
 */
fun String.isLessThanOrEqual(other: String): Boolean {
    return this == other || isLessThan(other)
}

fun Spanned.trimTrailingWhitespace(): Spanned {
    var i = length
    do {
        i--
    } while (i >= 0 && get(i).isWhitespace())
    return subSequence(0, i + 1) as Spanned
}

/**
 * BidiFormatter.unicodeWrap is insufficient in some cases (see #1921)
 * So we force isolation manually
 * https://unicode.org/reports/tr9/#Explicit_Directional_Isolates
 */
fun CharSequence.unicodeWrap(): String {
    return "\u2068${this}\u2069"
}
