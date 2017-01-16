package com.keylesspalace.tusky;

import android.graphics.Bitmap;
import android.os.AsyncTask;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class DownsizeImageTask extends AsyncTask<Bitmap, Void, Boolean> {
    private Listener listener;
    private int sizeLimit;
    private List<byte[]> resultList;

    public DownsizeImageTask(int sizeLimit, Listener listener) {
        this.listener = listener;
        this.sizeLimit = sizeLimit;
    }

    public static Bitmap scaleDown(Bitmap source, float maxImageSize, boolean filter) {
        float ratio = Math.min(maxImageSize / source.getWidth(), maxImageSize / source.getHeight());
        int width = Math.round(ratio * source.getWidth());
        int height = Math.round(ratio * source.getHeight());
        return Bitmap.createScaledBitmap(source, width, height, filter);
    }

    @Override
    protected Boolean doInBackground(Bitmap... bitmaps) {
        final int count = bitmaps.length;
        resultList = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
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
                Bitmap bitmap = scaleDown(bitmaps[i], scaledImageSize, true);
                Bitmap.CompressFormat format;
                /* It's not likely the user will give transparent images over the upload limit, but
                 * if they do, make sure the transparency is retained. */
                if (!bitmap.hasAlpha()) {
                    format = Bitmap.CompressFormat.JPEG;
                } else {
                    format = Bitmap.CompressFormat.PNG;
                }
                bitmap.compress(format, 75, stream);
                scaledImageSize /= 2;
                iterations++;
            } while (stream.size() > sizeLimit);
            assert(iterations < 3);
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
