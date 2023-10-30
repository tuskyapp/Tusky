package com.keylesspalace.tusky.components.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.NotificationsAdapter
import com.keylesspalace.tusky.databinding.FragmentTimelineNotificationsBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.CardViewMode
import com.keylesspalace.tusky.util.ListStatusAccessibilityDelegate
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.StatusProvider
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.viewdata.NotificationViewData
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class NotificationsFragment: Fragment(), SwipeRefreshLayout.OnRefreshListener,
    StatusActionListener, NotificationsAdapter.NotificationActionListener, AccountActionListener, Injectable {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val binding by viewBinding(FragmentTimelineNotificationsBinding::bind)

    private val viewModel: NotificationsViewModel by viewModels { viewModelFactory }

    private lateinit var layoutManager: LayoutManager
    private lateinit var adapter: NotificationsPagingAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_timeline_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val showNotificationsFilter = preferences.getBoolean(PrefKeys.SHOW_NOTIFICATIONS_FILTER, true)

        // Setup the SwipeRefreshLayout.
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)

        // Setup the RecyclerView.
        binding.recyclerView.setHasFixedSize(true)
        layoutManager = LinearLayoutManager(context)
        adapter = NotificationsPagingAdapter(
            accountId = "1", //TODO
            statusListener = this,
            notificationActionListener = this,
            accountActionListener = this,
            statusDisplayOptions = StatusDisplayOptions(
                true,true,true,true,true,CardViewMode.INDENTED,true,true,true,true,true,true,true
            ) //TODO
        )
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.setAccessibilityDelegateCompat(
            ListStatusAccessibilityDelegate(binding.recyclerView, this, StatusProvider { pos: Int ->
                if (pos in 0 until adapter.itemCount) {
                    val notification = adapter.peek(pos)
                    // We support replies only for now
                    if (notification is NotificationViewData.Concrete) {
                        return@StatusProvider notification.statusViewData
                    } else {
                        return@StatusProvider null
                    }
                } else {
                    null
                }
            })
        )

        binding.recyclerView.adapter = adapter

        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        binding.recyclerView.addItemDecoration(
            DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
        )

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
                            binding.statusView.setup(R.drawable.elephant_friend_empty, R.string.message_empty)
                        }
                    }
                    is LoadState.Error -> {
                        binding.statusView.show()
                        binding.statusView.setup((loadState.refresh as LoadState.Error).error) { onRefresh() }
                    }
                    is LoadState.Loading -> {
                        binding.progressBar.show()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.notifications.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        binding.recyclerView.show()
        binding.progressBar.hide()
    }

    override fun onRefresh() {
        adapter.refresh()
    }

    override fun onViewAccount(id: String) {
        TODO("Not yet implemented")
    }

    override fun onMute(mute: Boolean, id: String, position: Int, notifications: Boolean) {
        TODO("Not yet implemented")
    }

    override fun onBlock(block: Boolean, id: String, position: Int) {
        TODO("Not yet implemented")
    }

    override fun onRespondToFollowRequest(accept: Boolean, id: String, position: Int) {
        TODO("Not yet implemented")
    }

    override fun onViewStatusForNotificationId(notificationId: String?) {
        TODO("Not yet implemented")
    }

    override fun onViewReport(reportId: String?) {
        TODO("Not yet implemented")
    }

    override fun onViewUrl(url: String) {
        TODO("Not yet implemented")
    }

    override fun onViewTag(tag: String) {
        TODO("Not yet implemented")
    }

    override fun onReply(position: Int) {
        TODO("Not yet implemented")
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        TODO("Not yet implemented")
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        TODO("Not yet implemented")
    }

    override fun onBookmark(bookmark: Boolean, position: Int) {
        TODO("Not yet implemented")
    }

    override fun onMore(view: View, position: Int) {
        TODO("Not yet implemented")
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        TODO("Not yet implemented")
    }

    override fun onViewThread(position: Int) {
        TODO("Not yet implemented")
    }

    override fun onOpenReblog(position: Int) {
        TODO("Not yet implemented")
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        TODO("Not yet implemented")
    }

    override fun onNotificationContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        TODO("Not yet implemented")
    }

    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        TODO("Not yet implemented")
    }

    override fun onLoadMore(position: Int) {
        TODO("Not yet implemented")
    }

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        TODO("Not yet implemented")
    }

    override fun onVoteInPoll(position: Int, choices: MutableList<Int>) {
        TODO("Not yet implemented")
    }

    override fun clearWarningAction(position: Int) {
        TODO("Not yet implemented")
    }

    companion object {
        fun newInstance() = NotificationsFragment()
    }
}
