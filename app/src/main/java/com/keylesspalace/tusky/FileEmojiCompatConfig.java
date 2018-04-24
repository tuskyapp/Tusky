package com.keylesspalace.tusky;
/*
 * Original file (https://android.googlesource.com/platform/frameworks/support/+/master/emoji/bundled/src/main/java/android/support/text/emoji/bundled/BundledEmojiCompatConfig.java):
 *     Copyright (C) 2017 The Android Open Source Project
 * Modifications Copyright (C) 2018 Constantin A.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.text.emoji.EmojiCompat;
import android.support.text.emoji.MetadataRepo;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * A simple implementation of EmojiCompat.Config using typeface files.
 * Based on:
 * https://android.googlesource.com/platform/frameworks/support/+/master/emoji/bundled/src/main/java/android/support/text/emoji/bundled/BundledEmojiCompatConfig.java
 * Changes are marked with comments. Formatting and other simple changes are not always marked.
 */
public class FileEmojiCompatConfig extends EmojiCompat.Config {
    // The class name is obviously changed from the original file

    /**
     * Create a new configuration for this EmojiCompat
     * @param path The file name/path of the requested font
     */
    public FileEmojiCompatConfig(// @NonNull Context context,
                                 // NEW
                                 @NonNull String path) {
        // This one is obviously new
        super(new FileMetadataLoader(new File(path)));
    }

    public FileEmojiCompatConfig(@NonNull File fontFile) {
        super(new FileMetadataLoader(fontFile));
    }

    /**
     * This is the MetadataLoader. Derived from BundledMetadataLoader but with
     * the addition of a custom file name.
     */
    private static class FileMetadataLoader implements EmojiCompat.MetadataRepoLoader{
        // private final Context mContext;
        // NEW
        private final File file;

        private FileMetadataLoader(// @NonNull Context context,
                                    // NEW
                                    @NonNull File fontFile) {
            // no need for Context
            // this.mContext = context.getApplicationContext();
            // NEW
            this.file = fontFile;
        }


        // Copied from BundledEmojiCompatConfig
        @Override
        @RequiresApi(19)
        public void load(@NonNull EmojiCompat.MetadataRepoLoaderCallback loaderCallback) {
            //Preconditions.checkNotNull(loaderCallback, "loaderCallback cannot be null");
            // Removed Context as parameter
            final InitRunnable runnable = new InitRunnable(loaderCallback, file);
            final Thread thread = new Thread(runnable);
            thread.setDaemon(false);
            thread.start();
        }
    }

    @RequiresApi(19)
    private static class InitRunnable implements Runnable {
        // The font file is assigned in the constructor.
        // This has been changed to be a file
        private final File FONT_FILE;
        // private final String FONT_NAME;
        // Slightly different variable names
        private final EmojiCompat.MetadataRepoLoaderCallback loaderCallback;
        // private final Context context;

        private InitRunnable(// final Context context,
                             final EmojiCompat.MetadataRepoLoaderCallback loaderCallback,
                             // NEW parameter
                             final File FONT_FILE) {
            // This has been changed a bit in order to get some consistency
            // we don't need the context
            // This.context = context;
            this.loaderCallback = loaderCallback;
            this.FONT_FILE = FONT_FILE;
        }
        
        // This has been copied from BundledEmojiCompatConfig
        @Override
        public void run() {
            try {
                final Typeface typeface = Typeface.createFromFile(FONT_FILE);
                final InputStream stream = new FileInputStream(FONT_FILE);
                final MetadataRepo resourceIndex = MetadataRepo.create(typeface, stream);
                loaderCallback.onLoaded(resourceIndex);
            } catch (Throwable t) {
                Log.e("FUCK", "run: ERROR", t);
                loaderCallback.onFailed(t);
            }
        }
    }
}
