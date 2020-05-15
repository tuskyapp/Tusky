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
import androidx.core.view.size
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.keylesspalace.tusky.HASHTAG
import com.keylesspalace.tusky.LIST
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.TabData
import com.keylesspalace.tusky.util.ThemeUtils
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import kotlinx.android.synthetic.main.item_tab_preference.view.*

interface ItemInteractionListener {
    fun onTabAdded(tab: TabData)
    fun onTabRemoved(position: Int)
    fun onStartDelete(viewHolder: RecyclerView.ViewHolder)
    fun onStartDrag(viewHolder: RecyclerView.ViewHolder)
    fun onActionChipClicked(tab: TabData)
    fun onChipClicked(tab: TabData, chipPosition: Int)
}

class TabAdapter(private var data: List<TabData>,
                 private val small: Boolean,
                 private val listener: ItemInteractionListener,
                 private var removeButtonEnabled: Boolean = false) : RecyclerView.Adapter<TabAdapter.ViewHolder>() {

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
        if (!small && data[position].id == LIST) {
            holder.itemView.textView.text = data[position].arguments.getOrNull(1).orEmpty()
        } else {
            holder.itemView.textView.setText(data[position].text)
        }
        val iconDrawable = ThemeUtils.getTintedDrawable(context, data[position].icon, android.R.attr.textColorSecondary)
        holder.itemView.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(iconDrawable, null, null, null)
        if (small) {
            holder.itemView.textView.setOnClickListener {
                listener.onTabAdded(data[position])
            }
        }
        holder.itemView.imageView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                listener.onStartDrag(holder)
                true
            } else {
                false
            }
        }
        holder.itemView.removeButton?.setOnClickListener {
            listener.onTabRemoved(holder.adapterPosition)
        }
        if (holder.itemView.removeButton != null) {
            holder.itemView.removeButton.isEnabled = removeButtonEnabled
            ThemeUtils.setDrawableTint(
                    holder.itemView.context,
                    holder.itemView.removeButton.drawable,
                    (if (removeButtonEnabled) android.R.attr.textColorTertiary else R.attr.textColorDisabled)
            )
        }

        if (!small) {

            if (data[position].id == HASHTAG) {
                holder.itemView.chipGroup.show()

                /*
                 * The chip group will always contain the actionChip (it is defined in the xml layout).
                 * The other dynamic chips are inserted in front of the actionChip.
                 * This code tries to reuse already added chips to reduce the number of Views created.
                 */
                data[position].arguments.forEachIndexed { i, arg ->

                    val chip = holder.itemView.chipGroup.getChildAt(i).takeUnless { it.id == R.id.actionChip } as Chip?
                            ?: Chip(context).apply {
                                text = arg
                                holder.itemView.chipGroup.addView(this, holder.itemView.chipGroup.size - 1)
                            }

                    chip.text = arg

                    if(data[position].arguments.size <= 1) {
                        chip.chipIcon = null
                        chip.setOnClickListener(null)
                    } else {
                        val cancelIcon = ThemeUtils.getTintedDrawable(context, R.drawable.ic_cancel_24dp, android.R.attr.textColorPrimary)
                        chip.chipIcon = cancelIcon
                        chip.setOnClickListener {
                            listener.onChipClicked(data[position], i)
                        }
                    }
                }

                while(holder.itemView.chipGroup.size - 1 > data[position].arguments.size) {
                    holder.itemView.chipGroup.removeViewAt(data[position].arguments.size - 1)
                }

                holder.itemView.actionChip.setOnClickListener {
                    listener.onActionChipClicked(data[position])
                }

            } else {
                holder.itemView.chipGroup.hide()
            }
        }
    }

    override fun getItemCount(): Int {
        return data.size
    }

    fun setRemoveButtonVisible(enabled: Boolean) {
        if (removeButtonEnabled != enabled) {
            removeButtonEnabled = enabled
            notifyDataSetChanged()
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
