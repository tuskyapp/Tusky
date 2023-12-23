/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.util

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Shader
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.util.Util
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.security.MessageDigest

/**
 * Set an opaque background behind the non-transparent areas of a bitmap.
 *
 * Profile images may have areas that are partially transparent (i.e., alpha value >= 1 and < 255).
 *
 * Displaying those can be a problem if there is anything drawn under them, as it will show
 * through the image.
 *
 * Fix this, by:
 *
 * - Creating a mask that matches the partially transparent areas of the image
 * - Creating a new bitmap that, in the areas that match the mask, contains a background color
 * - Composite the original image over the top
 *
 * So the partially transparent areas on the original image are composited over the original
 * background, the fully transparent areas on the original image are left transparent.
 */
class CompositeWithOpaqueBackground(val backgroundColor: Int) : BitmapTransformation() {

    override fun equals(other: Any?): Boolean {
        if (other is CompositeWithOpaqueBackground) {
            return other.backgroundColor == backgroundColor
        }
        return false
    }

    override fun hashCode() = Util.hashCode(ID.hashCode(), backgroundColor.hashCode())

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID_BYTES)
        messageDigest.update(ByteBuffer.allocate(4).putInt(backgroundColor.hashCode()).array())
    }

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        // If the input bitmap has no alpha channel then there's nothing to do
        if (!toTransform.hasAlpha()) return toTransform

        // Convert the background to a bitmap.
        val backgroundBitmap = pool.get(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        backgroundBitmap.eraseColor(backgroundColor)

        // Convert the alphaBitmap (where the alpha channel has 8bpp) to a mask of 1bpp
        // TODO: toTransform.extractAlpha(paint, ...) could be used here, but I can't find any
        // useful documentation covering paints and mask filters.
        val maskBitmap = pool.get(outWidth, outHeight, Bitmap.Config.ALPHA_8).apply {
            val canvas = Canvas(this)
            canvas.drawBitmap(toTransform, 0f, 0f, EXTRACT_MASK_PAINT)
        }

        val shader = BitmapShader(backgroundBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val paintShader = Paint()
        paintShader.isAntiAlias = true
        paintShader.shader = shader
        paintShader.style = Paint.Style.FILL_AND_STROKE

        // Write the background to a new bitmap, masked to just the non-transparent areas of the
        // original image
        val dest = pool.get(outWidth, outHeight, toTransform.config)
        val canvas = Canvas(dest)
        canvas.drawBitmap(maskBitmap, 0f, 0f, paintShader)

        // Finally, write the original bitmap over the top
        canvas.drawBitmap(toTransform, 0f, 0f, null)

        // Clean up intermediate bitmaps
        pool.put(maskBitmap)
        pool.put(backgroundBitmap)

        return dest
    }

    companion object {
        @Suppress("unused")
        private const val TAG = "CompositeWithOpaqueBackground"
        private val ID = CompositeWithOpaqueBackground::class.qualifiedName!!
        private val ID_BYTES = ID.toByteArray(Charset.forName("UTF-8"))

        /** Paint with a color filter that converts 8bpp alpha images to a 1bpp mask */
        private val EXTRACT_MASK_PAINT = Paint().apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        0f, 0f, 0f, 0f, 0f,
                        0f, 0f, 0f, 0f, 0f,
                        0f, 0f, 0f, 0f, 0f,
                        0f, 0f, 0f, 255f, 0f
                    )
                )
            )
            isAntiAlias = false
        }
    }
}
