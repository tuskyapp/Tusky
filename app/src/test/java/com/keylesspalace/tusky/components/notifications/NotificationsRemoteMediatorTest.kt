package com.keylesspalace.tusky.components.notifications

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
import com.keylesspalace.tusky.components.timeline.Placeholder
import com.keylesspalace.tusky.components.timeline.fakeStatus
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.Converters
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.db.entity.NotificationDataEntity
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
class NotificationsRemoteMediatorTest {

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
        val remoteMediator = NotificationsRemoteMediator(
            accountManager = accountManager,
            api = mock {
                onBlocking { notifications(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()) } doReturn Response.error(500, "".toResponseBody())
            },
            db = db,
            excludes = emptySet()
        )

        val result = remoteMediator.load(LoadType.REFRESH, state())

        assertTrue(result is RemoteMediator.MediatorResult.Error)
        assertTrue((result as RemoteMediator.MediatorResult.Error).throwable is HttpException)
        assertEquals(500, (result.throwable as HttpException).code())
    }

    @Test
    @ExperimentalPagingApi
    fun `should return error when network call fails`() = runTest {
        val remoteMediator = NotificationsRemoteMediator(
            accountManager = accountManager,
            api = mock {
                onBlocking { notifications(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()) } doThrow IOException()
            },
            db = db,
            excludes = emptySet()
        )

        val result = remoteMediator.load(LoadType.REFRESH, state())

        assertTrue(result is RemoteMediator.MediatorResult.Error)
        assertTrue((result as RemoteMediator.MediatorResult.Error).throwable is IOException)
    }

    @Test
    @ExperimentalPagingApi
    fun `should not prepend notifications`() = runTest {
        val remoteMediator = NotificationsRemoteMediator(
            accountManager = accountManager,
            api = mock(),
            db = db,
            excludes = emptySet()
        )

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = listOf(
                        fakeNotification(id = "3").toNotificationDataEntity(1)
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
    fun `should refresh and insert placeholder when a whole page with no overlap to existing notifications is loaded`() = runTest {
        val notificationsAlreadyInDb = listOf(
            fakeNotification(id = "3"),
            fakeNotification(id = "2"),
            fakeNotification(id = "1")
        )

        db.insert(notificationsAlreadyInDb)

        val remoteMediator = NotificationsRemoteMediator(
            accountManager = accountManager,
            api = mock {
                onBlocking { notifications(limit = 3, excludes = emptySet()) } doReturn Response.success(
                    listOf(
                        fakeNotification(id = "8"),
                        fakeNotification(id = "7"),
                        fakeNotification(id = "5")
                    )
                )
                onBlocking { notifications(maxId = "3", limit = 3, excludes = emptySet()) } doReturn Response.success(
                    listOf(
                        fakeNotification(id = "3"),
                        fakeNotification(id = "2"),
                        fakeNotification(id = "1")
                    )
                )
            },
            db = db,
            excludes = emptySet()
        )

        val state = state(
            pages = listOf(
                PagingSource.LoadResult.Page(
                    data = notificationsAlreadyInDb.map { it.toNotificationDataEntity(1) },
                    prevKey = null,
                    nextKey = 0
                )
            ),
            pageSize = 3
        )

        val result = remoteMediator.load(LoadType.REFRESH, state)

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        db.assertNotifications(
            listOf(
                fakeNotification(id = "8").toNotificationDataEntity(1),
                fakeNotification(id = "7").toNotificationDataEntity(1),
                Placeholder(id = "5", loading = false).toNotificationDataEntity(1),
                fakeNotification(id = "3").toNotificationDataEntity(1),
                fakeNotification(id = "2").toNotificationDataEntity(1),
                fakeNotification(id = "1").toNotificationDataEntity(1)
            )
        )
    }

    @Test
    @ExperimentalPagingApi
    fun `should refresh and not insert placeholder when less than a whole page is loaded`() = runTest {
        val notificationsAlreadyInDb = listOf(
            fakeNotification(id = "3"),
            fakeNotification(id = "2"),
            fakeNotification(id = "1")
        )

        db.insert(notificationsAlreadyInDb)

        val remoteMediator = NotificationsRemoteMediator(
            accountManager = accountManager,
            api = mock {
                onBlocking { notifications(limit = 20, excludes = emptySet()) } doReturn Response.success(
                    listOf(
                        // testing for https://github.com/tuskyapp/Tusky/issues/4563
                        fakeNotification(
                            id = "8",
                            status = fakeStatus(
                                id = "r1",
                                reblog = fakeStatus(
                                    id = "8",
                                    authorServerId = "r1"
                                )
                            )
                        ),
                        fakeNotification(id = "7"),
                        fakeNotification(id = "5")
                    )
                )
                onBlocking { notifications(maxId = "3", limit = 20, excludes = emptySet()) } doReturn Response.success(
                    listOf(
                        fakeNotification(id = "3"),
                        fakeNotification(id = "2"),
                        fakeNotification(id = "1")
                    )
                )
            },
            db = db,
            excludes = emptySet()
        )

        val state = state(
            pages = listOf(
                PagingSource.LoadResult.Page(
                    data = notificationsAlreadyInDb.map { it.toNotificationDataEntity(1) },
                    prevKey = null,
                    nextKey = 0
                )
            )
        )

        val result = remoteMediator.load(LoadType.REFRESH, state)

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        db.assertNotifications(
            listOf(
                fakeNotification(
                    id = "8",
                    status = fakeStatus(
                        id = "8",
                        authorServerId = "r1"
                    )
                ).toNotificationDataEntity(1),
                fakeNotification(id = "7").toNotificationDataEntity(1),
                fakeNotification(id = "5").toNotificationDataEntity(1),
                fakeNotification(id = "3").toNotificationDataEntity(1),
                fakeNotification(id = "2").toNotificationDataEntity(1),
                fakeNotification(id = "1").toNotificationDataEntity(1)
            )
        )
    }

    @Test
    @ExperimentalPagingApi
    fun `should refresh and not insert placeholders when there is overlap with existing notifications`() = runTest {
        val notificationsAlreadyInDb = listOf(
            fakeNotification(id = "3"),
            fakeNotification(id = "2"),
            fakeNotification(id = "1")
        )

        db.insert(notificationsAlreadyInDb)

        val remoteMediator = NotificationsRemoteMediator(
            accountManager = accountManager,
            api = mock {
                onBlocking { notifications(limit = 3, excludes = emptySet()) } doReturn Response.success(
                    listOf(
                        fakeNotification(id = "6"),
                        fakeNotification(id = "4"),
                        fakeNotification(id = "3")
                    )
                )
                onBlocking { notifications(maxId = "3", limit = 3, excludes = emptySet()) } doReturn Response.success(
                    listOf(
                        fakeNotification(id = "3"),
                        fakeNotification(id = "2"),
                        fakeNotification(id = "1")
                    )
                )
            },
            db = db,
            excludes = emptySet()
        )

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = notificationsAlreadyInDb.map { it.toNotificationDataEntity(1) },
                    prevKey = null,
                    nextKey = 0
                )
            ),
            pageSize = 3
        )

        val result = remoteMediator.load(LoadType.REFRESH, state)

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        db.assertNotifications(
            listOf(
                fakeNotification(id = "6").toNotificationDataEntity(1),
                fakeNotification(id = "4").toNotificationDataEntity(1),
                fakeNotification(id = "3").toNotificationDataEntity(1),
                fakeNotification(id = "2").toNotificationDataEntity(1),
                fakeNotification(id = "1").toNotificationDataEntity(1)
            )
        )
    }

    @Test
    @ExperimentalPagingApi
    fun `should not try to refresh already cached notifications when db is empty`() = runTest {
        val remoteMediator = NotificationsRemoteMediator(
            accountManager = accountManager,
            api = mock {
                onBlocking { notifications(limit = 20, excludes = emptySet()) } doReturn Response.success(
                    listOf(
                        fakeNotification(id = "5"),
                        fakeNotification(id = "4"),
                        fakeNotification(id = "3")
                    )
                )
            },
            db = db,
            excludes = emptySet()
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

        db.assertNotifications(
            listOf(
                fakeNotification(id = "5").toNotificationDataEntity(1),
                fakeNotification(id = "4").toNotificationDataEntity(1),
                fakeNotification(id = "3").toNotificationDataEntity(1)
            )
        )
    }

    @Test
    @ExperimentalPagingApi
    fun `should remove deleted notification from db and keep state of statuses in the remaining ones`() = runTest {
        val notificationsAlreadyInDb = listOf(
            fakeNotification(id = "3"),
            fakeNotification(id = "2"),
            fakeNotification(id = "1")
        )
        db.insert(notificationsAlreadyInDb)

        db.timelineStatusDao().setExpanded(1, "3", true)
        db.timelineStatusDao().setExpanded(1, "2", true)
        db.timelineStatusDao().setContentCollapsed(1, "1", false)

        val remoteMediator = NotificationsRemoteMediator(
            accountManager = accountManager,
            api = mock {
                onBlocking { notifications(limit = 20, excludes = emptySet()) } doReturn Response.success(emptyList())

                onBlocking { notifications(maxId = "3", limit = 20, excludes = emptySet()) } doReturn Response.success(
                    listOf(
                        fakeNotification(id = "3"),
                        fakeNotification(id = "1")
                    )
                )
            },
            db = db,
            excludes = emptySet()
        )

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = listOf(
                        fakeNotification(id = "3").toNotificationDataEntity(1, isStatusExpanded = true),
                        fakeNotification(id = "2").toNotificationDataEntity(1, isStatusExpanded = true),
                        fakeNotification(id = "1").toNotificationDataEntity(1, isStatusContentShowing = true)
                    ),
                    prevKey = null,
                    nextKey = 0
                )
            )
        )

        val result = remoteMediator.load(LoadType.REFRESH, state)

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        db.assertNotifications(
            listOf(
                fakeNotification(id = "3").toNotificationDataEntity(1, isStatusExpanded = true),
                fakeNotification(id = "1").toNotificationDataEntity(1, isStatusContentShowing = true)
            )
        )
    }

    @Test
    @ExperimentalPagingApi
    fun `should not remove placeholder in timeline`() = runTest {
        val notificationsAlreadyInDb = listOf(
            fakeNotification(id = "8"),
            fakeNotification(id = "7"),
            fakeNotification(id = "1")
        )
        db.insert(notificationsAlreadyInDb)

        val placeholder = Placeholder(id = "6", loading = false).toNotificationEntity(1)
        db.notificationsDao().insertNotification(placeholder)

        val remoteMediator = NotificationsRemoteMediator(
            accountManager = accountManager,
            api = mock {
                onBlocking { notifications(sinceId = "6", limit = 20, excludes = emptySet()) } doReturn Response.success(
                    listOf(
                        fakeNotification(id = "9"),
                        fakeNotification(id = "8"),
                        fakeNotification(id = "7")
                    )
                )
                onBlocking { notifications(maxId = "8", sinceId = "6", limit = 20, excludes = emptySet()) } doReturn Response.success(
                    listOf(
                        fakeNotification(id = "8"),
                        fakeNotification(id = "7")
                    )
                )
            },
            db = db,
            excludes = emptySet()
        )

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = notificationsAlreadyInDb.map { it.toNotificationDataEntity(1) },
                    prevKey = null,
                    nextKey = 0
                )
            )
        )

        val result = remoteMediator.load(LoadType.REFRESH, state)

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertFalse((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        db.assertNotifications(
            listOf(
                fakeNotification(id = "9").toNotificationDataEntity(1),
                fakeNotification(id = "8").toNotificationDataEntity(1),
                fakeNotification(id = "7").toNotificationDataEntity(1),
                Placeholder(id = "6", loading = false).toNotificationDataEntity(1),
                fakeNotification(id = "1").toNotificationDataEntity(1)
            )
        )
    }

    @Test
    @ExperimentalPagingApi
    fun `should append notifications`() = runTest {
        val notificationsAlreadyInDb = listOf(
            fakeNotification(id = "8"),
            fakeNotification(id = "7"),
            fakeNotification(id = "5")
        )

        db.insert(notificationsAlreadyInDb)

        val remoteMediator = NotificationsRemoteMediator(
            accountManager = accountManager,
            api = mock {
                onBlocking { notifications(maxId = "5", limit = 20, excludes = emptySet()) } doReturn Response.success(
                    listOf(
                        fakeNotification(id = "3"),
                        fakeNotification(id = "2"),
                        fakeNotification(id = "1")
                    )
                )
            },
            db = db,
            excludes = emptySet()
        )

        val state = state(
            listOf(
                PagingSource.LoadResult.Page(
                    data = notificationsAlreadyInDb.map { it.toNotificationDataEntity(1) },
                    prevKey = null,
                    nextKey = 0
                )
            )
        )

        val result = remoteMediator.load(LoadType.APPEND, state)

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        db.assertNotifications(
            listOf(
                fakeNotification(id = "8").toNotificationDataEntity(1),
                fakeNotification(id = "7").toNotificationDataEntity(1),
                fakeNotification(id = "5").toNotificationDataEntity(1),
                fakeNotification(id = "3").toNotificationDataEntity(1),
                fakeNotification(id = "2").toNotificationDataEntity(1),
                fakeNotification(id = "1").toNotificationDataEntity(1)
            )
        )
    }

    private fun state(
        pages: List<PagingSource.LoadResult.Page<Int, NotificationDataEntity>> = emptyList(),
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
