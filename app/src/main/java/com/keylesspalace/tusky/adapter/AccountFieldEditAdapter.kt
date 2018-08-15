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

import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.StringField
import kotlinx.android.synthetic.main.item_edit_field.view.*

class AccountFieldEditAdapter : RecyclerView.Adapter<AccountFieldEditAdapter.ViewHolder>() {

    private val fieldData = mutableListOf<MutableStringPair>()

    fun setFields(fields: List<StringField>) {
        fieldData.clear()

        fields.forEach { field ->
            fieldData.add(MutableStringPair(field.name, field.value))
        }
        if(fieldData.isEmpty()) {
            fieldData.add(MutableStringPair("", ""))
        }

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

    override fun getItemCount(): Int = fieldData.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountFieldEditAdapter.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_edit_field, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: AccountFieldEditAdapter.ViewHolder, position: Int) {
        viewHolder.nameTextView.setText(fieldData[position].first)
        viewHolder.valueTextView.setText(fieldData[position].second)

        viewHolder.nameTextView.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(newText: Editable) {
                fieldData[viewHolder.adapterPosition].first = newText.toString()
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })

        viewHolder.valueTextView.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(newText: Editable) {
                fieldData[viewHolder.adapterPosition].second = newText.toString()
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })

    }

    class ViewHolder(rootView: View) : RecyclerView.ViewHolder(rootView) {
        val nameTextView: EditText = rootView.accountFieldName
        val valueTextView: EditText = rootView.accountFieldValue
    }

    class MutableStringPair (var first: String, var second: String)


}
