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

package com.keylesspalace.tusky.fragment

import android.text.SpannedString
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.SearchResults
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.network.TimelineCases
import okhttp3.Request
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*

class SFragmentTest {
    private lateinit var fragment : FakeSFragment
    private lateinit var apiMock: MastodonApi
    private val accountQuery = "http://mastodon.foo.bar/@User"
    private val statusQuery = "http://mastodon.foo.bar/@User/345678"
    private val nonMastodonQuery = "http://medium.com/@correspondent/345678"
    private val emptyCallback = FakeSearchResults()

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
            null
    )
    private val accountCallback = FakeSearchResults(account)

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
            arrayOf(),
            arrayOf(),
            null
    )
    private val statusCallback = FakeSearchResults(status)

    @Before
    fun setup() {
        fragment = FakeSFragment()

        apiMock = Mockito.mock(MastodonApi::class.java)
        `when`(apiMock.search(eq(accountQuery), ArgumentMatchers.anyBoolean())).thenReturn(accountCallback)
        `when`(apiMock.search(eq(statusQuery), ArgumentMatchers.anyBoolean())).thenReturn(statusCallback)
        `when`(apiMock.search(eq(nonMastodonQuery), ArgumentMatchers.anyBoolean())).thenReturn(emptyCallback)
        fragment.mastodonApi = apiMock
    }

    @RunWith(Parameterized::class)
    class UrlMatchingTests(val url: String, val expectedResult: Boolean) {
        companion object {
            @Parameterized.Parameters(name = "{0}")
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
            Assert.assertEquals(expectedResult, SFragment.looksLikeMastodonUrl(url))
        }
    }

    @Test
    fun beginEndSearch_setIsSearching() {
        val validUrl = "https://mastodon.foo.bar/@User"
        val invalidUrl = ""

        fragment.onBeginSearch(validUrl)
        Assert.assertTrue(fragment.isSearching)

        fragment.onEndSearch(invalidUrl)
        Assert.assertTrue(fragment.isSearching)

        fragment.onEndSearch(validUrl)
        Assert.assertFalse(fragment.isSearching)
    }

    @Test
    fun cancelActiveSearch() {
        val firstUrl = "https://mastodon.foo.bar/@User"
        val secondUrl = "https://mastodon.foo.bar/@meh"

        fragment.onBeginSearch(firstUrl)
        Assert.assertTrue(fragment.isSearching)

        fragment.cancelActiveSearch()
        Assert.assertFalse(fragment.isSearching)

        fragment.onBeginSearch(secondUrl)
        Assert.assertTrue(fragment.isSearching)
        Assert.assertTrue(fragment.getCancelSearchRequested(firstUrl))
        Assert.assertFalse(fragment.getCancelSearchRequested(secondUrl))

        fragment.onEndSearch(secondUrl)
        Assert.assertFalse(fragment.isSearching)
    }

    @Test
    fun search_inIdealConditions_returnsRequestedResults() {
        fragment.onViewURL(accountQuery)
        Assert.assertTrue(fragment.isSearching)
        accountCallback.invokeCallback()
        Assert.assertFalse(fragment.isSearching)
        Assert.assertEquals(account.id, fragment.accountId)

        fragment.onViewURL(statusQuery)
        Assert.assertTrue(fragment.isSearching)
        statusCallback.invokeCallback()
        Assert.assertFalse(fragment.isSearching)
        Assert.assertEquals(status, fragment.status)

        fragment.onViewURL(nonMastodonQuery)
        Assert.assertTrue(fragment.isSearching)
        emptyCallback.invokeCallback()
        Assert.assertFalse(fragment.isSearching)
        Assert.assertEquals(nonMastodonQuery, fragment.url)
    }

    @Test
    fun search_withCancellation_doesNotLoadUrl() {
        fragment.onViewURL(accountQuery)
        Assert.assertTrue(fragment.isSearching)
        fragment.cancelActiveSearch()
        Assert.assertFalse(fragment.isSearching)
        accountCallback.invokeCallback()
        Assert.assertEquals(null, fragment.accountId)

        fragment.onViewURL(statusQuery)
        Assert.assertTrue(fragment.isSearching)
        fragment.cancelActiveSearch()
        Assert.assertFalse(fragment.isSearching)
        statusCallback.invokeCallback()
        Assert.assertEquals(null, fragment.status)

        fragment.onViewURL(nonMastodonQuery)
        Assert.assertTrue(fragment.isSearching)
        fragment.cancelActiveSearch()
        Assert.assertFalse(fragment.isSearching)
        emptyCallback.invokeCallback()
        Assert.assertEquals(null, fragment.url)
    }

    @Test
    fun search_withPreviousCancellation_completes() {
        // begin account search
        fragment.onViewURL(accountQuery)
        Assert.assertTrue(fragment.isSearching)

        // cancel account search
        fragment.cancelActiveSearch()

        // begin status search
        fragment.onViewURL(statusQuery)
        Assert.assertTrue(fragment.isSearching)

        // return response from account search
        accountCallback.invokeCallback()
        Assert.assertEquals(null, fragment.accountId)

        // ensure that status search is still ongoing
        Assert.assertTrue(fragment.isSearching)
        statusCallback.invokeCallback()
        Assert.assertFalse(fragment.isSearching)
        Assert.assertEquals(status, fragment.status)
    }

    class FakeSearchResults : Call<SearchResults>
    {
        var searchResults: SearchResults
        var callback: Callback<SearchResults>? = null

        constructor() {
            searchResults = SearchResults(Collections.emptyList(), Collections.emptyList(), Collections.emptyList())
        }

        constructor(status: Status) {
            searchResults = SearchResults(Collections.emptyList(), listOf(status), Collections.emptyList())
        }

        constructor(account: Account) {
            searchResults = SearchResults(listOf(account), Collections.emptyList(), Collections.emptyList())
        }

        fun invokeCallback() {
            callback?.onResponse(this, Response.success(searchResults))
        }

        override fun enqueue(callback: Callback<SearchResults>?) {
            this.callback = callback
        }

        override fun isExecuted(): Boolean { TODO("not implemented") }
        override fun clone(): Call<SearchResults> { TODO("not implemented") }
        override fun isCanceled(): Boolean { TODO("not implemented") }
        override fun cancel() { TODO("not implemented") }
        override fun execute(): Response<SearchResults> { TODO("not implemented") }
        override fun request(): Request { TODO("not implemented") }
    }

    class FakeSFragment : SFragment {
        var status: Status? = null
        var accountId: String? = null
        var url: String? = null

        constructor(): super() {
            callList = mutableListOf()
        }

        override fun openLink(url: String) {
            this.url = url
        }

        override fun viewAccount(id: String?) {
            accountId = id
        }

        override fun viewThread(status: Status?) {
            this.status = status
        }

        override fun removeItem(position: Int) { TODO("not implemented") }
        override fun removeAllByAccountId(accountId: String?) { TODO("not implemented") }
        override fun timelineCases(): TimelineCases { TODO("not implemented") }
    }
}