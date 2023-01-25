package com.keylesspalace.tusky.components.notifications

import com.google.common.truth.Truth.assertThat
import com.keylesspalace.tusky.db.AccountEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTestVisibleId : NotificationsViewModelTestBase() {

    @Test
    fun `should save notification ID to active account`() = runTest {
        argumentCaptor<AccountEntity>().apply {
            // When
            viewModel.accept(InfallibleUiAction.SaveVisibleId("1234"))

            // Then
            verify(accountManager).saveAccount(capture())
            assertThat(this.lastValue.lastNotificationId)
                .isEqualTo("1234")
        }
    }
}
