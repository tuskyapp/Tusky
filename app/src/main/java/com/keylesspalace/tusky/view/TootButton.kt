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

package com.keylesspalace.tusky.view

import android.content.Context
import android.graphics.Color
import android.support.v7.widget.AppCompatButton
import android.util.AttributeSet
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Status
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable

class TootButton
@JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : AppCompatButton(context, attrs, defStyleAttr) {

    private val smallStyle: Boolean = context.resources.getBoolean(R.bool.show_small_toot_button)

    init {
        if(smallStyle) {
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_send_24dp, 0, 0, 0)
        } else {
            compoundDrawablePadding = context.resources.getDimensionPixelSize(R.dimen.toot_button_drawable_padding)
            setText(R.string.action_send)
        }
    }

    fun setStatusVisibility(visibility: Status.Visibility) {
        if(!smallStyle) {

            when (visibility) {
                Status.Visibility.PUBLIC -> {
                    setText(R.string.action_send_public)
                    setCompoundDrawables(null, null, null, null)
                }
                Status.Visibility.UNLISTED -> {
                    setText(R.string.action_send)
                    setCompoundDrawables(null, null, null, null)
                }
                Status.Visibility.PRIVATE,
                Status.Visibility.DIRECT -> {
                    addLock()
                }
                else -> {
                    setCompoundDrawables(null, null, null, null)
                }
            }
        }

    }

    private fun addLock() {
        setText(R.string.action_send)
        val lock = IconicsDrawable(context, GoogleMaterial.Icon.gmd_lock).sizeDp(18).color(Color.WHITE)
        setCompoundDrawablesWithIntrinsicBounds(lock, null, null, null)
    }

}

