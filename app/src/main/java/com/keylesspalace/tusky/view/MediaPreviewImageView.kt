/* Copyright 2018 Jochem Raat <jchmrt@riseup.net>
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
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet

import com.keylesspalace.tusky.util.FocalPointEnforcer

/**
 * This is an extension of the standard android ImageView, which makes sure to call a focal point
 * enforcer when its size changes.
 *
 * If the focalPointEnforcer is set on this view, it will call this enforcer to update the image
 * matrix each time the size of the view is changed. This is needed to ensure that the correct
 * cropping is maintained.
 *
 * However if there is no focalPointEnforcer set (e.g. it is null), then this view should simply
 * act exactly the same as an ordinary android ImageView.
 */
class MediaPreviewImageView
@JvmOverloads constructor(
context: Context,
attrs: AttributeSet? = null,
defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {
    private var focalPointEnforcer: FocalPointEnforcer? = null

    /**
     * Set the focal point enforcer for this view.
     */
    fun setFocalPointEnforcer(enforcer: FocalPointEnforcer) {
        this.focalPointEnforcer = enforcer
    }

    /**
     * Called when the size of the view changes, it calls the focalPointEnforcer to update the
     * matrix if we have one.
     */
    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        if (drawable != null) {
            focalPointEnforcer?.updateFocalPointMatrix()
        }
        super.onSizeChanged(width, height, oldWidth, oldHeight)
    }
}
