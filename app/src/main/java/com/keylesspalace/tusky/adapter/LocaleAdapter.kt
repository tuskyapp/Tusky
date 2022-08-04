package com.keylesspalace.tusky.adapter

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.keylesspalace.tusky.util.ThemeUtils
import java.util.*

class LocaleAdapter(context: Context, resource: Int, locales: List<Locale>) : ArrayAdapter<Locale>(context, resource, locales) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return (super.getView(position, convertView, parent) as TextView).apply {
            setTextColor(ThemeUtils.getColor(context, android.R.attr.textColorTertiary))
            typeface = Typeface.DEFAULT_BOLD
            text = super.getItem(position)?.language?.uppercase()
        }
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return (super.getDropDownView(position, convertView, parent) as TextView).apply {
            setTextColor(ThemeUtils.getColor(context, android.R.attr.textColorTertiary))
            val locale = super.getItem(position)
            text = "${locale?.displayLanguage} (${locale?.getDisplayLanguage(locale)})"
        }
    }
}