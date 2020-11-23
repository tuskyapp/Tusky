package com.keylesspalace.tusky.util

import org.junit.Assert.*
import org.junit.Test

class EmojiCompatFontTest {

    @Test
    fun testCompareVersions() {

        assertEquals(
                -1,
                EmojiCompatFont.compareVersions(
                        listOf(0),
                        listOf(1, 2, 3)
                )
        )
        assertEquals(
                1,
                EmojiCompatFont.compareVersions(
                        listOf(1, 2, 3),
                        listOf(0, 0, 0)
                )
        )
        assertEquals(
                -1,
                EmojiCompatFont.compareVersions(
                        listOf(1, 0, 1),
                        listOf(1, 1, 0)
                )
        )
        assertEquals(
                0,
                EmojiCompatFont.compareVersions(
                        listOf(4, 5, 6),
                        listOf(4, 5, 6)
                )
        )
        assertEquals(
                0,
                EmojiCompatFont.compareVersions(
                        listOf(0, 0),
                        listOf(0)
                )
        )
    }
}