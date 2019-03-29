/* Copyright 2019 Conny Duck
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma.adapter

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import tech.bigfig.roma.HASHTAG
import tech.bigfig.roma.R
import tech.bigfig.roma.TabData
import tech.bigfig.roma.util.ThemeUtils
import kotlinx.android.synthetic.main.item_tab_preference.view.*
import tech.bigfig.roma.util.hide
import tech.bigfig.roma.util.show


interface ItemInteractionListener {
    fun onTabAdded(tab: TabData)
    fun onStartDelete(viewHolder: RecyclerView.ViewHolder)
    fun onStartDrag(viewHolder: RecyclerView.ViewHolder)
    fun onActionChipClicked(tab: TabData)
}

class TabAdapter(private var data: List<TabData>,
                 private val small: Boolean = false,
                 private val listener: ItemInteractionListener? = null) : RecyclerView.Adapter<TabAdapter.ViewHolder>() {

    fun updateData(newData: List<TabData>) {
        this.data = newData
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = if (small) {
            R.layout.item_tab_preference_small
        } else {
            R.layout.item_tab_preference
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val context = holder.itemView.context
        holder.itemView.textView.setText(data[position].text)
        val iconDrawable = ThemeUtils.getTintedDrawable(context, data[position].icon, android.R.attr.textColorSecondary)
        holder.itemView.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(iconDrawable, null, null, null)
        if (small) {
            holder.itemView.textView.setOnClickListener {
                listener?.onTabAdded(data[position])
            }
        }
        holder.itemView.imageView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                listener?.onStartDrag(holder)
                true
            } else {
                false
            }
        }

        if (!small) {

            if (data[position].id == HASHTAG) {
                holder.itemView.chipGroup.show()
                holder.itemView.actionChip.text = data[position].arguments[0]

                holder.itemView.actionChip.setChipIconResource(R.drawable.ic_edit_chip)

                holder.itemView.actionChip.chipIcon = context.getDrawable(R.drawable.ic_edit_chip)
                holder.itemView.actionChip.setOnClickListener {
                    listener?.onActionChipClicked(data[position])
                }

            } else {
                holder.itemView.chipGroup.hide()
            }
        }
    }

    override fun getItemCount(): Int {
        return data.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
