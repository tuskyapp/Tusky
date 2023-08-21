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
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.entity.Notification
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify

/**
 * Verify that [ApplyFilter] is handled correctly on receipt:
 *
 * - Is the [UiState] updated correctly?
 * - Are the correct [AccountManager] functions called, with the correct arguments?
 */
class NotificationsViewModelTestFilter : NotificationsViewModelTestBase() {

    @Test
    fun `should load initial filter from active account`() = runTest {
        viewModel.uiState.test {
            assertThat(awaitItem().activeFilter)
                .containsExactlyElementsIn(setOf(Notification.Type.FOLLOW))
        }
    }

    @Test
    fun `should save filter to active account && update state`() = runTest {
        viewModel.uiState.test {
            // When
            viewModel.accept(InfallibleUiAction.ApplyFilter(setOf(Notification.Type.REBLOG)))

            // Then
            // - filter saved to active account
            argumentCaptor<AccountEntity>().apply {
                verify(accountManager).saveAccount(capture())
                assertThat(this.lastValue.notificationsFilter)
                    .isEqualTo("[\"reblog\"]")
            }

            // - filter updated in uiState
            assertThat(expectMostRecentItem().activeFilter)
                .containsExactlyElementsIn(setOf(Notification.Type.REBLOG))
        }
    }
}
