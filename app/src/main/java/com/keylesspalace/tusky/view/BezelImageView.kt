/* Copyright 2019 Tusky contributors
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
package com.keylesspalace.tusky.view

import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import com.mikepenz.materialdrawer.view.BezelImageView

/**
 * override BezelImageView from MaterialDrawer library to provide custom outline
 */
class BezelImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BezelImageView(context, attrs, defStyle) {
    override fun onSizeChanged(w: Int, h: Int, old_w: Int, old_h: Int) {
        outlineProvider = CustomOutline(w, h)
    }

    private class CustomOutline(var width: Int, var height: Int) :
        ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(
                0,
                0,
                width,
                height,
                if (width < height) width / 8f else height / 8f
            )
        }
    }
}
