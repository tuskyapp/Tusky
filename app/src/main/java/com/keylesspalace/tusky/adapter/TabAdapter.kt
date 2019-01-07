/* Copyright 2019 Conny Duck
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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.TabData
import com.keylesspalace.tusky.util.ThemeUtils
import kotlinx.android.synthetic.main.item_tab_preference.view.*

interface ItemInteractionListener {
    fun onTabAdded(tab: TabData)
    fun onStartDelete(viewHolder: RecyclerView.ViewHolder)
    fun onStartDrag(viewHolder: RecyclerView.ViewHolder)
}

class TabAdapter(var data: List<TabData>,
                 val small: Boolean = false,
                 val listener: ItemInteractionListener? = null) : RecyclerView.Adapter<TabAdapter.ViewHolder>() {

    fun updateData(newData: List<TabData>) {
        this.data = newData
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = if(small) {
            R.layout.item_tab_preference_small
        } else {
            R.layout.item_tab_preference
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.itemView.textView.setText(data[position].text)
        val iconDrawable = ThemeUtils.getTintedDrawable(holder.itemView.context, data[position].icon, android.R.attr.textColorSecondary)
        holder.itemView.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(iconDrawable, null, null, null)
        if(small) {
            holder.itemView.textView.setOnClickListener {
                listener?.onTabAdded(data[position])
            }
        }
        holder.itemView.imageView?.setOnTouchListener { _, event ->
            if(event.action == MotionEvent.ACTION_DOWN) {
                listener?.onStartDrag(holder)
                true
            } else {
                false
            }
        }
    }



    override fun getItemCount(): Int {
        return data.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
