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

package com.keylesspalace.tusky.components.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.FilteredStatusViewHolder
import com.keylesspalace.tusky.adapter.FollowRequestViewHolder
import com.keylesspalace.tusky.adapter.PlaceholderViewHolder
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder
import com.keylesspalace.tusky.databinding.ItemFollowBinding
import com.keylesspalace.tusky.databinding.ItemFollowRequestBinding
import com.keylesspalace.tusky.databinding.ItemModerationWarningNotificationBinding
import com.keylesspalace.tusky.databinding.ItemReportNotificationBinding
import com.keylesspalace.tusky.databinding.ItemSeveredRelationshipNotificationBinding
import com.keylesspalace.tusky.databinding.ItemStatusFilteredBinding
import com.keylesspalace.tusky.databinding.ItemStatusNotificationBinding
import com.keylesspalace.tusky.databinding.ItemStatusPlaceholderBinding
import com.keylesspalace.tusky.databinding.ItemUnknownNotificationBinding
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.AbsoluteTimeFormatter
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.viewdata.NotificationViewData

interface NotificationActionListener {
    fun onViewReport(reportId: String)
}

interface NotificationsViewHolder {
    fun bind(
        viewData: NotificationViewData.Concrete,
        payloads: List<*>,
        statusDisplayOptions: StatusDisplayOptions
    )
}

class NotificationsPagingAdapter(
    private val accountId: String,
    private var statusDisplayOptions: StatusDisplayOptions,
    private val statusListener: StatusActionListener,
    private val notificationActionListener: NotificationActionListener,
    private val accountActionListener: AccountActionListener,
    private val instanceName: String
) : PagingDataAdapter<NotificationViewData, RecyclerView.ViewHolder>(NotificationsDifferCallback) {

    var mediaPreviewEnabled: Boolean
        get() = statusDisplayOptions.mediaPreviewEnabled
        set(mediaPreviewEnabled) {
            statusDisplayOptions = statusDisplayOptions.copy(
                mediaPreviewEnabled = mediaPreviewEnabled
            )
            notifyItemRangeChanged(0, itemCount)
        }

    private val absoluteTimeFormatter = AbsoluteTimeFormatter()

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun getItemViewType(position: Int): Int {
        return when (val notification = getItem(position)) {
            is NotificationViewData.Concrete -> {
                when (notification.type) {
                    Notification.Type.MENTION,
                    Notification.Type.POLL -> if (notification.statusViewData?.filterAction == Filter.Action.WARN) {
                        VIEW_TYPE_STATUS_FILTERED
                    } else {
                        VIEW_TYPE_STATUS
                    }
                    Notification.Type.STATUS,
                    Notification.Type.UPDATE -> if (notification.statusViewData?.filterAction == Filter.Action.WARN) {
                        VIEW_TYPE_STATUS_FILTERED
                    } else {
                        VIEW_TYPE_STATUS_NOTIFICATION
                    }
                    Notification.Type.FAVOURITE,
                    Notification.Type.REBLOG -> VIEW_TYPE_STATUS_NOTIFICATION
                    Notification.Type.FOLLOW,
                    Notification.Type.SIGN_UP -> VIEW_TYPE_FOLLOW
                    Notification.Type.FOLLOW_REQUEST -> VIEW_TYPE_FOLLOW_REQUEST
                    Notification.Type.REPORT -> VIEW_TYPE_REPORT
                    Notification.Type.SEVERED_RELATIONSHIP -> VIEW_TYPE_SEVERED_RELATIONSHIP
                    Notification.Type.MODERATION_WARNING -> VIEW_TYPE_MODERATION_WARNING
                    else -> VIEW_TYPE_UNKNOWN
                }
            }
            else -> VIEW_TYPE_PLACEHOLDER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_STATUS -> StatusViewHolder(
                inflater.inflate(R.layout.item_status, parent, false),
                statusListener,
                accountId
            )
            VIEW_TYPE_STATUS_FILTERED -> FilteredStatusViewHolder(
                ItemStatusFilteredBinding.inflate(inflater, parent, false),
                statusListener
            )
            VIEW_TYPE_STATUS_NOTIFICATION -> StatusNotificationViewHolder(
                ItemStatusNotificationBinding.inflate(inflater, parent, false),
                statusListener,
                absoluteTimeFormatter
            )
            VIEW_TYPE_FOLLOW -> FollowViewHolder(
                ItemFollowBinding.inflate(inflater, parent, false),
                accountActionListener,
                statusListener
            )
            VIEW_TYPE_FOLLOW_REQUEST -> FollowRequestViewHolder(
                ItemFollowRequestBinding.inflate(inflater, parent, false),
                accountActionListener,
                statusListener,
                true
            )
            VIEW_TYPE_PLACEHOLDER -> PlaceholderViewHolder(
                ItemStatusPlaceholderBinding.inflate(inflater, parent, false),
                statusListener
            )
            VIEW_TYPE_REPORT -> ReportNotificationViewHolder(
                ItemReportNotificationBinding.inflate(inflater, parent, false),
                notificationActionListener,
                accountActionListener
            )
            VIEW_TYPE_SEVERED_RELATIONSHIP -> SeveredRelationshipNotificationViewHolder(
                ItemSeveredRelationshipNotificationBinding.inflate(inflater, parent, false),
                instanceName
            )
            VIEW_TYPE_MODERATION_WARNING -> ModerationWarningViewHolder(
                ItemModerationWarningNotificationBinding.inflate(inflater, parent, false),
                instanceName
            )
            else -> UnknownNotificationViewHolder(
                ItemUnknownNotificationBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        onBindViewHolder(viewHolder, position, emptyList())
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {
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
        private const val VIEW_TYPE_STATUS_FILTERED = 1
        private const val VIEW_TYPE_STATUS_NOTIFICATION = 2
        private const val VIEW_TYPE_FOLLOW = 3
        private const val VIEW_TYPE_FOLLOW_REQUEST = 4
        private const val VIEW_TYPE_PLACEHOLDER = 5
        private const val VIEW_TYPE_REPORT = 6
        private const val VIEW_TYPE_SEVERED_RELATIONSHIP = 8
        private const val VIEW_TYPE_MODERATION_WARNING = 9
        private const val VIEW_TYPE_UNKNOWN = 10

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
                    StatusBaseViewHolder.Key.KEY_CREATED
                } else {
                    // If items are different - update the whole view holder
                    null
                }
            }
        }
    }
}
