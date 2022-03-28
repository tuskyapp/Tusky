package com.keylesspalace.tusky

import com.keylesspalace.tusky.util.isLessThan
import com.keylesspalace.tusky.util.isLessThanOrEqual
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StringUtilsTest {
    @Test
    fun isLessThan() {
        val lessList = listOf(
            "abc" to "bcd",
            "ab" to "abc",
            "cb" to "abc",
            "1" to "2"
        )
        lessList.forEach { (l, r) -> assertTrue("$l < $r", l.isLessThan(r)) }
        val notLessList = lessList.map { (l, r) -> r to l } + listOf(
            "abc" to "abc"
        )
        notLessList.forEach { (l, r) -> assertFalse("not $l < $r", l.isLessThan(r)) }
    }

    @Test
    fun isLessThanOrEqual() {
        val lessList = listOf(
            "abc" to "bcd",
            "ab" to "abc",
            "cb" to "abc",
            "1" to "2",
            "abc" to "abc",
        )
        lessList.forEach { (l, r) -> assertTrue("$l < $r", l.isLessThanOrEqual(r)) }
        val notLessList = lessList.filterNot { (l, r) -> l == r }.map { (l, r) -> r to l }
        notLessList.forEach { (l, r) -> assertFalse("not $l < $r", l.isLessThanOrEqual(r)) }
    }
}
