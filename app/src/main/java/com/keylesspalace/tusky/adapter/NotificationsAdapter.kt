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
package com.keylesspalace.tusky.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import at.connyduck.sparkbutton.helpers.Utils
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.AbsoluteTimeFormatter
import com.keylesspalace.tusky.util.CardViewMode
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.viewdata.NotificationViewData

class NotificationsAdapter(
    private val accountId: String,
    private val dataSource: AdapterDataSource<NotificationViewData>,
    private var statusDisplayOptions: StatusDisplayOptions,
    private val statusListener: StatusActionListener,
    private val notificationActionListener: NotificationActionListener,
    private val accountActionListener: AccountActionListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    interface AdapterDataSource<T> {
        val itemCount: Int
        fun getItemAt(pos: Int): T
    }

    private val absoluteTimeFormatter = AbsoluteTimeFormatter()
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_PLACEHOLDER -> {
                val view = inflater
                    .inflate(R.layout.item_status_placeholder, parent, false)
                PlaceholderViewHolder(view)
            }
            VIEW_TYPE_UNKNOWN -> {
                val view = View(parent.context)
                view.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Utils.dpToPx(parent.context, 24)
                )
                object : RecyclerView.ViewHolder(view) {}
            }
            else -> {
                val view = View(parent.context)
                view.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Utils.dpToPx(parent.context, 24)
                )
                object : RecyclerView.ViewHolder(view) {}
            }
        }
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        bindViewHolder(viewHolder, position, null)
    }

    override fun onBindViewHolder(
        viewHolder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<*>
    ) {
        bindViewHolder(viewHolder, position, payloads)
    }

    private fun bindViewHolder(
        viewHolder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<*>?
    ) {
    }

    override fun getItemCount(): Int {
        return dataSource.itemCount
    }

    var isMediaPreviewEnabled: Boolean
        get() = statusDisplayOptions.mediaPreviewEnabled
        set(mediaPreviewEnabled) {
            statusDisplayOptions = statusDisplayOptions.copy(
                animateAvatars = statusDisplayOptions.animateAvatars,
                mediaPreviewEnabled = mediaPreviewEnabled,
                useAbsoluteTime = statusDisplayOptions.useAbsoluteTime,
                showBotOverlay = statusDisplayOptions.showBotOverlay,
                useBlurhash = statusDisplayOptions.useBlurhash,
                cardViewMode = CardViewMode.NONE,
                confirmReblogs = statusDisplayOptions.confirmReblogs,
                confirmFavourites = statusDisplayOptions.confirmFavourites,
                hideStats = statusDisplayOptions.hideStats,
                animateEmojis = statusDisplayOptions.animateEmojis
            )
        }

    override fun getItemViewType(position: Int): Int {
        return when (val notification = dataSource.getItemAt(position)) {
            is NotificationViewData.Concrete -> {
                when (notification.type) {
                    Notification.Type.MENTION, Notification.Type.POLL -> VIEW_TYPE_STATUS
                    Notification.Type.STATUS, Notification.Type.FAVOURITE, Notification.Type.REBLOG,
                    Notification.Type.UPDATE -> {
                        VIEW_TYPE_STATUS_NOTIFICATION
                    }
                    Notification.Type.FOLLOW, Notification.Type.SIGN_UP -> VIEW_TYPE_FOLLOW
                    Notification.Type.FOLLOW_REQUEST -> VIEW_TYPE_FOLLOW_REQUEST
                    Notification.Type.REPORT -> VIEW_TYPE_REPORT
                    else -> VIEW_TYPE_UNKNOWN
                }
            }
            is NotificationViewData.Placeholder -> VIEW_TYPE_PLACEHOLDER
            else -> throw AssertionError("Unknown notification type")
        }
    }

    interface NotificationActionListener {
        fun onViewAccount(id: String)
        fun onViewStatusForNotificationId(notificationId: String)
        fun onViewReport(reportId: String)
        fun onExpandedChange(expanded: Boolean, position: Int)

        /**
         * Called when the status [android.widget.ToggleButton] responsible for collapsing long
         * status content is interacted with.
         *
         * @param isCollapsed Whether the status content is shown in a collapsed state or fully.
         * @param position    The position of the status in the list.
         */
        fun onNotificationContentCollapsedChange(isCollapsed: Boolean, position: Int)
    }

    companion object {
        private const val VIEW_TYPE_STATUS = 0
        private const val VIEW_TYPE_STATUS_NOTIFICATION = 1
        private const val VIEW_TYPE_FOLLOW = 2
        private const val VIEW_TYPE_FOLLOW_REQUEST = 3
        private const val VIEW_TYPE_PLACEHOLDER = 4
        private const val VIEW_TYPE_REPORT = 5
        private const val VIEW_TYPE_UNKNOWN = 6
    }
}
