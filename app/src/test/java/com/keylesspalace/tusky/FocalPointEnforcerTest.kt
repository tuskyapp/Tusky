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

import com.keylesspalace.tusky.util.FocalPointEnforcer
import org.junit.Assert
import org.junit.Test

class FocalPointEnforcerTest {
    private val eps = 0.01f

    // focal[X|Y]ToCoordinate tests
    @Test
    fun positiveFocalXToCoordinateTest() {
        Assert.assertEquals(FocalPointEnforcer.focalXToCoordinate(0.4f), 0.7f, eps)
    }
    @Test
    fun negativeFocalXToCoordinateTest() {
        Assert.assertEquals(FocalPointEnforcer.focalXToCoordinate(-0.8f), 0.1f, eps)
    }
    @Test
    fun positiveFocalYToCoordinateTest() {
        Assert.assertEquals(FocalPointEnforcer.focalYToCoordinate(-0.2f), 0.6f, eps)
    }
    @Test
    fun negativeFocalYToCoordinateTest() {
        Assert.assertEquals(FocalPointEnforcer.focalYToCoordinate(0.0f), 0.5f, eps)
    }

    // isVerticalCrop tests
    @Test
    fun isVerticalCropTest() {
        Assert.assertTrue(FocalPointEnforcer.isVerticalCrop(2f, 1f,
                1f, 2f))
    }
    @Test
    fun isHorizontalCropTest() {
        Assert.assertFalse(FocalPointEnforcer.isVerticalCrop(1f, 2f,
                2f,1f))
    }
    @Test
    fun isPerfectFitTest() { // Doesn't matter what it returns, just check it doesn't crash
        FocalPointEnforcer.isVerticalCrop(3f, 1f,
                6f, 2f)
    }

    // calculateScaling tests
    @Test
    fun perfectFitScaleDownTest() {
        Assert.assertEquals(FocalPointEnforcer.calculateScaling(2f, 5f,
                5f, 12.5f), 0.4f, eps)
    }
    @Test
    fun perfectFitScaleUpTest() {
        Assert.assertEquals(FocalPointEnforcer.calculateScaling(2f, 5f,
                1f, 2.5f), 2f, eps)
    }
    @Test
    fun verticalCropScaleUpTest() {
        Assert.assertEquals(FocalPointEnforcer.calculateScaling(2f, 1f,
                1f, 2f), 2f, eps)
    }
    @Test
    fun verticalCropScaleDownTest() {
        Assert.assertEquals(FocalPointEnforcer.calculateScaling(4f, 3f,
                8f, 24f), 0.5f, eps)
    }
    @Test
    fun horizontalCropScaleUpTest() {
        Assert.assertEquals(FocalPointEnforcer.calculateScaling(1f, 2f,
                2f, 1f), 2f, eps)
    }
    @Test
    fun horizontalCropScaleDownTest() {
        Assert.assertEquals(FocalPointEnforcer.calculateScaling(3f, 4f,
                24f, 8f), 0.5f, eps)
    }

    // focalOffset tests
    @Test
    fun toLowFocalOffsetTest() {
        Assert.assertEquals(FocalPointEnforcer.focalOffset(2f, 8f, 1f, 0.05f),
                0f, eps)
    }
    @Test
    fun toHighFocalOffsetTest() {
        Assert.assertEquals(FocalPointEnforcer.focalOffset(2f, 4f, 2f,0.95f),
                -6f, eps)
    }
    @Test
    fun possibleFocalOffsetTest() {
        Assert.assertEquals(FocalPointEnforcer.focalOffset(2f, 4f, 2f,0.7f),
                -4.6f, eps)
    }
}