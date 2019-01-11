package com.keylesspalace.tusky

import android.arch.persistence.room.Room
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.keylesspalace.tusky.db.*
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.repository.TimelineRepository
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

    @Test
    fun cleanup() {
        val now = System.currentTimeMillis()
        val oldDate = now - TimelineRepository.CLEANUP_INTERVAL - 20_000
        val oldByThisAccount = makeStatus(
                statusId = 30,
                createdAt = oldDate
        )
        val oldByAnotherAccount = makeStatus(
                statusId = 10,
                createdAt = oldDate,
                authorServerId = "100"
        )
        val oldForAnotherAccount = makeStatus(
                accountId = 2,
                statusId = 20,
                authorServerId = "200",
                createdAt = oldDate
        )
        val recentByThisAccount = makeStatus(
                statusId = 50,
                createdAt = System.currentTimeMillis()
        )
        val recentByAnotherAccount = makeStatus(
                statusId = 60,
                createdAt = System.currentTimeMillis(),
                authorServerId = "200"
                )

        for ((status, author, reblogAuthor) in listOf(oldByThisAccount, oldByAnotherAccount,
                oldForAnotherAccount, recentByThisAccount, recentByAnotherAccount)) {
            timelineDao.insertInTransaction(status, author, reblogAuthor)
        }

        timelineDao.cleanup(1, "20",  now - TimelineRepository.CLEANUP_INTERVAL)

        assertEquals(
                listOf(recentByAnotherAccount, recentByThisAccount, oldByThisAccount),
                timelineDao.getStatusesForAccount(1, null, null, 100).blockingGet()
                        .map { it.toTriple() }
        )

        assertEquals(
                listOf(oldForAnotherAccount),
                timelineDao.getStatusesForAccount(2, null, null, 100).blockingGet()
                        .map { it.toTriple() }
        )
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
                "birb.site",
                "localUsername",
                "username",
                "displayName",
                "blah",
                "avatar"
        )

        val reblogAuthor = if (reblog) {
            TimelineAccountEntity(
                    "R$authorServerId",
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
                authorServerId = authorServerId,
                instance = "birb.site$statusId",
                inReplyToId = "inReplyToId$statusId",
                inReplyToAccountId = "inReplyToAccountId$statusId",
                content = "Content!$statusId",
                createdAt = createdAt,
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

    private fun TimelineStatusWithAccount.toTriple() = Triple(status, account, reblogAccount)
}