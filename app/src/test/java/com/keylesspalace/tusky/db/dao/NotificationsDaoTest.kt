package com.keylesspalace.tusky.db.dao

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.keylesspalace.tusky.components.notifications.fakeNotification
import com.keylesspalace.tusky.components.notifications.fakeReport
import com.keylesspalace.tusky.components.notifications.insert
import com.keylesspalace.tusky.components.notifications.toNotificationDataEntity
import com.keylesspalace.tusky.components.notifications.toNotificationEntity
import com.keylesspalace.tusky.components.timeline.Placeholder
import com.keylesspalace.tusky.components.timeline.fakeAccount
import com.keylesspalace.tusky.components.timeline.fakeStatus
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

@Config(sdk = [34])
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
                fakeNotification(id = "1"),
                fakeNotification(id = "2"),
                fakeNotification(id = "3"),
            ),
            tuskyAccountId = 1
        )
        db.insert(
            listOf(fakeNotification(id = "3")),
            tuskyAccountId = 2
        )

        val pagingSource = notificationsDao.getNotifications(tuskyAccountId = 1)

        val loadResult = pagingSource.load(PagingSource.LoadParams.Refresh(null, 2, false))

        val loadedStatuses = (loadResult as PagingSource.LoadResult.Page).data

        assertEquals(
            listOf(
                fakeNotification(id = "3").toNotificationDataEntity(1),
                fakeNotification(id = "2").toNotificationDataEntity(1)
            ),
            loadedStatuses
        )
    }

    @Test
    fun deleteRange() = runTest {
        val notifications = listOf(
            fakeNotification(id = "100"),
            fakeNotification(id = "50"),
            fakeNotification(id = "15"),
            fakeNotification(id = "14"),
            fakeNotification(id = "13"),
            fakeNotification(id = "12"),
            fakeNotification(id = "11"),
            fakeNotification(id = "9")
        )

        db.insert(notifications, 1)
        db.insert(listOf(fakeNotification(id = "13")), 2)

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
            fakeNotification(id = "100").toNotificationDataEntity(1),
            fakeNotification(id = "15").toNotificationDataEntity(1),
            fakeNotification(id = "11").toNotificationDataEntity(1),
            fakeNotification(id = "9").toNotificationDataEntity(1)
        )

        val remainingNotificationsAccount2 = listOf(
            fakeNotification(id = "13").toNotificationDataEntity(2)
        )

        assertEquals(remainingNotificationsAccount1, notificationsAccount1)
        assertEquals(remainingNotificationsAccount2, notificationsAccount2)
    }

    @Test
    fun deleteAllForInstance() = runTest {
        val redAccount = fakeNotification(id = "500", account = fakeAccount(id = "500", domain = "mastodon.red"))
        val blueAccount = fakeNotification(id = "501", account = fakeAccount(id = "501", domain = "mastodon.blue"))
        val redStatus = fakeNotification(id = "502", account = fakeAccount(id = "502", domain = "mastodon.example"), status = fakeStatus(id = "502", domain = "mastodon.red", authorServerId = "502a"))
        val blueStatus = fakeNotification(id = "503", account = fakeAccount(id = "503", domain = "mastodon.example"), status = fakeStatus(id = "503", domain = "mastodon.blue", authorServerId = "503a"))

        val redStatus2 = fakeNotification(id = "600", account = fakeAccount(id = "600", domain = "mastodon.red"))

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
                fakeNotification(id = "100"),
                fakeNotification(id = "3"),
                fakeNotification(id = "33"),
                fakeNotification(id = "8"),
            ),
            tuskyAccountId = 1
        )
        db.insert(
            listOf(
                fakeNotification(id = "200"),
                fakeNotification(id = "300"),
                fakeNotification(id = "1000"),
            ),
            tuskyAccountId = 2
        )

        assertEquals("100", notificationsDao.getTopId(1))
        assertEquals("1000", notificationsDao.getTopId(2))
    }

    @Test
    fun `should return correct top placeholderId`() = runTest {
        val notifications = listOf(
            fakeNotification(id = "1000"),
            fakeNotification(id = "97"),
            fakeNotification(id = "90"),
            fakeNotification(id = "77")
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
            fakeNotification(id = "1", account = fakeAccount(id = "1"), status = fakeStatus(id = "1", authorServerId = "100")),
            // will be removed because it references a status by account 1
            fakeNotification(id = "2", account = fakeAccount(id = "2"), status = fakeStatus(id = "2", authorServerId = "1")),
            // will not be removed because they are admin notifications
            fakeNotification(type = Notification.Type.Report, id = "3", account = fakeAccount(id = "3"), status = null, report = fakeReport(id = "1", targetAccount = fakeAccount(id = "1"))),
            fakeNotification(type = Notification.Type.SignUp, id = "4", account = fakeAccount(id = "1"), status = null, report = fakeReport(id = "1", targetAccount = fakeAccount(id = "4"))),
            // will not be removed because it does not reference account 1
            fakeNotification(id = "5", account = fakeAccount(id = "5"), status = fakeStatus(id = "5", authorServerId = "100")),
            fakeNotification(type = Notification.Type.Follow, id = "6", account = fakeAccount(id = "1"), status = null)
        )

        db.insert(notificationsAccount1, tuskyAccountId = 1)
        db.insert(listOf(fakeNotification(id = "2000")), tuskyAccountId = 2)

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

        val expectedNotifications = listOf("2000", "3", "4", "5")

        assertEquals(expectedNotifications, loadedNotifications)
    }
}
