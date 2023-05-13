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

package com.keylesspalace.tusky.components.timeline

import com.google.common.truth.Truth.assertThat
import com.keylesspalace.tusky.components.timeline.viewmodel.InfallibleUiAction
import com.keylesspalace.tusky.db.AccountEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkTimelineViewModelTestVisibleId : NetworkTimelineViewModelTestBase() {

    @Test
    fun `should not save status ID to active account`() = runTest {
        argumentCaptor<AccountEntity>().apply {
            // Given
            assertThat(accountManager.activeAccount?.lastVisibleHomeTimelineStatusId)
                .isNull()
            assertThat(viewModel.timelineKind)
                .isNotEqualTo(TimelineKind.Home)

            // When
            viewModel.accept(InfallibleUiAction.SaveVisibleId("1234"))

            // Then
            // As a non-Home timline this should *not* save the account, and
            // the last visible property should *not* have changed.
            verify(accountManager, never()).saveAccount(capture())
        }
    }
}
