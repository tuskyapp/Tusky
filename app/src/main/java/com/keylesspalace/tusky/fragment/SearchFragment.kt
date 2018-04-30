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
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.keylesspalace.tusky.AccountActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.ViewTagActivity
import com.keylesspalace.tusky.adapter.SearchResultsAdapter
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.SearchResults
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.network.TimelineCases
import com.keylesspalace.tusky.util.ViewDataUtils
import com.keylesspalace.tusky.viewdata.StatusViewData
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


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        alwaysShowSensitiveMedia = preferences.getBoolean("alwaysShowSensitiveMedia", false)
        mediaPreviewEnabled = preferences.getBoolean("mediaPreviewEnabled", true)

        searchRecyclerView.addItemDecoration(DividerItemDecoration(view.context, DividerItemDecoration.VERTICAL))
        searchRecyclerView.layoutManager = LinearLayoutManager(view.context)
        searchAdapter = SearchResultsAdapter(mediaPreviewEnabled, alwaysShowSensitiveMedia, this, this)
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
        searchProgressBar.visibility = View.GONE
        searchNoResultsText.visibility = View.VISIBLE
    }

    private fun hideFeedback() {
        searchProgressBar.visibility = View.GONE
        searchNoResultsText.visibility = View.GONE
    }

    override fun timelineCases(): TimelineCases {
        return timelineCases
    }

    override fun removeItem(position: Int) {
        searchAdapter.removeStatusAtPosition(position)
    }

    override fun removeAllByAccountId(accountId: String?) {
        // not supported
    }

    override fun onReply(position: Int) {
        val status = searchAdapter.getStatusAtPosition(position)
        if(status != null) {
            super.reply(status)
        }
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        val status = searchAdapter.getStatusAtPosition(position)
        if(status != null) {
            timelineCases.reblogWithCallback(status, reblog, object: Callback<Status> {
                override fun onResponse(call: Call<Status>?, response: Response<Status>?) {
                    status.reblogged = true
                    searchAdapter.updateStatusAtPosition(ViewDataUtils.statusToViewData(status, alwaysShowSensitiveMedia), position)
                }

                override fun onFailure(call: Call<Status>?, t: Throwable?) {
                    Log.d(TAG, "Failed to reblog status " + status.id, t)
                }

            })
        }
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        val status = searchAdapter.getStatusAtPosition(position)
        if(status != null) {
            timelineCases.favouriteWithCallback(status, favourite, object: Callback<Status> {
                override fun onResponse(call: Call<Status>?, response: Response<Status>?) {
                    status.favourited = true
                    searchAdapter.updateStatusAtPosition(ViewDataUtils.statusToViewData(status, alwaysShowSensitiveMedia), position)
                }

                override fun onFailure(call: Call<Status>?, t: Throwable?) {
                    Log.d(TAG, "Failed to favourite status " + status.id, t)
                }

            })
        }
    }

    override fun onMore(view: View?, position: Int) {
        val status = searchAdapter.getStatusAtPosition(position)
        if(status != null) {
            more(status, view, position)
        }
    }

    override fun onViewMedia(urls: Array<out String>?, index: Int, type: Attachment.Type?, view: View?) {
        viewMedia(urls, index, type, view)
    }

    override fun onViewThread(position: Int) {
        val status = searchAdapter.getStatusAtPosition(position)
        if(status != null) {
            viewThread(status)
        }
    }

    override fun onOpenReblog(position: Int) {
        // there are no reblogs in search results
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        val status = searchAdapter.getConcreteStatusAtPosition(position)
        if(status != null) {
            val newStatus = StatusViewData.Builder(status)
                    .setIsExpanded(expanded).createStatusViewData()
            searchAdapter.updateStatusAtPosition(newStatus, position)
        }
    }

    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        val status = searchAdapter.getConcreteStatusAtPosition(position)
        if(status != null) {
            val newStatus = StatusViewData.Builder(status)
                    .setIsShowingSensitiveContent(isShowing).createStatusViewData()
            searchAdapter.updateStatusAtPosition(newStatus, position)
        }
    }

    override fun onLoadMore(position: Int) {
        // not needed here, search is not paginated
    }

    companion object {
        const val TAG = "SearchFragment"
    }

    override fun onViewAccount(id: String) {
        val intent = Intent(context, AccountActivity::class.java)
        intent.putExtra("id", id)
        startActivity(intent)
    }

    override fun onViewTag(tag: String) {
        val intent = Intent(context, ViewTagActivity::class.java)
        intent.putExtra("hashtag", tag)
        startActivity(intent)
    }

}