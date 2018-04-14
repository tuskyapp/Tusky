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

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Class who will have all the code link with Media
 * <p>
 * Motivation : try to keep the ComposeActivity "smaller" and make modular method
 */
public class MediaUtils {
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
     * @return the size of the media or {@link MediaUtils#MEDIA_SIZE_UNKNOWN}
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
    public static Bitmap getImageThumbnail(ContentResolver contentResolver, Uri uri,
                                            @Px int thumbnailSize) {
        InputStream stream;
        try {
            stream = contentResolver.openInputStream(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        Bitmap source = BitmapFactory.decodeStream(stream);
        if (source == null) {
            IOUtils.closeQuietly(stream);
            return null;
        }
        Bitmap bitmap = ThumbnailUtils.extractThumbnail(source, thumbnailSize, thumbnailSize);
        source.recycle();
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (IOException e) {
            bitmap.recycle();
            return null;
        }
        return bitmap;
    }

    @Nullable
    public static Bitmap getVideoThumbnail(Context context, Uri uri, @Px int thumbnailSize) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(context, uri);
        Bitmap source = retriever.getFrameAtTime();
        if (source == null) {
            return null;
        }
        Bitmap bitmap = ThumbnailUtils.extractThumbnail(source, thumbnailSize, thumbnailSize);
        source.recycle();
        return bitmap;
    }
}
