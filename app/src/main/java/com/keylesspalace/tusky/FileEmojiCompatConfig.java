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
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.text.emoji.EmojiCompat;
import android.support.text.emoji.MetadataRepo;

import java.io.File;
import java.io.FileInputStream;
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
     * If loading the file failed, a fallback solution will be used.
     * In order to create minimum friction for the user, replacing all
     * emojis will be disabled.
     */
    private final boolean fallback;

    public FileEmojiCompatConfig(@NonNull Context context,
                                 // NEW
                                 @NonNull String path) {
        // This one is obviously new
        this(context, new File(path));
    }

    public FileEmojiCompatConfig(@NonNull Context context, File fontFile) {
        super(new FileMetadataLoader(context, fontFile));
        fallback = fontFile == null || !fontFile.exists();
    }

    /**
     * If set to true, EmojiCompat will replace every replacable emoji.
     * Anyhow it will be ignored if the fallback font is used.
     * @param replaceAll If you want to replace all emojis
     * @return This EmojiCompat.Config
     */
    @Override
    public EmojiCompat.Config setReplaceAll(boolean replaceAll) {
        return super.setReplaceAll(replaceAll && !fallback);
    }

    /**
     * This is the MetadataLoader. Derived from BundledMetadataLoader but with
     * the addition of a custom file name.
     */
    private static class FileMetadataLoader implements EmojiCompat.MetadataRepoLoader{
        private final Context mContext;
        // NEW
        private final File file;

        private FileMetadataLoader(@NonNull Context context,
                                    // NEW
                                    File fontFile) {
            // We'll need the context for the fallback solution
            this.mContext = context.getApplicationContext();
            // NEW
            this.file = fontFile;
        }


        // Copied from BundledEmojiCompatConfig
        @Override
        public void load(@NonNull EmojiCompat.MetadataRepoLoaderCallback loaderCallback) {
            final InitRunnable runnable = new InitRunnable(mContext, loaderCallback, file);
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
        private final Context context;

        private InitRunnable(final Context context,
                             final EmojiCompat.MetadataRepoLoaderCallback loaderCallback,
                             // NEW parameter
                             final File FONT_FILE) {
            // This has been changed a bit in order to get some consistency
            // we need the context as a fallback method
            this.context = context;
            this.loaderCallback = loaderCallback;
            this.FONT_FILE = FONT_FILE;
        }
        
        // This has been changed
        @Override
        public void run() {
            try {
                // We'll need the font...
                final Typeface typeface = Typeface.createFromFile(FONT_FILE);
                // As well as the font file's InputStream...
                final InputStream stream = new FileInputStream(FONT_FILE);
                // To create the repo...
                final MetadataRepo resourceIndex = MetadataRepo.create(typeface, stream);
                // ...used in EmojiCompat.
                loaderCallback.onLoaded(resourceIndex);
            }
            catch (Throwable t) {
                // We didn't find the proper font file (or something else went wrong) :/
                // OR the File was null all the time...
                t.printStackTrace();
                // So we'll simply use an asset based solution.
                AssetManager manager = context.getApplicationContext().getAssets();
                try {
                    // Luckily there is a minimal EmojiCompat font available...
                    final MetadataRepo repo = MetadataRepo.create(manager, "NoEmojiCompat.ttf");
                    // Which we can use instead.
                    loaderCallback.onLoaded(repo);
                    /*
                        Don't worry, if you don't provide an EmojiCompat font, your emojis won't
                        change (or let's say, it's extremely unlikely).
                        In order to do so, replacing all emojis is forbidden.
                     */
                } catch (Throwable fail) {
                    // Well, loading an asset based font didn't work either.
                    // I'm giving up.
                    loaderCallback.onFailed(fail);
                }
            }
        }
    }
}
