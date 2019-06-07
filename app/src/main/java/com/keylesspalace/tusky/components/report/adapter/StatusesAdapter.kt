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
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.report.model.StatusViewState
import com.keylesspalace.tusky.entity.Status

class StatusesAdapter(private val useAbsoluteTime: Boolean,
                      private val mediaPreviewEnabled: Boolean,
                      private val statusViewState: StatusViewState,
                      private val adapterHandler: AdapterHandler)
    : PagedListAdapter<Status, RecyclerView.ViewHolder>(STATUS_COMPARATOR) {

    private val statusForPosition: (Int) -> Status? = { position: Int ->
        if (position != RecyclerView.NO_POSITION) getItem(position) else null
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return StatusViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_report_status, parent, false),
                useAbsoluteTime, mediaPreviewEnabled, statusViewState, adapterHandler, statusForPosition)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        getItem(position)?.let { status ->
            (holder as? StatusViewHolder)?.bind(status)
        }

    }

    companion object {

        val STATUS_COMPARATOR = object : DiffUtil.ItemCallback<Status>() {
            override fun areContentsTheSame(oldItem: Status, newItem: Status): Boolean =
                    oldItem == newItem

            override fun areItemsTheSame(oldItem: Status, newItem: Status): Boolean =
                    oldItem.id == newItem.id
        }

    }

}