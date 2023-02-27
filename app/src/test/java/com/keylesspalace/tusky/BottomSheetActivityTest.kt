/* Copyright 2018 Levi Bard
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.keylesspalace.tusky.entity.SearchResult
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.network.MastodonApi
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.TestScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito.eq
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.Date
import java.util.concurrent.TimeUnit

class BottomSheetActivityTest {

    @get:Rule
    val instantTaskExecutorRule: InstantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var activity: FakeBottomSheetActivity
    private lateinit var apiMock: MastodonApi
    private val accountQuery = "http://mastodon.foo.bar/@User"
    private val statusQuery = "http://mastodon.foo.bar/@User/345678"
    private val nonexistentStatusQuery = "http://mastodon.foo.bar/@User/345678000"
    private val nonMastodonQuery = "http://medium.com/@correspondent/345678"
    private val emptyCallback = Single.just(SearchResult(emptyList(), emptyList(), emptyList()))
    private val testScheduler = TestScheduler()

    private val account = TimelineAccount(
        id = "1",
        localUsername = "admin",
        username = "admin",
        displayName = "Ad Min",
        url = "http://mastodon.foo.bar/@User",
        avatar = ""
    )
    private val accountSingle = Single.just(SearchResult(listOf(account), emptyList(), emptyList()))

    private val status = Status(
        id = "1",
        url = statusQuery,
        account = account,
        inReplyToId = null,
        inReplyToAccountId = null,
        reblog = null,
        content = "omgwat",
        createdAt = Date(),
        editedAt = null,
        emojis = emptyList(),
        reblogsCount = 0,
        favouritesCount = 0,
        repliesCount = 0,
        reblogged = false,
        favourited = false,
        bookmarked = false,
        sensitive = false,
        spoilerText = "",
        visibility = Status.Visibility.PUBLIC,
        attachments = ArrayList(),
        mentions = emptyList(),
        tags = emptyList(),
        application = null,
        pinned = false,
        muted = false,
        poll = null,
        card = null,
        language = null,
    )
    private val statusSingle = Single.just(SearchResult(emptyList(), listOf(status), emptyList()))

    @Before
    fun setup() {

        RxJavaPlugins.setIoSchedulerHandler { testScheduler }
        RxAndroidPlugins.setMainThreadSchedulerHandler { testScheduler }

        apiMock = mock {
            on { searchObservable(eq(accountQuery), eq(null), anyBoolean(), eq(null), eq(null), eq(null)) } doReturn accountSingle
            on { searchObservable(eq(statusQuery), eq(null), anyBoolean(), eq(null), eq(null), eq(null)) } doReturn statusSingle
            on { searchObservable(eq(nonexistentStatusQuery), eq(null), anyBoolean(), eq(null), eq(null), eq(null)) } doReturn accountSingle
            on { searchObservable(eq(nonMastodonQuery), eq(null), anyBoolean(), eq(null), eq(null), eq(null)) } doReturn emptyCallback
        }

        activity = FakeBottomSheetActivity(apiMock)
    }

    @Test
    fun beginEndSearch_setIsSearching_isSearchingAfterBegin() {
        activity.onBeginSearch("https://mastodon.foo.bar/@User")
        assertTrue(activity.isSearching())
    }

    @Test
    fun beginEndSearch_setIsSearching_isNotSearchingAfterEnd() {
        val validUrl = "https://mastodon.foo.bar/@User"
        activity.onBeginSearch(validUrl)
        activity.onEndSearch(validUrl)
        assertFalse(activity.isSearching())
    }

    @Test
    fun beginEndSearch_setIsSearching_doesNotCancelSearchWhenResponseFromPreviousSearchIsReceived() {
        val validUrl = "https://mastodon.foo.bar/@User"
        val invalidUrl = ""

        activity.onBeginSearch(validUrl)
        activity.onEndSearch(invalidUrl)
        assertTrue(activity.isSearching())
    }

    @Test
    fun cancelActiveSearch() {
        val url = "https://mastodon.foo.bar/@User"

        activity.onBeginSearch(url)
        activity.cancelActiveSearch()
        assertFalse(activity.isSearching())
    }

    @Test
    fun getCancelSearchRequested_detectsURL() {
        val firstUrl = "https://mastodon.foo.bar/@User"
        val secondUrl = "https://mastodon.foo.bar/@meh"

        activity.onBeginSearch(firstUrl)
        activity.cancelActiveSearch()

        activity.onBeginSearch(secondUrl)
        assertTrue(activity.getCancelSearchRequested(firstUrl))
        assertFalse(activity.getCancelSearchRequested(secondUrl))
    }

    @Test
    fun search_inIdealConditions_returnsRequestedResults_forAccount() {
        activity.viewUrl(accountQuery)
        testScheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS)
        assertEquals(account.id, activity.accountId)
    }

    @Test
    fun search_inIdealConditions_returnsRequestedResults_forStatus() {
        activity.viewUrl(statusQuery)
        testScheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS)
        assertEquals(status.id, activity.statusId)
    }

    @Test
    fun search_inIdealConditions_returnsRequestedResults_forNonMastodonURL() {
        activity.viewUrl(nonMastodonQuery)
        testScheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS)
        assertEquals(nonMastodonQuery, activity.link)
    }

    @Test
    fun search_withNoResults_appliesRequestedFallbackBehavior() {
        for (fallbackBehavior in listOf(PostLookupFallbackBehavior.OPEN_IN_BROWSER, PostLookupFallbackBehavior.DISPLAY_ERROR)) {
            activity.viewUrl(nonMastodonQuery, fallbackBehavior)
            testScheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS)
            assertEquals(nonMastodonQuery, activity.link)
            assertEquals(fallbackBehavior, activity.fallbackBehavior)
        }
    }

    @Test
    fun search_doesNotRespectUnrelatedResult() {
        activity.viewUrl(nonexistentStatusQuery)
        testScheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS)
        assertEquals(nonexistentStatusQuery, activity.link)
        assertEquals(null, activity.accountId)
    }

    @Test
    fun search_withCancellation_doesNotLoadUrl_forAccount() {
        activity.viewUrl(accountQuery)
        assertTrue(activity.isSearching())
        activity.cancelActiveSearch()
        assertFalse(activity.isSearching())
        assertEquals(null, activity.accountId)
    }

    @Test
    fun search_withCancellation_doesNotLoadUrl_forStatus() {
        activity.viewUrl(accountQuery)
        activity.cancelActiveSearch()
        assertEquals(null, activity.accountId)
    }

    @Test
    fun search_withCancellation_doesNotLoadUrl_forNonMastodonURL() {
        activity.viewUrl(nonMastodonQuery)
        activity.cancelActiveSearch()
        assertEquals(null, activity.searchUrl)
    }

    @Test
    fun search_withPreviousCancellation_completes() {
        // begin/cancel account search
        activity.viewUrl(accountQuery)
        activity.cancelActiveSearch()

        // begin status search
        activity.viewUrl(statusQuery)

        // ensure that search is still ongoing
        assertTrue(activity.isSearching())

        // return searchResults
        testScheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS)

        // ensure that the result of the status search was recorded
        // and the account search wasn't
        assertEquals(status.id, activity.statusId)
        assertEquals(null, activity.accountId)
    }

    class FakeBottomSheetActivity(api: MastodonApi) : BottomSheetActivity() {

        var statusId: String? = null
        var accountId: String? = null
        var link: String? = null
        var fallbackBehavior: PostLookupFallbackBehavior? = null

        init {
            mastodonApi = api
            bottomSheet = mock()
        }

        override fun openLink(url: String) {
            this.link = url
        }

        override fun viewAccount(id: String) {
            this.accountId = id
        }

        override fun viewThread(statusId: String, url: String?) {
            this.statusId = statusId
        }

        override fun performUrlFallbackAction(url: String, fallbackBehavior: PostLookupFallbackBehavior) {
            this.link = url
            this.fallbackBehavior = fallbackBehavior
        }
    }
}
