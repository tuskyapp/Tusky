package com.keylesspalace.tusky.util

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.math.pow

@RunWith(Parameterized::class)
class NumberUtilsTest(private val input: Long, private val want: String) {
    companion object {
        @Parameterized.Parameters(name = "formatNumber_{0}")
        @JvmStatic
        fun data(): Iterable<Any> {
            return listOf(
                arrayOf(0, "0"),
                arrayOf(1, "1"),
                arrayOf(-1, "-1"),
                arrayOf(999, "999"),
                arrayOf(1000, "1.0K"),
                arrayOf(1500, "1.5K"),
                arrayOf(-1500, "-1.5K"),
                arrayOf(1000.0.pow(2).toLong(), "1.0M"),
                arrayOf(1000.0.pow(3).toLong(), "1.0G"),
                arrayOf(1000.0.pow(4).toLong(), "1.0T"),
                arrayOf(1000.0.pow(5).toLong(), "1.0P"),
                arrayOf(1000.0.pow(6).toLong(), "1.0E"),
                arrayOf(3, "3"),
                arrayOf(35, "35"),
                arrayOf(350, "350"),
                arrayOf(3500, "3.5K"),
                arrayOf(-3500, "-3.5K"),
                arrayOf(3500 * 1000, "3.5M"),
                arrayOf(3500 * 1000.0.pow(2).toLong(), "3.5G"),
                arrayOf(3500 * 1000.0.pow(3).toLong(), "3.5T"),
                arrayOf(3500 * 1000.0.pow(4).toLong(), "3.5P"),
                arrayOf(3500 * 1000.0.pow(5).toLong(), "3.5E")
            )
        }
    }

    @Test
    fun test() {
        Assert.assertEquals(want, formatNumber(input, 1000))
    }
}
