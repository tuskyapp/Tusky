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
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.util.FocalPointUtil

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
open class MediaPreviewImageView
@JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr), RequestListener<Drawable> {
    private var focus: Attachment.Focus? = null
    private var focalMatrix: Matrix? = null

    /**
     * Set the focal point for this view.
     */
    fun setFocalPoint(focus: Attachment.Focus?) {
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
     * from this view to the PhotoView when animating between this view and the detailed view of
     * the image. Since the PhotoView does not support a MATRIX scale type, the app would crash
     * if we simply passed that on, so instead we pretend that CENTER_CROP is still used here
     * even if we have a focus point set.
     */
    override fun getScaleType(): ScaleType {
        return if (focus != null) {
            ScaleType.CENTER_CROP
        } else {
            super.getScaleType()
        }
    }

    /**
     * Overridden setScaleType method which only accepts the new type if we don't have a focal
     * point set.
     *
     */
    override fun setScaleType(type: ScaleType) {
        if (focus != null) {
            super.setScaleType(ScaleType.MATRIX)
        } else {
            super.setScaleType(type)
        }
    }

    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
        return false
    }

    override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
        recalculateMatrix(width, height, resource)
        return false
    }

    /**
     * Called when the size of the view changes, it calls the FocalPointUtil to update the
     * matrix if we have a set focal point. It then reassigns the matrix to this imageView.
     */
    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        recalculateMatrix(width, height, drawable)

        super.onSizeChanged(width, height, oldWidth, oldHeight)
    }

    private fun recalculateMatrix(width: Int, height: Int, drawable: Drawable?) {
        if (drawable != null && focus != null && focalMatrix != null) {
            scaleType = ScaleType.MATRIX
            FocalPointUtil.updateFocalPointMatrix(
                width.toFloat(), height.toFloat(),
                drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat(),
                focus as Attachment.Focus, focalMatrix as Matrix
            )
            imageMatrix = focalMatrix
        }
    }
}
