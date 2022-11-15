package com.keylesspalace.tusky.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.Date
import java.util.TimeZone

class AbsoluteTimeFormatterTest {

    private val formatter = AbsoluteTimeFormatter(TimeZone.getTimeZone("UTC"))
    private val now = Date.from(Instant.parse("2022-04-11T00:00:00.00Z"))

    @Test
    fun `null handling`() {
        assertEquals("??", formatter.format(null, true, now))
        assertEquals("??", formatter.format(null, false, now))
    }

    @Test
    fun `same day formatting`() {
        val tenTen = Date.from(Instant.parse("2022-04-11T10:10:00.00Z"))
        assertEquals("10:10", formatter.format(tenTen, true, now))
        assertEquals("10:10", formatter.format(tenTen, false, now))
    }

    @Test
    fun `same year formatting`() {
        val nextDay = Date.from(Instant.parse("2022-04-12T00:10:00.00Z"))
        assertEquals("12 Apr, 00:10", formatter.format(nextDay, true, now))
        assertEquals("12 Apr, 00:10", formatter.format(nextDay, false, now))
        val endOfYear = Date.from(Instant.parse("2022-12-31T23:59:00.00Z"))
        assertEquals("31 Dec, 23:59", formatter.format(endOfYear, true, now))
        assertEquals("31 Dec, 23:59", formatter.format(endOfYear, false, now))
    }

    @Test
    fun `other year formatting`() {
        val firstDayNextYear = Date.from(Instant.parse("2023-01-01T00:00:00.00Z"))
        assertEquals("2023-01-01", formatter.format(firstDayNextYear, true, now))
        assertEquals("2023-01-01 00:00", formatter.format(firstDayNextYear, false, now))
        val inTenYears = Date.from(Instant.parse("2032-04-11T10:10:00.00Z"))
        assertEquals("2032-04-11", formatter.format(inTenYears, true, now))
        assertEquals("2032-04-11 10:10", formatter.format(inTenYears, false, now))
    }
}
