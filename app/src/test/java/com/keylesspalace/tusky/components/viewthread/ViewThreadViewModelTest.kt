package com.keylesspalace.tusky.components.viewthread

import android.os.Looper.getMainLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.connyduck.calladapter.networkresult.NetworkResult
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.components.timeline.mockStatus
import com.keylesspalace.tusky.components.timeline.mockStatusViewData
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.StatusContext
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.usecase.TimelineCases
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class ViewThreadViewModelTest {

    private lateinit var api: MastodonApi
    private lateinit var viewModel: ViewThreadViewModel

    private val threadId = "1234"

    @Before
    fun setup() {
        shadowOf(getMainLooper()).idle()

        api = mock()
        val eventHub = EventHub()
        val filterModel = FilterModel()
        val timelineCases = TimelineCases(api, eventHub)
        val accountManager: AccountManager = mock {
            on { activeAccount } doReturn AccountEntity(
                id = 1,
                domain = "mastodon.test",
                accessToken = "fakeToken",
                clientId = "fakeId",
                clientSecret = "fakeSecret",
                isActive = true
            )
        }
        viewModel = ViewThreadViewModel(api, filterModel, timelineCases, eventHub, accountManager)
    }

    @Test
    fun `should emit status and context when both load`() {
        api.stub {
            onBlocking { statusAsync(threadId) } doReturn NetworkResult.success(mockStatus(id = "2", inReplyToId = "1", inReplyToAccountId = "1"))
            onBlocking { statusContext(threadId) } doReturn NetworkResult.success(
                StatusContext(
                    ancestors = listOf(mockStatus(id = "1")),
                    descendants = listOf(mockStatus(id = "3", inReplyToId = "2", inReplyToAccountId = "1"))
                )
            )
        }

        viewModel.loadThread(threadId)

        runBlocking {
            assertEquals(
                ThreadUiState.Success(
                    statuses = listOf(
                        mockStatusViewData(id = "1"),
                        mockStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true),
                        mockStatusViewData(id = "3", inReplyToId = "2", inReplyToAccountId = "1")
                    ),
                    revealButton = RevealButtonState.NO_BUTTON,
                    refreshing = false
                ),
                viewModel.uiState.first()
            )
        }
    }

    @Test
    fun `should emit status even if context fails to load`() {
        api.stub {
            onBlocking { statusAsync(threadId) } doReturn NetworkResult.success(mockStatus(id = "2", inReplyToId = "1", inReplyToAccountId = "1"))
            onBlocking { statusContext(threadId) } doReturn NetworkResult.failure(IOException())
        }

        viewModel.loadThread(threadId)

        runBlocking {
            assertEquals(
                ThreadUiState.Success(
                    statuses = listOf(
                        mockStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true)
                    ),
                    revealButton = RevealButtonState.NO_BUTTON,
                    refreshing = false
                ),
                viewModel.uiState.first()
            )
        }
    }

    @Test
    fun `should emit error when status and context fail to load`() {
        api.stub {
            onBlocking { statusAsync(threadId) } doReturn NetworkResult.failure(IOException())
            onBlocking { statusContext(threadId) } doReturn NetworkResult.failure(IOException())
        }

        viewModel.loadThread(threadId)

        runBlocking {
            assertEquals(
                ThreadUiState.Error::class.java,
                viewModel.uiState.first().javaClass
            )
        }
    }

    @Test
    fun `should emit error when status fails to load`() {
        api.stub {
            onBlocking { statusAsync(threadId) } doReturn NetworkResult.failure(IOException())
            onBlocking { statusContext(threadId) } doReturn NetworkResult.success(
                StatusContext(
                    ancestors = listOf(mockStatus(id = "1")),
                    descendants = listOf(mockStatus(id = "3", inReplyToId = "2", inReplyToAccountId = "1"))
                )
            )
        }

        viewModel.loadThread(threadId)

        runBlocking {
            assertEquals(
                ThreadUiState.Error::class.java,
                viewModel.uiState.first().javaClass
            )
        }
    }


}