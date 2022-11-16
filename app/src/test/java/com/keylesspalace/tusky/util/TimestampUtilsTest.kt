package com.keylesspalace.tusky.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.junit.Assert.assertEquals

private const val S_IN_MS = 1000L

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class TimestampUtilsTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun shouldShowNowForSmallTimeSpans() {
        assertEquals("now", TimestampUtils.getRelativeTimeSpanString(ctx, 0, 300))
        assertEquals("now", TimestampUtils.getRelativeTimeSpanString(ctx, 300, 0))
    }

    @Test
    fun shouldShowSecondsForLTOneMinute() {
        assertEquals("1s", TimestampUtils.getRelativeTimeSpanString(ctx, 0, 1 * S_IN_MS))
        assertEquals("54s", TimestampUtils.getRelativeTimeSpanString(ctx, 0, 54 * S_IN_MS))
    }
}