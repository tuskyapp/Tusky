/* Copyright 2019 Tusky Contributors
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
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.util.ThemeUtils

class PreviewPollOptionsAdapter: RecyclerView.Adapter<PreviewViewHolder>() {

    private var options: List<String> = emptyList()
    private var multiple: Boolean = false
    private var clickListener: View.OnClickListener? = null

    fun update(newOptions: List<String>, multiple: Boolean) {
        this.options = newOptions
        this.multiple = multiple
        notifyDataSetChanged()
    }

    fun setOnClickListener(l: View.OnClickListener?) {
        clickListener = l
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewViewHolder {
        return PreviewViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_poll_preview_option, parent, false))
    }

    override fun getItemCount() = options.size

    override fun onBindViewHolder(holder: PreviewViewHolder, position: Int) {
        val textView = holder.itemView as TextView

        val iconId = if (multiple) {
            R.drawable.ic_check_box_outline_blank_18dp
        } else {
            R.drawable.ic_radio_button_unchecked_18dp
        }

        val iconDrawable = ThemeUtils.getTintedDrawable(textView.context, iconId, android.R.attr.textColorTertiary)

        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(textView, iconDrawable, null, null, null)

        textView.text = options[position]

        textView.setOnClickListener(clickListener)
    }

}


class PreviewViewHolder(itemView: View): RecyclerView.ViewHolder(itemView)