package com.keylesspalace.tusky.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class VersionUtilsTest(
        private val versionString: String,
        private val supportsScheduledToots: Boolean
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data() = listOf(
                arrayOf("2.0.0", false),
                arrayOf("2a9a0", false),
                arrayOf("1.0", false),
                arrayOf("error", false),
                arrayOf("", false),
                arrayOf("2.6.9", false),
                arrayOf("2.7.0", true),
                arrayOf("2.00008.0", true),
                arrayOf("2.7.2 (compatible; Pleroma 1.0.0-1168-ge18c7866-pleroma-dot-site)", true),
                arrayOf("3.0.1", true)
        )
    }

    @Test
    fun testVersionUtils() {
        assertEquals(VersionUtils(versionString).supportsScheduledToots(), supportsScheduledToots)
    }

}