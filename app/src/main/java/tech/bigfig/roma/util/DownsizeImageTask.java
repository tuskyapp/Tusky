/* Copyright 2017 Andrew Dawson
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

package tech.bigfig.roma.util;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import static tech.bigfig.roma.util.MediaUtilsKt.calculateInSampleSize;
import static tech.bigfig.roma.util.MediaUtilsKt.getImageOrientation;
import static tech.bigfig.roma.util.MediaUtilsKt.reorientBitmap;

/**
 * Reduces the file size of images to fit under a given limit by resizing them, maintaining both
 * aspect ratio and orientation.
 */
public class DownsizeImageTask extends AsyncTask<Uri, Void, Boolean> {
    private int sizeLimit;
    private ContentResolver contentResolver;
    private Listener listener;
    private File tempFile;

    /**
     * @param sizeLimit the maximum number of bytes each image can take
     * @param contentResolver to resolve the specified images' URIs
     * @param tempFile the file where the result will be stored
     * @param listener to whom the results are given
     */
    public DownsizeImageTask(int sizeLimit, ContentResolver contentResolver, File tempFile, Listener listener) {
        this.sizeLimit = sizeLimit;
        this.contentResolver = contentResolver;
        this.tempFile = tempFile;
        this.listener = listener;
    }

    @Override
    protected Boolean doInBackground(Uri... uris) {
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
            int orientation = getImageOrientation(uri, contentResolver);
            /* Unfortunately, there isn't a determined worst case compression ratio for image
             * formats. So, the only way to tell if they're too big is to compress them and
             * test, and keep trying at smaller sizes. The initial estimate should be good for
             * many cases, so it should only iterate once, but the loop is used to be absolutely
             * sure it gets downsized to below the limit. */
            int scaledImageSize = 1024;
            do {
                OutputStream stream;
                try {
                    stream = new FileOutputStream(tempFile);
                } catch (FileNotFoundException e) {
                    return false;
                }
                try {
                    inputStream = contentResolver.openInputStream(uri);
                } catch (FileNotFoundException e) {
                    return false;
                }
                options.inSampleSize = calculateInSampleSize(options, scaledImageSize, scaledImageSize);
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
                Bitmap reorientedBitmap = reorientBitmap(scaledBitmap, orientation);
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
            } while (tempFile.length() > sizeLimit);

            if (isCancelled()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onPostExecute(Boolean successful) {
        if (successful) {
            listener.onSuccess(tempFile);
        } else {
            listener.onFailure();
        }
        super.onPostExecute(successful);
    }

    /** Used to communicate the results of the task. */
    public interface Listener {
        void onSuccess(File file);
        void onFailure();
    }
}
