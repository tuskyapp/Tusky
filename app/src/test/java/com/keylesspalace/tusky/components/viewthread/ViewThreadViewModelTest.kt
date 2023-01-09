package com.keylesspalace.tusky.components.viewthread

import android.os.Looper.getMainLooper
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import at.connyduck.calladapter.networkresult.NetworkResult
import com.google.gson.Gson
import com.keylesspalace.tusky.appstore.BookmarkEvent
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.FavoriteEvent
import com.keylesspalace.tusky.appstore.ReblogEvent
import com.keylesspalace.tusky.components.timeline.mockStatus
import com.keylesspalace.tusky.components.timeline.mockStatusViewData
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.Converters
import com.keylesspalace.tusky.entity.StatusContext
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.usecase.TimelineCases
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
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
    private lateinit var eventHub: EventHub
    private lateinit var viewModel: ViewThreadViewModel
    private lateinit var db: AppDatabase

    private val threadId = "1234"

    /**
     * Execute each task synchronously.
     *
     * If you do not do this, and you have code like this under test:
     *
     * ```
     * fun someFunc() = viewModelScope.launch {
     *     _uiState.value = "initial value"
     *     // ...
     *     call_a_suspend_fun()
     *     // ...
     *     _uiState.value = "new value"
     * }
     * ```
     *
     * and a test like:
     *
     * ```
     * someFunc()
     * assertEquals("new value", viewModel.uiState.value)
     * ```
     *
     * The test will fail, because someFunc() yields at the `call_a_suspend_func()` point,
     * and control returns to the test before `_uiState.value` has been changed.
     */
    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        shadowOf(getMainLooper()).idle()

        api = mock()
        eventHub = EventHub()
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
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addTypeConverter(Converters(Gson()))
            .allowMainThreadQueries()
            .build()

        val gson = Gson()
        viewModel = ViewThreadViewModel(api, filterModel, timelineCases, eventHub, accountManager, db, gson)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `should emit status and context when both load`() {
        mockSuccessResponses()

        viewModel.loadThread(threadId)

        runBlocking {
            assertEquals(
                ThreadUiState.Success(
                    statusViewData = listOf(
                        mockStatusViewData(id = "1", spoilerText = "Test"),
                        mockStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test"),
                        mockStatusViewData(id = "3", inReplyToId = "2", inReplyToAccountId = "1", spoilerText = "Test")
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.REVEAL
                ),
                viewModel.uiState.first()
            )
        }
    }

    @Test
    fun `should emit status even if context fails to load`() {
        api.stub {
            onBlocking { status(threadId) } doReturn NetworkResult.success(mockStatus(id = "2", inReplyToId = "1", inReplyToAccountId = "1"))
            onBlocking { statusContext(threadId) } doReturn NetworkResult.failure(IOException())
        }

        viewModel.loadThread(threadId)

        runBlocking {
            assertEquals(
                ThreadUiState.Success(
                    statusViewData = listOf(
                        mockStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true)
                    ),
                    detailedStatusPosition = 0,
                    revealButton = RevealButtonState.NO_BUTTON,
                ),
                viewModel.uiState.first()
            )
        }
    }

    @Test
    fun `should emit error when status and context fail to load`() {
        api.stub {
            onBlocking { status(threadId) } doReturn NetworkResult.failure(IOException())
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
            onBlocking { status(threadId) } doReturn NetworkResult.failure(IOException())
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

    @Test
    fun `should update state when reveal button is toggled`() {
        mockSuccessResponses()

        viewModel.loadThread(threadId)
        viewModel.toggleRevealButton()

        runBlocking {
            assertEquals(
                ThreadUiState.Success(
                    statusViewData = listOf(
                        mockStatusViewData(id = "1", spoilerText = "Test", isExpanded = true),
                        mockStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test", isExpanded = true),
                        mockStatusViewData(id = "3", inReplyToId = "2", inReplyToAccountId = "1", spoilerText = "Test", isExpanded = true)
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.HIDE,
                ),
                viewModel.uiState.first()
            )
        }
    }

    @Test
    fun `should handle favorite event`() {
        mockSuccessResponses()

        viewModel.loadThread(threadId)

        eventHub.dispatch(FavoriteEvent(statusId = "1", false))

        runBlocking {
            assertEquals(
                ThreadUiState.Success(
                    statusViewData = listOf(
                        mockStatusViewData(id = "1", spoilerText = "Test", favourited = false),
                        mockStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test"),
                        mockStatusViewData(id = "3", inReplyToId = "2", inReplyToAccountId = "1", spoilerText = "Test")
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.REVEAL,
                ),
                viewModel.uiState.first()
            )
        }
    }

    @Test
    fun `should handle reblog event`() {
        mockSuccessResponses()

        viewModel.loadThread(threadId)

        eventHub.dispatch(ReblogEvent(statusId = "2", true))

        runBlocking {
            assertEquals(
                ThreadUiState.Success(
                    statusViewData = listOf(
                        mockStatusViewData(id = "1", spoilerText = "Test"),
                        mockStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test", reblogged = true),
                        mockStatusViewData(id = "3", inReplyToId = "2", inReplyToAccountId = "1", spoilerText = "Test")
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.REVEAL,
                ),
                viewModel.uiState.first()
            )
        }
    }

    @Test
    fun `should handle bookmark event`() {
        mockSuccessResponses()

        viewModel.loadThread(threadId)

        eventHub.dispatch(BookmarkEvent(statusId = "3", false))

        runBlocking {
            assertEquals(
                ThreadUiState.Success(
                    statusViewData = listOf(
                        mockStatusViewData(id = "1", spoilerText = "Test"),
                        mockStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test"),
                        mockStatusViewData(id = "3", inReplyToId = "2", inReplyToAccountId = "1", spoilerText = "Test", bookmarked = false)
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.REVEAL,
                ),
                viewModel.uiState.first()
            )
        }
    }

    @Test
    fun `should remove status`() {
        mockSuccessResponses()

        viewModel.loadThread(threadId)

        viewModel.removeStatus(mockStatusViewData(id = "3", inReplyToId = "2", inReplyToAccountId = "1", spoilerText = "Test"))

        runBlocking {
            assertEquals(
                ThreadUiState.Success(
                    statusViewData = listOf(
                        mockStatusViewData(id = "1", spoilerText = "Test"),
                        mockStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test")
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.REVEAL,
                ),
                viewModel.uiState.first()
            )
        }
    }

    @Test
    fun `should change status expanded state`() {
        mockSuccessResponses()

        viewModel.loadThread(threadId)

        viewModel.changeExpanded(
            true,
            mockStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test")
        )

        runBlocking {
            assertEquals(
                ThreadUiState.Success(
                    statusViewData = listOf(
                        mockStatusViewData(id = "1", spoilerText = "Test"),
                        mockStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test", isExpanded = true),
                        mockStatusViewData(id = "3", inReplyToId = "2", inReplyToAccountId = "1", spoilerText = "Test")
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.REVEAL,
                ),
                viewModel.uiState.first()
            )
        }
    }

    @Test
    fun `should change content collapsed state`() {
        mockSuccessResponses()

        viewModel.loadThread(threadId)

        viewModel.changeContentCollapsed(
            true,
            mockStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test")
        )

        runBlocking {
            assertEquals(
                ThreadUiState.Success(
                    statusViewData = listOf(
                        mockStatusViewData(id = "1", spoilerText = "Test"),
                        mockStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test", isCollapsed = true),
                        mockStatusViewData(id = "3", inReplyToId = "2", inReplyToAccountId = "1", spoilerText = "Test")
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.REVEAL,
                ),
                viewModel.uiState.first()
            )
        }
    }

    @Test
    fun `should change content showing state`() {
        mockSuccessResponses()

        viewModel.loadThread(threadId)

        viewModel.changeContentShowing(
            true,
            mockStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test")
        )

        runBlocking {
            assertEquals(
                ThreadUiState.Success(
                    statusViewData = listOf(
                        mockStatusViewData(id = "1", spoilerText = "Test"),
                        mockStatusViewData(id = "2", inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test", isShowingContent = true),
                        mockStatusViewData(id = "3", inReplyToId = "2", inReplyToAccountId = "1", spoilerText = "Test")
                    ),
                    detailedStatusPosition = 1,
                    revealButton = RevealButtonState.REVEAL,
                ),
                viewModel.uiState.first()
            )
        }
    }

    private fun mockSuccessResponses() {
        api.stub {
            onBlocking { status(threadId) } doReturn NetworkResult.success(mockStatus(id = "2", inReplyToId = "1", inReplyToAccountId = "1", spoilerText = "Test"))
            onBlocking { statusContext(threadId) } doReturn NetworkResult.success(
                StatusContext(
                    ancestors = listOf(mockStatus(id = "1", spoilerText = "Test")),
                    descendants = listOf(mockStatus(id = "3", inReplyToId = "2", inReplyToAccountId = "1", spoilerText = "Test"))
                )
            )
        }
    }
}
