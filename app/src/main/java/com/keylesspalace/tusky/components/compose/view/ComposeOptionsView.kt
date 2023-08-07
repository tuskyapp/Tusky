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
import android.util.AttributeSet
import android.widget.RadioGroup
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.core.database.model.StatusVisibility

class ComposeOptionsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : RadioGroup(context, attrs) {

    var listener: ComposeOptionsListener? = null

    init {
        inflate(context, R.layout.view_compose_options, this)

        setOnCheckedChangeListener { _, checkedId ->
            val visibility = when (checkedId) {
                R.id.publicRadioButton ->
                    StatusVisibility.PUBLIC
                R.id.unlistedRadioButton ->
                    StatusVisibility.UNLISTED
                R.id.privateRadioButton ->
                    StatusVisibility.PRIVATE
                R.id.directRadioButton ->
                    StatusVisibility.DIRECT
                else ->
                    StatusVisibility.PUBLIC
            }
            listener?.onVisibilityChanged(visibility)
        }
    }

    fun setStatusVisibility(visibility: StatusVisibility) {
        val selectedButton = when (visibility) {
            StatusVisibility.PUBLIC ->
                R.id.publicRadioButton
            StatusVisibility.UNLISTED ->
                R.id.unlistedRadioButton
            StatusVisibility.PRIVATE ->
                R.id.privateRadioButton
            StatusVisibility.DIRECT ->
                R.id.directRadioButton
            else ->
                R.id.directRadioButton
        }

        check(selectedButton)
    }
}

interface ComposeOptionsListener {
    fun onVisibilityChanged(visibility: StatusVisibility)
}
