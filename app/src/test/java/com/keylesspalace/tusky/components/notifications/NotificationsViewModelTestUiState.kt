package com.keylesspalace.tusky.components.notifications

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.entity.Notification
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
