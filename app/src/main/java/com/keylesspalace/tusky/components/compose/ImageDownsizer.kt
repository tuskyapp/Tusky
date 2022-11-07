/* Copyright 2022 Tusky contributors
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

package com.keylesspalace.tusky.components.compose

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.net.Uri
import com.keylesspalace.tusky.util.calculateInSampleSize
import com.keylesspalace.tusky.util.closeQuietly
import com.keylesspalace.tusky.util.getImageOrientation
import com.keylesspalace.tusky.util.reorientBitmap
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

/**
 * @param uri             the uri pointing to the input file
 * @param sizeLimit       the maximum number of bytes the output image is allowed to have
 * @param contentResolver to resolve the specified input uri
 * @param tempFile        the file where the result will be stored
 * @return true when the image was successfully resized, false otherwise
 */
fun downsizeImage(
    uri: Uri,
    sizeLimit: Int,
    contentResolver: ContentResolver,
    tempFile: File
): Boolean {

    val decodeBoundsInputStream = try {
        contentResolver.openInputStream(uri)
    } catch (e: FileNotFoundException) {
        return false
    }
    // Initially, just get the image dimensions.
    val options = BitmapFactory.Options()
    options.inJustDecodeBounds = true
    BitmapFactory.decodeStream(decodeBoundsInputStream, null, options)
    decodeBoundsInputStream.closeQuietly()
    // Get EXIF data, for orientation info.
    val orientation = getImageOrientation(uri, contentResolver)
    /* Unfortunately, there isn't a determined worst case compression ratio for image
             * formats. So, the only way to tell if they're too big is to compress them and
             * test, and keep trying at smaller sizes. The initial estimate should be good for
             * many cases, so it should only iterate once, but the loop is used to be absolutely
             * sure it gets downsized to below the limit. */
    var scaledImageSize = 1024
    do {
        val outputStream = try {
            FileOutputStream(tempFile)
        } catch (e: FileNotFoundException) {
            return false
        }
        val decodeBitmapInputStream = try {
            contentResolver.openInputStream(uri)
        } catch (e: FileNotFoundException) {
            return false
        }
        options.inSampleSize = calculateInSampleSize(options, scaledImageSize, scaledImageSize)
        options.inJustDecodeBounds = false
        val scaledBitmap: Bitmap = try {
            BitmapFactory.decodeStream(decodeBitmapInputStream, null, options)
        } catch (error: OutOfMemoryError) {
            return false
        } finally {
            decodeBitmapInputStream.closeQuietly()
        } ?: return false

        val reorientedBitmap = reorientBitmap(scaledBitmap, orientation)
        if (reorientedBitmap == null) {
            scaledBitmap.recycle()
            return false
        }
        /* Retain transparency if there is any by encoding as png */
        val format: CompressFormat = if (!reorientedBitmap.hasAlpha()) {
            CompressFormat.JPEG
        } else {
            CompressFormat.PNG
        }
        reorientedBitmap.compress(format, 85, outputStream)
        reorientedBitmap.recycle()
        scaledImageSize /= 2
    } while (tempFile.length() > sizeLimit)

    return true
}
