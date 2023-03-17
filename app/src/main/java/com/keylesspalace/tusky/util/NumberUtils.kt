@file:JvmName("NumberUtils")

package com.keylesspalace.tusky.util

import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sign

val shortLetters = arrayOf(' ', 'K', 'M', 'B', 'T', 'P', 'E')

fun shortNumber(number: Number): String {
    val numberAsDouble = number.toDouble()
    val nonNegativeValue = abs(numberAsDouble)
    var sign = ""
    if (numberAsDouble.sign < 0) { sign = "-" }
    val value = floor(log10(nonNegativeValue)).toInt()
    val base = value / 3
    if (value >= 3 && base < shortLetters.size) {
        return DecimalFormat("$sign#0.0").format(nonNegativeValue / 10.0.pow((base * 3).toDouble())) + shortLetters[base]
    } else {
        return DecimalFormat("$sign#,##0").format(nonNegativeValue)
    }
}
