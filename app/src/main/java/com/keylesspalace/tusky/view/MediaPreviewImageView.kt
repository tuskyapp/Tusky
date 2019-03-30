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
import android.graphics.Matrix
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import com.keylesspalace.tusky.entity.Attachment

import com.keylesspalace.tusky.util.FocalPointUtil
import com.squareup.picasso.Callback
import java.lang.Exception

/**
 * This is an extension of the standard android ImageView, which makes sure to update the custom
 * matrix when its size changes if a focal point is set.
 *
 * If a focal point is set on this view, it will use the FocalPointUtil to update the image
 * matrix each time the size of the view is changed. This is needed to ensure that the correct
 * cropping is maintained.
 *
 * However if there is no focal point set (e.g. it is null), then this view should simply
 * act exactly the same as an ordinary android ImageView.
 */
class MediaPreviewImageView
@JvmOverloads constructor(
context: Context,
attrs: AttributeSet? = null,
defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr), Callback {
    private var focus: Attachment.Focus? = null
    private var focalMatrix: Matrix? = null

    /**
     * Set the focal point for this view.
     */
    fun setFocalPoint(focus: Attachment.Focus) {
        this.focus = focus
        super.setScaleType(ScaleType.MATRIX)

        if (focalMatrix == null) {
            focalMatrix = Matrix()
        }
    }

    /**
     * Remove the focal point from this view (if there was one).
     */
    fun removeFocalPoint() {
        super.setScaleType(ScaleType.CENTER_CROP)
        focus = null
    }

    /**
     * Overridden getScaleType method which returns CENTER_CROP if we have a focal point set.
     *
     * This is necessary because the Android transitions framework tries to copy the scale type
     * from this view to the PhotoView when animating between this view and the detailled view of
     * the image. Since the PhotoView does not support a MATRIX scale type, the app would crash
     * if we simply passed that on, so instead we pretend that CENTER_CROP is still used here
     * even if we have a focus point set.
     */
    override fun getScaleType(): ScaleType {
        if (focus != null) {
            return ScaleType.CENTER_CROP
        } else {
            return super.getScaleType()
        }
    }

    /**
     * Overridden setScaleType method which only accepts the new type if we don't have a focal
     * point set.
     *
     *
     */
    override fun setScaleType(type: ScaleType) {
        if (focus != null) {
            super.setScaleType(ScaleType.MATRIX)
        } else {
            super.setScaleType(type)
        }
    }

    /**
     * Called when the image is first succesfully loaded by Picasso, this function makes sure
     * that the custom matrix of this image is initialized if a focus point is set.
     */
    override fun onSuccess() {
        onSizeChanged(width, height, width, height)
    }

    // We do not handle the error here, instead it will be handled higher up the call chain.
    override fun onError(e: Exception) {
    }

    /**
     * Called when the size of the view changes, it calls the FocalPointUtil to update the
     * matrix if we have a set focal point. It then reassigns the matrix to this imageView.
     */
    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        if (drawable != null && focus != null && focalMatrix != null) {
            scaleType = ScaleType.MATRIX
            FocalPointUtil.updateFocalPointMatrix(width.toFloat(), height.toFloat(),
                    drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat(),
                    focus as Attachment.Focus, focalMatrix as Matrix)
            imageMatrix = focalMatrix
        }

        super.onSizeChanged(width, height, oldWidth, oldHeight)
    }
}
