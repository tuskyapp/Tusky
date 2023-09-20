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

import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.databinding.ItemStatusPlaceholderBinding
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.visible

/**
 * Placeholder for missing parts in timelines.
 *
 * Displays a "Load more" button to load the gap, or a
 * circular progress bar if the missing page is being loaded.
 */
class PlaceholderViewHolder(
    private val binding: ItemStatusPlaceholderBinding,
    private val listener: StatusActionListener
) : RecyclerView.ViewHolder(binding.root) {

    fun setup(loading: Boolean) {
        binding.loadMoreButton.visible(!loading)
        binding.loadMoreProgressBar.visible(loading)

        if (!loading) {
            binding.loadMoreButton.setOnClickListener {
                binding.loadMoreButton.hide()
                binding.loadMoreProgressBar.show()
                listener.onLoadMore(bindingAdapterPosition)
            }
        }
    }
}
