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

package com.keylesspalace.tusky.components.search.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.keylesspalace.tusky.databinding.ItemHashtagBinding
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.util.BindingHolder

class SearchHashtagsAdapter(private val linkListener: LinkListener) :
    PagingDataAdapter<HashTag, BindingHolder<ItemHashtagBinding>>(HASHTAG_COMPARATOR) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemHashtagBinding> {
        val binding = ItemHashtagBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BindingHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemHashtagBinding>, position: Int) {
        getItem(position)?.let { (name) ->
            holder.binding.root.text = String.format("#%s", name)
            holder.binding.root.setOnClickListener { linkListener.onViewTag(name) }
        }
    }

    companion object {

        val HASHTAG_COMPARATOR = object : DiffUtil.ItemCallback<HashTag>() {
            override fun areContentsTheSame(oldItem: HashTag, newItem: HashTag): Boolean =
                oldItem.name == newItem.name

            override fun areItemsTheSame(oldItem: HashTag, newItem: HashTag): Boolean =
                oldItem.name == newItem.name
        }
    }
}
