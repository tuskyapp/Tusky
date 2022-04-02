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
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.StatusDisplayOptions

class ConversationAdapter(
    private val statusDisplayOptions: StatusDisplayOptions,
    private val listener: StatusActionListener
) : PagingDataAdapter<ConversationViewData, ConversationViewHolder>(CONVERSATION_COMPARATOR) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(view, statusDisplayOptions, listener)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.setupWithConversation(getItem(position))
    }

    companion object {
        val CONVERSATION_COMPARATOR = object : DiffUtil.ItemCallback<ConversationViewData>() {
            override fun areItemsTheSame(oldItem: ConversationViewData, newItem: ConversationViewData): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ConversationViewData, newItem: ConversationViewData): Boolean {
                return oldItem == newItem
            }
        }
    }
}
