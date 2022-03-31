@file:JvmName("StringUtils")

package com.keylesspalace.tusky.util

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
