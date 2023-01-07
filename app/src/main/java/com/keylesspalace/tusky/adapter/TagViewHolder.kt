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

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.TrendingTagHistory
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.view.GraphView
import com.keylesspalace.tusky.viewdata.TrendingViewData

class TagViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val graphView: GraphView
    private val tagView: TextView
    private val textView: TextView

    init {
        graphView = itemView.findViewById(R.id.graph)
        tagView = itemView.findViewById(R.id.tag)
        textView = itemView.findViewById(R.id.text)
    }

    fun setup(
        tagViewData: TrendingViewData.Tag,
        maxTrendingValue: Int,
        trendingListener: LinkListener,
    ) {
        setGraph(tagViewData.tag.history, maxTrendingValue)
        setTag(tagViewData.tag.name)

        val totalAccounts = tagViewData.tag.history.sumOf { it.accounts.toIntOrNull() ?: 0 }
        setTextWithAccounts(totalAccounts)

        itemView.setOnClickListener {
            trendingListener.onViewTag(tagViewData.tag.name)
        }

        setAccessibility(totalAccounts, tagViewData.tag.name)
    }

    private fun setGraph(history: List<TrendingTagHistory>, maxTrendingValue: Int) {
        graphView.maxTrendingValue = maxTrendingValue
        graphView.data = history
            .reversed()
            .mapNotNull { it.accounts.toIntOrNull() }
    }

    private fun setTag(tag: String) {
        tagView.text = itemView.context.getString(R.string.title_tag, tag)
    }

    private fun setTextWithAccounts(totalAccounts: Int) {
        textView.text = itemView.context.getString(R.string.talking_about_tag, totalAccounts)
    }

    private fun setAccessibility(totalAccounts: Int, tag: String) {
        itemView.contentDescription =
            itemView.context.getString(R.string.accessibility_talking_about_tag, totalAccounts, tag)
    }
}
