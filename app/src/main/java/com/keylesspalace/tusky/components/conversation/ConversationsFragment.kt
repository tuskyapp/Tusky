/* Copyright 2021 Tusky Contributors
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
import androidx.lifecycle.lifecycleScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadState
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.keylesspalace.tusky.AccountActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.ViewTagActivity
import com.keylesspalace.tusky.databinding.FragmentTimelineBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.fragment.SFragment
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.CardViewMode
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.viewBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class ConversationsFragment : SFragment(), StatusActionListener, Injectable, ReselectableFragment {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: ConversationsViewModel by viewModels { viewModelFactory }

    private val binding by viewBinding(FragmentTimelineBinding::bind)

    private lateinit var adapter: ConversationAdapter

    private var layoutManager: LinearLayoutManager? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_timeline, container, false)
    }

    @ExperimentalPagingApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(view.context)

        val statusDisplayOptions = StatusDisplayOptions(
                animateAvatars = preferences.getBoolean("animateGifAvatars", false),
                mediaPreviewEnabled = accountManager.activeAccount?.mediaPreviewEnabled ?: true,
                useAbsoluteTime = preferences.getBoolean("absoluteTimeView", false),
                showBotOverlay = preferences.getBoolean("showBotOverlay", true),
                useBlurhash = preferences.getBoolean("useBlurhash", true),
                cardViewMode = CardViewMode.NONE,
                confirmReblogs = preferences.getBoolean("confirmReblogs", true),
                hideStats = preferences.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, false),
                animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)
        )

        adapter = ConversationAdapter(statusDisplayOptions, this)

        binding.recyclerView.addItemDecoration(DividerItemDecoration(view.context, DividerItemDecoration.VERTICAL))
        layoutManager = LinearLayoutManager(view.context)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter.withLoadStateFooter(ConversationLoadStateAdapter(adapter::retry))
        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        binding.progressBar.hide()
        binding.statusView.hide()

        initSwipeToRefresh()

        lifecycleScope.launch {
            viewModel.conversationFlow.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        adapter.addLoadStateListener {
            if (it.refresh != LoadState.Loading) {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun initSwipeToRefresh() {
        adapter.addLoadStateListener { loadState ->
            binding.swipeRefreshLayout.isRefreshing = loadState.refresh == LoadState.Loading
        }
        binding.swipeRefreshLayout.setOnRefreshListener {
            adapter.refresh()
        }
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)
    }

    private fun onTopLoaded() {
        binding.recyclerView.scrollToPosition(0)
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        // its impossible to reblog private messages
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        adapter.item(position)?.let { conversation ->
            viewModel.favourite(favourite, conversation)
        }
    }

    override fun onBookmark(favourite: Boolean, position: Int) {
        adapter.item(position)?.let { conversation ->
            viewModel.bookmark(favourite, conversation)
        }
    }

    override fun onMore(view: View, position: Int) {
        adapter.item(position)?.let { conversation ->
            more(conversation.lastStatus.toStatus(), view, position)
        }
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        adapter.item(position)?.let { conversation ->
            viewMedia(attachmentIndex, conversation.lastStatus.toStatus(), view)
        }
    }

    override fun onViewThread(position: Int) {
        adapter.item(position)?.let { conversation ->
            viewThread(conversation.lastStatus.toStatus())
        }
    }

    override fun onOpenReblog(position: Int) {
        // there are no reblogs in search results
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        adapter.item(position)?.let { conversation ->
            viewModel.expandHiddenStatus(expanded, conversation)
        }
    }

    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        adapter.item(position)?.let { conversation ->
            viewModel.showContent(isShowing, conversation)
        }
    }

    override fun onLoadMore(position: Int) {
        // not using the old way of pagination
    }

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        adapter.item(position)?.let { conversation ->
            viewModel.collapseLongStatus(isCollapsed, conversation)
        }
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
        adapter.item(position)?.let { conversation ->
            viewModel.remove(conversation)
        }
    }

    override fun onReply(position: Int) {
        adapter.item(position)?.let { conversation ->
            reply(conversation.lastStatus.toStatus())
        }
    }

    private fun jumpToTop() {
        if (isAdded) {
            layoutManager?.scrollToPosition(0)
            binding.recyclerView.stopScroll()
        }
    }

    override fun onReselect() {
        jumpToTop()
    }

    override fun onVoteInPoll(position: Int, choices: MutableList<Int>) {
        adapter.item(position)?.let { conversation ->
            viewModel.voteInPoll(choices, conversation)
        }
    }

    companion object {
        fun newInstance() = ConversationsFragment()
    }
}
