/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.components.trending

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil.ItemCallback
import androidx.recyclerview.widget.ListAdapter
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemTrendingLinkBinding
import com.keylesspalace.tusky.entity.TrendsLink
import com.keylesspalace.tusky.util.StatusDisplayOptions

class TrendingLinksAdapter(
    statusDisplayOptions: StatusDisplayOptions,
    private val onViewLink: (String) -> Unit
) : ListAdapter<TrendsLink, TrendingLinkViewHolder>(diffCallback) {
    var statusDisplayOptions = statusDisplayOptions
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrendingLinkViewHolder {
        return TrendingLinkViewHolder(
            ItemTrendingLinkBinding.inflate(LayoutInflater.from(parent.context)),
            onViewLink
        )
    }

    override fun onBindViewHolder(holder: TrendingLinkViewHolder, position: Int) {
        holder.bind(getItem(position), statusDisplayOptions)
    }

    override fun getItemViewType(position: Int): Int {
        return R.layout.item_trending_link
    }

    companion object {
        val diffCallback = object : ItemCallback<TrendsLink>() {
            override fun areItemsTheSame(oldItem: TrendsLink, newItem: TrendsLink): Boolean {
                return oldItem.url == newItem.url
            }

            override fun areContentsTheSame(oldItem: TrendsLink, newItem: TrendsLink): Boolean {
                return oldItem == newItem
            }
        }
    }
}
