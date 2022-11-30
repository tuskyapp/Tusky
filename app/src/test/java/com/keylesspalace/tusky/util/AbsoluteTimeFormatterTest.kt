package com.keylesspalace.tusky.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.Date
import java.util.TimeZone

class AbsoluteTimeFormatterTest {

    private val formatter24 = AbsoluteTimeFormatter(TimeZone.getTimeZone("UTC"), true)
    private val formatter12 = AbsoluteTimeFormatter(TimeZone.getTimeZone("UTC"), false)
    private val now = Date.from(Instant.parse("2022-04-11T00:00:00.00Z"))

    @Test
    fun `null handling`() {
        assertEquals("??", formatter24.format(null, true, now))
        assertEquals("??", formatter24.format(null, false, now))
    }

    @Test
    fun `same day formatting`() {
        val tenTen = Date.from(Instant.parse("2022-04-11T10:10:00.00Z"))
        assertEquals("10:10", formatter24.format(tenTen, true, now))
        assertEquals("10:10", formatter24.format(tenTen, false, now))
        val pmTen = Date.from(Instant.parse("2022-04-11T22:00:00.00Z"))
        assertEquals("10:00 PM", formatter12.format(pmTen, true, now))
        assertEquals("10:00 PM", formatter12.format(pmTen, false, now))
    }

    @Test
    fun `same year formatting`() {
        val nextDay = Date.from(Instant.parse("2022-04-12T00:10:00.00Z"))
        assertEquals("12 Apr, 00:10", formatter24.format(nextDay, true, now))
        assertEquals("12 Apr, 00:10", formatter24.format(nextDay, false, now))
        assertEquals("12 Apr, 12:10 AM", formatter12.format(nextDay, true, now))
        assertEquals("12 Apr, 12:10 AM", formatter12.format(nextDay, false, now))
        val endOfYear = Date.from(Instant.parse("2022-12-31T23:59:00.00Z"))
        assertEquals("31 Dec, 23:59", formatter24.format(endOfYear, true, now))
        assertEquals("31 Dec, 23:59", formatter24.format(endOfYear, false, now))
        assertEquals("31 Dec, 11:59 PM", formatter12.format(endOfYear, true, now))
        assertEquals("31 Dec, 11:59 PM", formatter12.format(endOfYear, false, now))
    }

    @Test
    fun `other year formatting`() {
        val firstDayNextYear = Date.from(Instant.parse("2023-01-01T00:00:00.00Z"))
        assertEquals("2023-01-01", formatter24.format(firstDayNextYear, true, now))
        assertEquals("2023-01-01 00:00", formatter24.format(firstDayNextYear, false, now))
        assertEquals("2023-01-01 12:00 AM", formatter12.format(firstDayNextYear, false, now))
        val inTenYears = Date.from(Instant.parse("2032-04-11T10:10:00.00Z"))
        assertEquals("2032-04-11", formatter24.format(inTenYears, true, now))
        assertEquals("2032-04-11 10:10", formatter24.format(inTenYears, false, now))
        assertEquals("2032-04-11 10:10 AM", formatter12.format(inTenYears, false, now))
    }
}
