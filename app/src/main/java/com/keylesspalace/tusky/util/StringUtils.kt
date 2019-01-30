@file:JvmName("StringUtils")

package com.keylesspalace.tusky.util

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

// We sort statuses by ID. Something we need to invent some ID for placeholder.
// Not sure if inc()/dec() should be made `operator` or not

/**
 * "Increment" string so that during sorting it's bigger than [this].
 */
fun String.inc(): String {
    // We assume that we will stay in the safe range for now
    val builder = this.toCharArray()
    builder.last().inc()
    return String(builder)
}


/**
 * "Decrement" string so that during sorting it's smaller than [this].
 */
fun String.dec(): String {
    val builder = this.toCharArray()
    var i = builder.lastIndex
    while (i > 0) {
        if (builder[i] > '0') {
            builder[i] = builder[i].dec()
            break
        } else {
            builder[i] = 'z'
        }
        i--
    }
    // All characters were '0'
    if (i == 0 && this.isNotEmpty()) {
        // Remove one character
        return String(builder.copyOfRange(1, builder.size))
    }

    return String(builder)
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
