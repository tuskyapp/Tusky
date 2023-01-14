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

package com.keylesspalace.tusky.adapter

import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemTrendingCellBinding
import com.keylesspalace.tusky.entity.TrendingTagHistory
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.viewdata.TrendingViewData

class TrendingTagViewHolder(
    private val binding: ItemTrendingCellBinding,
) : RecyclerView.ViewHolder(binding.root) {

    fun setup(
        tagViewData: TrendingViewData.Tag,
        maxTrendingValue: Long,
        trendingListener: LinkListener,
    ) {
        val reversedHistory = tagViewData.tag.history.reversed()
        setGraph(reversedHistory, maxTrendingValue)
        setTag(tagViewData.tag.name)

        val totalUsage = tagViewData.tag.history.sumOf { it.uses.toLongOrNull() ?: 0 }
        setUsageText(totalUsage)

        val totalAccounts = tagViewData.tag.history.sumOf { it.accounts.toLongOrNull() ?: 0 }
        setAccountsText(totalAccounts)

        itemView.setOnClickListener {
            trendingListener.onViewTag(tagViewData.tag.name)
        }

        setAccessibility(totalAccounts, tagViewData.tag.name)
    }

    private fun setGraph(history: List<TrendingTagHistory>, maxTrendingValue: Long) {
        binding.graph.maxTrendingValue = maxTrendingValue
        binding.graph.primaryLineData = history
            .mapNotNull { it.uses.toLongOrNull() }
        binding.graph.secondaryLineData = history
            .mapNotNull { it.accounts.toLongOrNull() }
    }

    private fun setTag(tag: String) {
        binding.tag.text = tag
    }

    private fun setUsageText(usage: Long) {
        val safeUsage = when {
            usage >= 100_000_000L -> "${usage / 1000}m"
            usage >= 100_000L -> "${usage / 1000}k"
            else -> "$usage"
        }
        binding.usage.text = safeUsage
    }

    private fun setAccountsText(accounts: Long) {
        val safeAccounts = when {
            accounts >= 100_000_000L -> "${accounts / 1000}m"
            accounts >= 100_000L -> "${accounts / 1000}k"
            else -> "$accounts"
        }
        binding.accounts.text = safeAccounts
    }

    private fun setAccessibility(totalAccounts: Long, tag: String) {
        itemView.contentDescription =
            itemView.context.getString(R.string.accessibility_talking_about_tag, totalAccounts, tag)
    }
}
