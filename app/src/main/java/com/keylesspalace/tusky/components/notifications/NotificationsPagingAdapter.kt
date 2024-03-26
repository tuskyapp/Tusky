package com.keylesspalace.tusky.components.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.adapter.FollowRequestViewHolder
import com.keylesspalace.tusky.adapter.NotificationsAdapter
import com.keylesspalace.tusky.adapter.PlaceholderViewHolder
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder
import com.keylesspalace.tusky.databinding.ItemFollowBinding
import com.keylesspalace.tusky.databinding.ItemFollowRequestBinding
import com.keylesspalace.tusky.databinding.ItemReportNotificationBinding
import com.keylesspalace.tusky.databinding.ItemStatusBinding
import com.keylesspalace.tusky.databinding.ItemStatusNotificationBinding
import com.keylesspalace.tusky.databinding.ItemStatusPlaceholderBinding
import com.keylesspalace.tusky.databinding.ItemUnknownNotificationBinding
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.AbsoluteTimeFormatter
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.viewdata.NotificationViewData

interface NotificationsViewHolder {
    fun bind(
        viewData: NotificationViewData.Concrete,
        payloads: List<*>?,
        statusDisplayOptions: StatusDisplayOptions
    )
}

class NotificationsPagingAdapter(
    private val accountId: String,
    private val statusDisplayOptions: StatusDisplayOptions,
    private val statusListener: StatusActionListener,
    private val notificationActionListener: NotificationsAdapter.NotificationActionListener,
    private val accountActionListener: AccountActionListener
) : PagingDataAdapter<NotificationViewData, RecyclerView.ViewHolder>(NotificationsDifferCallback) {

    private val absoluteTimeFormatter = AbsoluteTimeFormatter()

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun getItemViewType(position: Int): Int {
        return when (val notification = getItem(position)) {
            is NotificationViewData.Concrete -> {
                when (notification.type) {
                    Notification.Type.MENTION,
                    Notification.Type.POLL -> VIEW_TYPE_STATUS
                    Notification.Type.STATUS,
                    Notification.Type.FAVOURITE,
                    Notification.Type.REBLOG,
                    Notification.Type.UPDATE -> VIEW_TYPE_STATUS_NOTIFICATION
                    Notification.Type.FOLLOW,
                    Notification.Type.SIGN_UP -> VIEW_TYPE_FOLLOW
                    Notification.Type.FOLLOW_REQUEST -> VIEW_TYPE_FOLLOW_REQUEST
                    Notification.Type.REPORT -> VIEW_TYPE_REPORT
                    else -> VIEW_TYPE_UNKNOWN
                }
            }
            is NotificationViewData.Placeholder -> VIEW_TYPE_PLACEHOLDER
            null -> throw IllegalStateException("")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_STATUS -> StatusViewHolder(
                ItemStatusBinding.inflate(inflater, parent, false),
                statusListener,
                accountId
            )
            VIEW_TYPE_STATUS_NOTIFICATION -> StatusNotificationViewHolder(
                ItemStatusNotificationBinding.inflate(inflater, parent, false),
                statusListener,
                notificationActionListener,
                absoluteTimeFormatter
            )
            VIEW_TYPE_FOLLOW -> FollowViewHolder(
                ItemFollowBinding.inflate(inflater, parent, false),
                notificationActionListener
            )
            VIEW_TYPE_FOLLOW_REQUEST -> FollowRequestViewHolder(
                ItemFollowRequestBinding.inflate(inflater, parent, false),
                statusListener,
                true
            )
            VIEW_TYPE_PLACEHOLDER -> PlaceholderViewHolder(
                ItemStatusPlaceholderBinding.inflate(inflater, parent, false),
                statusListener
            )
            VIEW_TYPE_REPORT -> ReportNotificationViewHolder(
                ItemReportNotificationBinding.inflate(inflater, parent, false),
                notificationActionListener
            )
            else -> UnknownNotificationViewHolder(
                ItemUnknownNotificationBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        bindViewHolder(viewHolder, position, null)
    }

    override fun onBindViewHolder(
        viewHolder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        bindViewHolder(viewHolder, position, payloads)
    }

    private fun bindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>?) {
        getItem(position)?.let { notification ->
            when (notification) {
                is NotificationViewData.Concrete ->
                    (viewHolder as NotificationsViewHolder).bind(notification, payloads, statusDisplayOptions)
                is NotificationViewData.Placeholder -> {
                    (viewHolder as PlaceholderViewHolder).setup(notification.isLoading)
                }
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_STATUS = 0
        private const val VIEW_TYPE_STATUS_NOTIFICATION = 1
        private const val VIEW_TYPE_FOLLOW = 2
        private const val VIEW_TYPE_FOLLOW_REQUEST = 3
        private const val VIEW_TYPE_PLACEHOLDER = 4
        private const val VIEW_TYPE_REPORT = 5
        private const val VIEW_TYPE_UNKNOWN = 6

        val NotificationsDifferCallback = object : DiffUtil.ItemCallback<NotificationViewData>() {
            override fun areItemsTheSame(
                oldItem: NotificationViewData,
                newItem: NotificationViewData
            ): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: NotificationViewData,
                newItem: NotificationViewData
            ): Boolean {
                return false // Items are different always. It allows to refresh timestamp on every view holder update
            }

            override fun getChangePayload(
                oldItem: NotificationViewData,
                newItem: NotificationViewData
            ): Any? {
                return if (oldItem == newItem) {
                    // If items are equal - update timestamp only
                    listOf(StatusBaseViewHolder.Key.KEY_CREATED)
                } else {
                    // If items are different - update the whole view holder
                    null
                }
            }
        }
    }
}
