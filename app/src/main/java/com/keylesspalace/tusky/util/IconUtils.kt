package com.keylesspalace.tusky.util

import android.content.Context
import com.keylesspalace.tusky.R
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizePx

fun makeIcon(context: Context, icon: GoogleMaterial.Icon, iconSize: Int): IconicsDrawable {
    return IconicsDrawable(context, icon).apply {
        sizePx = iconSize
        colorInt = ThemeUtils.getColor(context, R.attr.iconColor)
    }
}
