/* Copyright 2022 Tusky contributors
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
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import com.keylesspalace.tusky.util.getTuskyDisplayName
import com.keylesspalace.tusky.util.modernLanguageCode
import java.util.Locale

class LocaleAdapter(context: Context, resource: Int, locales: List<Locale>) : ArrayAdapter<Locale>(context, resource, locales) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return (super.getView(position, convertView, parent) as TextView).apply {
            setTextColor(MaterialColors.getColor(this, android.R.attr.textColorTertiary))
            typeface = Typeface.DEFAULT_BOLD
            text = super.getItem(position)?.modernLanguageCode?.uppercase()
        }
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return (super.getDropDownView(position, convertView, parent) as TextView).apply {
            setTextColor(MaterialColors.getColor(this, android.R.attr.textColorTertiary))
            text = super.getItem(position)?.getTuskyDisplayName(context)
        }
    }
}
