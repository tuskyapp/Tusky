package com.keylesspalace.tusky.fragment

import android.text.Spanned
import com.google.gson.Gson
import com.keylesspalace.tusky.SpanUtilsTest
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.TimelineDao
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.repository.*
import com.keylesspalace.tusky.util.Either
import com.keylesspalace.tusky.util.HtmlConverter
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Single
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.TestScheduler
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
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
    lateinit var accountManager: AccountManager

    lateinit var gson: Gson

    lateinit var subject: TimelineRepository

    lateinit var testScheduler: TestScheduler


    val limit = 30
    val accountId = 1L

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        whenever(accountManager.activeAccount).thenReturn(AccountEntity(
                id = accountId,
                accessToken = "token",
                domain = "domain.com",
                isActive = true)
        )
        val htmlConverter = object : HtmlConverter {
            override fun fromHtml(html: String): Spanned {
                return SpanUtilsTest.FakeSpannable(html)
            }

            override fun toHtml(text: Spanned): String {
                return text.toString()
            }
        }
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

        assertEquals(statuses.map { Either.Right<Placeholder, Status>(it) }, result)
        testScheduler.advanceTimeBy(100, TimeUnit.SECONDS)
        verify(timelineDao).insertStatusIfNotThere(Placeholder("1").toEntity(accountId))
    }

    fun makeStatus(id: String, account: Account = makeAccount(id)): Status {
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

    fun makeAccount(id: String): Account {
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