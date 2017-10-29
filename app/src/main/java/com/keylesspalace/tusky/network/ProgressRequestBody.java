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

package com.keylesspalace.tusky.network;

import android.support.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;

public final class ProgressRequestBody extends RequestBody {
    private final byte[] content;
    private final UploadCallback mListener;
    private final MediaType mediaType;
    private boolean shouldIgnoreThisPass;

    private static final int DEFAULT_BUFFER_SIZE = 2048;

    public interface UploadCallback {
        void onProgressUpdate(int percentage);
    }

    public ProgressRequestBody(final byte[] content, final MediaType mediaType, boolean shouldIgnoreFirst, final UploadCallback listener) {
        this.content = content;
        this.mediaType = mediaType;
        mListener = listener;
        shouldIgnoreThisPass = shouldIgnoreFirst;
    }

    @Override
    public MediaType contentType() {
        return mediaType;
    }

    @Override
    public long contentLength() throws IOException {
        return content.length;
    }

    @Override
    public void writeTo(@NonNull BufferedSink sink) throws IOException {
        long length = content.length;

        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        ByteArrayInputStream in = new ByteArrayInputStream(content);
        long uploaded = 0;

        try {
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (!shouldIgnoreThisPass) {
                    mListener.onProgressUpdate((int)(100 * uploaded / length));
                }
                uploaded += read;
                sink.write(buffer, 0, read);
            }
        } finally {
            in.close();
        }
        shouldIgnoreThisPass = false;
    }
}