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

package com.keylesspalace.tusky.fragment

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import com.keylesspalace.tusky.AccountActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.ViewTagActivity
import com.keylesspalace.tusky.adapter.SearchResultsAdapter
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.SearchResults
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.network.TimelineCases
import com.keylesspalace.tusky.util.ViewDataUtils
import com.keylesspalace.tusky.viewdata.StatusViewData
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider.from
import com.uber.autodispose.autoDisposable
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.fragment_search.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import javax.inject.Inject

class SearchFragment : SFragment(), StatusActionListener, Injectable {

    @Inject
    lateinit var timelineCases: TimelineCases

    private lateinit var searchAdapter: SearchResultsAdapter

    private var alwaysShowSensitiveMedia = false
    private var mediaPreviewEnabled = true
    private var useAbsoluteTime = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(view.context)
        useAbsoluteTime = preferences.getBoolean("absoluteTimeView", false)

        val account = accountManager.activeAccount
        alwaysShowSensitiveMedia = account?.alwaysShowSensitiveMedia ?: false
        mediaPreviewEnabled = account?.mediaPreviewEnabled ?: true

        searchRecyclerView.addItemDecoration(DividerItemDecoration(view.context, DividerItemDecoration.VERTICAL))
        searchRecyclerView.layoutManager = LinearLayoutManager(view.context)
        searchAdapter = SearchResultsAdapter(
                mediaPreviewEnabled,
                alwaysShowSensitiveMedia,
                this,
                this,
                useAbsoluteTime)
        searchRecyclerView.adapter = searchAdapter

    }

    fun search(query: String) {
        clearResults()
        val callback = object : Callback<SearchResults> {
            override fun onResponse(call: Call<SearchResults>, response: Response<SearchResults>) {
                if (response.isSuccessful) {
                    val results = response.body()
                    if (results != null && (results.accounts.isNotEmpty() || results.statuses.isNotEmpty() || results.hashtags.isNotEmpty())) {
                        searchAdapter.updateSearchResults(results)
                        hideFeedback()
                    } else {
                        displayNoResults()
                    }
                } else {
                    onSearchFailure()
                }
            }

            override fun onFailure(call: Call<SearchResults>, t: Throwable) {
                onSearchFailure()
            }
        }
        mastodonApi.search(query, true)
                .enqueue(callback)
    }

    private fun onSearchFailure() {
        displayNoResults()
        Log.e(TAG, "Search request failed.")
    }

    private fun clearResults() {
        searchAdapter.updateSearchResults(null)
        searchProgressBar.visibility = View.VISIBLE
        searchNoResultsText.visibility = View.GONE
    }

    private fun displayNoResults() {
        if (isAdded) {
            searchProgressBar.visibility = View.GONE
            searchNoResultsText.visibility = View.VISIBLE
        }
    }

    private fun hideFeedback() {
        if (isAdded) {
            searchProgressBar.visibility = View.GONE
            searchNoResultsText.visibility = View.GONE
        }
    }

    override fun timelineCases(): TimelineCases {
        return timelineCases
    }

    override fun removeItem(position: Int) {
        searchAdapter.removeStatusAtPosition(position)
    }

    override fun onReply(position: Int) {
        val status = searchAdapter.getStatusAtPosition(position)
        if (status != null) {
            super.reply(status)
        }
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        val status = searchAdapter.getStatusAtPosition(position)
        if (status != null) {
            timelineCases.reblog(status, reblog)
                    .observeOn(AndroidSchedulers.mainThread())
                    .autoDisposable(from(this, Lifecycle.Event.ON_DESTROY))
                    .subscribe({
                        status.reblogged = reblog
                        searchAdapter.updateStatusAtPosition(
                                ViewDataUtils.statusToViewData(
                                        status,
                                        alwaysShowSensitiveMedia
                                ),
                                position
                        )
                    }, { t -> Log.d(TAG, "Failed to reblog status " + status.id, t) })
        }
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        val status = searchAdapter.getStatusAtPosition(position)
        if (status != null) {
            timelineCases.favourite(status, favourite)
                    .observeOn(AndroidSchedulers.mainThread())
                    .autoDisposable(from(this, Lifecycle.Event.ON_DESTROY))
                    .subscribe({
                        status.favourited = favourite
                        searchAdapter.updateStatusAtPosition(
                                ViewDataUtils.statusToViewData(
                                        status,
                                        alwaysShowSensitiveMedia
                                ),
                                position
                        )
                    }, { t -> Log.d(TAG, "Failed to favourite status " + status.id, t) })
        }
    }

    override fun onMore(view: View, position: Int) {
        val status = searchAdapter.getStatusAtPosition(position)
        if (status != null) {
            more(status, view, position)
        }
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        val status = searchAdapter.getStatusAtPosition(position) ?: return
        viewMedia(attachmentIndex, status, view)
    }

    override fun onViewThread(position: Int) {
        val status = searchAdapter.getStatusAtPosition(position)
        if (status != null) {
            viewThread(status)
        }
    }

    override fun onOpenReblog(position: Int) {
        // there are no reblogs in search results
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        val status = searchAdapter.getConcreteStatusAtPosition(position)
        if (status != null) {
            val newStatus = StatusViewData.Builder(status)
                    .setIsExpanded(expanded).createStatusViewData()
            searchAdapter.updateStatusAtPosition(newStatus, position)
        }
    }

    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        val status = searchAdapter.getConcreteStatusAtPosition(position)
        if (status != null) {
            val newStatus = StatusViewData.Builder(status)
                    .setIsShowingSensitiveContent(isShowing).createStatusViewData()
            searchAdapter.updateStatusAtPosition(newStatus, position)
        }
    }

    override fun onLoadMore(position: Int) {
        // not needed here, search is not paginated
    }

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        // TODO: No out-of-bounds check in getConcreteStatusAtPosition
        val status = searchAdapter.getConcreteStatusAtPosition(position)
        if (status == null) {
            Log.e(TAG, String.format("Tried to access status but got null at position: %d", position))
            return
        }

        val updatedStatus = StatusViewData.Builder(status)
                .setCollapsed(isCollapsed)
                .createStatusViewData()
        searchAdapter.updateStatusAtPosition(updatedStatus, position)
        searchRecyclerView.post { searchAdapter.notifyItemChanged(position, updatedStatus) }
    }

    companion object {
        const val TAG = "SearchFragment"
    }

    override fun onViewAccount(id: String) {
        val intent = AccountActivity.getIntent(requireContext(), id)
        startActivity(intent)
    }

    override fun onViewTag(tag: String) {
        val intent = Intent(context, ViewTagActivity::class.java)
        intent.putExtra("hashtag", tag)
        startActivity(intent)
    }

}
