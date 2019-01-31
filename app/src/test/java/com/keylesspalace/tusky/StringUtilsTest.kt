package com.keylesspalace.tusky

import com.keylesspalace.tusky.util.dec
import com.keylesspalace.tusky.util.isLessThan
import org.junit.Assert.*
import org.junit.Test

class StringUtilsTest {
    @Test
    fun isLessThan() {
        val lessList = listOf(
                "abc" to "bcd",
                "ab" to "abc",
                "cb" to "abc"
        )
        lessList.forEach { (l, r) -> assertTrue("$l < $r", l.isLessThan(r)) }
        val notLessList = lessList.map { (l, r) -> r to l } + listOf(
                "abc" to "abc"
        )
        notLessList.forEach { (l, r) -> assertFalse("not $l < $r", l.isLessThan(r)) }
    }

    @Test
    fun dec() {
        listOf(
                "123" to "122",
                "12B" to "12A",
                "120" to "11z",
                "100" to "zz",
                "0" to "",
                "" to ""
        ).forEach { (l, r) -> assertEquals("$l - 1 = $r", r, l.dec()) }
    }
}