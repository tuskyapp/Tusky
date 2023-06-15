/* Copyright 2023 Tusky Contributors
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

package com.keylesspalace.tusky.components.trending

import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemTrendingCellBinding
import com.keylesspalace.tusky.util.formatNumber
import com.keylesspalace.tusky.viewdata.TrendingViewData

class TrendingTagViewHolder(
    private val binding: ItemTrendingCellBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun setup(
        tagViewData: TrendingViewData.Tag,
        onViewTag: (String) -> Unit
    ) {
        binding.tag.text = binding.root.context.getString(R.string.title_tag, tagViewData.name)

        binding.graph.maxTrendingValue = tagViewData.maxTrendingValue
        binding.graph.primaryLineData = tagViewData.usage
        binding.graph.secondaryLineData = tagViewData.accounts

        binding.totalUsage.text = formatNumber(tagViewData.usage.sum(), 1000)

        val totalAccounts = tagViewData.accounts.sum()
        binding.totalAccounts.text = formatNumber(totalAccounts, 1000)

        binding.currentUsage.text = tagViewData.usage.last().toString()
        binding.currentAccounts.text = tagViewData.usage.last().toString()

        itemView.setOnClickListener {
            onViewTag(tagViewData.name)
        }

        itemView.contentDescription =
            itemView.context.getString(
                R.string.accessibility_talking_about_tag,
                totalAccounts,
                tagViewData.name
            )
    }
}
