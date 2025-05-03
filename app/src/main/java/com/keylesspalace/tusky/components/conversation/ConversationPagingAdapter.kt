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

package com.keylesspalace.tusky.components.conversation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.PlaceholderViewHolder
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder
import com.keylesspalace.tusky.databinding.ItemPlaceholderBinding
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.StatusDisplayOptions

class ConversationPagingAdapter(
    private var statusDisplayOptions: StatusDisplayOptions,
    private val listener: StatusActionListener
) : PagingDataAdapter<ConversationViewData, RecyclerView.ViewHolder>(CONVERSATION_COMPARATOR) {

    var mediaPreviewEnabled: Boolean
        get() = statusDisplayOptions.mediaPreviewEnabled
        set(mediaPreviewEnabled) {
            statusDisplayOptions = statusDisplayOptions.copy(
                mediaPreviewEnabled = mediaPreviewEnabled
            )
        }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position) == null) {
            VIEW_TYPE_PLACEHOLDER
        } else {
            VIEW_TYPE_CONVERSATION
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_CONVERSATION) {
            ConversationViewHolder(layoutInflater.inflate(R.layout.item_conversation, parent, false), statusDisplayOptions, listener)
        } else {
            PlaceholderViewHolder(
                ItemPlaceholderBinding.inflate(layoutInflater, parent, false),
                mode = PlaceholderViewHolder.Mode.CONVERSATION
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        onBindViewHolder(holder, position, emptyList())
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {
        val conversationViewData = getItem(position)
        if (holder is ConversationViewHolder && conversationViewData != null) {
            holder.setupWithConversation(conversationViewData, payloads)
        }
    }

    companion object {
        private const val VIEW_TYPE_PLACEHOLDER = 0
        private const val VIEW_TYPE_CONVERSATION = 1

        private val CONVERSATION_COMPARATOR = object : DiffUtil.ItemCallback<ConversationViewData>() {
            override fun areItemsTheSame(
                oldItem: ConversationViewData,
                newItem: ConversationViewData
            ): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: ConversationViewData,
                newItem: ConversationViewData
            ): Boolean {
                return false // Items are different always. It allows to refresh timestamp on every view holder update
            }

            override fun getChangePayload(
                oldItem: ConversationViewData,
                newItem: ConversationViewData
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
