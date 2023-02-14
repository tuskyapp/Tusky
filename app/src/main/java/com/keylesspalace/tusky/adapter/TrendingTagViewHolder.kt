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
import java.text.NumberFormat
import kotlin.math.ln
import kotlin.math.pow

class TrendingTagViewHolder(
    private val binding: ItemTrendingCellBinding
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
        binding.totalUsage.text = formatNumber(totalUsage)

        val totalAccounts = tagViewData.tag.history.sumOf { it.accounts.toLongOrNull() ?: 0 }
        binding.totalAccounts.text = formatNumber(totalAccounts)

        binding.currentUsage.text = reversedHistory.last().uses
        binding.currentAccounts.text = reversedHistory.last().accounts

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
        binding.tag.text = binding.root.context.getString(R.string.title_tag, tag)
    }

    private fun setAccessibility(totalAccounts: Long, tag: String) {
        itemView.contentDescription =
            itemView.context.getString(R.string.accessibility_talking_about_tag, totalAccounts, tag)
    }

    companion object {
        private val numberFormatter: NumberFormat = NumberFormat.getInstance()
        private val ln_1k = ln(1000.0)

        /**
         * Format numbers according to the current locale. Numbers < min have
         * separators (',', '.', etc) inserted according to the locale.
         *
         * Numbers > min are scaled down to that by multiples of 1,000, and
         * a suffix appropriate to the scaling is appended.
         */
        private fun formatNumber(num: Long, min: Int = 100000): String {
            if (num < min) return numberFormatter.format(num)

            val exp = (ln(num.toDouble()) / ln_1k).toInt()

            // TODO: is the choice of suffixes here locale-agnostic?
            return String.format("%.1f %c", num / 1000.0.pow(exp.toDouble()), "KMGTPE"[exp - 1])
        }
    }
}
