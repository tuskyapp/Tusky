package com.keylesspalace.tusky

import android.arch.persistence.room.Room
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.TimelineAccountEntity
import com.keylesspalace.tusky.db.TimelineDao
import com.keylesspalace.tusky.db.TimelineStatusEntity
import com.keylesspalace.tusky.entity.Status
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimelineDAOTest {
    private lateinit var timelineDao: TimelineDao
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getTargetContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        timelineDao = db.timelineDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertGetStatus() {
        val setOne = makeStatus()
        val setTwo = makeStatus(statusId = 20, reblog = true)
        val ignoredOne = makeStatus(statusId = 1)
        val ignoredTwo = makeStatus(accountId = 2)

        for ((status, author, reblogger) in listOf(setOne, setTwo, ignoredOne, ignoredTwo)) {
            timelineDao.insertInTransaction(status, author, reblogger)
        }

        val resultsFromDb = timelineDao.getStatusesForAccount(setOne.first.timelineUserId,
                maxId = "21", sinceId = ignoredOne.first.serverId, limit = 10)
                .blockingGet()

        assertEquals(2, resultsFromDb.size)
        for ((set, fromDb) in listOf(setTwo, setOne).zip(resultsFromDb)) {
            val (status, author, reblogger) = set
            assertEquals(status, fromDb.status)
            assertEquals(author, fromDb.account)
            assertEquals(reblogger, fromDb.reblogAccount)
        }
    }

    @Test
    fun doNotOverwrite() {
        val (status, author) = makeStatus()
        timelineDao.insertInTransaction(status, author, null)

        val placeholder = createPlaceholder(status.serverId, status.timelineUserId)

        timelineDao.insertStatusIfNotThere(placeholder)

        val fromDb = timelineDao.getStatusesForAccount(status.timelineUserId, null, null, 10)
                .blockingGet()
        val result = fromDb.first()

        assertEquals(1, fromDb.size)
        assertEquals(author, result.account)
        assertEquals(status, result.status)
        assertNull(result.reblogAccount)

    }

    private fun makeStatus(
            accountId: Long = 1,
            statusId: Long = 10,
            reblog: Boolean = false
    ): Triple<TimelineStatusEntity, TimelineAccountEntity, TimelineAccountEntity?> {
        val authorId = "20"
        val author = TimelineAccountEntity(
                authorId,
                accountId,
                "birb.site",
                "localUsername",
                "username",
                "displayName",
                "blah",
                "avatar"
        )

        val reblogAuthor = if (reblog) {
            TimelineAccountEntity(
                    "R$authorId",
                    accountId,
                    "Rbirb.site",
                    "RlocalUsername",
                    "Rusername",
                    "RdisplayName",
                    "Rblah",
                    "Ravatar"
            )
        } else null


        val even = accountId % 2 == 0L
        val status = TimelineStatusEntity(
                serverId = statusId.toString(),
                url = "url$statusId",
                timelineUserId = accountId,
                authorServerId = authorId,
                instance = "birb.site$statusId",
                inReplyToId = "inReplyToId$statusId",
                inReplyToAccountId = "inReplyToAccountId$statusId",
                content = "Content!$statusId",
                createdAt = statusId,
                emojis = "emojis$statusId",
                reblogsCount = 1 * statusId,
                favouritesCount = 2 * statusId,
                reblogged = even,
                favourited = !even,
                sensitive = even,
                spoilerText = "spoier$statusId",
                visibility = Status.Visibility.PRIVATE,
                attachments = "attachments$accountId",
                mentions = "mentions$accountId",
                application = "application$accountId",
                reblogServerId = if (reblog) (statusId * 100).toString() else null,
                reblogAccountId = reblogAuthor?.serverId
        )
        return Triple(status, author, reblogAuthor)
    }

    fun createPlaceholder(serverId: String, timelineUserId: Long): TimelineStatusEntity {
        return TimelineStatusEntity(
                serverId = serverId,
                url = null,
                timelineUserId = timelineUserId,
                authorServerId = null,
                instance = null,
                inReplyToId = null,
                inReplyToAccountId = null,
                content = null,
                createdAt = 0L,
                emojis = null,
                reblogsCount = 0,
                favouritesCount = 0,
                reblogged = false,
                favourited = false,
                sensitive = false,
                spoilerText = null,
                visibility = null,
                attachments = null,
                mentions = null,
                application = null,
                reblogServerId = null,
                reblogAccountId = null

        )
    }
}