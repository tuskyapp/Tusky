package com.keylesspalace.tusky.db

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.keylesspalace.tusky.components.timeline.Placeholder
import com.keylesspalace.tusky.components.timeline.toEntity
import com.keylesspalace.tusky.entity.Status
import kotlinx.coroutines.runBlocking
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

        val pagingSource = timelineDao.getStatuses(setOne.first.timelineUserId)

        val loadResult = pagingSource.load(PagingSource.LoadParams.Refresh(null, 2, false))

        val loadedStatuses = (loadResult as PagingSource.LoadResult.Page).data

        assertEquals(2, loadedStatuses.size)
        assertStatuses(listOf(setTwo, setOne), loadedStatuses)
    }

    @Test
    fun cleanup() = runBlocking {

        val statusesBeforeCleanup = listOf(
            makeStatus(statusId = 100),
            makeStatus(statusId = 10, authorServerId = "3"),
            makeStatus(statusId = 8, reblog = true, authorServerId = "10"),
            makeStatus(statusId = 5),
            makeStatus(statusId = 3, authorServerId = "4"),
            makeStatus(statusId = 2, accountId = 2, authorServerId = "5"),
            makeStatus(statusId = 1, authorServerId = "5")
        )

        val statusesAfterCleanup = listOf(
            makeStatus(statusId = 100),
            makeStatus(statusId = 10, authorServerId = "3"),
            makeStatus(statusId = 8, reblog = true, authorServerId = "10"),
            makeStatus(statusId = 2, accountId = 2, authorServerId = "5"),
        )

        for ((status, author, reblogAuthor) in statusesBeforeCleanup) {
            timelineDao.insertAccount(author)
            reblogAuthor?.let {
                timelineDao.insertAccount(it)
            }
            timelineDao.insertStatus(status)
        }

        timelineDao.cleanup(accountId = 1, limit = 3)
        timelineDao.cleanupAccounts(accountId = 1)

        val loadParams: PagingSource.LoadParams<Int> = PagingSource.LoadParams.Refresh(null, 100, false)

        val loadedStatuses = (timelineDao.getStatuses(1).load(loadParams) as PagingSource.LoadResult.Page).data

        assertStatuses(statusesAfterCleanup, loadedStatuses)

        val loadedAccounts: MutableList<Pair<Long, String>> = mutableListOf()
        val accountCursor = db.query("SELECT timelineUserId, serverId FROM TimelineAccountEntity ORDER BY timelineUserId, serverId", null)
        accountCursor.moveToFirst()
        while (!accountCursor.isAfterLast) {
            val accountId: Long = accountCursor.getLong(accountCursor.getColumnIndex("timelineUserId"))
            val serverId: String = accountCursor.getString(accountCursor.getColumnIndex("serverId"))
            loadedAccounts.add(accountId to serverId)
            accountCursor.moveToNext()
        }

        val expectedAccounts = listOf(
            1L to "10",
            1L to "20",
            1L to "3",
            1L to "R10",
            2L to "5"
        )

        assertEquals(expectedAccounts, loadedAccounts)
    }

    @Test
    fun overwriteDeletedStatus() = runBlocking {

        val oldStatuses = listOf(
            makeStatus(statusId = 3),
            makeStatus(statusId = 2),
            makeStatus(statusId = 1)
        )

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

        val deletedCount = timelineDao.deleteRange(1, newStatuses.last().first.serverId, newStatuses.first().first.serverId)
        assertEquals(3, deletedCount)

        for ((status, author, reblogAuthor) in newStatuses) {
            timelineDao.insertAccount(author)
            reblogAuthor?.let {
                timelineDao.insertAccount(it)
            }
            timelineDao.insertStatus(status)
        }

        // make sure status 2 is no longer in db

        val pagingSource = timelineDao.getStatuses(1)

        val loadResult = pagingSource.load(PagingSource.LoadParams.Refresh(null, 100, false))

        val loadedStatuses = (loadResult as PagingSource.LoadResult.Page).data

        assertStatuses(newStatuses, loadedStatuses)
    }

    @Test
    fun deleteRange() = runBlocking {
        val statuses = listOf(
            makeStatus(statusId = 100),
            makeStatus(statusId = 50),
            makeStatus(statusId = 15),
            makeStatus(statusId = 14),
            makeStatus(statusId = 13),
            makeStatus(statusId = 13, accountId = 2),
            makeStatus(statusId = 12),
            makeStatus(statusId = 11),
            makeStatus(statusId = 9)
        )

        for ((status, author, reblogAuthor) in statuses) {
            timelineDao.insertAccount(author)
            reblogAuthor?.let {
                timelineDao.insertAccount(it)
            }
            timelineDao.insertStatus(status)
        }

        assertEquals(3, timelineDao.deleteRange(1, "12", "14"))
        assertEquals(0, timelineDao.deleteRange(1, "80", "80"))
        assertEquals(0, timelineDao.deleteRange(1, "60", "80"))
        assertEquals(0, timelineDao.deleteRange(1, "5", "8"))
        assertEquals(0, timelineDao.deleteRange(1, "101", "1000"))
        assertEquals(1, timelineDao.deleteRange(1, "50", "50"))

        val loadParams: PagingSource.LoadParams<Int> = PagingSource.LoadParams.Refresh(null, 100, false)

        val statusesAccount1 = (timelineDao.getStatuses(1).load(loadParams) as PagingSource.LoadResult.Page).data
        val statusesAccount2 = (timelineDao.getStatuses(2).load(loadParams) as PagingSource.LoadResult.Page).data

        val remainingStatusesAccount1 = listOf(
            makeStatus(statusId = 100),
            makeStatus(statusId = 15),
            makeStatus(statusId = 11),
            makeStatus(statusId = 9)
        )

        val remainingStatusesAccount2 = listOf(
            makeStatus(statusId = 13, accountId = 2)
        )

        assertStatuses(remainingStatusesAccount1, statusesAccount1)
        assertStatuses(remainingStatusesAccount2, statusesAccount2)
    }

    @Test
    fun deleteAllForInstance() = runBlocking {

        val statusWithRedDomain1 = makeStatus(
            statusId = 15,
            accountId = 1,
            domain = "mastodon.red",
            authorServerId = "1"
        )
        val statusWithRedDomain2 = makeStatus(
            statusId = 14,
            accountId = 1,
            domain = "mastodon.red",
            authorServerId = "2"
        )
        val statusWithRedDomainOtherAccount = makeStatus(
            statusId = 12,
            accountId = 2,
            domain = "mastodon.red",
            authorServerId = "2"
        )
        val statusWithBlueDomain = makeStatus(
            statusId = 10,
            accountId = 1,
            domain = "mastodon.blue",
            authorServerId = "4"
        )
        val statusWithBlueDomainOtherAccount = makeStatus(
            statusId = 10,
            accountId = 2,
            domain = "mastodon.blue",
            authorServerId = "5"
        )
        val statusWithGreenDomain = makeStatus(
            statusId = 8,
            accountId = 1,
            domain = "mastodon.green",
            authorServerId = "6"
        )

        for ((status, author, reblogAuthor) in listOf(statusWithRedDomain1, statusWithRedDomain2, statusWithRedDomainOtherAccount, statusWithBlueDomain, statusWithBlueDomainOtherAccount, statusWithGreenDomain)) {
            timelineDao.insertAccount(author)
            reblogAuthor?.let {
                timelineDao.insertAccount(it)
            }
            timelineDao.insertStatus(status)
        }

        timelineDao.deleteAllFromInstance(1, "mastodon.red")
        timelineDao.deleteAllFromInstance(1, "mastodon.blu") // shouldn't delete anything
        timelineDao.deleteAllFromInstance(1, "greenmastodon.green") // shouldn't delete anything

        val loadParams: PagingSource.LoadParams<Int> = PagingSource.LoadParams.Refresh(null, 100, false)

        val statusesAccount1 = (timelineDao.getStatuses(1).load(loadParams) as PagingSource.LoadResult.Page).data
        val statusesAccount2 = (timelineDao.getStatuses(2).load(loadParams) as PagingSource.LoadResult.Page).data

        assertStatuses(listOf(statusWithBlueDomain, statusWithGreenDomain), statusesAccount1)
        assertStatuses(listOf(statusWithRedDomainOtherAccount, statusWithBlueDomainOtherAccount), statusesAccount2)
    }

    @Test
    fun `should return null as topId when db is empty`() = runBlocking {
        assertNull(timelineDao.getTopId(1))
    }

    @Test
    fun `should return correct topId`() = runBlocking {

        val statusData = listOf(
            makeStatus(
                statusId = 4,
                accountId = 1,
                domain = "mastodon.test",
                authorServerId = "1"
            ),
            makeStatus(
                statusId = 33,
                accountId = 1,
                domain = "mastodon.test",
                authorServerId = "2"
            ),
            makeStatus(
                statusId = 22,
                accountId = 1,
                domain = "mastodon.test",
                authorServerId = "2"
            )
        )

        for ((status, author, reblogAuthor) in statusData) {
            timelineDao.insertAccount(author)
            reblogAuthor?.let {
                timelineDao.insertAccount(it)
            }
            timelineDao.insertStatus(status)
        }

        assertEquals("33", timelineDao.getTopId(1))
    }

    @Test
    fun `should return correct placeholderId after other ids`() = runBlocking {

        val statusData = listOf(
            makeStatus(statusId = 1000),
            makePlaceholder(id = 99),
            makeStatus(statusId = 97),
            makeStatus(statusId = 95),
            makePlaceholder(id = 94),
            makeStatus(statusId = 90)
        )

        for ((status, author, reblogAuthor) in statusData) {
            author?.let {
                timelineDao.insertAccount(it)
            }
            reblogAuthor?.let {
                timelineDao.insertAccount(it)
            }
            timelineDao.insertStatus(status)
        }

        assertEquals("99", timelineDao.getNextPlaceholderIdAfter(1, "1000"))
        assertEquals("94", timelineDao.getNextPlaceholderIdAfter(1, "99"))
        assertNull(timelineDao.getNextPlaceholderIdAfter(1, "90"))
    }

    @Test
    fun `should return correct top placeholderId`() = runBlocking {

        val statusData = listOf(
            makeStatus(statusId = 1000),
            makePlaceholder(id = 99),
            makeStatus(statusId = 97),
            makePlaceholder(id = 96),
            makeStatus(statusId = 90),
            makePlaceholder(id = 80),
            makeStatus(statusId = 77)
        )

        for ((status, author, reblogAuthor) in statusData) {
            author?.let {
                timelineDao.insertAccount(it)
            }
            reblogAuthor?.let {
                timelineDao.insertAccount(it)
            }
            timelineDao.insertStatus(status)
        }

        assertEquals("99", timelineDao.getTopPlaceholderId(1))
    }

    @Test
    fun `preview card survives roundtrip`() = runBlocking {
        val setOne = makeStatus(statusId = 3, cardUrl = "https://foo.bar")

        for ((status, author, reblogger) in listOf(setOne)) {
            timelineDao.insertAccount(author)
            reblogger?.let {
                timelineDao.insertAccount(it)
            }
            timelineDao.insertStatus(status)
        }

        val pagingSource = timelineDao.getStatuses(setOne.first.timelineUserId)

        val loadResult = pagingSource.load(PagingSource.LoadParams.Refresh(null, 2, false))

        val loadedStatuses = (loadResult as PagingSource.LoadResult.Page).data

        assertEquals(1, loadedStatuses.size)
        assertStatuses(listOf(setOne), loadedStatuses)
    }

    private fun makeStatus(
        accountId: Long = 1,
        statusId: Long = 10,
        reblog: Boolean = false,
        createdAt: Long = statusId,
        authorServerId: String = "20",
        domain: String = "mastodon.example",
        cardUrl: String? = null,
    ): Triple<TimelineStatusEntity, TimelineAccountEntity, TimelineAccountEntity?> {
        val author = TimelineAccountEntity(
            serverId = authorServerId,
            timelineUserId = accountId,
            localUsername = "localUsername@$domain",
            username = "username@$domain",
            displayName = "displayName",
            url = "blah",
            avatar = "avatar",
            emojis = "[\"tusky\": \"http://tusky.cool/emoji.jpg\"]",
            bot = false
        )

        val reblogAuthor = if (reblog) {
            TimelineAccountEntity(
                serverId = "R$authorServerId",
                timelineUserId = accountId,
                localUsername = "RlocalUsername",
                username = "Rusername",
                displayName = "RdisplayName",
                url = "Rblah",
                avatar = "Ravatar",
                emojis = "[]",
                bot = false
            )
        } else null

        val card = when (cardUrl) {
            null -> null
            else -> "{ url: \"$cardUrl\" }"
        }
        val even = accountId % 2 == 0L
        val status = TimelineStatusEntity(
            serverId = statusId.toString(),
            url = "https://$domain/whatever/$statusId",
            timelineUserId = accountId,
            authorServerId = authorServerId,
            inReplyToId = "inReplyToId$statusId",
            inReplyToAccountId = "inReplyToAccountId$statusId",
            content = "Content!$statusId",
            createdAt = createdAt,
            editedAt = null,
            emojis = "emojis$statusId",
            reblogsCount = 1 * statusId.toInt(),
            favouritesCount = 2 * statusId.toInt(),
            repliesCount = 3 * statusId.toInt(),
            reblogged = even,
            favourited = !even,
            bookmarked = false,
            sensitive = even,
            spoilerText = "spoiler$statusId",
            visibility = Status.Visibility.PRIVATE,
            attachments = "attachments$accountId",
            mentions = "mentions$accountId",
            tags = "tags$accountId",
            application = "application$accountId",
            reblogServerId = if (reblog) (statusId * 100).toString() else null,
            reblogAccountId = reblogAuthor?.serverId,
            poll = null,
            muted = false,
            expanded = false,
            contentCollapsed = false,
            contentShowing = true,
            pinned = false,
            card = card,
            language = null,
        )
        return Triple(status, author, reblogAuthor)
    }

    private fun makePlaceholder(
        accountId: Long = 1,
        id: Long
    ): Triple<TimelineStatusEntity, TimelineAccountEntity?, TimelineAccountEntity?> {
        val placeholder = Placeholder(id.toString(), false).toEntity(accountId)
        return Triple(placeholder, null, null)
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
