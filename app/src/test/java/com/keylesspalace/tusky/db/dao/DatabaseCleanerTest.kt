package com.keylesspalace.tusky.db.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.keylesspalace.tusky.components.notifications.fakeNotification
import com.keylesspalace.tusky.components.notifications.fakeReport
import com.keylesspalace.tusky.components.notifications.insert
import com.keylesspalace.tusky.components.timeline.fakeAccount
import com.keylesspalace.tusky.components.timeline.fakeHomeTimelineData
import com.keylesspalace.tusky.components.timeline.fakeStatus
import com.keylesspalace.tusky.components.timeline.insert
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.Converters
import com.keylesspalace.tusky.db.DatabaseCleaner
import com.keylesspalace.tusky.db.entity.HomeTimelineEntity
import com.keylesspalace.tusky.db.entity.NotificationEntity
import com.keylesspalace.tusky.db.entity.NotificationReportEntity
import com.keylesspalace.tusky.db.entity.TimelineAccountEntity
import com.keylesspalace.tusky.db.entity.TimelineStatusEntity
import com.keylesspalace.tusky.di.NetworkModule
import kotlin.reflect.KClass
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class DatabaseCleanerTest {
    private lateinit var timelineDao: TimelineDao
    private lateinit var dbCleaner: DatabaseCleaner
    private lateinit var db: AppDatabase

    private val moshi = NetworkModule.providesMoshi()

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addTypeConverter(Converters(moshi))
            .allowMainThreadQueries()
            .build()
        timelineDao = db.timelineDao()
        dbCleaner = DatabaseCleaner(db)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun cleanupOldData() = runTest {
        fillDatabase()

        dbCleaner.cleanupOldData(tuskyAccountId = 1, timelineLimit = 3, notificationLimit = 3)

        // all but 3 timeline items and notifications and all references items should be gone for Tusky account 1
        // items of Tusky account 2 should be untouched
        expect(
            hometimelineItems = listOf(
                1L to "10",
                1L to "100",
                1L to "8",
                2L to "2"
            ),
            statuses = listOf(
                1L to "10",
                1L to "100",
                1L to "8",
                1L to "n3",
                1L to "n4",
                1L to "n5",
                2L to "2",
                2L to "n1",
                2L to "n2",
                2L to "n3",
                2L to "n4"
            ),
            notifications = listOf(
                1L to "3",
                1L to "4",
                1L to "5",
                2L to "1",
                2L to "2",
                2L to "3",
                2L to "4",
            ),
            accounts = listOf(
                1L to "10",
                1L to "100",
                1L to "3",
                1L to "R10",
                1L to "n3",
                1L to "n4",
                1L to "n5",
                1L to "r2",
                2L to "100",
                2L to "5",
                2L to "n1",
                2L to "n2",
                2L to "n3",
                2L to "n4",
                2L to "r1"
            ),
            reports = listOf(
                1L to "2",
                2L to "1"
            ),
        )
    }

    @Test
    fun cleanupEverything() = runTest {
        fillDatabase()

        dbCleaner.cleanupEverything(tuskyAccountId = 1)

        // everything from Tusky account 1 should be gone
        // items of Tusky account 2 should be untouched
        expect(
            hometimelineItems = listOf(
                2L to "2"
            ),
            statuses = listOf(
                2L to "2",
                2L to "n1",
                2L to "n2",
                2L to "n3",
                2L to "n4"
            ),
            notifications = listOf(
                2L to "1",
                2L to "2",
                2L to "3",
                2L to "4",
            ),
            accounts = listOf(
                2L to "100",
                2L to "5",
                2L to "n1",
                2L to "n2",
                2L to "n3",
                2L to "n4",
                2L to "r1"
            ),
            reports = listOf(
                2L to "1"
            ),
        )
    }

    private suspend fun fillDatabase() {
        db.insert(
            listOf(
                fakeHomeTimelineData(id = "100", authorServerId = "100"),
                fakeHomeTimelineData(id = "10", authorServerId = "3"),
                fakeHomeTimelineData(id = "8", reblogAuthorServerId = "R10", authorServerId = "10"),
                fakeHomeTimelineData(id = "5", authorServerId = "100"),
                fakeHomeTimelineData(id = "3", authorServerId = "4"),
                fakeHomeTimelineData(id = "1", authorServerId = "5")
            ),
            tuskyAccountId = 1
        )
        db.insert(
            listOf(
                fakeHomeTimelineData(id = "2", tuskyAccountId = 2, authorServerId = "5")
            ),
            tuskyAccountId = 2
        )

        db.insert(
            listOf(
                fakeNotification(id = "1", account = fakeAccount(id = "n1"), status = fakeStatus(id = "n1")),
                fakeNotification(id = "2", account = fakeAccount(id = "n2"), status = fakeStatus(id = "n2"), report = fakeReport(targetAccount = fakeAccount(id = "r1"))),
                fakeNotification(id = "3", account = fakeAccount(id = "n3"), status = fakeStatus(id = "n3")),
                fakeNotification(id = "4", account = fakeAccount(id = "n4"), status = fakeStatus(id = "n4"), report = fakeReport(id = "2", targetAccount = fakeAccount(id = "r2"))),
                fakeNotification(id = "5", account = fakeAccount(id = "n5"), status = fakeStatus(id = "n5")),
            ),
            tuskyAccountId = 1
        )
        db.insert(
            listOf(
                fakeNotification(id = "1", account = fakeAccount(id = "n1"), status = fakeStatus(id = "n1")),
                fakeNotification(id = "2", account = fakeAccount(id = "n2"), status = fakeStatus(id = "n2")),
                fakeNotification(id = "3", account = fakeAccount(id = "n3"), status = fakeStatus(id = "n3")),
                fakeNotification(id = "4", account = fakeAccount(id = "n4"), status = fakeStatus(id = "n4"), report = fakeReport(targetAccount = fakeAccount(id = "r1")))
            ),
            tuskyAccountId = 2
        )
    }

    private fun expect(
        hometimelineItems: List<Pair<Long, String>>,
        statuses: List<Pair<Long, String>>,
        notifications: List<Pair<Long, String>>,
        accounts: List<Pair<Long, String>>,
        reports: List<Pair<Long, String>>,
    ) {
        expect(HomeTimelineEntity::class, "id", hometimelineItems)
        expect(TimelineStatusEntity::class, "serverId", statuses)
        expect(NotificationEntity::class, "id", notifications)
        expect(TimelineAccountEntity::class, "serverId", accounts)
        expect(NotificationReportEntity::class, "serverId", reports)
    }

    private fun expect(
        entity: KClass<*>,
        idName: String,
        expectedItems: List<Pair<Long, String>>
    ) {
        val loadedItems: MutableList<Pair<Long, String>> = mutableListOf()
        val cursor = db.query("SELECT tuskyAccountId, $idName FROM ${entity.simpleName} ORDER BY tuskyAccountId, $idName", null)
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            val tuskyAccountId: Long = cursor.getLong(cursor.getColumnIndex("tuskyAccountId"))
            val id: String = cursor.getString(cursor.getColumnIndex(idName))
            loadedItems.add(tuskyAccountId to id)
            cursor.moveToNext()
        }
        cursor.close()

        assertEquals(expectedItems, loadedItems)
    }
}
