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

package com.keylesspalace.tusky.components.notifications

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

/**
 * Verify that [ClearNotifications] is handled correctly on receipt:
 *
 * - Is the correct [UiSuccess] or [UiError] value emitted?
 * - Are the correct [NotificationsRepository] functions called, with the correct arguments?
 *   This is only tested in the success case; if it passed there it must also
 *   have passed in the error case.
 */
class NotificationsViewModelTestClearNotifications : NotificationsViewModelTestBase() {
    @Test
    fun `clearing notifications succeeds && invalidate the repository`() = runTest {
        // Given
        notificationsRepository.stub { onBlocking { clearNotifications() } doReturn emptySuccess }

        // When
        viewModel.accept(FallibleUiAction.ClearNotifications)

        // Then
        verify(notificationsRepository).clearNotifications()
        verify(notificationsRepository).invalidate()
    }

    @Test
    fun `clearing notifications fails && emits UiError`() = runTest {
        // Given
        notificationsRepository.stub { onBlocking { clearNotifications() } doReturn emptyError }

        viewModel.uiError.test {
            // When
            viewModel.accept(FallibleUiAction.ClearNotifications)

            // Then
            assertThat(awaitItem()).isInstanceOf(UiError::class.java)
        }
    }
}
