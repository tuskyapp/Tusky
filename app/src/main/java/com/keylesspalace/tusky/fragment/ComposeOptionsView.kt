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

package com.keylesspalace.tusky.fragment

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Status
import kotlinx.android.synthetic.main.view_compose_options.view.*


class ComposeOptionsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : LinearLayout(context, attrs, defStyleAttr) {

    var listener: ComposeOptionsListener? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_compose_options, this)
        visibilityRadioGroup.setOnCheckedChangeListener({ _, checkedId ->
            val visibility = when (checkedId) {
                R.id.publicRadioButton ->
                    Status.Visibility.PUBLIC
                R.id.unlistedRadioButton ->
                    Status.Visibility.UNLISTED
                R.id.privateRadioButton ->
                    Status.Visibility.PRIVATE
                R.id.directRadioButton ->
                    Status.Visibility.DIRECT
                else ->
                    Status.Visibility.PUBLIC
            }
            listener?.onVisibilityChanged(visibility)
        })
    }

    fun setStatusVisibility(visibility: Status.Visibility) {
        val selectedButton = when (visibility) {
            Status.Visibility.PUBLIC ->
                R.id.publicRadioButton
            Status.Visibility.UNLISTED ->
                R.id.unlistedRadioButton
            Status.Visibility.PRIVATE ->
                R.id.privateRadioButton
            Status.Visibility.DIRECT ->
                R.id.directRadioButton
            else ->
                R.id.directRadioButton

        }

        visibilityRadioGroup.check(selectedButton)
    }

}

interface ComposeOptionsListener {
    fun onVisibilityChanged(visibility: Status.Visibility)
}
