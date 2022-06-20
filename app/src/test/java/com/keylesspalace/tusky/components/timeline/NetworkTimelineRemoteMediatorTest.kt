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
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.viewdata.StatusViewData
import kotlinx.coroutines.runBlocking
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
import java.io.IOException

@Config(sdk = [29])
@RunWith(AndroidJUnit4::class)
class NetworkTimelineRemoteMediatorTest {

    private val accountManager: AccountManager = mock {
        on { activeAccount } doReturn AccountEntity(
            id = 1,
            domain = "mastodon.example",
            accessToken = "token",
            clientId = "id",
            clientSecret = "secret",
            isActive = true
        )
    }

    @Test
    @ExperimentalPagingApi
    fun `should return error when network call returns error code`() {

        val timelineViewModel: NetworkTimelineViewModel = mock {
            on { statusData } doReturn mutableListOf()
            onBlocking { fetchStatusesForKind(anyOrNull(), anyOrNull(), anyOrNull()) } doReturn Response.error(500, "".toResponseBody())
        }

        val remoteMediator = NetworkTimelineRemoteMediator(accountManager, timelineViewModel)

        val result = runBlocking { remoteMediator.load(LoadType.REFRESH, state()) }

        assertTrue(result is RemoteMediator.MediatorResult.Error)
        assertTrue((result as RemoteMediator.MediatorResult.Error).throwable is HttpException)
        assertEquals(500, (result.throwable as HttpException).code())
    }

    @Test
    @ExperimentalPagingApi
    fun `should return error when network call fails`() {

        val timelineViewModel: NetworkTimelineViewModel = mock {
            on { statusData } doReturn mutableListOf()
            onBlocking { fetchStatusesForKind(anyOrNull(), anyOrNull(), anyOrNull()) } doThrow IOException()
        }

        val remoteMediator = NetworkTimelineRemoteMediator(accountManager, timelineViewModel)

        val result = runBlocking { remoteMediator.load(LoadType.REFRESH, state()) }

        assertTrue(result is RemoteMediator.MediatorResult.Error)
        assertTrue((result as RemoteMediator.MediatorResult.Error).throwable is IOException)
    }

    @Test
    @ExperimentalPagingApi
    fun `should do initial loading`() {
        val statuses: MutableList<StatusViewData> = mutableListOf()

        val timelineViewModel: NetworkTimelineViewModel = mock {
            on { statusData } doReturn statuses
            on { nextKey } doReturn null
            onBlocking { fetchStatusesForKind(null, null, 20) } doReturn Response.success(
                listOf(
                    mockStatus("7"),
                    mockStatus("6"),
                    mockStatus("5")
                ),
                Headers.headersOf(
                    "Link", "<https://mastodon.example/api/v1/favourites?limit=20&max_id=4>; rel=\"next\", <https://mastodon.example/api/v1/favourites?limit=20&min_id=8>; rel=\"prev\""
                )
            )
        }

        val remoteMediator = NetworkTimelineRemoteMediator(accountManager, timelineViewModel)

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = emptyList(),
                    prevKey = null,
                    nextKey = null
                )
            )
        )

        val result = runBlocking { remoteMediator.load(LoadType.REFRESH, state) }

        val newStatusData = mutableListOf(
            mockStatusViewData("7"),
            mockStatusViewData("6"),
            mockStatusViewData("5"),
        )

        verify(timelineViewModel).nextKey = "4"
        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(newStatusData, statuses)
    }

    @Test
    @ExperimentalPagingApi
    fun `should not prepend statuses`() {
        val statuses: MutableList<StatusViewData> = mutableListOf(
            mockStatusViewData("3"),
            mockStatusViewData("2"),
            mockStatusViewData("1"),
        )

        val timelineViewModel: NetworkTimelineViewModel = mock {
            on { statusData } doReturn statuses
            on { nextKey } doReturn "0"
            onBlocking { fetchStatusesForKind(null, null, 20) } doReturn Response.success(
                listOf(
                    mockStatus("5"),
                    mockStatus("4"),
                    mockStatus("3")
                )
            )
        }

        val remoteMediator = NetworkTimelineRemoteMediator(accountManager, timelineViewModel)

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = listOf(
                        mockStatusViewData("3"),
                        mockStatusViewData("2"),
                        mockStatusViewData("1"),
                    ),
                    prevKey = null,
                    nextKey = "0"
                )
            )
        )

        val result = runBlocking { remoteMediator.load(LoadType.REFRESH, state) }

        val newStatusData = mutableListOf(
            mockStatusViewData("5"),
            mockStatusViewData("4"),
            mockStatusViewData("3"),
            mockStatusViewData("2"),
            mockStatusViewData("1"),
        )

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(newStatusData, statuses)
    }

    @Test
    @ExperimentalPagingApi
    fun `should refresh and insert placeholder`() {
        val statuses: MutableList<StatusViewData> = mutableListOf(
            mockStatusViewData("3"),
            mockStatusViewData("2"),
            mockStatusViewData("1"),
        )

        val timelineViewModel: NetworkTimelineViewModel = mock {
            on { statusData } doReturn statuses
            on { nextKey } doReturn "0"
            onBlocking { fetchStatusesForKind(null, null, 20) } doReturn Response.success(
                listOf(
                    mockStatus("10"),
                    mockStatus("9"),
                    mockStatus("7")
                )
            )
        }

        val remoteMediator = NetworkTimelineRemoteMediator(accountManager, timelineViewModel)

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = listOf(
                        mockStatusViewData("3"),
                        mockStatusViewData("2"),
                        mockStatusViewData("1"),
                    ),
                    prevKey = null,
                    nextKey = "0"
                )
            )
        )

        val result = runBlocking { remoteMediator.load(LoadType.REFRESH, state) }

        val newStatusData = mutableListOf(
            mockStatusViewData("10"),
            mockStatusViewData("9"),
            StatusViewData.Placeholder("7", false),
            mockStatusViewData("3"),
            mockStatusViewData("2"),
            mockStatusViewData("1"),
        )

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(newStatusData, statuses)
    }

    @Test
    @ExperimentalPagingApi
    fun `should refresh and not insert placeholders`() {
        val statuses: MutableList<StatusViewData> = mutableListOf(
            mockStatusViewData("8"),
            mockStatusViewData("7"),
            mockStatusViewData("5"),
        )

        val timelineViewModel: NetworkTimelineViewModel = mock {
            on { statusData } doReturn statuses
            on { nextKey } doReturn "3"
            onBlocking { fetchStatusesForKind("3", null, 20) } doReturn Response.success(
                listOf(
                    mockStatus("3"),
                    mockStatus("2"),
                    mockStatus("1")
                )
            )
        }

        val remoteMediator = NetworkTimelineRemoteMediator(accountManager, timelineViewModel)

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = listOf(
                        mockStatusViewData("8"),
                        mockStatusViewData("7"),
                        mockStatusViewData("5"),
                    ),
                    prevKey = null,
                    nextKey = "3"
                )
            )
        )

        val result = runBlocking { remoteMediator.load(LoadType.APPEND, state) }

        val newStatusData = mutableListOf(
            mockStatusViewData("8"),
            mockStatusViewData("7"),
            mockStatusViewData("5"),
            mockStatusViewData("3"),
            mockStatusViewData("2"),
            mockStatusViewData("1"),
        )

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(newStatusData, statuses)
    }

    @Test
    @ExperimentalPagingApi
    fun `should append statuses`() {
        val statuses: MutableList<StatusViewData> = mutableListOf(
            mockStatusViewData("8"),
            mockStatusViewData("7"),
            mockStatusViewData("5"),
        )

        val timelineViewModel: NetworkTimelineViewModel = mock {
            on { statusData } doReturn statuses
            on { nextKey } doReturn "3"
            onBlocking { fetchStatusesForKind("3", null, 20) } doReturn Response.success(
                listOf(
                    mockStatus("3"),
                    mockStatus("2"),
                    mockStatus("1")
                ),
                Headers.headersOf(
                    "Link", "<https://mastodon.example/api/v1/favourites?limit=20&max_id=0>; rel=\"next\", <https://mastodon.example/api/v1/favourites?limit=20&min_id=4>; rel=\"prev\""
                )
            )
        }

        val remoteMediator = NetworkTimelineRemoteMediator(accountManager, timelineViewModel)

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = listOf(
                        mockStatusViewData("8"),
                        mockStatusViewData("7"),
                        mockStatusViewData("5"),
                    ),
                    prevKey = null,
                    nextKey = "3"
                )
            )
        )

        val result = runBlocking { remoteMediator.load(LoadType.APPEND, state) }

        val newStatusData = mutableListOf(
            mockStatusViewData("8"),
            mockStatusViewData("7"),
            mockStatusViewData("5"),
            mockStatusViewData("3"),
            mockStatusViewData("2"),
            mockStatusViewData("1"),
        )
        verify(timelineViewModel).nextKey = "0"
        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(newStatusData, statuses)
    }

    @Test
    @ExperimentalPagingApi
    fun `should not append statuses when pagination end has been reached`() {
        val statuses: MutableList<StatusViewData> = mutableListOf(
            mockStatusViewData("8"),
            mockStatusViewData("7"),
            mockStatusViewData("5"),
        )

        val timelineViewModel: NetworkTimelineViewModel = mock {
            on { statusData } doReturn statuses
            on { nextKey } doReturn null
        }

        val remoteMediator = NetworkTimelineRemoteMediator(accountManager, timelineViewModel)

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = listOf(
                        mockStatusViewData("8"),
                        mockStatusViewData("7"),
                        mockStatusViewData("5"),
                    ),
                    prevKey = null,
                    nextKey = null
                )
            )
        )

        val result = runBlocking { remoteMediator.load(LoadType.APPEND, state) }

        val newStatusData = mutableListOf(
            mockStatusViewData("8"),
            mockStatusViewData("7"),
            mockStatusViewData("5")
        )

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
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
