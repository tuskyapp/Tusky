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

package com.keylesspalace.tusky.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Px;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Class with helper methods for obtaining and resizing media files
 */
public class MediaUtils {
    private static final String TAG = "MediaUtils";
    public static final int MEDIA_SIZE_UNKNOWN = -1;

    /**
     * Copies the entire contents of the given stream into a byte array and returns it. Beware of
     * OutOfMemoryError for streams of unknown size.
     */
    @Nullable
    public static byte[] inputStreamGetBytes(InputStream stream) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int read;
        byte[] data = new byte[16384];
        try {
            while ((read = stream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, read);
            }
            buffer.flush();
        } catch (IOException e) {
            return null;
        }
        return buffer.toByteArray();
    }

    /**
     * Fetches the size of the media represented by the given URI, assuming it is openable and
     * the ContentResolver is able to resolve it.
     *
     * @return the size of the media in bytes or {@link MediaUtils#MEDIA_SIZE_UNKNOWN}
     */
    public static long getMediaSize(@NonNull ContentResolver contentResolver, @Nullable Uri uri) {
        if(uri == null) return MEDIA_SIZE_UNKNOWN;
        long mediaSize;
        Cursor cursor;
        try {
            cursor = contentResolver.query(uri, null, null, null, null);
        } catch (SecurityException e) {
            return MEDIA_SIZE_UNKNOWN;
        }
        if (cursor != null) {
            int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
            cursor.moveToFirst();
            mediaSize = cursor.getLong(sizeIndex);
            cursor.close();
        } else {
            mediaSize = MEDIA_SIZE_UNKNOWN;
        }
        return mediaSize;
    }

    @Nullable
    public static Bitmap getSampledBitmap(ContentResolver contentResolver, Uri uri, @Px int reqWidth, @Px int reqHeight) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        InputStream stream;
        try {
            stream = contentResolver.openInputStream(uri);
        } catch (FileNotFoundException e) {
            Log.w(TAG, e);
            return null;
        }

        BitmapFactory.decodeStream(stream, null, options);

        IOUtils.closeQuietly(stream);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        try {
            stream = contentResolver.openInputStream(uri);
            return BitmapFactory.decodeStream(stream, null, options);
        } catch (FileNotFoundException e) {
            Log.w(TAG, e);
            return null;
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "OutOfMemoryError while trying to get sampled Bitmap", e);
            return null;
        } finally {
            IOUtils.closeQuietly(stream);
        }

    }

    @Nullable
    public static Bitmap getImageThumbnail(ContentResolver contentResolver, Uri uri, @Px int thumbnailSize) {
        Bitmap source = getSampledBitmap(contentResolver, uri, thumbnailSize, thumbnailSize);
        if(source == null) {
            return null;
        }
        return ThumbnailUtils.extractThumbnail(source, thumbnailSize, thumbnailSize, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
    }

    @Nullable
    public static Bitmap getVideoThumbnail(Context context, Uri uri, @Px int thumbnailSize) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(context, uri);
        Bitmap source = retriever.getFrameAtTime();
        if (source == null) {
            return null;
        }
        return ThumbnailUtils.extractThumbnail(source, thumbnailSize, thumbnailSize, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
    }

    public static long getImageSquarePixels(ContentResolver contentResolver, Uri uri) throws FileNotFoundException {
        InputStream input = contentResolver.openInputStream(uri);

        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(input, null, options);

        IOUtils.closeQuietly(input);

        return (long) options.outWidth * options.outHeight;
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }
}
