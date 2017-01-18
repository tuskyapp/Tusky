package com.keylesspalace.tusky;

import android.support.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;

public class IOUtils {
    public static void closeQuietly(@Nullable InputStream stream) {
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (IOException e) {
            // intentionally unhandled
        }
    }
}
