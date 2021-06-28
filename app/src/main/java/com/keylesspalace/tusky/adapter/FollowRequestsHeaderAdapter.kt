/* Copyright 2020 Tusky Contributors
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

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R

class FollowRequestsHeaderAdapter(private val instanceName: String, private val accountLocked: Boolean) : RecyclerView.Adapter<HeaderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_follow_requests_header, parent, false) as TextView
        return HeaderViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: HeaderViewHolder, position: Int) {
        viewHolder.textView.text = viewHolder.textView.context.getString(R.string.follow_requests_info, instanceName)
    }

    override fun getItemCount() = if (accountLocked) 0 else 1
}

class HeaderViewHolder(var textView: TextView) : RecyclerView.ViewHolder(textView)
