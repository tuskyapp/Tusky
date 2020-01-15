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

package com.keylesspalace.tusky.components.compose.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Status
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable

class TootButton
@JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : MaterialButton(context, attrs, defStyleAttr) {

    private val smallStyle: Boolean = context.resources.getBoolean(R.bool.show_small_toot_button)

    init {
        if(smallStyle) {
            setIconResource(R.drawable.ic_send_24dp)
        } else {
            setText(R.string.action_send)
            iconGravity = ICON_GRAVITY_TEXT_START
        }
        val padding = resources.getDimensionPixelSize(R.dimen.toot_button_horizontal_padding)
        setPadding(padding, 0, padding, 0)
    }

    fun setStatusVisibility(visibility: Status.Visibility) {
        if(!smallStyle) {

            icon = when (visibility) {
                Status.Visibility.PUBLIC -> {
                    setText(R.string.action_send_public)
                    null
                }
                Status.Visibility.UNLISTED -> {
                    setText(R.string.action_send)
                    null
                }
                Status.Visibility.PRIVATE,
                Status.Visibility.DIRECT -> {
                    setText(R.string.action_send)
                    IconicsDrawable(context, GoogleMaterial.Icon.gmd_lock).sizeDp(18).color(Color.WHITE)
                }
                else -> {
                    null
                }
            }
        }

    }

}

