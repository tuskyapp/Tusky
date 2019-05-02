/* Copyright 2018 Conny Duck
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

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import android.widget.TextView
import tech.bigfig.roma.R
import tech.bigfig.roma.entity.Emoji
import tech.bigfig.roma.entity.Field
import tech.bigfig.roma.interfaces.LinkListener
import tech.bigfig.roma.util.CustomEmojiHelper
import tech.bigfig.roma.util.LinkHelper
import kotlinx.android.synthetic.main.item_account_field.view.*

class AccountFieldAdapter(private val linkListener: LinkListener) : RecyclerView.Adapter<AccountFieldAdapter.ViewHolder>() {

    var emojis: List<Emoji> = emptyList()
    var fields: List<Field> = emptyList()

    override fun getItemCount() = fields.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_account_field, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val field = fields[position]
        viewHolder.nameTextView.text = field.name
        val emojifiedValue = CustomEmojiHelper.emojifyText(field.value, emojis, viewHolder.valueTextView)
        LinkHelper.setClickableText(viewHolder.valueTextView, emojifiedValue, null, linkListener)

        if(field.verifiedAt != null) {
            viewHolder.valueTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0,  R.drawable.ic_check_circle, 0)
        } else {
            viewHolder.valueTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0 )
        }

    }

    class ViewHolder(rootView: View) : RecyclerView.ViewHolder(rootView) {
        val nameTextView: TextView = rootView.accountFieldName
        val valueTextView: TextView = rootView.accountFieldValue
    }

}
