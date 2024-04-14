package com.keylesspalace.tusky.db.dao

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.keylesspalace.tusky.components.notifications.insert
import com.keylesspalace.tusky.components.notifications.mockNotification
import com.keylesspalace.tusky.components.notifications.mockReport
import com.keylesspalace.tusky.components.notifications.toNotificationDataEntity
import com.keylesspalace.tusky.components.notifications.toNotificationEntity
import com.keylesspalace.tusky.components.timeline.Placeholder
import com.keylesspalace.tusky.components.timeline.mockAccount
import com.keylesspalace.tusky.components.timeline.mockStatus
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.Converters
import com.keylesspalace.tusky.di.NetworkModule
import com.keylesspalace.tusky.entity.Notification
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class NotificationsDaoTest {
    private lateinit var notificationsDao: NotificationsDao
    private lateinit var db: AppDatabase

    private val moshi = NetworkModule.providesMoshi()

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addTypeConverter(Converters(moshi))
            .allowMainThreadQueries()
            .build()
        notificationsDao = db.notificationsDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndGetNotification() = runTest {
        db.insert(
            listOf(
                mockNotification(id = "1"),
                mockNotification(id = "2"),
                mockNotification(id = "3"),
            ),
            tuskyAccountId = 1
        )
        db.insert(
            listOf(mockNotification(id = "3")),
            tuskyAccountId = 2
        )

        val pagingSource = notificationsDao.getNotifications(tuskyAccountId = 1)

        val loadResult = pagingSource.load(PagingSource.LoadParams.Refresh(null, 2, false))

        val loadedStatuses = (loadResult as PagingSource.LoadResult.Page).data

        assertEquals(
            listOf(
                mockNotification(id = "3").toNotificationDataEntity(1),
                mockNotification(id = "2").toNotificationDataEntity(1)
            ),
            loadedStatuses
        )
    }

    @Test
    fun deleteRange() = runTest {
        val notifications = listOf(
            mockNotification(id = "100"),
            mockNotification(id = "50"),
            mockNotification(id = "15"),
            mockNotification(id = "14"),
            mockNotification(id = "13"),
            mockNotification(id = "12"),
            mockNotification(id = "11"),
            mockNotification(id = "9")
        )

        db.insert(notifications, 1)
        db.insert(listOf(mockNotification(id = "13")), 2)

        assertEquals(3, notificationsDao.deleteRange(1, "12", "14"))
        assertEquals(0, notificationsDao.deleteRange(1, "80", "80"))
        assertEquals(0, notificationsDao.deleteRange(1, "60", "80"))
        assertEquals(0, notificationsDao.deleteRange(1, "5", "8"))
        assertEquals(0, notificationsDao.deleteRange(1, "101", "1000"))
        assertEquals(1, notificationsDao.deleteRange(1, "50", "50"))

        val loadParams: PagingSource.LoadParams<Int> = PagingSource.LoadParams.Refresh(null, 100, false)

        val notificationsAccount1 = (notificationsDao.getNotifications(1).load(loadParams) as PagingSource.LoadResult.Page).data
        val notificationsAccount2 = (notificationsDao.getNotifications(2).load(loadParams) as PagingSource.LoadResult.Page).data

        val remainingNotificationsAccount1 = listOf(
            mockNotification(id = "100").toNotificationDataEntity(1),
            mockNotification(id = "15").toNotificationDataEntity(1),
            mockNotification(id = "11").toNotificationDataEntity(1),
            mockNotification(id = "9").toNotificationDataEntity(1)
        )

        val remainingNotificationsAccount2 = listOf(
            mockNotification(id = "13").toNotificationDataEntity(2)
        )

        assertEquals(remainingNotificationsAccount1, notificationsAccount1)
        assertEquals(remainingNotificationsAccount2, notificationsAccount2)
    }

    @Test
    fun deleteAllForInstance() = runTest {
        val redAccount = mockNotification(id = "500", account = mockAccount(id = "500", domain = "mastodon.red"))
        val blueAccount = mockNotification(id = "501", account = mockAccount(id = "501", domain = "mastodon.blue"))
        val redStatus = mockNotification(id = "502", account = mockAccount(id = "502", domain = "mastodon.example"), status = mockStatus(id = "502", domain = "mastodon.red", authorServerId = "502a"))
        val blueStatus = mockNotification(id = "503", account = mockAccount(id = "503", domain = "mastodon.example"), status = mockStatus(id = "503", domain = "mastodon.blue", authorServerId = "503a"))

        val redStatus2 = mockNotification(id = "600", account = mockAccount(id = "600", domain = "mastodon.red"))

        db.insert(listOf(redAccount, blueAccount, redStatus, blueStatus), 1)
        db.insert(listOf(redStatus2), 2)

        notificationsDao.deleteAllFromInstance(1, "mastodon.red")
        notificationsDao.deleteAllFromInstance(1, "mastodon.blu") // shouldn't delete anything
        notificationsDao.deleteAllFromInstance(1, "mastodon.green") // shouldn't delete anything

        val loadParams: PagingSource.LoadParams<Int> = PagingSource.LoadParams.Refresh(null, 100, false)

        val notificationsAccount1 = (notificationsDao.getNotifications(1).load(loadParams) as PagingSource.LoadResult.Page).data
        val notificationsAccount2 = (notificationsDao.getNotifications(2).load(loadParams) as PagingSource.LoadResult.Page).data

        assertEquals(
            listOf(
                blueStatus.toNotificationDataEntity(1),
                blueAccount.toNotificationDataEntity(1)
            ),
            notificationsAccount1
        )
        assertEquals(listOf(redStatus2.toNotificationDataEntity(2)), notificationsAccount2)
    }

    @Test
    fun `should return null as topId when db is empty`() = runTest {
        assertNull(notificationsDao.getTopId(1))
    }

    @Test
    fun `should return correct topId`() = runTest {
        db.insert(
            listOf(
                mockNotification(id = "100"),
                mockNotification(id = "3"),
                mockNotification(id = "33"),
                mockNotification(id = "8"),
            ),
            tuskyAccountId = 1
        )
        db.insert(
            listOf(
                mockNotification(id = "200"),
                mockNotification(id = "300"),
                mockNotification(id = "1000"),
            ),
            tuskyAccountId = 2
        )

        assertEquals("100", notificationsDao.getTopId(1))
        assertEquals("1000", notificationsDao.getTopId(2))
    }

    @Test
    fun `should return correct top placeholderId`() = runTest {
        val notifications = listOf(
            mockNotification(id = "1000"),
            mockNotification(id = "97"),
            mockNotification(id = "90"),
            mockNotification(id = "77")
        )
        db.insert(notifications)

        notificationsDao.insertNotification(Placeholder(id = "99", loading = false).toNotificationEntity(1))
        notificationsDao.insertNotification(Placeholder(id = "96", loading = false).toNotificationEntity(1))
        notificationsDao.insertNotification(Placeholder(id = "80", loading = false).toNotificationEntity(1))

        assertEquals("99", notificationsDao.getTopPlaceholderId(1))
    }

    @Test
    fun `should correctly delete all by user`() = runTest {
        val notificationsAccount1 = listOf(
            // will be removed because it is a like by account 1
            mockNotification(id = "1", account = mockAccount(id = "1"), status = mockStatus(id = "1", authorServerId = "100")),
            // will be removed because it references a status by account 1
            mockNotification(id = "2", account = mockAccount(id = "2"), status = mockStatus(id = "2", authorServerId = "1")),
            // will not be removed because they are admin notifications
            mockNotification(type = Notification.Type.REPORT, id = "3", account = mockAccount(id = "3"), status = null, report = mockReport(id = "1", targetAccount = mockAccount(id = "1"))),
            mockNotification(type = Notification.Type.SIGN_UP, id = "4", account = mockAccount(id = "1"), status = null, report = mockReport(id = "1", targetAccount = mockAccount(id = "4"))),
            // will not be removed because it does not reference account 1
            mockNotification(id = "5", account = mockAccount(id = "5"), status = mockStatus(id = "5", authorServerId = "100")),
        )

        db.insert(notificationsAccount1, tuskyAccountId = 1)
        db.insert(listOf(mockNotification(id = "6")), tuskyAccountId = 2)

        notificationsDao.removeAllByUser(1, "1")

        val loadedNotifications: MutableList<String> = mutableListOf()
        val cursor = db.query("SELECT id FROM NotificationEntity ORDER BY id ASC", null)
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            val id: String = cursor.getString(cursor.getColumnIndex("id"))
            loadedNotifications.add(id)
            cursor.moveToNext()
        }
        cursor.close()

        val expectedNotifications = listOf("3", "4", "5", "6")

        assertEquals(expectedNotifications, loadedNotifications)
    }
}