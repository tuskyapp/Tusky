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
import com.keylesspalace.tusky.viewdata.TrendingViewData
import java.text.NumberFormat
import kotlin.math.ln
import kotlin.math.pow

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

        binding.totalUsage.text = formatNumber(tagViewData.usage.sum())

        val totalAccounts = tagViewData.accounts.sum()
        binding.totalAccounts.text = formatNumber(totalAccounts)

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
