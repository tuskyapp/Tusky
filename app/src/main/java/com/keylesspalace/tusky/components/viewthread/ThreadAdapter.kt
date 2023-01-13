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

package com.keylesspalace.tusky.components.viewthread

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder
import com.keylesspalace.tusky.adapter.StatusDetailedViewHolder
import com.keylesspalace.tusky.adapter.StatusViewHolder
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.viewdata.StatusViewData

class ThreadAdapter(
    private val statusDisplayOptions: StatusDisplayOptions,
    private val statusActionListener: StatusActionListener
) : ListAdapter<StatusViewData.Concrete, StatusBaseViewHolder>(ThreadDifferCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusBaseViewHolder {
        return when (viewType) {
            VIEW_TYPE_STATUS -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_status, parent, false)
                StatusViewHolder(view)
            }
            VIEW_TYPE_STATUS_DETAILED -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_status_detailed, parent, false)
                StatusDetailedViewHolder(view)
            }
            else -> error("Unknown item type: $viewType")
        }
    }

    override fun onBindViewHolder(viewHolder: StatusBaseViewHolder, position: Int) {
        val status = getItem(position)
        viewHolder.setupWithStatus(status, statusActionListener, statusDisplayOptions)
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isDetailed) {
            VIEW_TYPE_STATUS_DETAILED
        } else {
            VIEW_TYPE_STATUS
        }
    }

    companion object {
        private const val TAG = "ThreadAdapter"
        private const val VIEW_TYPE_STATUS = 0
        private const val VIEW_TYPE_STATUS_DETAILED = 1

        val ThreadDifferCallback = object : DiffUtil.ItemCallback<StatusViewData.Concrete>() {
            override fun areItemsTheSame(
                oldItem: StatusViewData.Concrete,
                newItem: StatusViewData.Concrete
            ): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: StatusViewData.Concrete,
                newItem: StatusViewData.Concrete
            ): Boolean {
                return false // Items are different always. It allows to refresh timestamp on every view holder update
            }

            override fun getChangePayload(
                oldItem: StatusViewData.Concrete,
                newItem: StatusViewData.Concrete
            ): Any? {
                return if (oldItem == newItem) {
                    // If items are equal - update timestamp only
                    listOf(StatusBaseViewHolder.Key.KEY_CREATED)
                } else // If items are different - update the whole view holder
                    null
            }
        }
    }
}
