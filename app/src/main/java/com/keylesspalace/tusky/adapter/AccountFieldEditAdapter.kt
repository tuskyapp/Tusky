/* Copyright 2018 Conny Duck
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

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.databinding.ItemEditFieldBinding
import com.keylesspalace.tusky.entity.StringField
import com.keylesspalace.tusky.util.BindingHolder

class AccountFieldEditAdapter : RecyclerView.Adapter<BindingHolder<ItemEditFieldBinding>>() {

    private val fieldData = mutableListOf<MutableStringPair>()
    private var maxNameLength: Int? = null
    private var maxValueLength: Int? = null

    fun setFields(fields: List<StringField>) {
        fieldData.clear()

        fields.forEach { field ->
            fieldData.add(MutableStringPair(field.name, field.value))
        }
        if (fieldData.isEmpty()) {
            fieldData.add(MutableStringPair("", ""))
        }

        notifyDataSetChanged()
    }

    fun setFieldLimits(maxNameLength: Int?, maxValueLength: Int?) {
        this.maxNameLength = maxNameLength
        this.maxValueLength = maxValueLength
        notifyDataSetChanged()
    }

    fun getFieldData(): List<StringField> {
        return fieldData.map {
            StringField(it.first, it.second)
        }
    }

    fun addField() {
        fieldData.add(MutableStringPair("", ""))
        notifyItemInserted(fieldData.size - 1)
    }

    override fun getItemCount() = fieldData.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemEditFieldBinding> {
        val binding = ItemEditFieldBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BindingHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemEditFieldBinding>, position: Int) {
        holder.binding.accountFieldNameText.setText(fieldData[position].first)
        holder.binding.accountFieldValueText.setText(fieldData[position].second)

        holder.binding.accountFieldNameTextLayout.isCounterEnabled = maxNameLength != null
        maxNameLength?.let {
            holder.binding.accountFieldNameTextLayout.counterMaxLength = it
        }

        holder.binding.accountFieldValueTextLayout.isCounterEnabled = maxValueLength != null
        maxValueLength?.let {
            holder.binding.accountFieldValueTextLayout.counterMaxLength = it
        }

        holder.binding.accountFieldNameText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(newText: Editable) {
                fieldData[holder.bindingAdapterPosition].first = newText.toString()
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })

        holder.binding.accountFieldValueText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(newText: Editable) {
                fieldData[holder.bindingAdapterPosition].second = newText.toString()
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })
    }

    class MutableStringPair(var first: String, var second: String)
}
