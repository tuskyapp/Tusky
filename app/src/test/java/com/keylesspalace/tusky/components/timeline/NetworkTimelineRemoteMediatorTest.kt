package com.keylesspalace.tusky.components.timeline

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keylesspalace.tusky.components.timeline.viewmodel.NetworkTimelineRemoteMediator
import com.keylesspalace.tusky.components.timeline.viewmodel.NetworkTimelineViewModel
import com.keylesspalace.tusky.components.timeline.viewmodel.TimelineViewModel
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.viewdata.StatusViewData
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

@Config(sdk = [34])
@RunWith(AndroidJUnit4::class)
class NetworkTimelineRemoteMediatorTest {

    private val account = AccountEntity(
        id = 1,
        domain = "mastodon.example",
        accessToken = "token",
        clientId = "id",
        clientSecret = "secret",
        isActive = true
    )

    private val accountManager: AccountManager = mock {
        on { activeAccount } doReturn account
    }

    @Test
    @ExperimentalPagingApi
    fun `should return error when network call returns error code`() = runTest {
        val timelineViewModel: NetworkTimelineViewModel = mock {
            on { statusData } doReturn mutableListOf()
            onBlocking { fetchStatusesForKind(anyOrNull(), anyOrNull(), anyOrNull()) } doReturn Response.error(500, "".toResponseBody())
        }

        val remoteMediator = NetworkTimelineRemoteMediator(timelineViewModel)

        val result = remoteMediator.load(LoadType.REFRESH, state())

        assertTrue(result is RemoteMediator.MediatorResult.Error)
        assertTrue((result as RemoteMediator.MediatorResult.Error).throwable is HttpException)
        assertEquals(500, (result.throwable as HttpException).code())
    }

    @Test
    @ExperimentalPagingApi
    fun `should return error when network call fails`() = runTest {
        val timelineViewModel: NetworkTimelineViewModel = mock {
            on { accountManager } doReturn accountManager
            on { activeAccountFlow } doReturn MutableStateFlow(account)
            on { statusData } doReturn mutableListOf()
            onBlocking { fetchStatusesForKind(anyOrNull(), anyOrNull(), anyOrNull()) } doThrow IOException()
        }

        val remoteMediator = NetworkTimelineRemoteMediator(timelineViewModel)

        val result = remoteMediator.load(LoadType.REFRESH, state())

        assertTrue(result is RemoteMediator.MediatorResult.Error)
        assertTrue((result as RemoteMediator.MediatorResult.Error).throwable is IOException)
    }

    @Test
    @ExperimentalPagingApi
    fun `should do initial loading`() = runTest {
        val statuses: MutableList<StatusViewData> = mutableListOf()

        val timelineViewModel: NetworkTimelineViewModel = mock {
            on { accountManager } doReturn accountManager
            on { activeAccountFlow } doReturn MutableStateFlow(account)
            on { activeAccountFlow } doReturn MutableStateFlow(account)
            on { statusData } doReturn statuses
            on { nextKey } doReturn null
            onBlocking { fetchStatusesForKind(null, null, 20) } doReturn Response.success(
                listOf(
                    fakeStatus("7"),
                    fakeStatus("6"),
                    fakeStatus("5")
                ),
                Headers.headersOf(
                    "Link",
                    "<https://mastodon.example/api/v1/favourites?limit=20&max_id=4>; rel=\"next\", <https://mastodon.example/api/v1/favourites?limit=20&min_id=8>; rel=\"prev\""
                )
            )
        }

        val remoteMediator = NetworkTimelineRemoteMediator(timelineViewModel)

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = emptyList(),
                    prevKey = null,
                    nextKey = null
                )
            )
        )

        val result = remoteMediator.load(LoadType.REFRESH, state)

        val newStatusData = mutableListOf(
            fakeStatusViewData("7"),
            fakeStatusViewData("6"),
            fakeStatusViewData("5")
        )

        verify(timelineViewModel).nextKey = "4"
        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(newStatusData, statuses)
    }

    @Test
    @ExperimentalPagingApi
    fun `should not prepend statuses`() = runTest {
        val statuses: MutableList<StatusViewData> = mutableListOf(
            fakeStatusViewData("3"),
            fakeStatusViewData("2"),
            fakeStatusViewData("1")
        )

        val timelineViewModel: NetworkTimelineViewModel = mock {
            on { accountManager } doReturn accountManager
            on { activeAccountFlow } doReturn MutableStateFlow(account)
            on { statusData } doReturn statuses
            on { nextKey } doReturn "0"
            onBlocking { fetchStatusesForKind(null, null, 20) } doReturn Response.success(
                listOf(
                    fakeStatus("5"),
                    fakeStatus("4"),
                    fakeStatus("3")
                )
            )
        }

        val remoteMediator = NetworkTimelineRemoteMediator(timelineViewModel)

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = listOf(
                        fakeStatusViewData("3"),
                        fakeStatusViewData("2"),
                        fakeStatusViewData("1")
                    ),
                    prevKey = null,
                    nextKey = "0"
                )
            )
        )

        val result = remoteMediator.load(LoadType.REFRESH, state)

        val newStatusData = mutableListOf(
            fakeStatusViewData("5"),
            fakeStatusViewData("4"),
            fakeStatusViewData("3"),
            fakeStatusViewData("2"),
            fakeStatusViewData("1")
        )

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(newStatusData, statuses)
    }

    @Test
    @ExperimentalPagingApi
    fun `should refresh and insert placeholder`() = runTest {
        val statuses: MutableList<StatusViewData> = mutableListOf(
            fakeStatusViewData("3"),
            fakeStatusViewData("2"),
            fakeStatusViewData("1")
        )

        val timelineViewModel: NetworkTimelineViewModel = mock {
            on { accountManager } doReturn accountManager
            on { activeAccountFlow } doReturn MutableStateFlow(account)
            on { statusData } doReturn statuses
            on { nextKey } doReturn "0"
            onBlocking { fetchStatusesForKind(null, null, 20) } doReturn Response.success(
                listOf(
                    fakeStatus("10"),
                    fakeStatus("9"),
                    fakeStatus("7")
                )
            )
        }

        val remoteMediator = NetworkTimelineRemoteMediator(timelineViewModel)

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = listOf(
                        fakeStatusViewData("3"),
                        fakeStatusViewData("2"),
                        fakeStatusViewData("1")
                    ),
                    prevKey = null,
                    nextKey = "0"
                )
            )
        )

        val result = remoteMediator.load(LoadType.REFRESH, state)

        val newStatusData = mutableListOf(
            fakeStatusViewData("10"),
            fakeStatusViewData("9"),
            StatusViewData.LoadMore("7", false),
            fakeStatusViewData("3"),
            fakeStatusViewData("2"),
            fakeStatusViewData("1")
        )

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(newStatusData, statuses)
    }

    @Test
    @ExperimentalPagingApi
    fun `should refresh and not insert placeholders`() = runTest {
        val statuses: MutableList<StatusViewData> = mutableListOf(
            fakeStatusViewData("8"),
            fakeStatusViewData("7"),
            fakeStatusViewData("5")
        )

        val timelineViewModel: NetworkTimelineViewModel = mock {
            on { accountManager } doReturn accountManager
            on { activeAccountFlow } doReturn MutableStateFlow(account)
            on { statusData } doReturn statuses
            on { nextKey } doReturn "3"
            onBlocking { fetchStatusesForKind("3", null, 20) } doReturn Response.success(
                listOf(
                    fakeStatus("3"),
                    fakeStatus("2"),
                    fakeStatus("1")
                )
            )
        }

        val remoteMediator = NetworkTimelineRemoteMediator(timelineViewModel)

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = listOf(
                        fakeStatusViewData("8"),
                        fakeStatusViewData("7"),
                        fakeStatusViewData("5")
                    ),
                    prevKey = null,
                    nextKey = "3"
                )
            )
        )

        val result = remoteMediator.load(LoadType.APPEND, state)

        val newStatusData = mutableListOf(
            fakeStatusViewData("8"),
            fakeStatusViewData("7"),
            fakeStatusViewData("5"),
            fakeStatusViewData("3"),
            fakeStatusViewData("2"),
            fakeStatusViewData("1")
        )

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(newStatusData, statuses)
    }

    @Test
    @ExperimentalPagingApi
    fun `should append statuses`() = runTest {
        val statuses: MutableList<StatusViewData> = mutableListOf(
            fakeStatusViewData("8"),
            fakeStatusViewData("7"),
            fakeStatusViewData("5")
        )

        val timelineViewModel: NetworkTimelineViewModel = mock {
            on { accountManager } doReturn accountManager
            on { activeAccountFlow } doReturn MutableStateFlow(account)
            on { statusData } doReturn statuses
            on { nextKey } doReturn "3"
            onBlocking { fetchStatusesForKind("3", null, 20) } doReturn Response.success(
                listOf(
                    fakeStatus("3"),
                    fakeStatus("2"),
                    fakeStatus("1")
                ),
                Headers.headersOf(
                    "Link",
                    "<https://mastodon.example/api/v1/favourites?limit=20&max_id=0>; rel=\"next\", <https://mastodon.example/api/v1/favourites?limit=20&min_id=4>; rel=\"prev\""
                )
            )
        }

        val remoteMediator = NetworkTimelineRemoteMediator(timelineViewModel)

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = listOf(
                        fakeStatusViewData("8"),
                        fakeStatusViewData("7"),
                        fakeStatusViewData("5")
                    ),
                    prevKey = null,
                    nextKey = "3"
                )
            )
        )

        val result = remoteMediator.load(LoadType.APPEND, state)

        val newStatusData = mutableListOf(
            fakeStatusViewData("8"),
            fakeStatusViewData("7"),
            fakeStatusViewData("5"),
            fakeStatusViewData("3"),
            fakeStatusViewData("2"),
            fakeStatusViewData("1")
        )
        verify(timelineViewModel).nextKey = "0"
        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(newStatusData, statuses)
    }

    @Test
    @ExperimentalPagingApi
    fun `should not append statuses when pagination end has been reached`() = runTest {
        val statuses: MutableList<StatusViewData> = mutableListOf(
            fakeStatusViewData("8"),
            fakeStatusViewData("7"),
            fakeStatusViewData("5")
        )

        val timelineViewModel: NetworkTimelineViewModel = mock {
            on { accountManager } doReturn accountManager
            on { activeAccountFlow } doReturn MutableStateFlow(account)
            on { statusData } doReturn statuses
            on { nextKey } doReturn null
        }

        val remoteMediator = NetworkTimelineRemoteMediator(timelineViewModel)

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = listOf(
                        fakeStatusViewData("8"),
                        fakeStatusViewData("7"),
                        fakeStatusViewData("5")
                    ),
                    prevKey = null,
                    nextKey = null
                )
            )
        )

        val result = remoteMediator.load(LoadType.APPEND, state)

        val newStatusData = mutableListOf(
            fakeStatusViewData("8"),
            fakeStatusViewData("7"),
            fakeStatusViewData("5")
        )

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(newStatusData, statuses)
    }

    @Test
    @ExperimentalPagingApi
    fun `should not append duplicates for trending statuses`() = runTest {
        val statuses: MutableList<StatusViewData> = mutableListOf(
            fakeStatusViewData("5"),
            fakeStatusViewData("4"),
            fakeStatusViewData("3")
        )

        val timelineViewModel: NetworkTimelineViewModel = mock {
            on { accountManager } doReturn accountManager
            on { activeAccountFlow } doReturn MutableStateFlow(account)
            on { statusData } doReturn statuses
            on { nextKey } doReturn "3"
            on { kind } doReturn TimelineViewModel.Kind.PUBLIC_TRENDING_STATUSES
            onBlocking { fetchStatusesForKind("3", null, 20) } doReturn Response.success(
                listOf(
                    fakeStatus("3"),
                    fakeStatus("2"),
                    fakeStatus("1")
                ),
                Headers.headersOf(
                    "Link",
                    "<https://mastodon.example/api/v1/trends/statuses?offset=5>; rel=\"next\""
                )
            )
        }

        val remoteMediator = NetworkTimelineRemoteMediator(timelineViewModel)

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = statuses,
                    prevKey = null,
                    nextKey = "3"
                )
            )
        )

        val result = remoteMediator.load(LoadType.APPEND, state)

        val newStatusData = mutableListOf(
            fakeStatusViewData("5"),
            fakeStatusViewData("4"),
            fakeStatusViewData("3"),
            fakeStatusViewData("2"),
            fakeStatusViewData("1")
        )
        verify(timelineViewModel).nextKey = "5"
        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(newStatusData, statuses)
    }

    private fun state(pages: List<PagingSource.LoadResult.Page<String, StatusViewData>> = emptyList()) = PagingState(
        pages = pages,
        anchorPosition = null,
        config = PagingConfig(
            pageSize = 20
        ),
        leadingPlaceholderCount = 0
    )
}
