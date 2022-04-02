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

import android.widget.LinearLayout
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.keylesspalace.tusky.entity.SearchResult
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.network.MastodonApi
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
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
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import java.util.ArrayList
import java.util.Date
import java.util.concurrent.TimeUnit

class BottomSheetActivityTest {

    @get:Rule
    val instantTaskExecutorRule: InstantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var activity: FakeBottomSheetActivity
    private lateinit var apiMock: MastodonApi
    private val accountQuery = "http://mastodon.foo.bar/@User"
    private val statusQuery = "http://mastodon.foo.bar/@User/345678"
    private val nonMastodonQuery = "http://medium.com/@correspondent/345678"
    private val emptyCallback = Single.just(SearchResult(emptyList(), emptyList(), emptyList()))
    private val testScheduler = TestScheduler()

    private val account = TimelineAccount(
        id = "1",
        localUsername = "admin",
        username = "admin",
        displayName = "Ad Min",
        url = "http://mastodon.foo.bar",
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
        emojis = emptyList(),
        reblogsCount = 0,
        favouritesCount = 0,
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
        card = null
    )
    private val statusSingle = Single.just(SearchResult(emptyList(), listOf(status), emptyList()))

    @Before
    fun setup() {

        RxJavaPlugins.setIoSchedulerHandler { testScheduler }
        RxAndroidPlugins.setMainThreadSchedulerHandler { testScheduler }

        apiMock = mock {
            on { searchObservable(eq(accountQuery), eq(null), anyBoolean(), eq(null), eq(null), eq(null)) } doReturn accountSingle
            on { searchObservable(eq(statusQuery), eq(null), anyBoolean(), eq(null), eq(null), eq(null)) } doReturn statusSingle
            on { searchObservable(eq(nonMastodonQuery), eq(null), anyBoolean(), eq(null), eq(null), eq(null)) } doReturn emptyCallback
        }

        activity = FakeBottomSheetActivity(apiMock)
    }

    @RunWith(Parameterized::class)
    class UrlMatchingTests(private val url: String, private val expectedResult: Boolean) {
        companion object {
            @Parameterized.Parameters(name = "match_{0}")
            @JvmStatic
            fun data(): Iterable<Any> {
                return listOf(
                    arrayOf("https://mastodon.foo.bar/@User", true),
                    arrayOf("http://mastodon.foo.bar/@abc123", true),
                    arrayOf("https://mastodon.foo.bar/@user/345667890345678", true),
                    arrayOf("https://mastodon.foo.bar/@user/3", true),
                    arrayOf("https://pleroma.foo.bar/users/meh3223", true),
                    arrayOf("https://pleroma.foo.bar/users/meh3223_bruh", true),
                    arrayOf("https://pleroma.foo.bar/users/2345", true),
                    arrayOf("https://pleroma.foo.bar/notice/9", true),
                    arrayOf("https://pleroma.foo.bar/notice/9345678", true),
                    arrayOf("https://pleroma.foo.bar/notice/wat", true),
                    arrayOf("https://pleroma.foo.bar/notice/9qTHT2ANWUdXzENqC0", true),
                    arrayOf("https://pleroma.foo.bar/objects/abcdef-123-abcd-9876543", true),
                    arrayOf("https://misskey.foo.bar/notes/mew", true),
                    arrayOf("https://misskey.foo.bar/notes/1421564653", true),
                    arrayOf("https://misskey.foo.bar/notes/qwer615985ddf", true),
                    arrayOf("https://friendica.foo.bar/profile/user", true),
                    arrayOf("https://friendica.foo.bar/profile/uSeR", true),
                    arrayOf("https://friendica.foo.bar/profile/user_user", true),
                    arrayOf("https://friendica.foo.bar/profile/123", true),
                    arrayOf("https://friendica.foo.bar/display/abcdef-123-abcd-9876543", true),
                    arrayOf("https://google.com/", false),
                    arrayOf("https://mastodon.foo.bar/@User?foo=bar", false),
                    arrayOf("https://mastodon.foo.bar/@User#foo", false),
                    arrayOf("http://mastodon.foo.bar/@", false),
                    arrayOf("http://mastodon.foo.bar/@/345678", false),
                    arrayOf("https://mastodon.foo.bar/@user/345667890345678/", false),
                    arrayOf("https://mastodon.foo.bar/@user/3abce", false),
                    arrayOf("https://pleroma.foo.bar/users/", false),
                    arrayOf("https://pleroma.foo.bar/users/meow/", false),
                    arrayOf("https://pleroma.foo.bar/users/@meow", false),
                    arrayOf("https://pleroma.foo.bar/user/2345", false),
                    arrayOf("https://pleroma.foo.bar/notices/123456", false),
                    arrayOf("https://pleroma.foo.bar/notice/@neverhappen/", false),
                    arrayOf("https://pleroma.foo.bar/object/abcdef-123-abcd-9876543", false),
                    arrayOf("https://pleroma.foo.bar/objects/xabcdef-123-abcd-9876543", false),
                    arrayOf("https://pleroma.foo.bar/objects/xabcdef-123-abcd-9876543/", false),
                    arrayOf("https://pleroma.foo.bar/objects/xabcdef-123-abcd_9876543", false),
                    arrayOf("https://friendica.foo.bar/display/xabcdef-123-abcd-9876543", false),
                    arrayOf("https://friendica.foo.bar/display/xabcdef-123-abcd-9876543/", false),
                    arrayOf("https://friendica.foo.bar/display/xabcdef-123-abcd_9876543", false),
                    arrayOf("https://friendica.foo.bar/profile/@mew", false),
                    arrayOf("https://friendica.foo.bar/profile/@mew/", false),
                    arrayOf("https://misskey.foo.bar/notes/@nyan", false),
                    arrayOf("https://misskey.foo.bar/notes/NYAN123", false),
                    arrayOf("https://misskey.foo.bar/notes/meow123/", false),
                    arrayOf("https://pixelfed.social/p/connyduck/391263492998670833", true),
                    arrayOf("https://pixelfed.social/connyduck", true)
                )
            }
        }

        @Test
        fun test() {
            assertEquals(expectedResult, looksLikeMastodonUrl(url))
        }
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
            @Suppress("UNCHECKED_CAST")
            bottomSheet = mock(BottomSheetBehavior::class.java) as BottomSheetBehavior<LinearLayout>
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
