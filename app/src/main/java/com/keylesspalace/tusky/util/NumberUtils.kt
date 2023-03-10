package com.keylesspalace.tusky.util

import java.text.DecimalFormat
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

fun shortNumber(number: Number): String {
    val array = arrayOf(' ', 'K', 'M', 'B', 'T', 'P', 'E')
    val value = floor(log10(number.toDouble())).toInt()
    val base = value / 3
    if (value >= 3 && base < array.size) {
        return DecimalFormat("#0.0").format(number.toDouble() / 10.0.pow((base * 3).toDouble())) + array[base]
    } else {
        return DecimalFormat("#,##0").format(number)
    }
}
