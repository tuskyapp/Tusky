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

import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import at.connyduck.sparkbutton.helpers.Utils
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.StatusListActivity
import com.keylesspalace.tusky.appstore.ConversationsLoadingEvent
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.components.account.AccountActivity
import com.keylesspalace.tusky.databinding.FragmentTimelineBinding
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.fragment.SFragment
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.CardViewMode
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.ensureBottomPadding
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.isAnyLoading
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.updateRelativeTimePeriodically
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ConversationsFragment :
    SFragment(R.layout.fragment_timeline),
    StatusActionListener,
    ReselectableFragment,
    MenuProvider {

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var preferences: SharedPreferences

    private val viewModel: ConversationsViewModel by viewModels()

    private val binding by viewBinding(FragmentTimelineBinding::bind)

    private var adapter: ConversationAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        val statusDisplayOptions = StatusDisplayOptions(
            animateAvatars = preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false),
            mediaPreviewEnabled = accountManager.activeAccount?.mediaPreviewEnabled != false,
            useAbsoluteTime = preferences.getBoolean(PrefKeys.ABSOLUTE_TIME_VIEW, false),
            showBotOverlay = preferences.getBoolean(PrefKeys.SHOW_BOT_OVERLAY, true),
            useBlurhash = preferences.getBoolean(PrefKeys.USE_BLURHASH, true),
            cardViewMode = CardViewMode.NONE,
            confirmReblogs = preferences.getBoolean(PrefKeys.CONFIRM_REBLOGS, true),
            confirmFavourites = preferences.getBoolean(PrefKeys.CONFIRM_FAVOURITES, false),
            hideStats = preferences.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, false),
            animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false),
            showStatsInline = preferences.getBoolean(PrefKeys.SHOW_STATS_INLINE, false),
            showSensitiveMedia = accountManager.activeAccount!!.alwaysShowSensitiveMedia,
            openSpoiler = accountManager.activeAccount!!.alwaysOpenSpoiler
        )

        val adapter = ConversationAdapter(statusDisplayOptions, this)
        this.adapter = adapter

        setupRecyclerView(adapter)

        binding.swipeRefreshLayout.setOnRefreshListener { refreshContent() }

        adapter.addLoadStateListener { loadState ->
            if (loadState.refresh != LoadState.Loading && loadState.source.refresh != LoadState.Loading) {
                binding.swipeRefreshLayout.isRefreshing = false
            }

            binding.statusView.hide()
            binding.progressBar.hide()

            if (loadState.isAnyLoading()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    eventHub.dispatch(
                        ConversationsLoadingEvent(
                            accountManager.activeAccount?.accountId ?: ""
                        )
                    )
                }
            }

            if (adapter.itemCount == 0) {
                when (loadState.refresh) {
                    is LoadState.NotLoading -> {
                        if (loadState.append is LoadState.NotLoading && loadState.source.refresh is LoadState.NotLoading) {
                            binding.statusView.show()
                            binding.statusView.setup(
                                R.drawable.elephant_friend_empty,
                                R.string.message_empty,
                                null
                            )
                            binding.statusView.showHelp(R.string.help_empty_conversations)
                        }
                    }

                    is LoadState.Error -> {
                        binding.statusView.show()
                        binding.statusView.setup(
                            (loadState.refresh as LoadState.Error).error
                        ) { refreshContent() }
                    }

                    is LoadState.Loading -> {
                        binding.progressBar.show()
                    }
                }
            }
        }

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                val firstPos = (binding.recyclerView.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
                if (firstPos == 0 && positionStart == 0 && adapter.itemCount != itemCount) {
                    binding.recyclerView.post {
                        if (getView() != null) {
                            binding.recyclerView.scrollBy(0, Utils.dpToPx(requireContext(), -30))
                        }
                    }
                }
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.conversationFlow.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        updateRelativeTimePeriodically(preferences, adapter)

        viewLifecycleOwner.lifecycleScope.launch {
            eventHub.events.collect { event ->
                if (event is PreferenceChangedEvent) {
                    onPreferenceChanged(adapter, event.preferenceKey)
                }
            }
        }
    }

    override fun onDestroyView() {
        // Clear the adapter to prevent leaking the View
        adapter = null
        super.onDestroyView()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_conversations, menu)
        menu.findItem(R.id.action_refresh)?.apply {
            icon = IconicsDrawable(requireContext(), GoogleMaterial.Icon.gmd_refresh).apply {
                sizeDp = 20
                colorInt = MaterialColors.getColor(binding.root, android.R.attr.textColorPrimary)
            }
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_refresh -> {
                binding.swipeRefreshLayout.isRefreshing = true
                refreshContent()
                true
            }

            else -> false
        }
    }

    private fun setupRecyclerView(adapter: ConversationAdapter) {
        binding.recyclerView.ensureBottomPadding(fab = true)
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)

        binding.recyclerView.addItemDecoration(
            DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
        )

        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        binding.recyclerView.adapter =
            adapter.withLoadStateFooter(ConversationLoadStateAdapter(adapter::retry))
    }

    private fun refreshContent() {
        adapter?.refresh()
    }

    override fun onReblog(reblog: Boolean, position: Int, visibility: Status.Visibility) {
        // its impossible to reblog private messages
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        adapter?.peek(position)?.let { conversation ->
            viewModel.favourite(favourite, conversation)
        }
    }

    override fun onBookmark(favourite: Boolean, position: Int) {
        adapter?.peek(position)?.let { conversation ->
            viewModel.bookmark(favourite, conversation)
        }
    }

    override val onMoreTranslate: ((translate: Boolean, position: Int) -> Unit)? = null

    override fun onMore(view: View, position: Int) {
        adapter?.peek(position)?.let { conversation ->

            val popup = PopupMenu(requireContext(), view)
            popup.inflate(R.menu.conversation_more)

            if (conversation.lastStatus.status.muted) {
                popup.menu.removeItem(R.id.status_mute_conversation)
            } else {
                popup.menu.removeItem(R.id.status_unmute_conversation)
            }

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.status_mute_conversation -> viewModel.muteConversation(conversation)
                    R.id.status_unmute_conversation -> viewModel.muteConversation(conversation)
                    R.id.conversation_delete -> deleteConversation(conversation)
                }
                true
            }
            popup.show()
        }
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        adapter?.peek(position)?.let { conversation ->
            viewMedia(
                attachmentIndex,
                AttachmentViewData.list(conversation.lastStatus),
                view
            )
        }
    }

    override fun onViewThread(position: Int) {
        adapter?.peek(position)?.let { conversation ->
            viewThread(conversation.lastStatus.id, conversation.lastStatus.status.url)
        }
    }

    override fun onOpenReblog(position: Int) {
        // there are no reblogs in conversations
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        adapter?.peek(position)?.let { conversation ->
            viewModel.expandHiddenStatus(expanded, conversation)
        }
    }

    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        adapter?.peek(position)?.let { conversation ->
            viewModel.showContent(isShowing, conversation)
        }
    }

    override fun onLoadMore(position: Int) {
        // not using the old way of pagination
    }

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        adapter?.peek(position)?.let { conversation ->
            viewModel.collapseLongStatus(isCollapsed, conversation)
        }
    }

    override fun onViewAccount(id: String) {
        val intent = AccountActivity.getIntent(requireContext(), id)
        startActivity(intent)
    }

    override fun onViewTag(tag: String) {
        val intent = StatusListActivity.newHashtagIntent(requireContext(), tag)
        startActivity(intent)
    }

    override fun removeItem(position: Int) {
        // not needed
    }

    override fun onReply(position: Int) {
        adapter?.peek(position)?.let { conversation ->
            reply(conversation.lastStatus.status)
        }
    }

    override fun onVoteInPoll(position: Int, choices: List<Int>) {
        adapter?.peek(position)?.let { conversation ->
            viewModel.voteInPoll(choices, conversation)
        }
    }

    override fun onShowPollResults(position: Int) {
        adapter?.peek(position)?.let { conversation ->
            viewModel.showPollResults(conversation)
        }
    }

    override fun clearWarningAction(position: Int) {
    }

    override fun onReselect() {
        if (view != null) {
            binding.recyclerView.layoutManager?.scrollToPosition(0)
            binding.recyclerView.stopScroll()
        }
    }

    override fun onUntranslate(position: Int) {
        // not needed
    }

    private fun deleteConversation(conversation: ConversationViewData) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.dialog_delete_conversation_warning)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.remove(conversation)
            }
            .show()
    }

    private fun onPreferenceChanged(adapter: ConversationAdapter, key: String) {
        when (key) {
            PrefKeys.MEDIA_PREVIEW_ENABLED -> {
                val enabled = accountManager.activeAccount!!.mediaPreviewEnabled
                val oldMediaPreviewEnabled = adapter.mediaPreviewEnabled
                if (enabled != oldMediaPreviewEnabled) {
                    adapter.mediaPreviewEnabled = enabled
                    adapter.notifyItemRangeChanged(0, adapter.itemCount)
                }
            }
        }
    }

    companion object {
        fun newInstance() = ConversationsFragment()
    }
}
