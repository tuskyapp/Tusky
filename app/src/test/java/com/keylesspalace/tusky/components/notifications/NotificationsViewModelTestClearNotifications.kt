package com.keylesspalace.tusky.components.notifications

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTestClearNotifications : NotificationsViewModelTestBase() {
    @Test
    fun `clearing notifications succeeds && invalidate the repository`() = runTest {
        // Given
        notificationsRepository.stub { on { clearNotifications() } doReturn emptySuccess }

        // When
        viewModel.accept(FallibleUiAction.ClearNotifications)

        // Then
        verify(notificationsRepository).clearNotifications()
        verify(notificationsRepository).invalidate()
    }

    @Test
    fun `clearing notifications fails && emits UiError`() = runTest {
        // Given
        notificationsRepository.stub { on { clearNotifications() } doReturn emptyError }

        viewModel.uiError.test {
            // When
            viewModel.accept(FallibleUiAction.ClearNotifications)

            // Then
            assertThat(awaitItem()).isInstanceOf(UiError::class.java)
        }
    }
}
