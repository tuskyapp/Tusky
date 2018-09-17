/* Copyright 2017 Andrew Dawson
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

package com.keylesspalace.tusky.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.OpenableColumns
import android.support.annotation.NonNull
import android.support.annotation.Nullable
import android.support.annotation.Px
import android.support.media.ExifInterface
import android.util.Log
import java.io.*

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Class with helper methods for obtaining and resizing media files
 */
class MediaUtils {
    companion object {
        private val TAG = "MediaUtils"
        private val MEDIA_TEMP_PREFIX = "Tusky_Share_Media"
        @JvmField val MEDIA_SIZE_UNKNOWN = -1L

        /**
         * Copies the entire contents of the given stream into a byte array and returns it. Beware of
         * OutOfMemoryError for streams of unknown size.
         */
        @JvmStatic
        fun inputStreamGetBytes(stream: InputStream): ByteArray? {
            val buffer = ByteArrayOutputStream()
            var read: Int
            val data = ByteArray(16384)
            try {
                read = stream.read(data, 0, data.size)
                while (read != -1) {
                    buffer.write(data, 0, read)
                    read = stream.read(data, 0, data.size)
                }
                buffer.flush()
            } catch (e: IOException) {
                return null
            }
            return buffer.toByteArray()
        }

        /**
         * Fetches the size of the media represented by the given URI, assuming it is openable and
         * the ContentResolver is able to resolve it.
         *
         * @return the size of the media in bytes or {@link MediaUtils#MEDIA_SIZE_UNKNOWN}
         */
        @JvmStatic
        fun getMediaSize(contentResolver: ContentResolver, uri: Uri?): Long {
            if(uri == null) {
                return MEDIA_SIZE_UNKNOWN
            }

            var mediaSize = MEDIA_SIZE_UNKNOWN
            var cursor: Cursor
            try {
                cursor = contentResolver.query(uri, null, null, null, null)
            } catch (e: SecurityException) {
                return MEDIA_SIZE_UNKNOWN
            }
            if (cursor != null) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                cursor.moveToFirst()
                mediaSize = cursor.getLong(sizeIndex)
                cursor.close()
            }
            return mediaSize
        }

        @JvmStatic
        fun getSampledBitmap(contentResolver: ContentResolver, uri: Uri, @Px reqWidth: Int, @Px reqHeight: Int): Bitmap? {
            // First decode with inJustDecodeBounds=true to check dimensions
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            var stream: InputStream
            try {
                stream = contentResolver.openInputStream(uri)
            } catch (e: FileNotFoundException) {
                Log.w(TAG, e)
                return null
            }

            BitmapFactory.decodeStream(stream, null, options)

            IOUtils.closeQuietly(stream)

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false
            try {
                stream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(stream, null, options)
                val orientation = getImageOrientation(uri, contentResolver)
                return reorientBitmap(bitmap, orientation)
            } catch (e: FileNotFoundException) {
                Log.w(TAG, e)
                return null
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OutOfMemoryError while trying to get sampled Bitmap", e)
                return null
            } finally {
                IOUtils.closeQuietly(stream)
            }
        }

        @JvmStatic
        fun getImageThumbnail(contentResolver: ContentResolver, uri: Uri, @Px thumbnailSize: Int): Bitmap? {
            val source = getSampledBitmap(contentResolver, uri, thumbnailSize, thumbnailSize) ?: return null
            return ThumbnailUtils.extractThumbnail(source, thumbnailSize, thumbnailSize, ThumbnailUtils.OPTIONS_RECYCLE_INPUT)
        }

        @JvmStatic
        fun getVideoThumbnail(context: Context, uri: Uri, @Px thumbnailSize: Int): Bitmap? {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val source = retriever.frameAtTime ?: return null
            return ThumbnailUtils.extractThumbnail(source, thumbnailSize, thumbnailSize, ThumbnailUtils.OPTIONS_RECYCLE_INPUT)
        }

        @Throws(FileNotFoundException::class)
        @JvmStatic
        fun getImageSquarePixels(contentResolver: ContentResolver, uri: Uri): Long {
            val input = contentResolver.openInputStream(uri)

            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeStream(input, null, options)

            IOUtils.closeQuietly(input)

            return (options.outWidth * options.outHeight).toLong()
        }

        @JvmStatic
        fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
            // Raw height and width of image
            val height = options.outHeight
            val width = options.outWidth
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {

                val halfHeight = height / 2
                val halfWidth = width / 2

                // Calculate the largest inSampleSize value that is a power of 2 and keeps both
                // height and width larger than the requested height and width.
                while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
                    inSampleSize *= 2
                }
            }

            return inSampleSize
        }

        @JvmStatic
        fun reorientBitmap(bitmap: Bitmap, orientation: Int): Bitmap? {
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_NORMAL -> return bitmap
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1.0f, 1.0f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180.0f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                    matrix.setRotate(180.0f)
                    matrix.postScale(-1.0f, 1.0f)
                }
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.setRotate(90.0f)
                    matrix.postScale(-1.0f, 1.0f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90.0f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.setRotate(-90.0f)
                    matrix.postScale(-1.0f, 1.0f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90.0f)
                else -> return bitmap
            }

            return try {
                val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width,
                        bitmap.height, matrix, true)
                if (!bitmap.sameAs(result)) {
                    bitmap.recycle()
                }
                result
            } catch (e: OutOfMemoryError) {
                null
            }
        }

        @JvmStatic
        fun getImageOrientation(uri: Uri, contentResolver: ContentResolver): Int {
            val inputStream: InputStream
            try {
                inputStream = contentResolver.openInputStream(uri)
            } catch (e: FileNotFoundException) {
                Log.w(TAG, e)
                return ExifInterface.ORIENTATION_UNDEFINED
            }
            if (inputStream == null) {
                return ExifInterface.ORIENTATION_UNDEFINED
            }
            val exifInterface: ExifInterface
            try {
                exifInterface = ExifInterface(inputStream)
            } catch (e: IOException) {
                Log.w(TAG, e)
                IOUtils.closeQuietly(inputStream)
                return ExifInterface.ORIENTATION_UNDEFINED
            }
            val orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            IOUtils.closeQuietly(inputStream)
            return orientation
        }

        @JvmStatic
        fun deleteStaleCachedMedia(mediaDirectory: File?) {
            if (mediaDirectory == null || !mediaDirectory.exists()) {
                // Nothing to do
                return
            }

            val twentyfourHoursAgo = Calendar.getInstance()
            twentyfourHoursAgo.add(Calendar.HOUR, -24)
            val unixTime = twentyfourHoursAgo.timeInMillis

            val files = mediaDirectory.listFiles{ file -> unixTime > file.lastModified() && file.name.contains(MEDIA_TEMP_PREFIX) }
            if (files == null || files.isEmpty()) {
                // Nothing to do
                return
            }

            for (file in files) {
                try {
                    file.delete()
                } catch (se: SecurityException) {
                    Log.e(TAG, "Error removing stale cached media")
                }
            }
        }

        @JvmStatic
        fun getTemporaryMediaFilename(extension: String): String {
            return "${MEDIA_TEMP_PREFIX}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.$extension"
        }
    }
}
