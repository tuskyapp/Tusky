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
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.settings.PrefKeys
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Verify that [UiState] is handled correctly.
 *
 * - Is the initial value taken from values in sharedPreferences and account?
 * - Is the correct update emitted when a relevant preference changes?
 */
class NotificationsViewModelTestUiState : NotificationsViewModelTestBase() {

    private val initialUiState = UiState(
        activeFilter = setOf(Notification.Type.FOLLOW),
        showFilterOptions = true,
        showFabWhileScrolling = true
    )

    @Test
    fun `should load initial filter from active account`() = runTest {
        viewModel.uiState.test {
            assertThat(expectMostRecentItem()).isEqualTo(initialUiState)
        }
    }

    @Test
    fun `showFabWhileScrolling depends on FAB_HIDE preference`() = runTest {
        // Prior
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().showFabWhileScrolling).isTrue()
        }

        // Given
        sharedPreferencesMap[PrefKeys.FAB_HIDE] = true

        // When
        eventHub.dispatch(PreferenceChangedEvent(PrefKeys.FAB_HIDE))

        // Then
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().showFabWhileScrolling).isFalse()
        }
    }

    @Test
    fun `showFilterOptions depends on SHOW_NOTIFICATIONS_FILTER preference`() = runTest {
        // Prior
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().showFilterOptions).isTrue()
        }

        // Given
        sharedPreferencesMap[PrefKeys.SHOW_NOTIFICATIONS_FILTER] = false

        // When
        eventHub.dispatch(PreferenceChangedEvent(PrefKeys.SHOW_NOTIFICATIONS_FILTER))

        // Then
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().showFilterOptions).isFalse()
        }
    }
}
