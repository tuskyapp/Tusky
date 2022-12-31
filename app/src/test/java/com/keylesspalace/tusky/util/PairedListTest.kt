package com.keylesspalace.tusky.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Tests for PairedList, with a mapper that multiples everything by 2.
 */
class PairedListTest {
    private lateinit var pairedList: PairedList<Int, Int>

    @Before
    fun beforeEachTest() {
        pairedList = PairedList { it * 2 }
        for (i in 0..10) {
            pairedList.add(i)
        }
    }

    @Test
    fun pairedCopy() {
        val copy = pairedList.pairedCopy
        for (i in 0..10) {
            assertEquals(i * 2, copy[i])
        }
    }

    @Test
    fun getPairedItem() {
        for (i in 0..10) {
            assertEquals(i * 2, pairedList.getPairedItem(i))
        }
    }

    @Test
    fun getPairedItemOrNull() {
        for (i in 0..10) {
            assertEquals(i * 2, pairedList.getPairedItem(i))
        }
        assertNull(pairedList.getPairedItemOrNull(11))
    }

    @Test
    fun setPairedItem() {
        pairedList.setPairedItem(2, 2)
        assertEquals(2, pairedList.getPairedItem(2))
    }

    @Test
    fun get() {
        for (i in 0..10) {
            assertEquals(i, pairedList[i])
        }
    }

    @Test
    fun set() {
        assertEquals(0, pairedList[0])
        pairedList[0] = 10
        assertEquals(10, pairedList[0])
        assertEquals(20, pairedList.getPairedItem(0))
    }

    @Test
    fun add() {
        pairedList.add(11)
        assertEquals(11, pairedList[11])
        assertEquals(22, pairedList.getPairedItem(11))
    }

    @Test
    fun addAtIndex() {
        pairedList.add(11, 11)
        assertEquals(11, pairedList[11])
        assertEquals(22, pairedList.getPairedItem(11))
    }

    @Test
    fun removeAt() {
        pairedList.removeAt(5)
        assertEquals(6, pairedList[5])
        assertEquals(12, pairedList.getPairedItem(5))
    }

    @Test
    fun size() {
        assertEquals(11, pairedList.size)
    }
}
