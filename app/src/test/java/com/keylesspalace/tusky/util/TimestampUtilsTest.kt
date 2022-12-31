package com.keylesspalace.tusky.util

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keylesspalace.tusky.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config

private const val STATUS_CREATED_AT_NOW = "test"

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class TimestampUtilsTest {
    private val ctx: Context = mock {
        on { getString(R.string.status_created_at_now) } doReturn STATUS_CREATED_AT_NOW
    }

    @Test
    fun shouldShowNowForSmallTimeSpans() {
        assertEquals(STATUS_CREATED_AT_NOW, getRelativeTimeSpanString(ctx, 0, 300))
        assertEquals(STATUS_CREATED_AT_NOW, getRelativeTimeSpanString(ctx, 300, 0))
        assertEquals(STATUS_CREATED_AT_NOW, getRelativeTimeSpanString(ctx, 501, 0))
        assertEquals(STATUS_CREATED_AT_NOW, getRelativeTimeSpanString(ctx, 0, 999))
    }
}
