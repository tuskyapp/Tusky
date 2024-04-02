package com.keylesspalace.tusky.db

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.keylesspalace.tusky.components.timeline.insert
import com.keylesspalace.tusky.components.timeline.mockHomeTimelineData
import com.keylesspalace.tusky.components.timeline.mockPlaceholderHomeTimelineData
import com.keylesspalace.tusky.db.dao.CleanupDao
import com.keylesspalace.tusky.db.dao.TimelineDao
import com.keylesspalace.tusky.di.NetworkModule
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
class TimelineDaoTest {
    private lateinit var timelineDao: TimelineDao
    private lateinit var cleanupDao: CleanupDao
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
        cleanupDao = db.cleanupDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertGetStatus() = runTest {
        val setOne = mockHomeTimelineData(id = "3")
        val setTwo = mockHomeTimelineData(id = "20", reblog = true)
        val ignoredOne = mockHomeTimelineData(id = "1")
        val ignoredTwo = mockHomeTimelineData(id = "2", tuskyAccountId = 2)

        db.insert(
            listOf(setOne, setTwo, ignoredOne),
            tuskyAccountId = 1
        )
        db.insert(
            listOf(ignoredTwo),
            tuskyAccountId = 2
        )

        val pagingSource = timelineDao.getHomeTimeline(1)

        val loadResult = pagingSource.load(PagingSource.LoadParams.Refresh(null, 2, false))

        val loadedStatuses = (loadResult as PagingSource.LoadResult.Page).data

        assertEquals(2, loadedStatuses.size)
        assertEquals(listOf(setTwo, setOne), loadedStatuses)
    }

    @Test
    fun cleanup() = runTest {
        val statusesBeforeCleanup = listOf(
            mockHomeTimelineData(id = "100", authorServerId = "100"),
            mockHomeTimelineData(id = "10", authorServerId = "3"),
            mockHomeTimelineData(id = "8", reblog = true, authorServerId = "10"),
            mockHomeTimelineData(id = "5", authorServerId = "100"),
            mockHomeTimelineData(id = "3", authorServerId = "4"),
            mockHomeTimelineData(id = "2", tuskyAccountId = 2, authorServerId = "5"),
            mockHomeTimelineData(id = "1", authorServerId = "5")
        )

        db.insert(statusesBeforeCleanup - statusesBeforeCleanup[5], 1)
        db.insert(listOf(statusesBeforeCleanup[5]), 2)

        cleanupDao.cleanupOldData(tuskyAccountId = 1, timelineLimit = 3, notificationLimit = 3)

        val loadedStatuses: MutableList<Pair<Long, String>> = mutableListOf()
        val statusesCursor = db.query("SELECT tuskyAccountId, serverId FROM TimelineStatusEntity ORDER BY tuskyAccountId, serverId", null)
        statusesCursor.moveToFirst()
        while (!statusesCursor.isAfterLast) {
            val tuskyAccountId: Long = statusesCursor.getLong(statusesCursor.getColumnIndex("tuskyAccountId"))
            val serverId: String = statusesCursor.getString(statusesCursor.getColumnIndex("serverId"))
            loadedStatuses.add(tuskyAccountId to serverId)
            statusesCursor.moveToNext()
        }
        statusesCursor.close()

        val expectedStatuses = listOf(
            1L to "10",
            1L to "100",
            1L to "8",
            2L to "2"
        )

        assertEquals(expectedStatuses, loadedStatuses)

        val loadedAccounts: MutableList<Pair<Long, String>> = mutableListOf()
        val accountCursor = db.query("SELECT tuskyAccountId, serverId FROM TimelineAccountEntity ORDER BY tuskyAccountId, serverId", null)
        accountCursor.moveToFirst()
        while (!accountCursor.isAfterLast) {
            val tuskyAccountId: Long = accountCursor.getLong(accountCursor.getColumnIndex("tuskyAccountId"))
            val serverId: String = accountCursor.getString(accountCursor.getColumnIndex("serverId"))
            loadedAccounts.add(tuskyAccountId to serverId)
            accountCursor.moveToNext()
        }
        accountCursor.close()

        val expectedAccounts = listOf(
            1L to "10",
            1L to "100",
            1L to "3",
            1L to "R10",
            2L to "5"
        )

        assertEquals(expectedAccounts, loadedAccounts)
    }

    @Test
    fun overwriteDeletedStatus() = runTest {
        val oldStatuses = listOf(
            mockHomeTimelineData(id = "3"),
            mockHomeTimelineData(id = "2"),
            mockHomeTimelineData(id = "1")
        )

        db.insert(oldStatuses, 1)

        // status 2 gets deleted, newly loaded status contain only 1 + 3
        val newStatuses = listOf(
            mockHomeTimelineData(id = "3"),
            mockHomeTimelineData(id = "1")
        )

        val deletedCount = timelineDao.deleteRange(1, newStatuses.last().id, newStatuses.first().id)
        assertEquals(3, deletedCount)

        db.insert(newStatuses, 1)

        // make sure status 2 is no longer in db
        val pagingSource = timelineDao.getHomeTimeline(1)

        val loadResult = pagingSource.load(PagingSource.LoadParams.Refresh(null, 100, false))

        val loadedStatuses = (loadResult as PagingSource.LoadResult.Page).data

        assertEquals(newStatuses, loadedStatuses)
    }

    @Test
    fun deleteRange() = runTest {
        val statuses = listOf(
            mockHomeTimelineData(id = "100"),
            mockHomeTimelineData(id = "50"),
            mockHomeTimelineData(id = "15"),
            mockHomeTimelineData(id = "14"),
            mockHomeTimelineData(id = "13"),
            mockHomeTimelineData(id = "13", tuskyAccountId = 2),
            mockHomeTimelineData(id = "12"),
            mockHomeTimelineData(id = "11"),
            mockHomeTimelineData(id = "9")
        )

        db.insert(statuses - statuses[5], 1)
        db.insert(listOf(statuses[5]), 2)

        assertEquals(3, timelineDao.deleteRange(1, "12", "14"))
        assertEquals(0, timelineDao.deleteRange(1, "80", "80"))
        assertEquals(0, timelineDao.deleteRange(1, "60", "80"))
        assertEquals(0, timelineDao.deleteRange(1, "5", "8"))
        assertEquals(0, timelineDao.deleteRange(1, "101", "1000"))
        assertEquals(1, timelineDao.deleteRange(1, "50", "50"))

        val loadParams: PagingSource.LoadParams<Int> = PagingSource.LoadParams.Refresh(null, 100, false)

        val statusesAccount1 = (timelineDao.getHomeTimeline(1).load(loadParams) as PagingSource.LoadResult.Page).data
        val statusesAccount2 = (timelineDao.getHomeTimeline(2).load(loadParams) as PagingSource.LoadResult.Page).data

        val remainingStatusesAccount1 = listOf(
            mockHomeTimelineData(id = "100"),
            mockHomeTimelineData(id = "15"),
            mockHomeTimelineData(id = "11"),
            mockHomeTimelineData(id = "9")
        )

        val remainingStatusesAccount2 = listOf(
            mockHomeTimelineData(id = "13", tuskyAccountId = 2)
        )

        assertEquals(remainingStatusesAccount1, statusesAccount1)
        assertEquals(remainingStatusesAccount2, statusesAccount2)
    }

    @Test
    fun deleteAllForInstance() = runTest {
        val statusWithRedDomain1 = mockHomeTimelineData(
            id = "15",
            tuskyAccountId = 1,
            domain = "mastodon.red",
            authorServerId = "1"
        )
        val statusWithRedDomain2 = mockHomeTimelineData(
            id = "14",
            tuskyAccountId = 1,
            domain = "mastodon.red",
            authorServerId = "2"
        )
        val statusWithRedDomainOtherAccount = mockHomeTimelineData(
            id = "12",
            tuskyAccountId = 2,
            domain = "mastodon.red",
            authorServerId = "2"
        )
        val statusWithBlueDomain = mockHomeTimelineData(
            id = "10",
            tuskyAccountId = 1,
            domain = "mastodon.blue",
            authorServerId = "4"
        )
        val statusWithBlueDomainOtherAccount = mockHomeTimelineData(
            id = "10",
            tuskyAccountId = 2,
            domain = "mastodon.blue",
            authorServerId = "5"
        )
        val statusWithGreenDomain = mockHomeTimelineData(
            id = "8",
            tuskyAccountId = 1,
            domain = "mastodon.green",
            authorServerId = "6"
        )

        db.insert(listOf(statusWithRedDomain1, statusWithRedDomain2, statusWithBlueDomain, statusWithGreenDomain), 1)
        db.insert(listOf(statusWithRedDomainOtherAccount, statusWithBlueDomainOtherAccount), 2)

        timelineDao.deleteAllFromInstance(1, "mastodon.red")
        timelineDao.deleteAllFromInstance(1, "mastodon.blu") // shouldn't delete anything
        timelineDao.deleteAllFromInstance(1, "greenmastodon.green") // shouldn't delete anything

        val loadParams: PagingSource.LoadParams<Int> = PagingSource.LoadParams.Refresh(null, 100, false)

        val statusesAccount1 = (timelineDao.getHomeTimeline(1).load(loadParams) as PagingSource.LoadResult.Page).data
        val statusesAccount2 = (timelineDao.getHomeTimeline(2).load(loadParams) as PagingSource.LoadResult.Page).data

        assertEquals(listOf(statusWithBlueDomain, statusWithGreenDomain), statusesAccount1)
        assertEquals(listOf(statusWithRedDomainOtherAccount, statusWithBlueDomainOtherAccount), statusesAccount2)
    }

    @Test
    fun `should return null as topId when db is empty`() = runTest {
        assertNull(timelineDao.getTopId(1))
    }

    @Test
    fun `should return correct topId`() = runTest {
        val statusData = listOf(
            mockHomeTimelineData(
                id = "4",
                tuskyAccountId = 1,
                domain = "mastodon.test",
                authorServerId = "1"
            ),
            mockHomeTimelineData(
                id = "33",
                tuskyAccountId = 1,
                domain = "mastodon.test",
                authorServerId = "2"
            ),
            mockHomeTimelineData(
                id = "22",
                tuskyAccountId = 1,
                domain = "mastodon.test",
                authorServerId = "2"
            )
        )

        db.insert(statusData, 1)

        assertEquals("33", timelineDao.getTopId(1))
    }

    @Test
    fun `should return correct placeholderId after other ids`() = runTest {
        val statusData = listOf(
            mockHomeTimelineData(id = "1000"),
            mockPlaceholderHomeTimelineData(id = "99"),
            mockHomeTimelineData(id = "97"),
            mockHomeTimelineData(id = "95"),
            mockPlaceholderHomeTimelineData(id = "94"),
            mockHomeTimelineData(id = "90")
        )

        db.insert(statusData, 1)

        assertEquals("99", timelineDao.getNextPlaceholderIdAfter(1, "1000"))
        assertEquals("94", timelineDao.getNextPlaceholderIdAfter(1, "99"))
        assertNull(timelineDao.getNextPlaceholderIdAfter(1, "90"))
    }

    @Test
    fun `should return correct top placeholderId`() = runTest {
        val statusData = listOf(
            mockHomeTimelineData(id = "1000"),
            mockPlaceholderHomeTimelineData(id = "99"),
            mockHomeTimelineData(id = "97"),
            mockPlaceholderHomeTimelineData(id = "96"),
            mockHomeTimelineData(id = "90"),
            mockPlaceholderHomeTimelineData(id = "80"),
            mockHomeTimelineData(id = "77")
        )

        db.insert(statusData)

        assertEquals("99", timelineDao.getTopPlaceholderId(1))
    }
}
