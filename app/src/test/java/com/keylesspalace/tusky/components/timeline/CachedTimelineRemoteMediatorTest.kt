package com.keylesspalace.tusky.components.timeline

import android.os.Looper.getMainLooper
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.keylesspalace.tusky.components.timeline.viewmodel.CachedTimelineRemoteMediator
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.Converters
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.db.entity.HomeTimelineData
import com.keylesspalace.tusky.di.NetworkModule
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class CachedTimelineRemoteMediatorTest {

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

    private lateinit var db: AppDatabase

    private val moshi = NetworkModule.providesMoshi()

    @Before
    @ExperimentalCoroutinesApi
    fun setup() {
        shadowOf(getMainLooper()).idle()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addTypeConverter(Converters(moshi))
            .build()
    }

    @After
    @ExperimentalCoroutinesApi
    fun tearDown() {
        db.close()
    }

    @Test
    @ExperimentalPagingApi
    fun `should return error when network call returns error code`() = runTest {
        val remoteMediator = CachedTimelineRemoteMediator(
            accountManager = accountManager,
            api = mock {
                onBlocking { homeTimeline(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()) } doReturn Response.error(500, "".toResponseBody())
            },
            db = db,
        )

        val result = remoteMediator.load(LoadType.REFRESH, state())

        assertTrue(result is RemoteMediator.MediatorResult.Error)
        assertTrue((result as RemoteMediator.MediatorResult.Error).throwable is HttpException)
        assertEquals(500, (result.throwable as HttpException).code())
    }

    @Test
    @ExperimentalPagingApi
    fun `should return error when network call fails`() = runTest {
        val remoteMediator = CachedTimelineRemoteMediator(
            accountManager = accountManager,
            api = mock {
                onBlocking { homeTimeline(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()) } doThrow IOException()
            },
            db = db,
        )

        val result = remoteMediator.load(LoadType.REFRESH, state())

        assertTrue(result is RemoteMediator.MediatorResult.Error)
        assertTrue((result as RemoteMediator.MediatorResult.Error).throwable is IOException)
    }

    @Test
    @ExperimentalPagingApi
    fun `should not prepend statuses`() = runTest {
        val remoteMediator = CachedTimelineRemoteMediator(
            accountManager = accountManager,
            api = mock(),
            db = db,
        )

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = listOf(
                        fakeHomeTimelineData("3")
                    ),
                    prevKey = null,
                    nextKey = 1
                )
            )
        )

        val result = remoteMediator.load(LoadType.PREPEND, state)

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
    }

    @Test
    @ExperimentalPagingApi
    fun `should refresh and insert placeholder when a whole page with no overlap to existing statuses is loaded`() = runTest {
        val statusesAlreadyInDb = listOf(
            fakeHomeTimelineData("3"),
            fakeHomeTimelineData("2"),
            fakeHomeTimelineData("1")
        )

        db.insert(statusesAlreadyInDb)

        val remoteMediator = CachedTimelineRemoteMediator(
            accountManager = accountManager,
            api = mock {
                onBlocking { homeTimeline(limit = 3) } doReturn Response.success(
                    listOf(
                        fakeStatus("8"),
                        fakeStatus("7"),
                        fakeStatus("5")
                    )
                )
                onBlocking { homeTimeline(maxId = "3", limit = 3) } doReturn Response.success(
                    listOf(
                        fakeStatus("3"),
                        fakeStatus("2"),
                        fakeStatus("1")
                    )
                )
            },
            db = db,
        )

        val state = state(
            pages = listOf(
                PagingSource.LoadResult.Page(
                    data = statusesAlreadyInDb,
                    prevKey = null,
                    nextKey = 0
                )
            ),
            pageSize = 3
        )

        val result = remoteMediator.load(LoadType.REFRESH, state)

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        db.assertTimeline(
            listOf(
                fakeHomeTimelineData("8"),
                fakeHomeTimelineData("7"),
                fakePlaceholderHomeTimelineData("5"),
                fakeHomeTimelineData("3"),
                fakeHomeTimelineData("2"),
                fakeHomeTimelineData("1")
            )
        )
    }

    @Test
    @ExperimentalPagingApi
    fun `should refresh and not insert placeholder when less than a whole page is loaded`() = runTest {
        val statusesAlreadyInDb = listOf(
            fakeHomeTimelineData("3"),
            fakeHomeTimelineData("2"),
            fakeHomeTimelineData("1")
        )

        db.insert(statusesAlreadyInDb)

        val remoteMediator = CachedTimelineRemoteMediator(
            accountManager = accountManager,
            api = mock {
                onBlocking { homeTimeline(limit = 20) } doReturn Response.success(
                    listOf(
                        fakeStatus("8"),
                        fakeStatus("7"),
                        fakeStatus("5")
                    )
                )
                onBlocking { homeTimeline(maxId = "3", limit = 20) } doReturn Response.success(
                    listOf(
                        fakeStatus("3"),
                        fakeStatus("2"),
                        fakeStatus("1")
                    )
                )
            },
            db = db,
        )

        val state = state(
            pages = listOf(
                PagingSource.LoadResult.Page(
                    data = statusesAlreadyInDb,
                    prevKey = null,
                    nextKey = 0
                )
            )
        )

        val result = remoteMediator.load(LoadType.REFRESH, state)

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        db.assertTimeline(
            listOf(
                fakeHomeTimelineData("8"),
                fakeHomeTimelineData("7"),
                fakeHomeTimelineData("5"),
                fakeHomeTimelineData("3"),
                fakeHomeTimelineData("2"),
                fakeHomeTimelineData("1")
            )
        )
    }

    @Test
    @ExperimentalPagingApi
    fun `should refresh and not insert placeholders when there is overlap with existing statuses`() = runTest {
        val statusesAlreadyInDb = listOf(
            fakeHomeTimelineData("3"),
            fakeHomeTimelineData("2"),
            fakeHomeTimelineData("1")
        )

        db.insert(statusesAlreadyInDb)

        val remoteMediator = CachedTimelineRemoteMediator(
            accountManager = accountManager,
            api = mock {
                onBlocking { homeTimeline(limit = 3) } doReturn Response.success(
                    listOf(
                        fakeStatus("6"),
                        fakeStatus("4"),
                        fakeStatus("3")
                    )
                )
                onBlocking { homeTimeline(maxId = "3", limit = 3) } doReturn Response.success(
                    listOf(
                        fakeStatus("3"),
                        fakeStatus("2"),
                        fakeStatus("1")
                    )
                )
            },
            db = db,
        )

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = statusesAlreadyInDb,
                    prevKey = null,
                    nextKey = 0
                )
            ),
            pageSize = 3
        )

        val result = remoteMediator.load(LoadType.REFRESH, state)

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        db.assertTimeline(
            listOf(
                fakeHomeTimelineData("6"),
                fakeHomeTimelineData("4"),
                fakeHomeTimelineData("3"),
                fakeHomeTimelineData("2"),
                fakeHomeTimelineData("1")
            )
        )
    }

    @Test
    @ExperimentalPagingApi
    fun `should not try to refresh already cached statuses when db is empty`() = runTest {
        val remoteMediator = CachedTimelineRemoteMediator(
            accountManager = accountManager,
            api = mock {
                onBlocking { homeTimeline(limit = 20) } doReturn Response.success(
                    listOf(
                        fakeStatus("5"),
                        fakeStatus("4"),
                        fakeStatus("3")
                    )
                )
            },
            db = db,
        )

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = emptyList(),
                    prevKey = null,
                    nextKey = 0
                )
            )
        )

        val result = remoteMediator.load(LoadType.REFRESH, state)

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        db.assertTimeline(
            listOf(
                fakeHomeTimelineData("5"),
                fakeHomeTimelineData("4"),
                fakeHomeTimelineData("3")
            )
        )
    }

    @Test
    @ExperimentalPagingApi
    fun `should remove deleted status from db and keep state of other cached statuses`() = runTest {
        val statusesAlreadyInDb = listOf(
            fakeHomeTimelineData("3", expanded = true),
            fakeHomeTimelineData("2"),
            fakeHomeTimelineData("1", expanded = false)
        )

        db.insert(statusesAlreadyInDb)

        val remoteMediator = CachedTimelineRemoteMediator(
            accountManager = accountManager,
            api = mock {
                onBlocking { homeTimeline(limit = 20) } doReturn Response.success(emptyList())

                onBlocking { homeTimeline(maxId = "3", limit = 20) } doReturn Response.success(
                    listOf(
                        fakeStatus("3"),
                        fakeStatus("1")
                    )
                )
            },
            db = db,
        )

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = statusesAlreadyInDb,
                    prevKey = null,
                    nextKey = 0
                )
            )
        )

        val result = remoteMediator.load(LoadType.REFRESH, state)

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        db.assertTimeline(
            listOf(
                fakeHomeTimelineData("3", expanded = true),
                fakeHomeTimelineData("1", expanded = false)
            )
        )
    }

    @Test
    @ExperimentalPagingApi
    fun `should not remove placeholder in timeline`() = runTest {
        val statusesAlreadyInDb = listOf(
            fakeHomeTimelineData("8"),
            fakeHomeTimelineData("7"),
            fakePlaceholderHomeTimelineData("6"),
            fakeHomeTimelineData("1")
        )

        db.insert(statusesAlreadyInDb)

        val remoteMediator = CachedTimelineRemoteMediator(
            accountManager = accountManager,
            api = mock {
                onBlocking { homeTimeline(sinceId = "6", limit = 20) } doReturn Response.success(
                    listOf(
                        fakeStatus("9"),
                        fakeStatus("8"),
                        fakeStatus("7")
                    )
                )
                onBlocking { homeTimeline(maxId = "8", sinceId = "6", limit = 20) } doReturn Response.success(
                    listOf(
                        fakeStatus("8"),
                        fakeStatus("7")
                    )
                )
            },
            db = db,
        )

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = statusesAlreadyInDb,
                    prevKey = null,
                    nextKey = 0
                )
            )
        )

        val result = remoteMediator.load(LoadType.REFRESH, state)

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertFalse((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        db.assertTimeline(
            listOf(
                fakeHomeTimelineData("9"),
                fakeHomeTimelineData("8"),
                fakeHomeTimelineData("7"),
                fakePlaceholderHomeTimelineData("6"),
                fakeHomeTimelineData("1")
            )
        )
    }

    @Test
    @ExperimentalPagingApi
    fun `should append statuses`() = runTest {
        val statusesAlreadyInDb = listOf(
            fakeHomeTimelineData("8"),
            fakeHomeTimelineData("7"),
            fakeHomeTimelineData("5")
        )

        db.insert(statusesAlreadyInDb)

        val remoteMediator = CachedTimelineRemoteMediator(
            accountManager = accountManager,
            api = mock {
                onBlocking { homeTimeline(maxId = "5", limit = 20) } doReturn Response.success(
                    listOf(
                        fakeStatus("3"),
                        fakeStatus("2"),
                        fakeStatus("1")
                    )
                )
            },
            db = db,
        )

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = statusesAlreadyInDb,
                    prevKey = null,
                    nextKey = 0
                )
            )
        )

        val result = remoteMediator.load(LoadType.APPEND, state)

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        db.assertTimeline(
            listOf(
                fakeHomeTimelineData("8"),
                fakeHomeTimelineData("7"),
                fakeHomeTimelineData("5"),
                fakeHomeTimelineData("3"),
                fakeHomeTimelineData("2"),
                fakeHomeTimelineData("1")
            )
        )
    }

    private fun state(
        pages: List<PagingSource.LoadResult.Page<Int, HomeTimelineData>> = emptyList(),
        pageSize: Int = 20
    ) = PagingState(
        pages = pages,
        anchorPosition = null,
        config = PagingConfig(
            pageSize = pageSize
        ),
        leadingPlaceholderCount = 0
    )
}
