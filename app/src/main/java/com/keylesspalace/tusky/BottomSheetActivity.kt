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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.lifecycleScope
import at.connyduck.calladapter.networkresult.fold
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.keylesspalace.tusky.components.account.AccountActivity
import com.keylesspalace.tusky.components.viewthread.ViewThreadActivity
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.looksLikeMastodonUrl
import com.keylesspalace.tusky.util.openLink
import com.keylesspalace.tusky.util.startActivityWithSlideInAnimation
import javax.inject.Inject
import kotlinx.coroutines.launch

/** this is the base class for all activities that open links
 *  links are checked against the api if they are mastodon links so they can be opened in Tusky
 *  Subclasses must have a bottom sheet with Id item_status_bottom_sheet in their layout hierarchy
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
        bottomSheet.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    cancelActiveSearch()
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
    }

    open fun viewUrl(
        url: String,
        lookupFallbackBehavior: PostLookupFallbackBehavior = PostLookupFallbackBehavior.OPEN_IN_BROWSER
    ) {
        if (!looksLikeMastodonUrl(url)) {
            openLink(url)
            return
        }

        lifecycleScope.launch {
            mastodonApi.search(
                query = url,
                resolve = true
            ).fold(
                onSuccess = { (accounts, statuses) ->
                    if (getCancelSearchRequested(url)) {
                        return@launch
                    }

                    onEndSearch(url)

                    if (statuses.isNotEmpty()) {
                        viewThread(statuses[0].id, statuses[0].url)
                        return@launch
                    }
                    accounts.firstOrNull { it.url.equals(url, ignoreCase = true) }?.let { account ->
                        // Some servers return (unrelated) accounts for url searches (#2804)
                        // Verify that the account's url matches the query
                        viewAccount(account.id)
                        return@launch
                    }

                    performUrlFallbackAction(url, lookupFallbackBehavior)
                },
                onFailure = {
                    if (!getCancelSearchRequested(url)) {
                        onEndSearch(url)
                        performUrlFallbackAction(url, lookupFallbackBehavior)
                    }
                }
            )
        }

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

    protected open fun performUrlFallbackAction(
        url: String,
        fallbackBehavior: PostLookupFallbackBehavior
    ) {
        when (fallbackBehavior) {
            PostLookupFallbackBehavior.OPEN_IN_BROWSER -> openLink(url)
            PostLookupFallbackBehavior.DISPLAY_ERROR -> Toast.makeText(
                this,
                getString(R.string.post_lookup_error_format, url),
                Toast.LENGTH_SHORT
            ).show()
        }
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

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    open fun openLink(url: String) {
        (this as Context).openLink(url)
    }

    private fun showQuerySheet() {
        bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun hideQuerySheet() {
        bottomSheet.state = BottomSheetBehavior.STATE_HIDDEN
    }
}

enum class PostLookupFallbackBehavior {
    OPEN_IN_BROWSER,
    DISPLAY_ERROR
}
