/* Copyright 2024 Tusky Contributors
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

package com.keylesspalace.tusky.components.notifications.requests.details

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import at.connyduck.calladapter.networkresult.onFailure
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.notifications.NotificationActionListener
import com.keylesspalace.tusky.components.notifications.NotificationsPagingAdapter
import com.keylesspalace.tusky.databinding.FragmentNotificationRequestDetailsBinding
import com.keylesspalace.tusky.fragment.SFragment
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.CardViewMode
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.getErrorString
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.openLink
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import com.keylesspalace.tusky.viewdata.TranslationViewData
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.getValue
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotificationRequestDetailsFragment : SFragment(R.layout.fragment_notification_request_details), StatusActionListener, NotificationActionListener, AccountActionListener {

    @Inject
    lateinit var preferences: SharedPreferences

    private val viewModel: NotificationRequestDetailsViewModel by activityViewModels()

    private val binding by viewBinding(FragmentNotificationRequestDetailsBinding::bind)

    private var adapter: NotificationsPagingAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapter().let { adapter ->
            this.adapter = adapter
            setupRecyclerView(adapter)

            lifecycleScope.launch {
                viewModel.pager.collectLatest { pagingData ->
                    adapter.submitData(pagingData)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.error.collect { error ->
                Snackbar.make(
                    binding.root,
                    error.getErrorString(requireContext()),
                    LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupRecyclerView(adapter: NotificationsPagingAdapter) {
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    }

    private fun setupAdapter(): NotificationsPagingAdapter {
        val statusDisplayOptions = StatusDisplayOptions(
            animateAvatars = preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false),
            mediaPreviewEnabled = accountManager.activeAccount!!.mediaPreviewEnabled,
            useAbsoluteTime = preferences.getBoolean(PrefKeys.ABSOLUTE_TIME_VIEW, false),
            showBotOverlay = preferences.getBoolean(PrefKeys.SHOW_BOT_OVERLAY, true),
            useBlurhash = preferences.getBoolean(PrefKeys.USE_BLURHASH, true),
            cardViewMode = if (preferences.getBoolean(PrefKeys.SHOW_CARDS_IN_TIMELINES, false)) {
                CardViewMode.INDENTED
            } else {
                CardViewMode.NONE
            },
            confirmReblogs = preferences.getBoolean(PrefKeys.CONFIRM_REBLOGS, true),
            confirmFavourites = preferences.getBoolean(PrefKeys.CONFIRM_FAVOURITES, false),
            hideStats = preferences.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, false),
            animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false),
            showStatsInline = preferences.getBoolean(PrefKeys.SHOW_STATS_INLINE, false),
            showSensitiveMedia = accountManager.activeAccount!!.alwaysShowSensitiveMedia,
            openSpoiler = accountManager.activeAccount!!.alwaysOpenSpoiler
        )

        return NotificationsPagingAdapter(
            accountId = accountManager.activeAccount!!.accountId,
            statusDisplayOptions = statusDisplayOptions,
            statusListener = this,
            notificationActionListener = this,
            accountActionListener = this
        ).apply {
            addLoadStateListener { loadState ->
                binding.progressBar.visible(
                    loadState.refresh == LoadState.Loading && itemCount == 0
                )

                if (loadState.refresh is LoadState.Error) {
                    binding.recyclerView.hide()
                    binding.statusView.show()
                    val errorState = loadState.refresh as LoadState.Error
                    binding.statusView.setup(errorState.error) { retry() }
                    Log.w(TAG, "error loading notifications for user ${viewModel.accountId}", errorState.error)
                } else {
                    binding.recyclerView.show()
                    binding.statusView.hide()
                }
            }
        }
    }

    override fun onReply(position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        super.reply(status.status)
    }

    override fun removeItem(position: Int) {
        val notification = adapter?.peek(position) ?: return
        viewModel.remove(notification)
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        viewModel.reblog(reblog, status)
    }

    override val onMoreTranslate: ((Boolean, Int) -> Unit)?
        get() = { translate: Boolean, position: Int ->
            if (translate) {
                onTranslate(position)
            } else {
                onUntranslate(position)
            }
        }

    override fun onFavourite(favourite: Boolean, position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        viewModel.favorite(favourite, status)
    }

    override fun onBookmark(bookmark: Boolean, position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        viewModel.bookmark(bookmark, status)
    }

    override fun onMore(view: View, position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        super.more(
            status.status,
            view,
            position,
            (status.translation as? TranslationViewData.Loaded)?.data
        )
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        super.viewMedia(attachmentIndex, AttachmentViewData.list(status), view)
    }

    override fun onViewThread(position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull()?.status ?: return
        super.viewThread(status.id, status.url)
    }

    override fun onOpenReblog(position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        super.openReblog(status.status)
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        viewModel.changeExpanded(expanded, status)
    }

    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        viewModel.changeContentShowing(isShowing, status)
    }

    override fun onLoadMore(position: Int) {
        // not applicable here
    }

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        viewModel.changeContentCollapsed(isCollapsed, status)
    }

    override fun onVoteInPoll(position: Int, choices: List<Int>) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        viewModel.voteInPoll(choices, status)
    }

    override fun clearWarningAction(position: Int) {
        // not applicable here
    }

    private fun onTranslate(position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.translate(status)
                .onFailure {
                    Snackbar.make(
                        requireView(),
                        getString(R.string.ui_error_translate, it.message),
                        LENGTH_LONG
                    ).show()
                }
        }
    }

    override fun onUntranslate(position: Int) {
        val status = adapter?.peek(position)?.asStatusOrNull() ?: return
        viewModel.untranslate(status)
    }

    override fun onViewTag(tag: String) {
        super.viewTag(tag)
    }

    override fun onViewAccount(id: String) {
        super.viewAccount(id)
    }

    override fun onViewReport(reportId: String) {
        requireContext().openLink(
            "https://${accountManager.activeAccount!!.domain}/admin/reports/$reportId"
        )
    }

    override fun onMute(mute: Boolean, id: String, position: Int, notifications: Boolean) {
        // not needed, muting via the more menu on statuses is handled in SFragment
    }

    override fun onBlock(block: Boolean, id: String, position: Int) {
        // not needed, blocking via the more menu on statuses is handled in SFragment
    }

    override fun onRespondToFollowRequest(accept: Boolean, id: String, position: Int) {
        val notification = adapter?.peek(position) ?: return
        viewModel.respondToFollowRequest(accept, accountId = id, notification = notification)
    }

    override fun onDestroyView() {
        adapter = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "NotificationRequestsDetailsFragment"
        private const val EXTRA_ACCOUNT_ID = "accountId"
        fun newIntent(accountId: String, context: Context) = Intent(context, NotificationRequestDetailsActivity::class.java).apply {
            putExtra(EXTRA_ACCOUNT_ID, accountId)
        }
    }
}
