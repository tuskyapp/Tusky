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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reduces the file size of images to fit under a given limit by resizing them, maintaining both
 * aspect ratio and orientation.
 */
public class DownsizeImageTask extends AsyncTask<Uri, Void, Boolean> {
    private int sizeLimit;
    private ContentResolver contentResolver;
    private Listener listener;
    private List<byte[]> resultList;

    /**
     * @param sizeLimit the maximum number of bytes each image can take
     * @param contentResolver to resolve the specified images' URIs
     * @param listener to whom the results are given
     */
    public DownsizeImageTask(int sizeLimit, ContentResolver contentResolver, Listener listener) {
        this.sizeLimit = sizeLimit;
        this.contentResolver = contentResolver;
        this.listener = listener;
    }

    @Override
    protected Boolean doInBackground(Uri... uris) {
        resultList = new ArrayList<>();
        for (Uri uri : uris) {
            InputStream inputStream;
            try {
                inputStream = contentResolver.openInputStream(uri);
            } catch (FileNotFoundException e) {
                return false;
            }
            // Initially, just get the image dimensions.
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(inputStream, null, options);
            IOUtils.closeQuietly(inputStream);
            // Get EXIF data, for orientation info.
            int orientation = MediaUtils.getImageOrientation(uri, contentResolver);
            // Then use that information to determine how much to compress.
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            /* Unfortunately, there isn't a determined worst case compression ratio for image
             * formats. So, the only way to tell if they're too big is to compress them and
             * test, and keep trying at smaller sizes. The initial estimate should be good for
             * many cases, so it should only iterate once, but the loop is used to be absolutely
             * sure it gets downsized to below the limit. */
            int scaledImageSize = 1024;
            do {
                stream.reset();
                try {
                    inputStream = contentResolver.openInputStream(uri);
                } catch (FileNotFoundException e) {
                    return false;
                }
                options.inSampleSize = MediaUtils.calculateInSampleSize(options, scaledImageSize, scaledImageSize);
                options.inJustDecodeBounds = false;
                Bitmap scaledBitmap;
                try {
                    scaledBitmap = BitmapFactory.decodeStream(inputStream, null, options);
                } catch (OutOfMemoryError error) {
                    return false;
                } finally {
                    IOUtils.closeQuietly(inputStream);
                }
                if (scaledBitmap == null) {
                    return false;
                }
                Bitmap reorientedBitmap = MediaUtils.reorientBitmap(scaledBitmap, orientation);
                if (reorientedBitmap == null) {
                    scaledBitmap.recycle();
                    return false;
                }
                Bitmap.CompressFormat format;
                /* It's not likely the user will give transparent images over the upload limit, but
                 * if they do, make sure the transparency is retained. */
                if (!reorientedBitmap.hasAlpha()) {
                    format = Bitmap.CompressFormat.JPEG;
                } else {
                    format = Bitmap.CompressFormat.PNG;
                }
                reorientedBitmap.compress(format, 85, stream);
                reorientedBitmap.recycle();
                scaledImageSize /= 2;
            } while (stream.size() > sizeLimit);

            resultList.add(stream.toByteArray());
            if (isCancelled()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onPostExecute(Boolean successful) {
        if (successful) {
            listener.onSuccess(resultList);
        } else {
            listener.onFailure();
        }
        super.onPostExecute(successful);
    }

    /** Used to communicate the results of the task. */
    public interface Listener {
        void onSuccess(List<byte[]> contentList);
        void onFailure();
    }
}
