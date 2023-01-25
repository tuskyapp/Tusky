package com.keylesspalace.tusky.components.notifications

import android.content.SharedPreferences
import android.os.Looper
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.usecase.TimelineCases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

class MainCoroutineRule(private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) {
        super.starting(description)
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        super.finished(description)
        Dispatchers.resetMain()
    }
}

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
open class NotificationsViewModelTestBase {
    protected lateinit var api: MastodonApi
    protected lateinit var notificationsRepository: NotificationsRepository
    protected lateinit var sharedPreferences: SharedPreferences
    protected lateinit var accountManager: AccountManager
    protected lateinit var timelineCases: TimelineCases
    private lateinit var eventHub: EventHub
    protected lateinit var viewModel: NotificationsViewModel

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    @Before
    fun setup() {
        shadowOf(Looper.getMainLooper()).idle()

        api = mock()
        notificationsRepository = mock()
        sharedPreferences = mock()
        accountManager = mock {
            on { activeAccount } doReturn AccountEntity(
                id = 1,
                domain = "mastodon.test",
                accessToken = "fakeToken",
                clientId = "fakeId",
                clientSecret = "fakeSecret",
                isActive = true,
                notificationsFilter = "['follow']"
            )
        }
        eventHub = EventHub()
        timelineCases = mock() //TimelineCases(api, eventHub)

        viewModel = NotificationsViewModel(
            notificationsRepository,
            sharedPreferences,
            accountManager,
            timelineCases,
            eventHub
        )
    }

    @Test
    fun `should have correct initial state`() = runTest {
        viewModel.uiState.test {
            assertThat(awaitItem())
                .isEqualTo(
                    UiState(
                        activeFilter = setOf(Notification.Type.FOLLOW),
                        showFilterOptions = false,
                        showFabWhileScrolling = true
                    )
                )
        }
    }
}
