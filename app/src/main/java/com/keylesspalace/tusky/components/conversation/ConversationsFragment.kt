/* Copyright 2019 Conny Duck
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

package com.keylesspalace.tusky.components.conversation

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.paging.PagedList
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.keylesspalace.tusky.AccountActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.ViewTagActivity
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.fragment.SFragment
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.NetworkState
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.ThemeUtils
import com.keylesspalace.tusky.util.hide
import kotlinx.android.synthetic.main.fragment_timeline.*
import javax.inject.Inject

class ConversationsFragment : SFragment(), StatusActionListener, Injectable, ReselectableFragment {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory
    @Inject
    lateinit var db: AppDatabase

    private val viewModel: ConversationsViewModel by viewModels { viewModelFactory }

    private lateinit var adapter: ConversationAdapter

    private var layoutManager: LinearLayoutManager? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_timeline, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(view.context)

        val statusDisplayOptions = StatusDisplayOptions(
                animateAvatars = preferences.getBoolean("animateGifAvatars", false),
                mediaPreviewEnabled = accountManager.activeAccount?.mediaPreviewEnabled ?: true,
                useAbsoluteTime = preferences.getBoolean("absoluteTimeView", false),
                showBotOverlay = preferences.getBoolean("showBotOverlay", true),
                useBlurhash = preferences.getBoolean("useBlurhash", true)
        )


        adapter = ConversationAdapter(statusDisplayOptions, this, ::onTopLoaded, viewModel::retry)

        recyclerView.addItemDecoration(DividerItemDecoration(view.context, DividerItemDecoration.VERTICAL))
        layoutManager = LinearLayoutManager(view.context)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        (recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        progressBar.hide()
        statusView.hide()

        initSwipeToRefresh()

        viewModel.conversations.observe(viewLifecycleOwner, Observer<PagedList<ConversationEntity>> {
            adapter.submitList(it)
        })
        viewModel.networkState.observe(viewLifecycleOwner, Observer {
            adapter.setNetworkState(it)
        })

        viewModel.load()

    }

    private fun initSwipeToRefresh() {
        viewModel.refreshState.observe(viewLifecycleOwner, Observer {
            swipeRefreshLayout.isRefreshing = it == NetworkState.LOADING
        })
        swipeRefreshLayout.setOnRefreshListener {
            viewModel.refresh()
        }
        swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ThemeUtils.getColor(swipeRefreshLayout.context, android.R.attr.colorBackground))
    }

    private fun onTopLoaded() {
        recyclerView.scrollToPosition(0)
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        // its impossible to reblog private messages
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        viewModel.favourite(favourite, position)
    }

    override fun onBookmark(favourite: Boolean, position: Int) {
        viewModel.bookmark(favourite, position)
    }

    override fun onMore(view: View, position: Int) {
        viewModel.conversations.value?.getOrNull(position)?.lastStatus?.let {
            more(it.toStatus(), view, position)
        }
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        viewModel.conversations.value?.getOrNull(position)?.lastStatus?.let {
            viewMedia(attachmentIndex, it.toStatus(), view)
        }
    }

    override fun onViewThread(position: Int) {
        viewModel.conversations.value?.getOrNull(position)?.lastStatus?.let {
            viewThread(it.toStatus())
        }
    }

    override fun onOpenReblog(position: Int) {
        // there are no reblogs in search results
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        viewModel.expandHiddenStatus(expanded, position)
    }

    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        viewModel.showContent(isShowing, position)
    }

    override fun onLoadMore(position: Int) {
        // not using the old way of pagination
    }

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        viewModel.collapseLongStatus(isCollapsed, position)
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

    override fun removeItem(position: Int) {
        viewModel.remove(position)
    }

    override fun onReply(position: Int) {
        viewModel.conversations.value?.getOrNull(position)?.lastStatus?.let {
            reply(it.toStatus())
        }
    }

    private fun jumpToTop() {
        if (isAdded) {
            layoutManager?.scrollToPosition(0)
            recyclerView.stopScroll()
        }
    }

    override fun onReselect() {
        jumpToTop()
    }

    override fun onVoteInPoll(position: Int, choices: MutableList<Int>) {
        viewModel.voteInPoll(position, choices)
    }

    companion object {
        fun newInstance() = ConversationsFragment()
    }
}
