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

import android.text.SpannedString
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.SearchResult
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import io.reactivex.Single
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.TestScheduler
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*
import java.util.*
import java.util.concurrent.TimeUnit


class BottomSheetActivityTest {
    private lateinit var activity : FakeBottomSheetActivity
    private lateinit var apiMock: MastodonApi
    private val accountQuery = "http://mastodon.foo.bar/@User"
    private val statusQuery = "http://mastodon.foo.bar/@User/345678"
    private val nonMastodonQuery = "http://medium.com/@correspondent/345678"
    private val emptyCallback = Single.just(SearchResult(emptyList(), emptyList(), emptyList()))
    private val testScheduler = TestScheduler()

    private val account = Account (
            "1",
            "admin",
            "admin",
            "Ad Min",
            SpannedString(""),
            "http://mastodon.foo.bar",
            "",
            "",
            false,
            0,
            0,
            0,
            null,
            false,
            emptyList(),
            emptyList()
    )
    private val accountSingle = Single.just(SearchResult(listOf(account), emptyList(), emptyList()))

    private val status = Status(
            "1",
            statusQuery,
            account,
            null,
            null,
            null,
            SpannedString("omgwat"),
            Date(),
            Collections.emptyList(),
            0,
            0,
            false,
            false,
            false,
            "",
            Status.Visibility.PUBLIC,
            ArrayList(),
            arrayOf(),
            null,
            pinned = false,
            poll = null,
            card = null
    )
    private val statusSingle = Single.just(SearchResult(emptyList(), listOf(status), emptyList()))

    @Before
    fun setup() {

        RxJavaPlugins.setIoSchedulerHandler { testScheduler }
        RxAndroidPlugins.setMainThreadSchedulerHandler { testScheduler }

        apiMock = mock(MastodonApi::class.java)
        `when`(apiMock.searchObservable(eq(accountQuery), eq(null), ArgumentMatchers.anyBoolean(), eq(null), eq(null), eq(null))).thenReturn(accountSingle)
        `when`(apiMock.searchObservable(eq(statusQuery), eq(null), ArgumentMatchers.anyBoolean(), eq(null), eq(null), eq(null))).thenReturn(statusSingle)
        `when`(apiMock.searchObservable(eq(nonMastodonQuery), eq(null), ArgumentMatchers.anyBoolean(), eq(null), eq(null), eq(null))).thenReturn(emptyCallback)

        activity = FakeBottomSheetActivity(apiMock)
    }

    @RunWith(Parameterized::class)
    class UrlMatchingTests(private val url: String, private val expectedResult: Boolean) {
        companion object {
            @Parameterized.Parameters(name = "match_{0}")
            @JvmStatic
            fun data() : Iterable<Any> {
                return listOf(
                    arrayOf("https://mastodon.foo.bar/@User", true),
                    arrayOf("http://mastodon.foo.bar/@abc123", true),
                    arrayOf("https://mastodon.foo.bar/@user/345667890345678", true),
                    arrayOf("https://mastodon.foo.bar/@user/3", true),
                    arrayOf("https://pleroma.foo.bar/users/meh3223", true),
                    arrayOf("https://pleroma.foo.bar/users/2345", true),
                    arrayOf("https://pleroma.foo.bar/notice/9", true),
                    arrayOf("https://pleroma.foo.bar/notice/9345678", true),
                    arrayOf("https://pleroma.foo.bar/objects/abcdef-123-abcd-9876543", true),
                    arrayOf("https://google.com/", false),
                    arrayOf("https://mastodon.foo.bar/@User?foo=bar", false),
                    arrayOf("https://mastodon.foo.bar/@User#foo", false),
                    arrayOf("http://mastodon.foo.bar/@", false),
                    arrayOf("http://mastodon.foo.bar/@/345678", false),
                    arrayOf("https://mastodon.foo.bar/@user/345667890345678/", false),
                    arrayOf("https://mastodon.foo.bar/@user/3abce", false),
                    arrayOf("https://pleroma.foo.bar/users/", false),
                    arrayOf("https://pleroma.foo.bar/user/2345", false),
                    arrayOf("https://pleroma.foo.bar/notice/wat", false),
                    arrayOf("https://pleroma.foo.bar/notices/123456", false),
                    arrayOf("https://pleroma.foo.bar/object/abcdef-123-abcd-9876543", false),
                    arrayOf("https://pleroma.foo.bar/objects/xabcdef-123-abcd-9876543", false),
                    arrayOf("https://pleroma.foo.bar/objects/xabcdef-123-abcd-9876543/", false),
                    arrayOf("https://pleroma.foo.bar/objects/xabcdef-123-abcd_9876543", false)
                )
            }
        }

        @Test
        fun test() {
            Assert.assertEquals(expectedResult, looksLikeMastodonUrl(url))
        }
    }

    @Test
    fun beginEndSearch_setIsSearching_isSearchingAfterBegin() {
        activity.onBeginSearch("https://mastodon.foo.bar/@User")
        Assert.assertTrue(activity.isSearching())
    }

    @Test
    fun beginEndSearch_setIsSearching_isNotSearchingAfterEnd() {
        val validUrl = "https://mastodon.foo.bar/@User"
        activity.onBeginSearch(validUrl)
        activity.onEndSearch(validUrl)
        Assert.assertFalse(activity.isSearching())
    }

    @Test
    fun beginEndSearch_setIsSearching_doesNotCancelSearchWhenResponseFromPreviousSearchIsReceived() {
        val validUrl = "https://mastodon.foo.bar/@User"
        val invalidUrl = ""

        activity.onBeginSearch(validUrl)
        activity.onEndSearch(invalidUrl)
        Assert.assertTrue(activity.isSearching())
    }

    @Test
    fun cancelActiveSearch() {
        val url = "https://mastodon.foo.bar/@User"

        activity.onBeginSearch(url)
        activity.cancelActiveSearch()
        Assert.assertFalse(activity.isSearching())
    }

    @Test
    fun getCancelSearchRequested_detectsURL() {
        val firstUrl = "https://mastodon.foo.bar/@User"
        val secondUrl = "https://mastodon.foo.bar/@meh"

        activity.onBeginSearch(firstUrl)
        activity.cancelActiveSearch()

        activity.onBeginSearch(secondUrl)
        Assert.assertTrue(activity.getCancelSearchRequested(firstUrl))
        Assert.assertFalse(activity.getCancelSearchRequested(secondUrl))
    }

    @Test
    fun search_inIdealConditions_returnsRequestedResults_forAccount() {
        activity.viewUrl(accountQuery)
        testScheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS)
        Assert.assertEquals(account.id, activity.accountId)
    }

    @Test
    fun search_inIdealConditions_returnsRequestedResults_forStatus() {
        activity.viewUrl(statusQuery)
        testScheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS)
        Assert.assertEquals(status.id, activity.statusId)
    }

    @Test
    fun search_inIdealConditions_returnsRequestedResults_forNonMastodonURL() {
        activity.viewUrl(nonMastodonQuery)
        testScheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS)
        Assert.assertEquals(nonMastodonQuery, activity.link)
    }

    @Test
    fun search_withCancellation_doesNotLoadUrl_forAccount() {
        activity.viewUrl(accountQuery)
        Assert.assertTrue(activity.isSearching())
        activity.cancelActiveSearch()
        Assert.assertFalse(activity.isSearching())
        Assert.assertEquals(null, activity.accountId)
    }

    @Test
    fun search_withCancellation_doesNotLoadUrl_forStatus() {
        activity.viewUrl(accountQuery)
        activity.cancelActiveSearch()
        Assert.assertEquals(null, activity.accountId)
    }

    @Test
    fun search_withCancellation_doesNotLoadUrl_forNonMastodonURL() {
        activity.viewUrl(nonMastodonQuery)
        activity.cancelActiveSearch()
        Assert.assertEquals(null, activity.searchUrl)
    }

    @Test
    fun search_withPreviousCancellation_completes() {
        // begin/cancel account search
        activity.viewUrl(accountQuery)
        activity.cancelActiveSearch()

        // begin status search
        activity.viewUrl(statusQuery)

        // ensure that search is still ongoing
        Assert.assertTrue(activity.isSearching())

        // return searchResults
        testScheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS)

        // ensure that the result of the status search was recorded
        // and the account search wasn't
        Assert.assertEquals(status.id, activity.statusId)
        Assert.assertEquals(null, activity.accountId)
    }

    class FakeBottomSheetActivity(api: MastodonApi) : BottomSheetActivity() {

        var statusId: String? = null
        var accountId: String? = null
        var link: String? = null

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

    }
}