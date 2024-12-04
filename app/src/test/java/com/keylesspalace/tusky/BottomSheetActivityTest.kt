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
import at.connyduck.calladapter.networkresult.NetworkResult
import com.keylesspalace.tusky.entity.SearchResult
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.network.MastodonApi
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

@OptIn(ExperimentalCoroutinesApi::class)
class BottomSheetActivityTest {

    @get:Rule
    val instantTaskExecutorRule: InstantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var activity: FakeBottomSheetActivity
    private lateinit var apiMock: MastodonApi
    private val accountQuery = "http://mastodon.foo.bar/@User"
    private val statusQuery = "http://mastodon.foo.bar/@User/345678"
    private val nonexistentStatusQuery = "http://mastodon.foo.bar/@User/345678000"
    private val nonMastodonQuery = "http://medium.com/@correspondent/345678"
    private val emptyResult = NetworkResult.success(SearchResult(emptyList(), emptyList(), emptyList()))

    private val account = TimelineAccount(
        id = "1",
        localUsername = "admin",
        username = "admin",
        displayName = "Ad Min",
        note = "This is their bio",
        url = "http://mastodon.foo.bar/@User",
        avatar = ""
    )
    private val accountResult = NetworkResult.success(SearchResult(listOf(account), emptyList(), emptyList()))

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
        filtered = emptyList()
    )
    private val statusResult = NetworkResult.success(SearchResult(emptyList(), listOf(status), emptyList()))

    @Before
    fun setup() {
        apiMock = mock {
            onBlocking { search(eq(accountQuery), eq(null), anyBoolean(), eq(null), eq(null), eq(null)) } doReturn accountResult
            onBlocking { search(eq(statusQuery), eq(null), anyBoolean(), eq(null), eq(null), eq(null)) } doReturn statusResult
            onBlocking { search(eq(nonexistentStatusQuery), eq(null), anyBoolean(), eq(null), eq(null), eq(null)) } doReturn accountResult
            onBlocking { search(eq(nonMastodonQuery), eq(null), anyBoolean(), eq(null), eq(null), eq(null)) } doReturn emptyResult
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
    fun search_inIdealConditions_returnsRequestedResults_forAccount() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            activity.viewUrl(accountQuery)
            testScheduler.advanceTimeBy(100.milliseconds)
            assertEquals(account.id, activity.accountId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun search_inIdealConditions_returnsRequestedResults_forStatus() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            activity.viewUrl(statusQuery)
            testScheduler.advanceTimeBy(100.milliseconds)
            assertEquals(status.id, activity.statusId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun search_inIdealConditions_returnsRequestedResults_forNonMastodonURL() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            activity.viewUrl(nonMastodonQuery)
            testScheduler.advanceTimeBy(100.milliseconds)
            assertEquals(nonMastodonQuery, activity.link)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun search_withNoResults_appliesRequestedFallbackBehavior() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            for (fallbackBehavior in listOf(
                PostLookupFallbackBehavior.OPEN_IN_BROWSER,
                PostLookupFallbackBehavior.DISPLAY_ERROR
            )) {
                activity.viewUrl(nonMastodonQuery, fallbackBehavior)
                testScheduler.advanceTimeBy(100.milliseconds)
                assertEquals(nonMastodonQuery, activity.link)
                assertEquals(fallbackBehavior, activity.fallbackBehavior)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun search_doesNotRespectUnrelatedResult() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            activity.viewUrl(nonexistentStatusQuery)
            testScheduler.advanceTimeBy(100.milliseconds)
            assertEquals(nonexistentStatusQuery, activity.link)
            assertEquals(null, activity.accountId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun search_withCancellation_doesNotLoadUrl_forAccount() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            activity.viewUrl(accountQuery)
            assertTrue(activity.isSearching())
            activity.cancelActiveSearch()
            assertFalse(activity.isSearching())
            assertEquals(null, activity.accountId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun search_withCancellation_doesNotLoadUrl_forStatus() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            activity.viewUrl(accountQuery)
            activity.cancelActiveSearch()
            assertEquals(null, activity.accountId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun search_withCancellation_doesNotLoadUrl_forNonMastodonURL() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            activity.viewUrl(nonMastodonQuery)
            activity.cancelActiveSearch()
            assertEquals(null, activity.searchUrl)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun search_withPreviousCancellation_completes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            // begin/cancel account search
            activity.viewUrl(accountQuery)
            activity.cancelActiveSearch()

            // begin status search
            activity.viewUrl(statusQuery)

            // ensure that search is still ongoing
            assertTrue(activity.isSearching())

            // return searchResults
            testScheduler.advanceTimeBy(100.milliseconds)

            // ensure that the result of the status search was recorded
            // and the account search wasn't
            assertEquals(status.id, activity.statusId)
            assertEquals(null, activity.accountId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    class FakeBottomSheetActivity(api: MastodonApi) : BottomSheetActivity() {

        var statusId: String? = null
        var accountId: String? = null
        var link: String? = null
        var fallbackBehavior: PostLookupFallbackBehavior? = null

        init {
            mastodonApi = api
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
