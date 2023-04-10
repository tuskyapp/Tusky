package com.keylesspalace.tusky.util

import org.junit.Assert
import org.junit.Test
import kotlin.math.pow

class NumberUtilsTest {

    @Test
    fun zeroShouldBeFormattedAsZero() {
        val shortNumber = shortNumber(0)
        Assert.assertEquals("0", shortNumber)
    }

    @Test
    fun negativeValueShouldBeFormattedToNegativeValue() {
        val shortNumber = shortNumber(-1)
        Assert.assertEquals("-1", shortNumber)
    }

    @Test
    fun positiveValueShouldBeFormattedToPositiveValue() {
        val shortNumber = shortNumber(1)
        Assert.assertEquals("1", shortNumber)
    }

    @Test
    fun bigNumbersShouldBeShortened() {
        var shortNumber = 1L
        Assert.assertEquals("1", shortNumber(shortNumber))
        for (i in shortLetters.indices) {
            if (i == 0) {
                continue
            }
            shortNumber = 1000.0.pow(i.toDouble()).toLong()
            Assert.assertEquals("1.0" + shortLetters[i], shortNumber(shortNumber))
        }
    }

    @Test
    fun roundingForNegativeAndPositiveValuesShouldBeTheSame() {
        var value = 3492
        Assert.assertEquals("-3.5K", shortNumber(-value))
        Assert.assertEquals("3.5K", shortNumber(value))
        value = 1501
        Assert.assertEquals("-1.5K", shortNumber(-value))
        Assert.assertEquals("1.5K", shortNumber(value))
    }
}
