package com.keylesspalace.tusky.db

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.keylesspalace.tusky.appstore.CacheUpdater
import com.keylesspalace.tusky.entity.Status
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class TimelineDAOTest {
    private lateinit var timelineDao: TimelineDao
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addTypeConverter(Converters(Gson()))
            .allowMainThreadQueries()
            .build()
        timelineDao = db.timelineDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertGetStatus() = runBlocking {
        val setOne = makeStatus(statusId = 3)
        val setTwo = makeStatus(statusId = 20, reblog = true)
        val ignoredOne = makeStatus(statusId = 1)
        val ignoredTwo = makeStatus(accountId = 2)

        for ((status, author, reblogger) in listOf(setOne, setTwo, ignoredOne, ignoredTwo)) {
            timelineDao.insertAccount(author)
            reblogger?.let {
                timelineDao.insertAccount(it)
            }
            timelineDao.insertStatus(status)
        }

        val pagingSource = timelineDao.getStatusesForAccount(setOne.first.timelineUserId)

        val loadResult = pagingSource.load(PagingSource.LoadParams.Refresh(null, 2, false))

        val loadedStatuses = (loadResult as PagingSource.LoadResult.Page).data

        assertEquals(2, loadedStatuses.size)
        assertStatuses(listOf(setTwo, setOne), loadedStatuses)
    }

    @Test
    fun cleanup() = runBlocking {
        val now = System.currentTimeMillis()
        val oldDate = now - CacheUpdater.CLEANUP_INTERVAL - 20_000
        val oldThisAccount = makeStatus(
            statusId = 5,
            createdAt = oldDate
        )
        val oldAnotherAccount = makeStatus(
            statusId = 10,
            createdAt = oldDate,
            accountId = 2
        )
        val recentThisAccount = makeStatus(
            statusId = 30,
            createdAt = System.currentTimeMillis()
        )
        val recentAnotherAccount = makeStatus(
            statusId = 60,
            createdAt = System.currentTimeMillis(),
            accountId = 2
        )

        for ((status, author, reblogAuthor) in listOf(oldThisAccount, oldAnotherAccount, recentThisAccount, recentAnotherAccount)) {
            timelineDao.insertAccount(author)
            reblogAuthor?.let {
                timelineDao.insertAccount(it)
            }
            timelineDao.insertStatus(status)
        }

        timelineDao.cleanup(now - CacheUpdater.CLEANUP_INTERVAL)

        val loadParams: PagingSource.LoadParams<Int> = PagingSource.LoadParams.Refresh(null, 100, false)

        val loadedStatusAccount1 = (timelineDao.getStatusesForAccount(1).load(loadParams) as PagingSource.LoadResult.Page).data
        val loadedStatusAccount2 = (timelineDao.getStatusesForAccount(2).load(loadParams) as PagingSource.LoadResult.Page).data

        assertStatuses(listOf(recentThisAccount), loadedStatusAccount1)
        assertStatuses(listOf(recentAnotherAccount), loadedStatusAccount2)
    }

    @Test
    fun overwriteDeletedStatus() = runBlocking {

        val oldStatuses = listOf(
            makeStatus(statusId = 3),
            makeStatus(statusId = 2),
            makeStatus(statusId = 1)
        )

        timelineDao.deleteRange(1, oldStatuses.last().first.serverId, oldStatuses.first().first.serverId)

        for ((status, author, reblogAuthor) in oldStatuses) {
            timelineDao.insertAccount(author)
            reblogAuthor?.let {
                timelineDao.insertAccount(it)
            }
            timelineDao.insertStatus(status)
        }

        // status 2 gets deleted, newly loaded status contain only 1 + 3
        val newStatuses = listOf(
            makeStatus(statusId = 3),
            makeStatus(statusId = 1)
        )

        timelineDao.deleteRange(1, newStatuses.last().first.serverId, newStatuses.first().first.serverId)

        for ((status, author, reblogAuthor) in newStatuses) {
            timelineDao.insertAccount(author)
            reblogAuthor?.let {
                timelineDao.insertAccount(it)
            }
            timelineDao.insertStatus(status)
        }

        // make sure status 2 is no longer in db

        val pagingSource = timelineDao.getStatusesForAccount(1)

        val loadResult = pagingSource.load(PagingSource.LoadParams.Refresh(null, 100, false))

        val loadedStatuses = (loadResult as PagingSource.LoadResult.Page).data

        assertStatuses(newStatuses, loadedStatuses)
    }

    private fun makeStatus(
        accountId: Long = 1,
        statusId: Long = 10,
        reblog: Boolean = false,
        createdAt: Long = statusId,
        authorServerId: String = "20"
    ): Triple<TimelineStatusEntity, TimelineAccountEntity, TimelineAccountEntity?> {
        val author = TimelineAccountEntity(
            authorServerId,
            accountId,
            "localUsername",
            "username",
            "displayName",
            "blah",
            "avatar",
            "[\"tusky\": \"http://tusky.cool/emoji.jpg\"]",
            false
        )

        val reblogAuthor = if (reblog) {
            TimelineAccountEntity(
                "R$authorServerId",
                accountId,
                "RlocalUsername",
                "Rusername",
                "RdisplayName",
                "Rblah",
                "Ravatar",
                "[]",
                false
            )
        } else null

        val even = accountId % 2 == 0L
        val status = TimelineStatusEntity(
            serverId = statusId.toString(),
            url = "url$statusId",
            timelineUserId = accountId,
            authorServerId = authorServerId,
            inReplyToId = "inReplyToId$statusId",
            inReplyToAccountId = "inReplyToAccountId$statusId",
            content = "Content!$statusId",
            createdAt = createdAt,
            emojis = "emojis$statusId",
            reblogsCount = 1 * statusId.toInt(),
            favouritesCount = 2 * statusId.toInt(),
            reblogged = even,
            favourited = !even,
            bookmarked = false,
            sensitive = even,
            spoilerText = "spoier$statusId",
            visibility = Status.Visibility.PRIVATE,
            attachments = "attachments$accountId",
            mentions = "mentions$accountId",
            application = "application$accountId",
            reblogServerId = if (reblog) (statusId * 100).toString() else null,
            reblogAccountId = reblogAuthor?.serverId,
            poll = null,
            muted = false,
            expanded = false,
            contentCollapsed = false,
            contentHidden = false,
            pinned = false
        )
        return Triple(status, author, reblogAuthor)
    }

    private fun assertStatuses(
        expected: List<Triple<TimelineStatusEntity, TimelineAccountEntity, TimelineAccountEntity?>>,
        provided: List<TimelineStatusWithAccount>
    ) {
        for ((exp, prov) in expected.zip(provided)) {
            val (status, author, reblogger) = exp
            assertEquals(status, prov.status)
            assertEquals(author, prov.account)
            assertEquals(reblogger, prov.reblogAccount)
        }
    }
}
