/* Copyright 2018 Conny Duck
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma.fragment

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import tech.bigfig.roma.AccountActivity
import tech.bigfig.roma.R
import tech.bigfig.roma.ViewTagActivity
import tech.bigfig.roma.adapter.SearchResultsAdapter
import tech.bigfig.roma.di.Injectable
import tech.bigfig.roma.entity.SearchResults
import tech.bigfig.roma.entity.Status
import tech.bigfig.roma.interfaces.StatusActionListener
import tech.bigfig.roma.network.TimelineCases
import tech.bigfig.roma.util.ViewDataUtils
import tech.bigfig.roma.viewdata.StatusViewData
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
        alwaysShowSensitiveMedia = preferences.getBoolean("alwaysShowSensitiveMedia", false)
        mediaPreviewEnabled = preferences.getBoolean("mediaPreviewEnabled", true)
        useAbsoluteTime = preferences.getBoolean("absoluteTimeView", false)

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
        if(isAdded) {
            searchProgressBar.visibility = View.GONE
            searchNoResultsText.visibility = View.VISIBLE
        }
    }

    private fun hideFeedback() {
        if(isAdded) {
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
        if(status != null) {
            super.reply(status)
        }
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        val status = searchAdapter.getStatusAtPosition(position)
        if (status != null) {
            timelineCases.reblogWithCallback(status, reblog, object: Callback<Status> {
                override fun onResponse(call: Call<Status>?, response: Response<Status>?) {
                    status.reblogged = true
                    searchAdapter.updateStatusAtPosition(
                            ViewDataUtils.statusToViewData(
                                    status,
                                    alwaysShowSensitiveMedia
                            ),
                            position
                    )
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
                    searchAdapter.updateStatusAtPosition(
                            ViewDataUtils.statusToViewData(
                                    status,
                                    alwaysShowSensitiveMedia
                            ),
                            position
                    )
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

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        val status = searchAdapter.getStatusAtPosition(position) ?: return
        viewMedia(attachmentIndex, status, view)
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

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        // TODO: No out-of-bounds check in getConcreteStatusAtPosition
        val status = searchAdapter.getConcreteStatusAtPosition(position)
        if(status == null) {
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
