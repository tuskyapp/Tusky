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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.FilteredStatusViewHolder
import com.keylesspalace.tusky.adapter.LoadMoreViewHolder
import com.keylesspalace.tusky.adapter.PlaceholderViewHolder
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder
import com.keylesspalace.tusky.adapter.StatusViewHolder
import com.keylesspalace.tusky.databinding.ItemLoadMoreBinding
import com.keylesspalace.tusky.databinding.ItemPlaceholderBinding
import com.keylesspalace.tusky.databinding.ItemStatusFilteredBinding
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.viewdata.StatusViewData

class TimelinePagingAdapter(
    private var statusDisplayOptions: StatusDisplayOptions,
    private val statusListener: StatusActionListener
) : PagingDataAdapter<StatusViewData, RecyclerView.ViewHolder>(TimelineDifferCallback) {

    var mediaPreviewEnabled: Boolean
        get() = statusDisplayOptions.mediaPreviewEnabled
        set(mediaPreviewEnabled) {
            statusDisplayOptions = statusDisplayOptions.copy(
                mediaPreviewEnabled = mediaPreviewEnabled
            )
        }

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_PLACEHOLDER -> {
                PlaceholderViewHolder(
                    ItemPlaceholderBinding.inflate(inflater, parent, false),
                    mode = PlaceholderViewHolder.Mode.STATUS
                )
            }
            VIEW_TYPE_STATUS_FILTERED -> {
                FilteredStatusViewHolder(
                    ItemStatusFilteredBinding.inflate(inflater, parent, false),
                    statusListener
                )
            }
            VIEW_TYPE_LOAD_MORE -> {
                LoadMoreViewHolder(
                    ItemLoadMoreBinding.inflate(inflater, parent, false),
                    statusListener
                )
            }
            else -> {
                StatusViewHolder(inflater.inflate(R.layout.item_status, parent, false))
            }
        }
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        onBindViewHolder(viewHolder, position, emptyList())
    }

    override fun onBindViewHolder(
        viewHolder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        val viewData = getItem(position)
        if (viewData is StatusViewData.LoadMore) {
            val holder = viewHolder as LoadMoreViewHolder
            holder.setup(viewData.isLoading)
        } else if (viewData is StatusViewData.Concrete) {
            if (viewData.filter?.action == Filter.Action.WARN) {
                val holder = viewHolder as FilteredStatusViewHolder
                holder.bind(viewData)
            } else {
                val holder = viewHolder as StatusViewHolder
                holder.setupWithStatus(
                    viewData,
                    statusListener,
                    statusDisplayOptions,
                    payloads,
                    true
                )
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val viewData = getItem(position)
        return when {
            viewData == null -> VIEW_TYPE_PLACEHOLDER
            viewData is StatusViewData.LoadMore -> VIEW_TYPE_LOAD_MORE
            viewData.filter?.action == Filter.Action.WARN -> VIEW_TYPE_STATUS_FILTERED
            else -> VIEW_TYPE_STATUS
        }
    }

    companion object {
        private const val VIEW_TYPE_PLACEHOLDER = 0
        private const val VIEW_TYPE_STATUS = 1
        private const val VIEW_TYPE_STATUS_FILTERED = 2
        private const val VIEW_TYPE_LOAD_MORE = 3

        private val TimelineDifferCallback = object : DiffUtil.ItemCallback<StatusViewData>() {
            override fun areItemsTheSame(
                oldItem: StatusViewData,
                newItem: StatusViewData
            ): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: StatusViewData,
                newItem: StatusViewData
            ): Boolean {
                return false // Items are different always. It allows to refresh timestamp on every view holder update
            }

            override fun getChangePayload(oldItem: StatusViewData, newItem: StatusViewData): Any? {
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
