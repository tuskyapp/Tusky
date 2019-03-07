package com.keylesspalace.tusky.fragment

import android.text.Spanned
import com.google.gson.Gson
import com.keylesspalace.tusky.SpanUtilsTest
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.TimelineDao
import com.keylesspalace.tusky.db.TimelineStatusWithAccount
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.repository.*
import com.keylesspalace.tusky.util.Either
import com.keylesspalace.tusky.util.HtmlConverter
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Single
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.util.*
import java.util.concurrent.TimeUnit

class TimelineRepositoryTest {
    @Mock
    lateinit var timelineDao: TimelineDao

    @Mock
    lateinit var mastodonApi: MastodonApi

    @Mock
    private lateinit var accountManager: AccountManager

    private lateinit var gson: Gson

    private lateinit var subject: TimelineRepository

    private lateinit var testScheduler: TestScheduler


    private val limit = 30
    private val account = AccountEntity(
            id = 2,
            accessToken = "token",
            domain = "domain.com",
            isActive = true
    )
    private val htmlConverter = object : HtmlConverter {
        override fun fromHtml(html: String): Spanned {
            return SpanUtilsTest.FakeSpannable(html)
        }

        override fun toHtml(text: Spanned): String {
            return text.toString()
        }
    }

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        whenever(accountManager.activeAccount).thenReturn(account)

        gson = Gson()
        testScheduler = TestScheduler()
        RxJavaPlugins.setIoSchedulerHandler { testScheduler }
        subject = TimelineRepositoryImpl(timelineDao, mastodonApi, accountManager, gson,
                htmlConverter)
    }

    @Test
    fun testNetworkUnbounded() {
        val statuses = listOf(
                makeStatus("3"),
                makeStatus("2")
        )
        whenever(mastodonApi.homeTimelineSingle(isNull(), isNull(), anyInt()))
                .thenReturn(Single.just(statuses))
        val result = subject.getStatuses(null, null, null, limit, TimelineRequestMode.NETWORK)
                .blockingGet()

        assertEquals(statuses.map(Status::lift), result)
        testScheduler.advanceTimeBy(100, TimeUnit.SECONDS)
        verify(timelineDao).insertStatusIfNotThere(Placeholder("1").toEntity(account.id))
        for (status in statuses) {
            verify(timelineDao).insertInTransaction(
                    status.toEntity(account.id, htmlConverter, gson),
                    status.account.toEntity(account.id, gson),
                    null
            )
        }
        verifyNoMoreInteractions(timelineDao)
    }

    @Test
    fun testNetworkLoadingTopNoGap() {
        val response = listOf(
                makeStatus("4"),
                makeStatus("3"),
                makeStatus("2")
        )
        val sinceId = "2"
        val sinceIdMinusOne = "1"
        whenever(mastodonApi.homeTimelineSingle(null, sinceIdMinusOne, limit + 1))
                .thenReturn(Single.just(response))
        val result = subject.getStatuses(null, sinceId, sinceIdMinusOne, limit,
                TimelineRequestMode.NETWORK)
                .blockingGet()

        assertEquals(
                response.subList(0, 2).map(Status::lift),
                result
        )
        testScheduler.advanceTimeBy(100, TimeUnit.SECONDS)
        // We assume for now that overlapped one is inserted but it's not that important
        for (status in response) {
            verify(timelineDao).insertInTransaction(
                    status.toEntity(account.id, htmlConverter, gson),
                    status.account.toEntity(account.id, gson),
                    null
            )
        }
        verify(timelineDao).removeAllPlaceholdersBetween(account.id, response.first().id,
                response.last().id)
        verifyNoMoreInteractions(timelineDao)
    }

    @Test
    fun testNetworkLoadingTopWithGap() {
        val response = listOf(
                makeStatus("5"),
                makeStatus("4")
        )
        val sinceId = "2"
        val sinceIdMinusOne = "1"
        whenever(mastodonApi.homeTimelineSingle(null, sinceIdMinusOne, limit + 1))
                .thenReturn(Single.just(response))
        val result = subject.getStatuses(null, sinceId, sinceIdMinusOne, limit,
                TimelineRequestMode.NETWORK)
                .blockingGet()

        val placeholder = Placeholder("3")
        assertEquals(response.map(Status::lift) + Either.Left(placeholder), result)
        testScheduler.advanceTimeBy(100, TimeUnit.SECONDS)
        for (status in response) {
            verify(timelineDao).insertInTransaction(
                    status.toEntity(account.id, htmlConverter, gson),
                    status.account.toEntity(account.id, gson),
                    null
            )
        }
        verify(timelineDao).insertStatusIfNotThere(placeholder.toEntity(account.id))
        verifyNoMoreInteractions(timelineDao)
    }

    @Test
    fun testNetworkLoadingMiddleNoGap() {
        // Example timelne:
        // 5
        // 4
        // [gap]
        // 2
        // 1

        val response = listOf(
                makeStatus("5"),
                makeStatus("4"),
                makeStatus("3"),
                makeStatus("2")
        )
        val sinceId = "2"
        val sinceIdMinusOne = "1"
        val maxId = "3"
        whenever(mastodonApi.homeTimelineSingle(maxId, sinceIdMinusOne, limit + 1))
                .thenReturn(Single.just(response))
        val result = subject.getStatuses(maxId, sinceId, sinceIdMinusOne, limit,
                TimelineRequestMode.NETWORK)
                .blockingGet()

        assertEquals(
                response.subList(0, response.lastIndex).map(Status::lift),
                result
        )
        testScheduler.advanceTimeBy(100, TimeUnit.SECONDS)
        // We assume for now that overlapped one is inserted but it's not that important
        for (status in response) {
            verify(timelineDao).insertInTransaction(
                    status.toEntity(account.id, htmlConverter, gson),
                    status.account.toEntity(account.id, gson),
                    null
            )
        }
        verify(timelineDao).removeAllPlaceholdersBetween(account.id, response.first().id,
                response.last().id)
        verifyNoMoreInteractions(timelineDao)
    }

    @Test
    fun testNetworkLoadingMiddleWithGap() {
        // Example timelne:
        // 6
        // 5
        // [gap]
        // 2
        // 1

        val response = listOf(
                makeStatus("6"),
                makeStatus("5"),
                makeStatus("4")
        )
        val sinceId = "2"
        val sinceIdMinusOne = "1"
        val maxId = "4"
        whenever(mastodonApi.homeTimelineSingle(maxId, sinceIdMinusOne, limit + 1))
                .thenReturn(Single.just(response))
        val result = subject.getStatuses(maxId, sinceId, sinceIdMinusOne, limit,
                TimelineRequestMode.NETWORK)
                .blockingGet()

        val placeholder = Placeholder("3")
        assertEquals(
                response.map(Status::lift) + Either.Left(placeholder),
                result
        )
        testScheduler.advanceTimeBy(100, TimeUnit.SECONDS)
        // We assume for now that overlapped one is inserted but it's not that important
        for (status in response) {
            verify(timelineDao).insertInTransaction(
                    status.toEntity(account.id, htmlConverter, gson),
                    status.account.toEntity(account.id, gson),
                    null
            )
        }
        verify(timelineDao).removeAllPlaceholdersBetween(account.id, response.first().id,
                response.last().id)
        verify(timelineDao).insertStatusIfNotThere(placeholder.toEntity(account.id))
        verifyNoMoreInteractions(timelineDao)
    }

    @Test
    fun addingFromDb() {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.single() }
        val status = makeStatus("2")
        val dbStatus = makeStatus("1")
        val dbResult = TimelineStatusWithAccount()
        dbResult.status = dbStatus.toEntity(account.id, htmlConverter, gson)
        dbResult.account = status.account.toEntity(account.id, gson)

        whenever(mastodonApi.homeTimelineSingle(any(), any(), any()))
                .thenReturn(Single.just(listOf(status)))
        whenever(timelineDao.getStatusesForAccount(account.id, status.id, null, 30))
                .thenReturn(Single.just(listOf(dbResult)))
        val result = subject.getStatuses(null, null, null, limit, TimelineRequestMode.ANY)
                .blockingGet()
        assertEquals(listOf(status, dbStatus).map(Status::lift), result)
    }

    @Test
    fun addingFromDbExhausted() {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.single() }
        val status = makeStatus("4")
        val dbResult = TimelineStatusWithAccount()
        dbResult.status = Placeholder("2").toEntity(account.id)
        val dbResult2 = TimelineStatusWithAccount()
        dbResult2.status = Placeholder("1").toEntity(account.id)

        whenever(mastodonApi.homeTimelineSingle(any(), any(), any()))
                .thenReturn(Single.just(listOf(status)))
        whenever(timelineDao.getStatusesForAccount(account.id, status.id, null, 30))
                .thenReturn(Single.just(listOf(dbResult, dbResult2)))
        val result = subject.getStatuses(null, null, null, limit, TimelineRequestMode.ANY)
                .blockingGet()
        assertEquals(listOf(status).map(Status::lift), result)
    }

    private fun makeStatus(id: String, account: Account = makeAccount(id)): Status {
        return Status(
                id = id,
                account = account,
                content = SpanUtilsTest.FakeSpannable("hello$id"),
                createdAt = Date(),
                emojis = listOf(),
                reblogsCount = 3,
                favouritesCount = 5,
                sensitive = false,
                visibility = Status.Visibility.PUBLIC,
                spoilerText = "",
                reblogged = true,
                favourited = false,
                attachments = listOf(),
                mentions = arrayOf(),
                application = null,
                inReplyToAccountId = null,
                inReplyToId = null,
                pinned = false,
                reblog = null,
                url = "http://example.com/statuses/$id"
        )
    }

    private fun makeAccount(id: String): Account {
        return Account(
                id = id,
                localUsername = "test$id",
                username = "test$id@example.com",
                displayName = "Example Account $id",
                note = SpanUtilsTest.FakeSpannable("Note! $id"),
                url = "https://example.com/@test$id",
                avatar = "avatar$id",
                header = "Header$id",
                followersCount = 300,
                followingCount = 400,
                statusesCount = 1000,
                bot = false,
                emojis = listOf(),
                fields = null,
                source = null
        )
    }
}