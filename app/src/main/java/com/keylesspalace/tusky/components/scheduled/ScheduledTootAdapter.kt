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

package com.keylesspalace.tusky.components.scheduled

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.ScheduledStatus

interface ScheduledTootAction {
    fun edit(position: Int, item: ScheduledStatus?)
    fun delete(position: Int, item: ScheduledStatus?)
}

class ScheduledTootAdapter(
        val listener: ScheduledTootAction
) : RecyclerView.Adapter<ScheduledTootAdapter.TootViewHolder>() {

    private var items: MutableList<ScheduledStatus> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TootViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_scheduled_toot, parent, false)
        return TootViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: TootViewHolder, position: Int) {
        viewHolder.bind(items[position])
    }

    override fun getItemCount() = items.size

    fun setItems(newItems: List<ScheduledStatus>) {
        items = newItems.toMutableList()
        notifyDataSetChanged()
    }

    fun removeItem(position: Int): ScheduledStatus? {
        if (position < 0 || position >= items.size) {
            return null
        }
        val toot = items.removeAt(position)
        notifyItemRemoved(position)
        return toot
    }

    inner class TootViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val text: TextView = view.findViewById(R.id.text)
        private val edit: ImageButton = view.findViewById(R.id.edit)
        private val delete: ImageButton = view.findViewById(R.id.delete)

        fun bind(item: ScheduledStatus) {
            edit.isEnabled = true
            delete.isEnabled = true
                text.text = item.params.text
                edit.setOnClickListener { v: View ->
                    v.isEnabled = false
                    listener.edit(adapterPosition, item)
                }
                delete.setOnClickListener { v: View ->
                    v.isEnabled = false
                    listener.delete(adapterPosition, item)
                }

        }

    }

}