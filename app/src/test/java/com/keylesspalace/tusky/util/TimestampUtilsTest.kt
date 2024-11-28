package com.keylesspalace.tusky.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [34])
@RunWith(AndroidJUnit4::class)
class TimestampUtilsTest {

    companion object {
        private lateinit var locale: Locale

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            locale = Locale.getDefault()
            Locale.setDefault(Locale.ENGLISH)
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            Locale.setDefault(locale)
        }
    }

    val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun `should return 'now' for small timespans`() {
        assertEquals("now", getRelativeTimeSpanString(context, 0, 300))
        assertEquals("now", getRelativeTimeSpanString(context, 300, 0))
        assertEquals("now", getRelativeTimeSpanString(context, 501, 0))
        assertEquals("now", getRelativeTimeSpanString(context, 0, 999))
    }

    @Test
    fun `should return 'in --' when then is after now`() {
        assertEquals("in 49s", getRelativeTimeSpanString(context, 49.seconds.inWholeMilliseconds, 0))
        assertEquals("in 34m", getRelativeTimeSpanString(context, 37.minutes.inWholeMilliseconds, 3.minutes.inWholeMilliseconds))
        assertEquals("in 7h", getRelativeTimeSpanString(context, 10.hours.inWholeMilliseconds, 3.hours.inWholeMilliseconds))
        assertEquals("in 10d", getRelativeTimeSpanString(context, 10.days.inWholeMilliseconds, 0))
        assertEquals("in 4y", getRelativeTimeSpanString(context, 800.days.inWholeMilliseconds + (4 * 365).days.inWholeMilliseconds, 800.days.inWholeMilliseconds))
    }

    @Test
    fun `should return correct timespans`() {
        assertEquals("49s", getRelativeTimeSpanString(context, 0, 49.seconds.inWholeMilliseconds))
        assertEquals("34m", getRelativeTimeSpanString(context, 3.minutes.inWholeMilliseconds, 37.minutes.inWholeMilliseconds))
        assertEquals("7h", getRelativeTimeSpanString(context, 3.hours.inWholeMilliseconds, 10.hours.inWholeMilliseconds))
        assertEquals("10d", getRelativeTimeSpanString(context, 0, 10.days.inWholeMilliseconds))
        assertEquals("4y", getRelativeTimeSpanString(context, 800.days.inWholeMilliseconds, 800.days.inWholeMilliseconds + (4 * 365).days.inWholeMilliseconds))
    }

    @Test
    fun `should return correct poll duration`() {
        assertEquals("1 second left", formatPollDuration(context, 1.seconds.inWholeMilliseconds, 0))
        assertEquals("49 seconds left", formatPollDuration(context, 49.seconds.inWholeMilliseconds, 0))
        assertEquals("1 minute left", formatPollDuration(context, 37.minutes.inWholeMilliseconds, 36.minutes.inWholeMilliseconds))
        assertEquals("34 minutes left", formatPollDuration(context, 37.minutes.inWholeMilliseconds, 3.minutes.inWholeMilliseconds))
        assertEquals("1 hour left", formatPollDuration(context, 10.hours.inWholeMilliseconds, 9.hours.inWholeMilliseconds))
        assertEquals("7 hours left", formatPollDuration(context, 10.hours.inWholeMilliseconds, 3.hours.inWholeMilliseconds))
        assertEquals("1 day left", formatPollDuration(context, 1.days.inWholeMilliseconds, 0))
        assertEquals("10 days left", formatPollDuration(context, 10.days.inWholeMilliseconds, 0))
        assertEquals("1460 days left", formatPollDuration(context, 800.days.inWholeMilliseconds + (4 * 365).days.inWholeMilliseconds, 800.days.inWholeMilliseconds))
    }
}
