package com.keylesspalace.tusky.util

import java.text.DecimalFormat

fun shortNumber(number: Number): String {
    val array = arrayOf(' ', 'k', 'M', 'B', 'T', 'P', 'E')
    val value = Math.floor(Math.log10(number.toDouble())).toInt()
    val base = value / 3
    if (value >= 3 && base < array.size) {
        return DecimalFormat("#0.0").format(number.toDouble() / Math.pow(10.0, (base * 3).toDouble())) + array[base]
    } else {
        return DecimalFormat("#,##0").format(number)
    }
}
