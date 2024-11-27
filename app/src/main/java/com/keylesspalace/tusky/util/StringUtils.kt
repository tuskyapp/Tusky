@file:JvmName("StringUtils")

package com.keylesspalace.tusky.util

import android.text.Spanned
import java.util.regex.Pattern
import kotlin.random.Random

private const val POSSIBLE_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"

const val WORD_BREAK_EXPRESSION = """(^|$|[^\p{L}\p{N}_])"""
const val WORD_BREAK_FROM_SPACE_EXPRESSION = """(^|$|\s)"""
const val HASHTAG_EXPRESSION = "([\\w_]*[\\p{Alpha}_][\\w_]*)"
val hashtagPattern = Pattern.compile(HASHTAG_EXPRESSION, Pattern.CASE_INSENSITIVE or Pattern.MULTILINE)

fun randomAlphanumericString(count: Int): String {
    val chars = CharArray(count)
    for (i in 0 until count) {
        chars[i] = POSSIBLE_CHARS[Random.nextInt(POSSIBLE_CHARS.length)]
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
