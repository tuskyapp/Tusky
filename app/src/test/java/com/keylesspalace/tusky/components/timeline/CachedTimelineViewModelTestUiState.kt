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

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.components.timeline.viewmodel.UiState
import com.keylesspalace.tusky.settings.PrefKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Verify that [UiState] is handled correctly.
 *
 * - Is the initial value taken from values in sharedPreferences and account?
 * - Is the correct update emitted when a relevant preference changes?
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CachedTimelineViewModelTestUiState : CachedTimelineViewModelTestBase() {

    private val initialUiState = UiState(
        showFabWhileScrolling = true,
        showMediaPreview = true
    )

    @Test
    fun `should load initial UI state`() = runTest {
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
    fun `showMediaPreview depends on MEDIA_PREVIEW_ENABLED preference`() = runTest {
        // Prior
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().showMediaPreview).isTrue()
        }

        // Given (nothing to do here, set up is in base class)

        // When
        accountPreferenceDataStore.putBoolean(PrefKeys.MEDIA_PREVIEW_ENABLED, false)

        // Then
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().showMediaPreview).isFalse()
        }
    }
}
