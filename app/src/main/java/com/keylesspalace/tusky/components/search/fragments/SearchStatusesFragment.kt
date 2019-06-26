/* Copyright 2019 Joel Pyska
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

package com.keylesspalace.tusky.components.search.fragments

import android.preference.PreferenceManager
import android.view.View
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.LiveData
import androidx.paging.PagedList
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.keylesspalace.tusky.ViewMediaActivity
import com.keylesspalace.tusky.components.search.adapter.SearchStatusesAdapter
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.NetworkState
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import kotlinx.android.synthetic.main.fragment_search.*

class SearchStatusesFragment : SearchFragment<Pair<Status, StatusViewData.Concrete>>(), StatusActionListener {

    override val networkStateRefresh: LiveData<NetworkState>
        get() = viewModel.networkStateStatusRefresh
    override val networkState: LiveData<NetworkState>
        get() = viewModel.networkStateStatus
    override val data: LiveData<PagedList<Pair<Status, StatusViewData.Concrete>>>
        get() = viewModel.statuses

    override fun createAdapter(): PagedListAdapter<Pair<Status, StatusViewData.Concrete>, *> {
        val preferences = PreferenceManager.getDefaultSharedPreferences(searchRecyclerView.context)
        val useAbsoluteTime = preferences.getBoolean("absoluteTimeView", false)
        val showBotOverlay = preferences.getBoolean("showBotOverlay", true)
        val animateAvatar = preferences.getBoolean("animateGifAvatars", false)

        val account = accountManager.activeAccount
        viewModel.alwaysShowSensitiveMedia = account?.alwaysShowSensitiveMedia ?: false
        val mediaPreviewEnabled = account?.mediaPreviewEnabled ?: true

        searchRecyclerView.addItemDecoration(DividerItemDecoration(searchRecyclerView.context, DividerItemDecoration.VERTICAL))
        searchRecyclerView.layoutManager = LinearLayoutManager(searchRecyclerView.context)
        return SearchStatusesAdapter(useAbsoluteTime, mediaPreviewEnabled, showBotOverlay, animateAvatar, this)
    }


    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.let {
            viewModel.contentHiddenChange(it, isShowing)
        }
    }

    override fun onReply(position: Int) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.first?.let { status ->
            reply(status)
        }
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.let { status ->
            viewModel.favorite(status, favourite)
        }
    }

    override fun onMore(view: View, position: Int) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.first?.let {
            more(it, view, position)
        }
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.first?.actionableStatus?.let { actionable ->
            when (actionable.attachments[attachmentIndex].type) {
                Attachment.Type.GIFV, Attachment.Type.VIDEO, Attachment.Type.IMAGE -> {
                    val attachments = AttachmentViewData.list(actionable)
                    val intent = ViewMediaActivity.newIntent(context, attachments,
                            attachmentIndex)
                    if (view != null) {
                        val url = actionable.attachments[attachmentIndex].url
                        ViewCompat.setTransitionName(view, url)
                        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity(),
                                view, url)
                        startActivity(intent, options.toBundle())
                    } else {
                        startActivity(intent)
                    }
                }
                Attachment.Type.UNKNOWN -> {
                }
            }

        }

    }

    override fun onViewThread(position: Int) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.first?.let {
            viewThread(it)
        }
    }

    override fun onOpenReblog(position: Int) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.first?.let {
            openReblog(it)
        }
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.let {
            viewModel.expandedChange(it, expanded)
        }
    }

    override fun onLoadMore(position: Int) {
        //Ignore
    }

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.let {
            viewModel.collapsedChange(it, isCollapsed)
        }
    }

    override fun onVoteInPoll(position: Int, choices: MutableList<Int>) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.let {
            viewModel.voteInPoll(it, choices)
        }
    }

    override fun removeItem(position: Int) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.let {
            viewModel.removeItem(it)
        }
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.let { status ->
            viewModel.reblog(status, reblog)
        }
    }

    companion object {
        fun newInstance() = SearchStatusesFragment()
    }

}
