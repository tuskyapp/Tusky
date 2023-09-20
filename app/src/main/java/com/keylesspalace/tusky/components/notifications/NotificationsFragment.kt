package com.keylesspalace.tusky.components.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.FragmentTimelineNotificationsBinding
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.ListStatusAccessibilityDelegate
import com.keylesspalace.tusky.util.StatusProvider
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.viewdata.NotificationViewData

class NotificationsFragment: Fragment(), SwipeRefreshLayout.OnRefreshListener,
    StatusActionListener {

    private val binding by viewBinding(FragmentTimelineNotificationsBinding::bind)

    private lateinit var layoutManager: LayoutManager
    //private lateinit var adapter: NotificationsAdapter

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

        // Setup the SwipeRefreshLayout.
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)

        // Setup the RecyclerView.

        // Setup the RecyclerView.
        binding.recyclerView.setHasFixedSize(true)
        layoutManager = LinearLayoutManager(context)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.setAccessibilityDelegateCompat(
            ListStatusAccessibilityDelegate(binding.recyclerView, this, StatusProvider { pos: Int ->
            /*    if (pos in 0 until adapter.itemCount) {
                    val notification = adapter.peek(pos)
                    // We support replies only for now
                    if (notification is NotificationViewData.Concrete) {
                        return@StatusProvider notification.statusViewData
                    } else {
                        return@StatusProvider null
                    }
                } else {
                    null
                }*/
                null
            })
        )

        binding.recyclerView.addItemDecoration(
            DividerItemDecoration(
                context,
                DividerItemDecoration.VERTICAL
            )
        )
    }

    override fun onRefresh() {


    }

    override fun onViewAccount(id: String) {
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

}
