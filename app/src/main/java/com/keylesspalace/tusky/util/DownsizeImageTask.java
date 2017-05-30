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
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.support.media.ExifInterface;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class DownsizeImageTask extends AsyncTask<Uri, Void, Boolean> {
    private static final String TAG = "DownsizeImageTask";
    private int sizeLimit;
    private ContentResolver contentResolver;
    private Listener listener;
    private List<byte[]> resultList;

    public DownsizeImageTask(int sizeLimit, ContentResolver contentResolver, Listener listener) {
        this.sizeLimit = sizeLimit;
        this.contentResolver = contentResolver;
        this.listener = listener;
    }

    @Nullable
    private static Bitmap reorientBitmap(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            default:
            case ExifInterface.ORIENTATION_NORMAL: {
                return bitmap;
            }
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: {
                matrix.setScale(-1, 1);
                break;
            }
            case ExifInterface.ORIENTATION_ROTATE_180: {
                matrix.setRotate(180);
                break;
            }
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: {
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            }
            case ExifInterface.ORIENTATION_TRANSPOSE: {
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            }
            case ExifInterface.ORIENTATION_ROTATE_90: {
                matrix.setRotate(90);
                break;
            }
            case ExifInterface.ORIENTATION_TRANSVERSE: {
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            }
            case ExifInterface.ORIENTATION_ROTATE_270: {
                matrix.setRotate(-90);
                break;
            }
        }
        try {
            Bitmap result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                    bitmap.getHeight(), matrix, true);
            if (!bitmap.sameAs(result)) {
                bitmap.recycle();
            }
            return result;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private static int getOrientation(Uri uri, ContentResolver contentResolver) {
        InputStream inputStream;
        try {
            inputStream = contentResolver.openInputStream(uri);
        } catch (FileNotFoundException e) {
            Log.d(TAG, Log.getStackTraceString(e));
            return ExifInterface.ORIENTATION_UNDEFINED;
        }
        if (inputStream == null) {
            return ExifInterface.ORIENTATION_UNDEFINED;
        }
        ExifInterface exifInterface;
        try {
            exifInterface = new ExifInterface(inputStream);
        } catch (IOException e) {
            Log.d(TAG, Log.getStackTraceString(e));
            IOUtils.closeQuietly(inputStream);
            return ExifInterface.ORIENTATION_UNDEFINED;
        }
        int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL);
        IOUtils.closeQuietly(inputStream);
        return orientation;
    }

    private static int calculateInSampleSize(int width, int height, int requiredScale) {
        int inSampleSize = 1;
        if (height > requiredScale || width > requiredScale) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            /* Calculate the largest inSampleSize value that is a power of 2 and keeps both height
             * and width larger than the requested height and width. */
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
            // Get EXIF data, for orientation info.
            int orientation = getOrientation(uri, contentResolver);
            // Then use that information to determine how much to compress.
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            /* Unfortunately, there isn't a determined worst case compression ratio for image
             * formats. So, the only way to tell if they're too big is to compress them and
             * test, and keep trying at smaller sizes. The initial estimate should be good for
             * many cases, so it should only iterate once, but the loop is used to be absolutely
             * sure it gets downsized to below the limit. */
            int iterations = 0;
            int scaledImageSize = 1024;
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
                reorientedBitmap.compress(format, 75, stream);
                reorientedBitmap.recycle();
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

    public interface Listener {
        void onSuccess(List<byte[]> contentList);
        void onFailure();
    }
}
