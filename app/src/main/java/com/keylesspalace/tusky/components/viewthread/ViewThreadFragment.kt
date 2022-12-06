/* Copyright 2022 Tusky Contributors
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

package com.keylesspalace.tusky.components.viewthread

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.AccountListActivity
import com.keylesspalace.tusky.AccountListActivity.Companion.newIntent
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.FragmentViewThreadBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.fragment.SFragment
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.CardViewMode
import com.keylesspalace.tusky.util.ListStatusAccessibilityDelegate
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.openLink
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.viewdata.AttachmentViewData.Companion.list
import com.keylesspalace.tusky.viewdata.StatusViewData
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

class ViewThreadFragment : SFragment(), OnRefreshListener, StatusActionListener, Injectable {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: ViewThreadViewModel by viewModels { viewModelFactory }

    private val binding by viewBinding(FragmentViewThreadBinding::bind)

    private lateinit var adapter: ThreadAdapter
    private lateinit var thisThreadsStatusId: String

    private var alwaysShowSensitiveMedia = false
    private var alwaysOpenSpoiler = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        thisThreadsStatusId = requireArguments().getString(ID_EXTRA)!!
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val statusDisplayOptions = StatusDisplayOptions(
            animateAvatars = preferences.getBoolean("animateGifAvatars", false),
            mediaPreviewEnabled = accountManager.activeAccount!!.mediaPreviewEnabled,
            useAbsoluteTime = preferences.getBoolean("absoluteTimeView", false),
            showBotOverlay = preferences.getBoolean("showBotOverlay", true),
            useBlurhash = preferences.getBoolean("useBlurhash", true),
            cardViewMode = if (preferences.getBoolean("showCardsInTimelines", false)) {
                CardViewMode.INDENTED
            } else {
                CardViewMode.NONE
            },
            confirmReblogs = preferences.getBoolean("confirmReblogs", true),
            confirmFavourites = preferences.getBoolean("confirmFavourites", false),
            hideStats = preferences.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, false),
            animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)
        )
        adapter = ThreadAdapter(statusDisplayOptions, this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_view_thread, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        binding.toolbar.setNavigationOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_reveal -> {
                    viewModel.toggleRevealButton()
                    true
                }
                R.id.action_open_in_web -> {
                    context?.openLink(requireArguments().getString(URL_EXTRA)!!)
                    true
                }
                else -> false
            }
        }

        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.setAccessibilityDelegateCompat(
            ListStatusAccessibilityDelegate(
                binding.recyclerView,
                this
            ) { index -> adapter.currentList.getOrNull(index) }
        )
        val divider = DividerItemDecoration(context, LinearLayout.VERTICAL)
        binding.recyclerView.addItemDecoration(divider)
        binding.recyclerView.addItemDecoration(ConversationLineItemDecoration(requireContext()))
        alwaysShowSensitiveMedia = accountManager.activeAccount!!.alwaysShowSensitiveMedia
        alwaysOpenSpoiler = accountManager.activeAccount!!.alwaysOpenSpoiler

        binding.recyclerView.adapter = adapter

        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { uiState ->
                when (uiState) {
                    is ThreadUiState.Loading -> {
                        updateRevealButton(RevealButtonState.NO_BUTTON)
                        binding.recyclerView.hide()
                        binding.statusView.hide()
                        binding.progressBar.show()
                    }
                    is ThreadUiState.Error -> {
                        Log.w(TAG, "failed to load status", uiState.throwable)

                        updateRevealButton(RevealButtonState.NO_BUTTON)
                        binding.swipeRefreshLayout.isRefreshing = false

                        binding.recyclerView.hide()
                        binding.statusView.show()
                        binding.progressBar.hide()

                        if (uiState.throwable is IOException) {
                            binding.statusView.setup(R.drawable.elephant_offline, R.string.error_network) {
                                viewModel.retry(thisThreadsStatusId)
                            }
                        } else {
                            binding.statusView.setup(R.drawable.elephant_error, R.string.error_generic) {
                                viewModel.retry(thisThreadsStatusId)
                            }
                        }
                    }
                    is ThreadUiState.Success -> {
                        if (uiState.statuses.none { viewData -> viewData.isDetailed }) {
                            // no detailed statuses available, e.g. because author is blocked
                            activity?.finish()
                            return@collect
                        }

                        adapter.submitList(uiState.statuses) {
                            if (viewModel.isInitialLoad) {
                                viewModel.isInitialLoad = false
                                val detailedPosition = adapter.currentList.indexOfFirst { viewData ->
                                    viewData.isDetailed
                                }
                                binding.recyclerView.scrollToPosition(detailedPosition)
                            }
                        }

                        updateRevealButton(uiState.revealButton)
                        binding.swipeRefreshLayout.isRefreshing = uiState.refreshing

                        binding.recyclerView.show()
                        binding.statusView.hide()
                        binding.progressBar.hide()
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.errors.collect { throwable ->
                Log.w(TAG, "failed to load status context", throwable)
                Snackbar.make(binding.root, R.string.error_generic, Snackbar.LENGTH_SHORT)
                    .setAction(R.string.action_retry) {
                        viewModel.retry(thisThreadsStatusId)
                    }
                    .show()
            }
        }

        viewModel.loadThread(thisThreadsStatusId)
    }

    private fun updateRevealButton(state: RevealButtonState) {
        val menuItem = binding.toolbar.menu.findItem(R.id.action_reveal)

        menuItem.isVisible = state != RevealButtonState.NO_BUTTON
        menuItem.setIcon(if (state == RevealButtonState.REVEAL) R.drawable.ic_eye_24dp else R.drawable.ic_hide_media_24dp)
    }

    override fun onRefresh() {
        viewModel.refresh(thisThreadsStatusId)
    }

    override fun onReply(position: Int) {
        super.reply(adapter.currentList[position].status)
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        val status = adapter.currentList[position]
        viewModel.reblog(reblog, status)
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        val status = adapter.currentList[position]
        viewModel.favorite(favourite, status)
    }

    override fun onBookmark(bookmark: Boolean, position: Int) {
        val status = adapter.currentList[position]
        viewModel.bookmark(bookmark, status)
    }

    override fun onMore(view: View, position: Int) {
        super.more(adapter.currentList[position].status, view, position)
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        val status = adapter.currentList[position].status
        super.viewMedia(attachmentIndex, list(status), view)
    }

    override fun onViewThread(position: Int) {
        val status = adapter.currentList[position]
        if (thisThreadsStatusId == status.id) {
            // If already viewing this thread, don't reopen it.
            return
        }
        super.viewThread(status.actionableId, status.actionable.url)
    }

    override fun onViewUrl(url: String) {
        val status: StatusViewData.Concrete? = viewModel.detailedStatus()
        if (status != null && status.status.url == url) {
            // already viewing the status with this url
            // probably just a preview federated and the user is clicking again to view more -> open the browser
            // this can happen with some friendica statuses
            requireContext().openLink(url)
            return
        }
        super.onViewUrl(url)
    }

    override fun onOpenReblog(position: Int) {
        // there are no reblogs in threads
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        viewModel.changeExpanded(expanded, adapter.currentList[position])
    }

    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        viewModel.changeContentShowing(isShowing, adapter.currentList[position])
    }

    override fun onLoadMore(position: Int) {
        // only used in timelines
    }

    override fun onShowReblogs(position: Int) {
        val statusId = adapter.currentList[position].id
        val intent = newIntent(requireContext(), AccountListActivity.Type.REBLOGGED, statusId)
        (requireActivity() as BaseActivity).startActivityWithSlideInAnimation(intent)
    }

    override fun onShowFavs(position: Int) {
        val statusId = adapter.currentList[position].id
        val intent = newIntent(requireContext(), AccountListActivity.Type.FAVOURITED, statusId)
        (requireActivity() as BaseActivity).startActivityWithSlideInAnimation(intent)
    }

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        viewModel.changeContentCollapsed(isCollapsed, adapter.currentList[position])
    }

    override fun onViewTag(tag: String) {
        super.viewTag(tag)
    }

    override fun onViewAccount(id: String) {
        super.viewAccount(id)
    }

    public override fun removeItem(position: Int) {
        val status = adapter.currentList[position]
        if (status.isDetailed) {
            // the main status we are viewing is being removed, finish the activity
            activity?.finish()
            return
        }
        viewModel.removeStatus(status)
    }

    override fun onVoteInPoll(position: Int, choices: List<Int>) {
        val status = adapter.currentList[position]
        viewModel.voteInPoll(choices, status)
    }

    companion object {
        private const val TAG = "ViewThreadFragment"

        private const val ID_EXTRA = "id"
        private const val URL_EXTRA = "url"

        fun newInstance(id: String, url: String): ViewThreadFragment {
            val arguments = Bundle(2)
            val fragment = ViewThreadFragment()
            arguments.putString(ID_EXTRA, id)
            arguments.putString(URL_EXTRA, url)
            fragment.arguments = arguments
            return fragment
        }
    }
}
