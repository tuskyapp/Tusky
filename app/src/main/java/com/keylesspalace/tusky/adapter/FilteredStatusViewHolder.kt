/* Copyright 2024 Tusky Contributors
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
import com.keylesspalace.tusky.components.notifications.NotificationsViewHolder
import com.keylesspalace.tusky.databinding.ItemStatusFilteredBinding
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.FilterResult
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.viewdata.NotificationViewData
import com.keylesspalace.tusky.viewdata.StatusViewData

class FilteredStatusViewHolder(
    private val binding: ItemStatusFilteredBinding,
    listener: StatusActionListener
) : RecyclerView.ViewHolder(binding.root),
    NotificationsViewHolder {

    init {
        binding.statusFilterShowAnyway.setOnClickListener {
            listener.clearWarningAction(bindingAdapterPosition)
        }
    }

    override fun bind(
        viewData: NotificationViewData.Concrete,
        payloads: List<*>,
        statusDisplayOptions: StatusDisplayOptions
    ) {
        if (payloads.isEmpty()) {
            bind(viewData.statusViewData!!)
        }
    }

    fun bind(viewData: StatusViewData.Concrete) {
        val matchedFilterResult: FilterResult? = viewData.actionable.filtered.orEmpty().find { filterResult ->
            filterResult.filter.action == Filter.Action.WARN
        }

        val matchedFilterTitle = matchedFilterResult?.filter?.title.orEmpty()

        binding.statusFilterLabel.text = itemView.context.getString(
            R.string.status_filter_placeholder_label_format,
            matchedFilterTitle
        )
    }
}
