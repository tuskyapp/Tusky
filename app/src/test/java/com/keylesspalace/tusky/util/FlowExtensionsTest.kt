/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.util

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class FlowExtensionsTest {
    @Test
    fun `throttleFirst throttles first`() = runTest {
        flow {
            emit(1) // t = 0, emitted
            delay(90.milliseconds)
            emit(2) // throttled, t = 90
            delay(90.milliseconds)
            emit(3) // throttled, t == 180
            delay(1010.milliseconds)
            emit(4) // t = 1190, emitted
            delay(1010.milliseconds)
            emit(5) // t = 2200, emitted
        }
            .throttleFirst(1000.milliseconds, timeSource = testScheduler.timeSource)
            .test {
                advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(1)
                assertThat(awaitItem()).isEqualTo(4)
                assertThat(awaitItem()).isEqualTo(5)
                awaitComplete()
            }
    }
}
