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

package tech.bigfig.roma.view

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.widget.LinearLayout
import tech.bigfig.roma.R
import tech.bigfig.roma.entity.Status
import kotlinx.android.synthetic.main.view_compose_options.view.*


class ComposeOptionsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : LinearLayout(context, attrs, defStyleAttr) {

    var listener: ComposeOptionsListener? = null

    init {
        inflate(context, R.layout.view_compose_options, this)

        publicRadioButton.setButtonDrawable(R.drawable.ic_public_24dp)
        unlistedRadioButton.setButtonDrawable(R.drawable.ic_lock_open_24dp)
        privateRadioButton.setButtonDrawable(R.drawable.ic_lock_outline_24dp)
        directRadioButton.setButtonDrawable(R.drawable.ic_email_24dp)

        visibilityRadioGroup.setOnCheckedChangeListener { _, checkedId ->
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
        }
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
