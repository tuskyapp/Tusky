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

import android.graphics.Bitmap;
import android.os.AsyncTask;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

class DownsizeImageTask extends AsyncTask<Bitmap, Void, Boolean> {
    private Listener listener;
    private int sizeLimit;
    private List<byte[]> resultList;

    DownsizeImageTask(int sizeLimit, Listener listener) {
        this.listener = listener;
        this.sizeLimit = sizeLimit;
    }

    private static Bitmap scaleDown(Bitmap source, float maxImageSize, boolean filter) {
        float ratio = Math.min(maxImageSize / source.getWidth(), maxImageSize / source.getHeight());
        int width = Math.round(ratio * source.getWidth());
        int height = Math.round(ratio * source.getHeight());
        return Bitmap.createScaledBitmap(source, width, height, filter);
    }

    @Override
    protected Boolean doInBackground(Bitmap... bitmaps) {
        final int count = bitmaps.length;
        resultList = new ArrayList<>(count);
        for (Bitmap bitmap : bitmaps) {
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
                Bitmap scaledBitmap = scaleDown(bitmap, scaledImageSize, true);
                Bitmap.CompressFormat format;
                /* It's not likely the user will give transparent images over the upload limit, but
                 * if they do, make sure the transparency is retained. */
                if (!scaledBitmap.hasAlpha()) {
                    format = Bitmap.CompressFormat.JPEG;
                } else {
                    format = Bitmap.CompressFormat.PNG;
                }
                scaledBitmap.compress(format, 75, stream);
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
