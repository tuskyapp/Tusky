/* Copyright 2021 Tusky Contributors
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
import android.widget.Button
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.interfaces.StatusActionListener

/**
 * Placeholder for different timelines.
 * Either displays "load more" button or a progress indicator.
 **/
class PlaceholderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val loadMoreButton: Button = itemView.findViewById(R.id.button_load_more)
    private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)

    fun setup(listener: StatusActionListener, progress: Boolean) {
        loadMoreButton.visibility = if (progress) View.GONE else View.VISIBLE
        progressBar.visibility = if (progress) View.VISIBLE else View.GONE
        loadMoreButton.isEnabled = true
        loadMoreButton.setOnClickListener { v: View? ->
            loadMoreButton.isEnabled = false
            listener.onLoadMore(bindingAdapterPosition)
        }
    }
}
