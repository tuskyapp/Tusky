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
import com.keylesspalace.tusky.databinding.ItemTrendingDateBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TrendingDateViewHolder(
    private val binding: ItemTrendingDateBinding,
) : RecyclerView.ViewHolder(binding.root) {

    private val dateFormat = SimpleDateFormat("EEE dd MMM yyyy", Locale.getDefault()).apply {
        this.timeZone = TimeZone.getDefault()
    }

    fun setup(start: Date, end: Date) {
        binding.dates.text = itemView.context.getString(
            R.string.date_range,
            dateFormat.format(start),
            dateFormat.format(end)
        )
    }
}
