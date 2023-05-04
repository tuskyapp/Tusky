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
import android.view.ViewGroup
import androidx.core.view.size
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.google.android.material.chip.Chip
import com.keylesspalace.tusky.HASHTAG
import com.keylesspalace.tusky.LIST
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.ScreenData
import com.keylesspalace.tusky.databinding.ItemTabPreferenceBinding
import com.keylesspalace.tusky.databinding.ItemTabPreferenceSmallBinding
import com.keylesspalace.tusky.util.BindingHolder
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.setDrawableTint
import com.keylesspalace.tusky.util.show
import com.mikepenz.iconics.IconicsDrawable

interface ItemInteractionListener {
    fun onScreenAdded(screen: ScreenData)
    fun onScreenRemoved(position: Int)
    fun onStartDelete(viewHolder: RecyclerView.ViewHolder)
    fun onStartDrag(viewHolder: RecyclerView.ViewHolder)
    fun onActionChipClicked(screen: ScreenData, screenPosition: Int)
    fun onChipClicked(screen: ScreenData, screenPosition: Int, chipPosition: Int)
}

class ScreenAdapter(
    private var data: List<ScreenData>,
    private val small: Boolean,
    private val listener: ItemInteractionListener,
    private var removeButtonEnabled: Boolean = false
) : RecyclerView.Adapter<BindingHolder<ViewBinding>>() {

    fun updateData(newData: List<ScreenData>) {
        this.data = newData
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ViewBinding> {
        val binding = if (small) {
            ItemTabPreferenceSmallBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        } else {
            ItemTabPreferenceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        }
        return BindingHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingHolder<ViewBinding>, position: Int) {
        val context = holder.itemView.context
        val screenData = data[position]

        if (small) {
            val binding = holder.binding as ItemTabPreferenceSmallBinding

            binding.textView.setText(screenData.text)
            binding.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(IconicsDrawable(context, screenData.icon), null, null, null)

            binding.textView.setOnClickListener {
                listener.onScreenAdded(screenData)
            }
        } else {
            val binding = holder.binding as ItemTabPreferenceBinding

            if (screenData.id == LIST) {
                binding.textView.text = screenData.arguments.getOrNull(1).orEmpty()
            } else {
                binding.textView.setText(screenData.text)
            }

            binding.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(IconicsDrawable(context, screenData.icon), null, null, null)

            binding.imageView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    listener.onStartDrag(holder)
                    true
                } else {
                    false
                }
            }
            binding.removeButton.setOnClickListener {
                listener.onScreenRemoved(holder.bindingAdapterPosition)
            }
            binding.removeButton.isEnabled = removeButtonEnabled
            setDrawableTint(
                holder.itemView.context,
                binding.removeButton.drawable,
                (if (removeButtonEnabled) android.R.attr.textColorTertiary else R.attr.textColorDisabled)
            )

            if (screenData.id == HASHTAG) {
                binding.chipGroup.show()

                /*
                 * The chip group will always contain the actionChip (it is defined in the xml layout).
                 * The other dynamic chips are inserted in front of the actionChip.
                 * This code tries to reuse already added chips to reduce the number of Views created.
                 */
                screenData.arguments.forEachIndexed { i, arg ->

                    val chip = binding.chipGroup.getChildAt(i).takeUnless { it.id == R.id.actionChip } as Chip?
                        ?: Chip(context).apply {
                            setCloseIconResource(R.drawable.ic_cancel_24dp)
                            isCheckable = false
                            binding.chipGroup.addView(this, binding.chipGroup.size - 1)
                        }

                    chip.text = arg

                    if (screenData.arguments.size <= 1) {
                        chip.isCloseIconVisible = false
                        chip.setOnClickListener(null)
                    } else {
                        chip.isCloseIconVisible = true
                        chip.setOnClickListener {
                            listener.onChipClicked(screenData, holder.bindingAdapterPosition, i)
                        }
                    }
                }

                while (binding.chipGroup.size - 1 > screenData.arguments.size) {
                    binding.chipGroup.removeViewAt(screenData.arguments.size)
                }

                binding.actionChip.setOnClickListener {
                    listener.onActionChipClicked(screenData, holder.bindingAdapterPosition)
                }
            } else {
                binding.chipGroup.hide()
            }
        }
    }

    override fun getItemCount() = data.size

    fun setRemoveButtonVisible(enabled: Boolean) {
        if (removeButtonEnabled != enabled) {
            removeButtonEnabled = enabled
            notifyDataSetChanged()
        }
    }
}
