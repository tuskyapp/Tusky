package com.keylesspalace.tusky

import com.keylesspalace.tusky.util.dec
import com.keylesspalace.tusky.util.inc
import com.keylesspalace.tusky.util.isLessThan
import org.junit.Assert.assertEquals
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
    fun inc() {
        listOf(
            "10786565059022968z" to "107865650590229690",
            "122" to "123",
            "12A" to "12B",
            "11z" to "120",
            "0zz" to "100",
            "zz" to "000",
            "4zzbz" to "4zzc0",
            "" to "0",
            "1" to "2",
            "0" to "1",
            "AGdxwSQqT3pW4xrLJA" to "AGdxwSQqT3pW4xrLJB",
            "AGdfqi1HnlBFVl0tkz" to "AGdfqi1HnlBFVl0tl0"
        ).forEach { (l, r) -> assertEquals("$l + 1 = $r", r, l.inc()) }
    }

    @Test
    fun dec() {
        listOf(
            "" to "",
            "107865650590229690" to "10786565059022968z",
            "123" to "122",
            "12B" to "12A",
            "120" to "11z",
            "100" to "0zz",
            "000" to "zz",
            "4zzc0" to "4zzbz",
            "0" to "",
            "2" to "1",
            "1" to "0",
            "AGdxwSQqT3pW4xrLJB" to "AGdxwSQqT3pW4xrLJA",
            "AGdfqi1HnlBFVl0tl0" to "AGdfqi1HnlBFVl0tkz"
        ).forEach { (l, r) -> assertEquals("$l - 1 = $r", r, l.dec()) }
    }
}
