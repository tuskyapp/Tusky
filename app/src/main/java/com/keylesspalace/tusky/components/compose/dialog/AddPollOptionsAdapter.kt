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

package com.keylesspalace.tusky.components.compose.dialog

import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemAddPollOptionBinding
import com.keylesspalace.tusky.util.BindingHolder
import com.keylesspalace.tusky.util.onTextChanged
import com.keylesspalace.tusky.util.visible

class AddPollOptionsAdapter(
    private var options: MutableList<String>,
    private val maxOptionLength: Int,
    private val onOptionRemoved: (Boolean) -> Unit,
    private val onOptionChanged: (Boolean) -> Unit
) : RecyclerView.Adapter<BindingHolder<ItemAddPollOptionBinding>>() {

    val pollOptions: List<String>
        get() = options.toList()

    fun addChoice() {
        options.add("")
        notifyItemInserted(options.size - 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemAddPollOptionBinding> {
        val binding = ItemAddPollOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val holder = BindingHolder(binding)
        binding.optionEditText.filters = arrayOf(InputFilter.LengthFilter(maxOptionLength))

        binding.optionEditText.onTextChanged { s, _, _, _ ->
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                options[pos] = s.toString()
                onOptionChanged(validateInput())
            }
        }

        return holder
    }

    override fun getItemCount() = options.size

    override fun onBindViewHolder(holder: BindingHolder<ItemAddPollOptionBinding>, position: Int) {
        holder.binding.optionEditText.setText(options[position])

        holder.binding.optionTextInputLayout.hint = holder.binding.root.context.getString(R.string.poll_new_choice_hint, position + 1)

        holder.binding.deleteButton.visible(position > 1, View.INVISIBLE)

        holder.binding.deleteButton.setOnClickListener {
            holder.binding.optionEditText.clearFocus()
            options.removeAt(holder.bindingAdapterPosition)
            notifyItemRemoved(holder.bindingAdapterPosition)
            onOptionRemoved(validateInput())
        }
    }

    private fun validateInput(): Boolean {
        if (options.contains("") || options.distinct().size != options.size) {
            return false
        }

        return true
    }
}
