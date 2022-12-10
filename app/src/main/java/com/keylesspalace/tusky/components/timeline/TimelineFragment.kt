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

package com.keylesspalace.tusky.components.timeline

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import at.connyduck.sparkbutton.helpers.Utils
import autodispose2.androidx.lifecycle.autoDispose
import com.keylesspalace.tusky.AccountListActivity
import com.keylesspalace.tusky.AccountListActivity.Companion.newIntent
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.appstore.StatusComposedEvent
import com.keylesspalace.tusky.components.timeline.viewmodel.CachedTimelineViewModel
import com.keylesspalace.tusky.components.timeline.viewmodel.NetworkTimelineViewModel
import com.keylesspalace.tusky.components.timeline.viewmodel.TimelineViewModel
import com.keylesspalace.tusky.databinding.FragmentTimelineBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.fragment.SFragment
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.interfaces.RefreshableFragment
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.CardViewMode
import com.keylesspalace.tusky.util.ListStatusAccessibilityDelegate
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TimelineFragment :
    SFragment(),
    OnRefreshListener,
    StatusActionListener,
    Injectable,
    ReselectableFragment,
    RefreshableFragment {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var eventHub: EventHub

    private val viewModel: TimelineViewModel by lazy {
        if (kind == TimelineViewModel.Kind.HOME) {
            ViewModelProvider(this, viewModelFactory)[CachedTimelineViewModel::class.java]
        } else {
            ViewModelProvider(this, viewModelFactory)[NetworkTimelineViewModel::class.java]
        }
    }

    private val binding by viewBinding(FragmentTimelineBinding::bind)

    private lateinit var kind: TimelineViewModel.Kind

    private lateinit var adapter: TimelinePagingAdapter

    private var isSwipeToRefreshEnabled = true
    private var hideFab = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val arguments = requireArguments()
        kind = TimelineViewModel.Kind.valueOf(arguments.getString(KIND_ARG)!!)
        val id: String? = if (kind == TimelineViewModel.Kind.USER ||
            kind == TimelineViewModel.Kind.USER_PINNED ||
            kind == TimelineViewModel.Kind.USER_WITH_REPLIES ||
            kind == TimelineViewModel.Kind.LIST
        ) {
            arguments.getString(ID_ARG)!!
        } else {
            null
        }

        val tags = if (kind == TimelineViewModel.Kind.TAG) {
            arguments.getStringArrayList(HASHTAGS_ARG)!!
        } else {
            listOf()
        }
        viewModel.init(
            kind,
            id,
            tags,
        )

        isSwipeToRefreshEnabled = arguments.getBoolean(ARG_ENABLE_SWIPE_TO_REFRESH, true)

        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val statusDisplayOptions = StatusDisplayOptions(
            animateAvatars = preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false),
            mediaPreviewEnabled = accountManager.activeAccount!!.mediaPreviewEnabled,
            useAbsoluteTime = preferences.getBoolean(PrefKeys.ABSOLUTE_TIME_VIEW, false),
            showBotOverlay = preferences.getBoolean(PrefKeys.SHOW_BOT_OVERLAY, true),
            useBlurhash = preferences.getBoolean(PrefKeys.USE_BLURHASH, true),
            cardViewMode = if (preferences.getBoolean(
                    PrefKeys.SHOW_CARDS_IN_TIMELINES,
                    false
                )
            ) CardViewMode.INDENTED else CardViewMode.NONE,
            confirmReblogs = preferences.getBoolean(PrefKeys.CONFIRM_REBLOGS, true),
            confirmFavourites = preferences.getBoolean(PrefKeys.CONFIRM_FAVOURITES, false),
            hideStats = preferences.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, false),
            animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)
        )
        adapter = TimelinePagingAdapter(
            statusDisplayOptions,
            this
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_timeline, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupSwipeRefreshLayout()
        setupRecyclerView()

        adapter.addLoadStateListener { loadState ->
            if (loadState.refresh != LoadState.Loading && loadState.source.refresh != LoadState.Loading) {
                binding.swipeRefreshLayout.isRefreshing = false
            }

            binding.statusView.hide()
            binding.progressBar.hide()

            if (adapter.itemCount == 0) {
                when (loadState.refresh) {
                    is LoadState.NotLoading -> {
                        if (loadState.append is LoadState.NotLoading && loadState.source.refresh is LoadState.NotLoading) {
                            binding.statusView.show()
                            binding.statusView.setup(R.drawable.elephant_friend_empty, R.string.message_empty, null)
                        }
                    }
                    is LoadState.Error -> {
                        binding.statusView.show()

                        if ((loadState.refresh as LoadState.Error).error is IOException) {
                            binding.statusView.setup(R.drawable.elephant_offline, R.string.error_network, null)
                        } else {
                            binding.statusView.setup(R.drawable.elephant_error, R.string.error_generic, null)
                        }
                    }
                    is LoadState.Loading -> {
                        binding.progressBar.show()
                    }
                }
            }
        }

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (positionStart == 0 && adapter.itemCount != itemCount) {
                    binding.recyclerView.post {
                        if (getView() != null) {
                            if (isSwipeToRefreshEnabled) {
                                binding.recyclerView.scrollBy(0, Utils.dpToPx(requireContext(), -30))
                            } else binding.recyclerView.scrollToPosition(0)
                        }
                    }
                }
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.statuses.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        if (actionButtonPresent()) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
            hideFab = preferences.getBoolean("fabHide", false)
            binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                    val composeButton = (activity as ActionButtonActivity).actionButton
                    if (composeButton != null) {
                        if (hideFab) {
                            if (dy > 0 && composeButton.isShown) {
                                composeButton.hide() // hides the button if we're scrolling down
                            } else if (dy < 0 && !composeButton.isShown) {
                                composeButton.show() // shows it if we are scrolling up
                            }
                        } else if (!composeButton.isShown) {
                            composeButton.show()
                        }
                    }
                }
            })
        }

        eventHub.events
            .observeOn(AndroidSchedulers.mainThread())
            .autoDispose(this, Lifecycle.Event.ON_DESTROY)
            .subscribe { event ->
                when (event) {
                    is PreferenceChangedEvent -> {
                        onPreferenceChanged(event.preferenceKey)
                    }
                    is StatusComposedEvent -> {
                        val status = event.status
                        handleStatusComposeEvent(status)
                    }
                }
            }
    }

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.isEnabled = isSwipeToRefreshEnabled
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)
    }

    private fun setupRecyclerView() {
        binding.recyclerView.setAccessibilityDelegateCompat(
            ListStatusAccessibilityDelegate(binding.recyclerView, this) { pos ->
                if (pos in 0 until adapter.itemCount) {
                    adapter.peek(pos)
                } else {
                    null
                }
            }
        )
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        val divider = DividerItemDecoration(context, RecyclerView.VERTICAL)
        binding.recyclerView.addItemDecoration(divider)

        // CWs are expanded without animation, buttons animate itself, we don't need it basically
        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        binding.recyclerView.adapter = adapter
    }

    override fun onRefresh() {
        binding.statusView.hide()

        adapter.refresh()
    }

    override fun onReply(position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        super.reply(status.status)
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        viewModel.reblog(reblog, status)
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        viewModel.favorite(favourite, status)
    }

    override fun onBookmark(bookmark: Boolean, position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        viewModel.bookmark(bookmark, status)
    }

    override fun onVoteInPoll(position: Int, choices: List<Int>) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        viewModel.voteInPoll(choices, status)
    }

    override fun onMore(view: View, position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        super.more(status.status, view, position)
    }

    override fun onOpenReblog(position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        super.openReblog(status.status)
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        viewModel.changeExpanded(expanded, status)
    }

    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        viewModel.changeContentShowing(isShowing, status)
    }

    override fun onShowReblogs(position: Int) {
        val statusId = adapter.peek(position)?.asStatusOrNull()?.id ?: return
        val intent = newIntent(requireContext(), AccountListActivity.Type.REBLOGGED, statusId)
        (activity as BaseActivity).startActivityWithSlideInAnimation(intent)
    }

    override fun onShowFavs(position: Int) {
        val statusId = adapter.peek(position)?.asStatusOrNull()?.id ?: return
        val intent = newIntent(requireContext(), AccountListActivity.Type.FAVOURITED, statusId)
        (activity as BaseActivity).startActivityWithSlideInAnimation(intent)
    }

    override fun onLoadMore(position: Int) {
        val placeholder = adapter.peek(position)?.asPlaceholderOrNull() ?: return
        viewModel.loadMore(placeholder.id)
    }

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        viewModel.changeContentCollapsed(isCollapsed, status)
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        super.viewMedia(
            attachmentIndex,
            AttachmentViewData.list(status.actionable),
            view
        )
    }

    override fun onViewThread(position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        super.viewThread(status.actionable.id, status.actionable.url)
    }

    override fun onViewTag(tag: String) {
        if (viewModel.kind == TimelineViewModel.Kind.TAG && viewModel.tags.size == 1 &&
            viewModel.tags.contains(tag)
        ) {
            // If already viewing a tag page, then ignore any request to view that tag again.
            return
        }
        super.viewTag(tag)
    }

    override fun onViewAccount(id: String) {
        if ((
            viewModel.kind == TimelineViewModel.Kind.USER ||
                viewModel.kind == TimelineViewModel.Kind.USER_WITH_REPLIES
            ) &&
            viewModel.id == id
        ) {
            /* If already viewing an account page, then any requests to view that account page
             * should be ignored. */
            return
        }
        super.viewAccount(id)
    }

    private fun onPreferenceChanged(key: String) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        when (key) {
            PrefKeys.FAB_HIDE -> {
                hideFab = sharedPreferences.getBoolean(PrefKeys.FAB_HIDE, false)
            }
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

    private fun handleStatusComposeEvent(status: Status) {
        when (kind) {
            TimelineViewModel.Kind.HOME,
            TimelineViewModel.Kind.PUBLIC_FEDERATED,
            TimelineViewModel.Kind.PUBLIC_LOCAL -> adapter.refresh()
            TimelineViewModel.Kind.USER,
            TimelineViewModel.Kind.USER_WITH_REPLIES -> if (status.account.id == viewModel.id) {
                adapter.refresh()
            }
            TimelineViewModel.Kind.TAG,
            TimelineViewModel.Kind.FAVOURITES,
            TimelineViewModel.Kind.LIST,
            TimelineViewModel.Kind.BOOKMARKS,
            TimelineViewModel.Kind.USER_PINNED -> return
        }
    }

    public override fun removeItem(position: Int) {
        val status = adapter.peek(position)?.asStatusOrNull() ?: return
        viewModel.removeStatusWithId(status.id)
    }

    private fun actionButtonPresent(): Boolean {
        return viewModel.kind != TimelineViewModel.Kind.TAG &&
            viewModel.kind != TimelineViewModel.Kind.FAVOURITES &&
            viewModel.kind != TimelineViewModel.Kind.BOOKMARKS &&
            activity is ActionButtonActivity
    }

    private var talkBackWasEnabled = false

    override fun onResume() {
        super.onResume()
        val a11yManager =
            ContextCompat.getSystemService(requireContext(), AccessibilityManager::class.java)

        val wasEnabled = talkBackWasEnabled
        talkBackWasEnabled = a11yManager?.isEnabled == true
        Log.d(TAG, "talkback was enabled: $wasEnabled, now $talkBackWasEnabled")
        if (talkBackWasEnabled && !wasEnabled) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }
        startUpdateTimestamp()
    }

    /**
     * Start to update adapter every minute to refresh timestamp
     * If setting absoluteTimeView is false
     * Auto dispose observable on pause
     */
    private fun startUpdateTimestamp() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val useAbsoluteTime = preferences.getBoolean(PrefKeys.ABSOLUTE_TIME_VIEW, false)
        if (!useAbsoluteTime) {
            Observable.interval(0, 1, TimeUnit.MINUTES)
                .observeOn(AndroidSchedulers.mainThread())
                .autoDispose(this, Lifecycle.Event.ON_PAUSE)
                .subscribe {
                    adapter.notifyItemRangeChanged(0, adapter.itemCount, listOf(StatusBaseViewHolder.Key.KEY_CREATED))
                }
        }
    }

    override fun onReselect() {
        if (isAdded) {
            binding.recyclerView.layoutManager?.scrollToPosition(0)
            binding.recyclerView.stopScroll()
        }
    }

    override fun refreshContent() {
        onRefresh()
    }

    companion object {
        private const val TAG = "TimelineF" // logging tag
        private const val KIND_ARG = "kind"
        private const val ID_ARG = "id"
        private const val HASHTAGS_ARG = "hashtags"
        private const val ARG_ENABLE_SWIPE_TO_REFRESH = "enableSwipeToRefresh"

        fun newInstance(
            kind: TimelineViewModel.Kind,
            hashtagOrId: String? = null,
            enableSwipeToRefresh: Boolean = true
        ): TimelineFragment {
            val fragment = TimelineFragment()
            val arguments = Bundle(3)
            arguments.putString(KIND_ARG, kind.name)
            arguments.putString(ID_ARG, hashtagOrId)
            arguments.putBoolean(ARG_ENABLE_SWIPE_TO_REFRESH, enableSwipeToRefresh)
            fragment.arguments = arguments
            return fragment
        }

        @JvmStatic
        fun newHashtagInstance(hashtags: List<String>): TimelineFragment {
            val fragment = TimelineFragment()
            val arguments = Bundle(3)
            arguments.putString(KIND_ARG, TimelineViewModel.Kind.TAG.name)
            arguments.putStringArrayList(HASHTAGS_ARG, ArrayList(hashtags))
            arguments.putBoolean(ARG_ENABLE_SWIPE_TO_REFRESH, true)
            fragment.arguments = arguments
            return fragment
        }
    }
}
