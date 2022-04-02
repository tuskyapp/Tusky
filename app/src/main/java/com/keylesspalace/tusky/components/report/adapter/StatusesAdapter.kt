/* Copyright 2019 Joel Pyska
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

package com.keylesspalace.tusky.components.report.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.components.report.model.StatusViewState
import com.keylesspalace.tusky.databinding.ItemReportStatusBinding
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.viewdata.StatusViewData

class StatusesAdapter(
    private val statusDisplayOptions: StatusDisplayOptions,
    private val statusViewState: StatusViewState,
    private val adapterHandler: AdapterHandler
) : PagingDataAdapter<StatusViewData.Concrete, StatusViewHolder>(STATUS_COMPARATOR) {

    private val statusForPosition: (Int) -> StatusViewData.Concrete? = { position: Int ->
        if (position != RecyclerView.NO_POSITION) getItem(position) else null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusViewHolder {
        val binding = ItemReportStatusBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StatusViewHolder(
            binding, statusDisplayOptions, statusViewState, adapterHandler,
            statusForPosition
        )
    }

    override fun onBindViewHolder(holder: StatusViewHolder, position: Int) {
        getItem(position)?.let { status ->
            holder.bind(status)
        }
    }

    companion object {
        val STATUS_COMPARATOR = object : DiffUtil.ItemCallback<StatusViewData.Concrete>() {
            override fun areContentsTheSame(oldItem: StatusViewData.Concrete, newItem: StatusViewData.Concrete): Boolean =
                oldItem == newItem

            override fun areItemsTheSame(oldItem: StatusViewData.Concrete, newItem: StatusViewData.Concrete): Boolean =
                oldItem.id == newItem.id
        }
    }
}
