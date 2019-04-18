/* Copyright 2018 Jochem Raat <jchmrt@riseup.net>
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

package tech.bigfig.roma.util

import android.graphics.Matrix

import tech.bigfig.roma.entity.Attachment.Focus

/**
 * Calculates the image matrix needed to maintain the correct cropping for image views based on
 * their focal point.
 *
 * The purpose of this class is to make sure that the focal point information on media
 * attachments are honoured. This class uses the custom matrix option of android ImageView's to
 * customize how the image is cropped into the view.
 *
 * See the explanation of focal points here:
 *    https://github.com/jonom/jquery-focuspoint#1-calculate-your-images-focus-point
 */
object FocalPointUtil {
    /**
     * Update the given matrix for the given parameters.
     *
     * How it works is using the following steps:
     *   - First we determine if the image is too wide or too tall for the view size. If it is
     *   too wide, we need to crop it horizontally and scale the height to fit the view
     *   exactly. If it is too tall we need to crop vertically and scale the width to fit the
     *   view exactly.
     *   - Then we determine what translation is needed to get the focal point in view. We
     *   prefer to get the focal point at the center of the preview. However if that would
     *   result in some part of the preview being empty, we instead align the image so that it
     *   fills the view, but still the focal point is always in view.
     *
     * @param viewWidth The width of the imageView.
     * @param viewHeight The height of the imageView
     * @param imageWidth The width of the actual image
     * @param imageHeight The height of the actual image
     * @param focus The focal point to focus
     * @param mat The matrix to update, this matrix is reset() and then updated with the new
     * configuration. We reuse the old matrix to prevent unnecessary allocations.
     *
     * @return The matrix which correctly crops the image
     */
    fun updateFocalPointMatrix(viewWidth: Float,
                               viewHeight: Float,
                               imageWidth: Float,
                               imageHeight: Float,
                               focus: Focus,
                               mat: Matrix) {
        // Reset the cached matrix:
        mat.reset()

        // calculate scaling:
        val scale = calculateScaling(viewWidth, viewHeight, imageWidth, imageHeight)
        mat.preScale(scale, scale)

        // calculate offsets:
        var top = 0f
        var left = 0f
        if (isVerticalCrop(viewWidth, viewHeight, imageWidth, imageHeight)) {
            top = focalOffset(viewHeight, imageHeight, scale, focalYToCoordinate(focus.y))
        } else { // horizontal crop
            left = focalOffset(viewWidth, imageWidth, scale, focalXToCoordinate(focus.x))
        }

        mat.postTranslate(left, top)
    }

    /**
     * Calculate the scaling of the image needed to make it fill the screen.
     *
     * The scaling used depends on if we need a vertical of horizontal crop.
     */
    fun calculateScaling(viewWidth: Float, viewHeight: Float,
                         imageWidth: Float, imageHeight: Float): Float {
        return if (isVerticalCrop(viewWidth, viewHeight, imageWidth, imageHeight)) {
            viewWidth / imageWidth
        } else {     // horizontal crop:
            viewHeight / imageHeight
        }
    }

    /**
     * Return true if we need a vertical crop, false for a horizontal crop.
     */
    fun isVerticalCrop(viewWidth: Float, viewHeight: Float,
                       imageWidth: Float, imageHeight: Float): Boolean {
        val viewRatio = viewWidth / viewHeight
        val imageRatio = imageWidth / imageHeight

        return viewRatio > imageRatio
    }

    /**
     * Transform the focal x component to the corresponding coordinate on the image.
     *
     * This means that we go from a representation where the left side of the image is -1 and
     * the right side +1, to a representation with the left side being 0 and the right side
     * being +1.
     */
    fun focalXToCoordinate(x: Float): Float {
        return (x + 1) / 2
    }

    /**
     * Transform the focal y component to the corresponding coordinate on the image.
     *
     * This means that we go from a representation where the bottom side of the image is -1 and
     * the top side +1, to a representation with the top side being 0 and the bottom side
     * being +1.
     */
    fun focalYToCoordinate(y: Float): Float {
        return (-y + 1) / 2
    }

    /**
     * Calculate the relative offset needed to focus on the focal point in one direction.
     *
     * This method works for both the vertical and horizontal crops. It simply calculates
     * what offset to take based on the proportions between the scaled image and the view
     * available. It also makes sure to always fill the bounds of the view completely with
     * the image. So it won't put the very edge of the image in center, because that would
     * leave part of the view empty.
     */
    fun focalOffset(view: Float, image: Float,
                    scale: Float, focal: Float): Float {
        // The fraction of the image that will be in view:
        val inView = view / (scale * image)
        var offset = 0f

        // These values indicate the maximum and minimum focal parameter possible while still
        // keeping the entire view filled with the image:
        val maxFocal = 1 - inView / 2
        val minFocal = inView / 2

        if (focal > maxFocal) {
            offset = -((2 - inView) / 2) * image * scale + view * 0.5f
        } else if (focal > minFocal) {
            offset = -focal * image * scale + view * 0.5f
        }

        return offset
    }
}
