/* Copyright 2019 kyori19
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

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.MastoList
import com.keylesspalace.tusky.util.ThemeUtils
import kotlinx.android.synthetic.main.item_picker_list.view.*

class ListSelectionAdapter(context: Context) : ArrayAdapter<MastoList>(context, R.layout.item_autocomplete_hashtag) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView

        if (convertView == null) {
            val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = layoutInflater.inflate(R.layout.item_picker_list, parent, false)
        }
        view!!

        val list = getItem(position)
        if (list != null) {
            val title = view.title
            title.text = list.title
            val icon = ThemeUtils.getTintedDrawable(context, R.drawable.ic_list, android.R.attr.textColorPrimary)
            title.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
        }

        return view
    }
}
