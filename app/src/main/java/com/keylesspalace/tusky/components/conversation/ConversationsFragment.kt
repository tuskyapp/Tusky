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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.paging.PagedList
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
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.network.TimelineCases
import com.keylesspalace.tusky.util.NetworkState
import com.keylesspalace.tusky.util.ThemeUtils
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import kotlinx.android.synthetic.main.fragment_timeline.*
import javax.inject.Inject

class ConversationsFragment : SFragment(), StatusActionListener, Injectable {

    @Inject
    lateinit var timelineCases: TimelineCases
    @Inject
    lateinit var viewModelFactory: ViewModelFactory
    @Inject
    lateinit var db: AppDatabase

    private lateinit var viewModel: ConversationsViewModel

    private lateinit var adapter: ConversationAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewModel = ViewModelProviders.of(this, viewModelFactory)[ConversationsViewModel::class.java]

        return inflater.inflate(R.layout.fragment_timeline, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val preferences = PreferenceManager.getDefaultSharedPreferences(view.context)
        val useAbsoluteTime = preferences.getBoolean("absoluteTimeView", false)

        val account = accountManager.activeAccount
        val mediaPreviewEnabled = account?.mediaPreviewEnabled ?: true


        adapter = ConversationAdapter(useAbsoluteTime, mediaPreviewEnabled,this, ::onTopLoaded, viewModel::retry)

        val divider = DividerItemDecoration(view.context, DividerItemDecoration.VERTICAL)
        val drawable = ThemeUtils.getDrawable(view.context, R.attr.status_divider_drawable, R.drawable.status_divider_dark)
        divider.setDrawable(drawable)
        recyclerView.addItemDecoration(divider)
        recyclerView.layoutManager = LinearLayoutManager(view.context)
        recyclerView.adapter = adapter
        (recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        progressBar.hide()
        statusView.hide()

        initSwipeToRefresh()

        viewModel.conversations.observe(this, Observer<PagedList<ConversationEntity>> {
            adapter.submitList(it)
        })
        viewModel.networkState.observe(this, Observer {
            adapter.setNetworkState(it)
        })

        viewModel.load()

        LocalBroadcastManager.getInstance(context!!).registerReceiver(refreshReceiver,
                IntentFilter("refresh"))

    }

    private fun initSwipeToRefresh() {
        viewModel.refreshState.observe(this, Observer {
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

    override fun onMore(view: View, position: Int) {
        viewModel.conversations.value?.getOrNull(position)?.lastStatus?.let {
            more(it.toStatus(), view, position)
        }
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View) {
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

    override fun timelineCases(): TimelineCases {
        return timelineCases
    }

    override fun removeItem(position: Int) {
        viewModel.remove(position)
    }

    override fun onReply(position: Int) {
        viewModel.conversations.value?.getOrNull(position)?.lastStatus?.let {
            reply(it.toStatus())
        }
    }

    companion object {
        fun newInstance() = ConversationsFragment()
    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            //refresh
            initSwipeToRefresh()
        }
    }
}
