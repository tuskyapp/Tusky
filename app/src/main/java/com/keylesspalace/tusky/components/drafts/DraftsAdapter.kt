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

package com.keylesspalace.tusky.components.drafts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.databinding.ItemDraftBinding
import com.keylesspalace.tusky.db.DraftEntity
import com.keylesspalace.tusky.util.BindingHolder
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.visible

interface DraftActionListener {
    fun onOpenDraft(draft: DraftEntity)
    fun onDeleteDraft(draft: DraftEntity)
}

class DraftsAdapter(
    private val listener: DraftActionListener
) : PagingDataAdapter<DraftEntity, BindingHolder<ItemDraftBinding>>(
    object : DiffUtil.ItemCallback<DraftEntity>() {
        override fun areItemsTheSame(oldItem: DraftEntity, newItem: DraftEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DraftEntity, newItem: DraftEntity): Boolean {
            return oldItem == newItem
        }
    }
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemDraftBinding> {

        val binding = ItemDraftBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        val viewHolder = BindingHolder(binding)

        binding.draftMediaPreview.layoutManager = LinearLayoutManager(binding.root.context, RecyclerView.HORIZONTAL, false)
        binding.draftMediaPreview.adapter = DraftMediaAdapter {
            getItem(viewHolder.bindingAdapterPosition)?.let { draft ->
                listener.onOpenDraft(draft)
            }
        }

        return viewHolder
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemDraftBinding>, position: Int) {
        getItem(position)?.let { draft ->
            holder.binding.root.setOnClickListener {
                listener.onOpenDraft(draft)
            }
            holder.binding.deleteButton.setOnClickListener {
                listener.onDeleteDraft(draft)
            }
            holder.binding.draftSendingInfo.visible(draft.failedToSend)

            holder.binding.contentWarning.visible(!draft.contentWarning.isNullOrEmpty())
            holder.binding.contentWarning.text = draft.contentWarning
            holder.binding.content.text = draft.content

            holder.binding.draftMediaPreview.visible(draft.attachments.isNotEmpty())
            (holder.binding.draftMediaPreview.adapter as DraftMediaAdapter).submitList(draft.attachments)

            if (draft.poll != null) {
                holder.binding.draftPoll.show()
                holder.binding.draftPoll.setPoll(draft.poll)
            } else {
                holder.binding.draftPoll.hide()
            }
        }
    }
}
