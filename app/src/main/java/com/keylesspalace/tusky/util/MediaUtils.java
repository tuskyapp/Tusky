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
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
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
    public static long getMediaSize(ContentResolver contentResolver, Uri uri) {
        long mediaSize;
        Cursor cursor = contentResolver.query(uri, null, null, null, null);
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

    /** Download an image with picasso asynchronously and call the given listener when completed. */
    public static Target picassoImageTarget(final Context context, final MediaListener mediaListener) {
        final String imageName = "temp";
        return new Target() {
            @Override
            public void onBitmapLoaded(final Bitmap bitmap, Picasso.LoadedFrom from) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        FileOutputStream fos = null;
                        Uri uriForFile;
                        try {
                            // we download only a "temp" file
                            File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                            File tempFile = File.createTempFile(
                                    imageName,
                                    ".jpg",
                                    storageDir
                            );

                            fos = new FileOutputStream(tempFile);
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                            uriForFile = FileProvider.getUriForFile(context,
                                    "com.keylesspalace.tusky.fileprovider",
                                    tempFile);

                            // giving to the activity the URI callback
                            mediaListener.onCallback(uriForFile);
                        } catch (IOException e) {
                            e.printStackTrace();
                            mediaListener.onError();
                        } finally {
                            try {
                                if (fos != null) {
                                    fos.close();
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }).start();
            }

            @Override
            public void onBitmapFailed(Drawable errorDrawable) {
                mediaListener.onError();
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
            }
        };
    }

    public interface MediaListener {
        void onCallback(Uri headerInfo);
        void onError();
    }
}
