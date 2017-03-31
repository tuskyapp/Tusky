/* Copyright 2017 Andrew Dawson
 *
 * This file is part of Tusky.
 *
 * Tusky is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with Tusky. If
 * not, see <http://www.gnu.org/licenses/>. */

package com.keylesspalace.tusky;

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

class DownsizeImageTask extends AsyncTask<Uri, Void, Boolean> {
    private int sizeLimit;
    private ContentResolver contentResolver;
    private Listener listener;
    private List<byte[]> resultList;

    DownsizeImageTask(int sizeLimit, ContentResolver contentResolver, Listener listener) {
        this.sizeLimit = sizeLimit;
        this.contentResolver = contentResolver;
        this.listener = listener;
    }

    private static int calculateInSampleSize(int width, int height, int requiredScale) {
        int inSampleSize = 1;
        if (height > requiredScale || width > requiredScale) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= requiredScale
                    && halfWidth / inSampleSize >= requiredScale) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
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
            int beforeWidth = options.outWidth;
            int beforeHeight = options.outHeight;
            IOUtils.closeQuietly(inputStream);
            // Then use that information to determine how much to compress.
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            /* Unfortunately, there isn't a determined worst case compression ratio for image
             * formats. So, the only way to tell if they're too big is to compress them and
             * test, and keep trying at smaller sizes. The initial estimate should be good for
             * many cases, so it should only iterate once, but the loop is used to be absolutely
             * sure it gets downsized to below the limit. */
            int iterations = 0;
            int scaledImageSize = 4096;
            do {
                stream.reset();
                try {
                    inputStream = contentResolver.openInputStream(uri);
                } catch (FileNotFoundException e) {
                    return false;
                }
                options.inSampleSize = calculateInSampleSize(beforeWidth, beforeHeight,
                        scaledImageSize);
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
                Bitmap.CompressFormat format;
                /* It's not likely the user will give transparent images over the upload limit, but
                 * if they do, make sure the transparency is retained. */
                if (!scaledBitmap.hasAlpha()) {
                    format = Bitmap.CompressFormat.JPEG;
                } else {
                    format = Bitmap.CompressFormat.PNG;
                }
                scaledBitmap.compress(format, 75, stream);
                scaledBitmap.recycle();
                scaledImageSize /= 2;
                iterations++;
            } while (stream.size() > sizeLimit);
            Assert.expect(iterations < 3);
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

    interface Listener {
        void onSuccess(List<byte[]> contentList);
        void onFailure();
    }
}
