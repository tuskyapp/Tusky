/* Copyright 2018 Jochem Raat <jchmrt@riseup.net>
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky

import com.keylesspalace.tusky.util.FocalPointUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class FocalPointUtilTest {
    private val eps = 0.01f

    // focal[X|Y]ToCoordinate tests
    @Test
    fun positiveFocalXToCoordinateTest() {
        assertEquals(FocalPointUtil.focalXToCoordinate(0.4f), 0.7f, eps)
    }
    @Test
    fun negativeFocalXToCoordinateTest() {
        assertEquals(FocalPointUtil.focalXToCoordinate(-0.8f), 0.1f, eps)
    }
    @Test
    fun positiveFocalYToCoordinateTest() {
        assertEquals(FocalPointUtil.focalYToCoordinate(-0.2f), 0.6f, eps)
    }
    @Test
    fun negativeFocalYToCoordinateTest() {
        assertEquals(FocalPointUtil.focalYToCoordinate(0.0f), 0.5f, eps)
    }

    // isVerticalCrop tests
    @Test
    fun isVerticalCropTest() {
        assertTrue(FocalPointUtil.isVerticalCrop(2f, 1f,
                1f, 2f))
    }
    @Test
    fun isHorizontalCropTest() {
        assertFalse(FocalPointUtil.isVerticalCrop(1f, 2f,
                2f,1f))
    }
    @Test
    fun isPerfectFitTest() { // Doesn't matter what it returns, just check it doesn't crash
        FocalPointUtil.isVerticalCrop(3f, 1f,
                6f, 2f)
    }

    // calculateScaling tests
    @Test
    fun perfectFitScaleDownTest() {
        assertEquals(FocalPointUtil.calculateScaling(2f, 5f,
                5f, 12.5f), 0.4f, eps)
    }
    @Test
    fun perfectFitScaleUpTest() {
        assertEquals(FocalPointUtil.calculateScaling(2f, 5f,
                1f, 2.5f), 2f, eps)
    }
    @Test
    fun verticalCropScaleUpTest() {
        assertEquals(FocalPointUtil.calculateScaling(2f, 1f,
                1f, 2f), 2f, eps)
    }
    @Test
    fun verticalCropScaleDownTest() {
        assertEquals(FocalPointUtil.calculateScaling(4f, 3f,
                8f, 24f), 0.5f, eps)
    }
    @Test
    fun horizontalCropScaleUpTest() {
        assertEquals(FocalPointUtil.calculateScaling(1f, 2f,
                2f, 1f), 2f, eps)
    }
    @Test
    fun horizontalCropScaleDownTest() {
        assertEquals(FocalPointUtil.calculateScaling(3f, 4f,
                24f, 8f), 0.5f, eps)
    }

    // focalOffset tests
    @Test
    fun toLowFocalOffsetTest() {
        assertEquals(FocalPointUtil.focalOffset(2f, 8f, 1f, 0.05f),
                0f, eps)
    }
    @Test
    fun toHighFocalOffsetTest() {
        assertEquals(FocalPointUtil.focalOffset(2f, 4f, 2f,0.95f),
                -6f, eps)
    }
    @Test
    fun possibleFocalOffsetTest() {
        assertEquals(FocalPointUtil.focalOffset(2f, 4f, 2f,0.7f),
                -4.6f, eps)
    }
}