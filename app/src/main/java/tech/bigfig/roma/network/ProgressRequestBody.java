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

package tech.bigfig.roma.network;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;

public final class ProgressRequestBody extends RequestBody {
    private final InputStream content;
    private final long contentLength;
    private final UploadCallback uploadListener;
    private final MediaType mediaType;

    private static final int DEFAULT_BUFFER_SIZE = 2048;

    public interface UploadCallback {
        void onProgressUpdate(int percentage);
    }

    public ProgressRequestBody(final InputStream content, long contentLength, final MediaType mediaType, final UploadCallback listener) {
        this.content = content;
        this.contentLength = contentLength;
        this.mediaType = mediaType;
        this.uploadListener = listener;
    }

    @Override
    public MediaType contentType() {
        return mediaType;
    }

    @Override
    public long contentLength() {
        return contentLength;
    }

    @Override
    public void writeTo(@NonNull BufferedSink sink) throws IOException {

        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        long uploaded = 0;

        try {
            int read;
            while ((read = content.read(buffer)) != -1) {
                uploadListener.onProgressUpdate((int)(100 * uploaded / contentLength));

                uploaded += read;
                sink.write(buffer, 0, read);
            }
        } finally {
            content.close();
        }
    }
}