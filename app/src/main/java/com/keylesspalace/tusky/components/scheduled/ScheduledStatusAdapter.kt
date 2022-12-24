/* Copyright 2019 Tusky Contributors
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

package com.keylesspalace.tusky.components.scheduled

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.keylesspalace.tusky.databinding.ItemScheduledStatusBinding
import com.keylesspalace.tusky.entity.ScheduledStatus
import com.keylesspalace.tusky.util.BindingHolder

interface ScheduledStatusActionListener {
    fun edit(item: ScheduledStatus)
    fun delete(item: ScheduledStatus)
}

class ScheduledStatusAdapter(
    val listener: ScheduledStatusActionListener
) : PagingDataAdapter<ScheduledStatus, BindingHolder<ItemScheduledStatusBinding>>(
    object : DiffUtil.ItemCallback<ScheduledStatus>() {
        override fun areItemsTheSame(oldItem: ScheduledStatus, newItem: ScheduledStatus): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ScheduledStatus, newItem: ScheduledStatus): Boolean {
            return oldItem == newItem
        }
    }
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemScheduledStatusBinding> {
        val binding = ItemScheduledStatusBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BindingHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemScheduledStatusBinding>, position: Int) {
        getItem(position)?.let { item ->
            holder.binding.edit.isEnabled = true
            holder.binding.delete.isEnabled = true
            holder.binding.text.text = item.params.text
            holder.binding.edit.setOnClickListener {
                listener.edit(item)
            }
            holder.binding.delete.setOnClickListener {
                listener.delete(item)
            }
        }
    }
}
