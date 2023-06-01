@file:JvmName("NumberUtils")

package com.keylesspalace.tusky.util

import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

private val numberFormatter: NumberFormat = NumberFormat.getInstance()
private val ln_1k = ln(1000.0)

/**
 * Format numbers according to the current locale. Numbers < min have
 * separators (',', '.', etc) inserted according to the locale.
 *
 * Numbers >= min are scaled down to that by multiples of 1,000, and
 * a suffix appropriate to the scaling is appended.
 */
fun formatNumber(num: Long, min: Int = 100000): String {
    val absNum = abs(num)
    if (absNum < min) return numberFormatter.format(num)

    val exp = (ln(absNum.toDouble()) / ln_1k).toInt()

    // Suffixes here are locale-agnostic
    return String.format("%.1f%c", num / 1000.0.pow(exp.toDouble()), "KMGTPE"[exp - 1])
}
