/* Copyright 2018 Conny Duck
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

import android.content.Intent
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import com.google.android.material.bottomsheet.BottomSheetBehavior
import android.view.View
import android.widget.LinearLayout
import com.keylesspalace.tusky.entity.SearchResults
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.LinkHelper
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject

/** this is the base class for all activities that open links
 *  links are checked against the api if they are mastodon links so they can be openend in Tusky
 *  Subclasses must have a bottom sheet with Id item_status_bottom_sheet in their layout hierachy
 */

abstract class BottomSheetActivity : BaseActivity() {

    lateinit var bottomSheet: BottomSheetBehavior<LinearLayout>
    var searchUrl: String? = null

    @Inject
    lateinit var mastodonApi: MastodonApi

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        val bottomSheetLayout: LinearLayout = findViewById(R.id.item_status_bottom_sheet)
            bottomSheet = BottomSheetBehavior.from(bottomSheetLayout)
            bottomSheet.state = BottomSheetBehavior.STATE_HIDDEN
            bottomSheet.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                   if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                        cancelActiveSearch()
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            })

    }

    open fun viewUrl(url: String) {
        if (!looksLikeMastodonUrl(url)) {
            openLink(url)
            return
        }

        val call = mastodonApi.search(url, true)
        call.enqueue(object : Callback<SearchResults> {
            override fun onResponse(call: Call<SearchResults>, response: Response<SearchResults>) {
                if (getCancelSearchRequested(url)) {
                    return
                }

                onEndSearch(url)
                if (response.isSuccessful) {
                    // According to the mastodon API doc, if the search query is a url,
                    // only exact matches for statuses or accounts are returned
                    // which is good, because pleroma returns a different url
                    // than the public post link
                    val searchResult = response.body()
                    if(searchResult != null) {
                        if (searchResult.statuses.isNotEmpty()) {
                            viewThread(searchResult.statuses[0].id, searchResult.statuses[0].url)
                            return
                        } else if (searchResult.accounts.isNotEmpty()) {
                            viewAccount(searchResult.accounts[0].id)
                            return
                        }
                    }
                }
                openLink(url)
            }

            override fun onFailure(call: Call<SearchResults>, t: Throwable) {
                if (!getCancelSearchRequested(url)) {
                    onEndSearch(url)
                    openLink(url)
                }
            }
        })
        callList.add(call)
        onBeginSearch(url)
    }

    open fun viewThread(statusId: String, url: String?) {
        if (!isSearching()) {
            val intent = Intent(this, ViewThreadActivity::class.java)
            intent.putExtra("id", statusId)
            intent.putExtra("url", url)
            startActivityWithSlideInAnimation(intent)
        }
    }

    open fun viewAccount(id: String) {
        val intent = AccountActivity.getIntent(this, id)
        startActivityWithSlideInAnimation(intent)
    }

    @VisibleForTesting
    fun onBeginSearch(url: String) {
        searchUrl = url
        showQuerySheet()
    }

    @VisibleForTesting
    fun getCancelSearchRequested(url: String): Boolean {
        return url != searchUrl
    }

    @VisibleForTesting
    fun isSearching(): Boolean {
        return searchUrl != null
    }

    @VisibleForTesting
    fun onEndSearch(url: String?) {
        if (url == searchUrl) {
            // Don't clear query if there's no match,
            // since we might just now be getting the response for a canceled search
            searchUrl = null
            hideQuerySheet()
        }
    }

    @VisibleForTesting
    fun cancelActiveSearch() {
        if (isSearching()) {
            onEndSearch(searchUrl)
        }
    }

    @VisibleForTesting
    open fun openLink(url: String) {
        LinkHelper.openLink(url, this)
    }

    private fun showQuerySheet() {
            bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun hideQuerySheet() {
            bottomSheet.state = BottomSheetBehavior.STATE_HIDDEN
    }
}

// https://mastodon.foo.bar/@User
// https://mastodon.foo.bar/@User/43456787654678
// https://pleroma.foo.bar/users/User
// https://pleroma.foo.bar/users/43456787654678
// https://pleroma.foo.bar/notice/43456787654678
// https://pleroma.foo.bar/objects/d4643c42-3ae0-4b73-b8b0-c725f5819207
fun looksLikeMastodonUrl(urlString: String): Boolean {
    val uri: URI
    try {
        uri = URI(urlString)
    } catch (e: URISyntaxException) {
        return false
    }

    if (uri.query != null ||
            uri.fragment != null ||
            uri.path == null) {
        return false
    }

    val path = uri.path
    return path.matches("^/@[^/]+$".toRegex()) ||
            path.matches("^/users/[^/]+$".toRegex()) ||
            path.matches("^/@[^/]+/\\d+$".toRegex()) ||
            path.matches("^/notice/\\d+$".toRegex()) ||
            path.matches("^/objects/[-a-f0-9]+$".toRegex())
}
