package com.keylesspalace.tusky.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.Nullable;

import com.keylesspalace.tusky.R;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.c1710.filemojicompat.FileEmojiCompatConfig;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;


/**
 * This class bundles information about an emoji font as well as many convenient actions.
 */
public class EmojiCompatFont {
    private static final String TAG = "EmojiCompatFont";
    /**
     * This String represents the sub-directory the fonts are stored in.
     */
    private static final String DIRECTORY = "emoji";

    // These are the items which are also present in the JSON files
    private final String name, display, url;
    // The thumbnail image and the caption are provided as resource ids
    private final int img, caption;
    // The version is stored as a String in the x.xx.xx format (to be able to compare versions)
    private final String version;
    private AsyncTask fontDownloader;
    // The system font gets some special behavior...
    private static final EmojiCompatFont SYSTEM_DEFAULT =
            new EmojiCompatFont("system-default",
                    "System Default",
                    R.string.caption_systememoji,
                    R.drawable.ic_emoji_34dp,
                    "",
                    "0");
    private static final EmojiCompatFont BLOBMOJI =
            new EmojiCompatFont("Blobmoji",
                    "Blobmoji",
                    R.string.caption_blobmoji,
                    R.drawable.ic_blobmoji,
                    "https://tusky.app/hosted/emoji/BlobmojiCompat.ttf",
                    "12.0.0"
            );
    private static final EmojiCompatFont TWEMOJI =
            new EmojiCompatFont("Twemoji",
                    "Twemoji",
                    R.string.caption_twemoji,
                    R.drawable.ic_twemoji,
                    "https://tusky.app/hosted/emoji/TwemojiCompat.ttf",
                    "12.0.0"
            );
    private static final EmojiCompatFont NOTOEMOJI =
            new EmojiCompatFont("NotoEmoji",
                    "Noto Emoji",
                    R.string.caption_notoemoji,
                    R.drawable.ic_notoemoji,
                    "https://tusky.app/hosted/emoji/NotoEmojiCompat.ttf",
                    "12.0.0"
                    );

    /**
     * This array stores all available EmojiCompat fonts.
     * References to them can simply be saved by saving their indices
     */
    public static final EmojiCompatFont[] FONTS = {SYSTEM_DEFAULT, BLOBMOJI, TWEMOJI, NOTOEMOJI};

    private EmojiCompatFont(String name,
                            String display,
                            int caption,
                            int img,
                            String url,
                            String version) {
        this.name = name;
        this.display = display;
        this.caption = caption;
        this.img = img;
        this.url = url;
        this.version = version;
    }

    /**
     * Returns the Emoji font associated with this ID
     * @param id the ID of this font
     * @return the corresponding font. Will default to SYSTEM_DEFAULT if not in range.
     */
    public static EmojiCompatFont byId(int id) {
        if(id >= 0 && id < FONTS.length) {
            return FONTS[id];
        }
        else {
            return SYSTEM_DEFAULT;
        }
    }

    public int getId() {
        return Arrays.asList(FONTS).indexOf(this);
    }

    public String getName() {
        return name;
    }


    public String getDisplay(Context context) {
        return this != SYSTEM_DEFAULT ? display : context.getString(R.string.system_default);
    }

    public String getCaption(Context context) {
        return context.getResources().getString(caption);
    }

    public String getUrl() {
        return url;
    }

    public Drawable getThumb(Context context) {
        return context.getResources().getDrawable(img);
    }

    public String getVersion() {
        return version;
    }

    /**
     * This method will return the actual font file (regardless of its existence).
     * @return The font (TTF) file or null if called on SYSTEM_FONT
     */
    @Nullable
    private File getFont(Context context) {
        if(this != SYSTEM_DEFAULT) {
            File directory = new File(context.getExternalFilesDir(null), DIRECTORY);
            return new File(directory, this.getName() + this.getVersion() + ".ttf");
        }
        else {
            return null;
        }
    }


    public FileEmojiCompatConfig getConfig(Context context) {
        return new FileEmojiCompatConfig(context, getFont(context));
    }

    public boolean isDownloaded(Context context) {
        // The existence of the current version is actually checked twice, although the first method should
        // be much faster and more common.
        return this == SYSTEM_DEFAULT || getFont(context) != null
                && (getFont(context).exists() || newerFileExists(context));
    }

    /**
     * Checks whether there is already a font version that satisfies the current version, i.e. it
     * has a higher or equal version code.
     * @param context The Context
     * @return Whether there is a font file with a higher or equal version code to the current
     */
    private boolean newerFileExists(Context context) {
        File directory = new File(context.getExternalFilesDir(null), DIRECTORY);
        // It will search for old versions using a regex that matches the font's name plus
        // (if present) a version code. No version code will be regarded as version 0.
        Pattern fontRegex = Pattern.compile(getName() + "(\\d+(.\\d+)*)?" + ".ttf");

        for(File file: directory.listFiles()) {
            Matcher matcher = fontRegex.matcher(file.getName());
            if(matcher.matches()) {
                String version = matcher.group(1);
                if(!isOlderVersion(version))
                    return true;
            }
        }
        return false;
    }

    /**
     * Downloads the TTF file for this font
     * @param listeners The listeners which will be notified when the download has been finished
     */
    public void downloadFont(Context context, Downloader.EmojiDownloadListener... listeners) {
        if(this != SYSTEM_DEFAULT) {
            // Additionally run a cleanup process after the download has been successful.
            Downloader.EmojiDownloadListener cleanup = font -> deleteOldVersions(context);
            List<Downloader.EmojiDownloadListener> allListeners = Arrays.asList(listeners);
            allListeners.add(cleanup);
            Downloader.EmojiDownloadListener[] allListenersA =
                    new Downloader.EmojiDownloadListener[allListeners.size()];

            fontDownloader = new Downloader(
                    this,
                    allListeners.toArray(allListenersA))
                    .execute(getFont(context));
        }
        else {
            for(Downloader.EmojiDownloadListener listener: listeners) {
                // The system emoji font is always downloaded...
                listener.onDownloaded(this);
            }
        }
    }

    /**
     * Deletes any older version of a font
     * @param context The current Context
     */
    private void deleteOldVersions(Context context) {
        File directory = new File(context.getExternalFilesDir(null), DIRECTORY);
        // It will search for old versions using a regex that matches the font's name plus
        // (if present) a version code. No version code will be regarded as version 0.
        Pattern fontRegex = Pattern.compile(getName() + "(\\d+(.\\d+)*)?" + ".ttf");

        for(File file: directory.listFiles()) {
            Matcher matcher = fontRegex.matcher(file.getName());
            if(matcher.matches()) {
                String version = matcher.group(1);
                if(isOlderVersion(version)) {
                    // Uses side effects!
                    Log.d(TAG, String.format("Deleted %s successfully: %s", file.getAbsolutePath(),
                            file.delete()));
                }
            }
        }
    }

    /**
     * Checks whether a given version code is older than the current one
     * @param comparedVersion The other version code as a String. Null means version 0
     * @return whether it is an older version or not (which means that it is at least as new as the current one)
     */
    private boolean isOlderVersion(@Nullable String comparedVersion) {
        if(comparedVersion == null)
            return true;
        // The most common case: both versions are exactly equal to each other
        if(comparedVersion.equals(this.getVersion()))
            return false;
        String[] currentVersionCode = this.getVersion().split("\\.");
        String[] otherVersionCode = comparedVersion.split("\\.");
        List<Integer> currentVersionCodeI = new ArrayList<>(currentVersionCode.length);
        List<Integer> otherVersionCodeI = new ArrayList<>(otherVersionCode.length);
        // TL;DR: a map-function converting everything to int.
        for(String subVersion: currentVersionCode) {
            try {
                currentVersionCodeI.add(Integer.parseInt(subVersion));
            } catch (NumberFormatException e) {
                e.printStackTrace();
                currentVersionCodeI.add(0);
            }
        }
        for(String subVersion: otherVersionCode) {
            try {
                otherVersionCodeI.add(Integer.parseInt(subVersion));
            } catch (NumberFormatException e) {
                e.printStackTrace();
                currentVersionCodeI.add(0);
            }
        }

        for (int i = 0; i < Math.max(currentVersionCodeI.size(), otherVersionCodeI.size()); i++) {
            if(currentVersionCodeI.size() < i) {
                for(int subVersion: otherVersionCodeI.subList(i, otherVersionCodeI.size()-1))
                    if(subVersion > 0)
                        return false;
                return true;
            }
            if(otherVersionCodeI.size() < i) {
                for(int subVersion: currentVersionCodeI.subList(i, currentVersionCodeI.size()-1))
                    if(subVersion > 0)
                        return true;
                // If only 0 follows, they are both on the same version
                return false;
            }
            int currentVersion = currentVersionCodeI.get(i);
            int otherVersion = otherVersionCodeI.get(i);
            if(currentVersion > otherVersion)
                return true;
            if(otherVersion > currentVersion)
                return false;
        }
        // Both have an equal length and share the same numbers.
        // However this should have been covered by the second condition...
        return false;
    }

    /**
     * Stops downloading the font. If no one started a font download, nothing happens.
     */
    public void cancelDownload() {
        if(fontDownloader != null) {
            fontDownloader.cancel(false);
            fontDownloader = null;
        }
    }

    /**
     * This class is used to easily manage the download of a font
     */
    public static class Downloader extends AsyncTask<File, Float, File> {
        // All interested objects/methods
        private final EmojiDownloadListener[] listeners;
        // The MIME-Type which might be unnecessary
        private static final String MIME = "application/woff";
        // The font belonging to this download
        private final EmojiCompatFont font;
        private static final String TAG = "Emoji-Font Downloader";
        private static long CHUNK_SIZE = 4096;
        private boolean failed = false;

        Downloader(EmojiCompatFont font, EmojiDownloadListener... listeners) {
            super();
            this.listeners = listeners;
            this.font = font;
        }

        @Override
        protected File doInBackground(File... files){
            // Only download to one file...
            File downloadFile = files[0];
            try {
                // It is possible (and very likely) that the file does not exist yet
                if (!downloadFile.exists()) {
                    downloadFile.getParentFile().mkdirs();
                    downloadFile.createNewFile();
                }
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(font.getUrl())
                        .addHeader("Content-Type", MIME)
                        .build();
                Response response = client.newCall(request).execute();
                BufferedSink sink = Okio.buffer(Okio.sink(downloadFile));
                Source source = null;
                try {
                    long size;
                    // Download!
                    if (response.body() != null
                            && response.isSuccessful()
                            && (size = response.body().contentLength()) > 0) {
                        float progress = 0;
                        source = response.body().source();
                        try {
                            while (!isCancelled()) {
                                sink.write(response.body().source(), CHUNK_SIZE);
                                progress += CHUNK_SIZE;
                                publishProgress(progress / size);
                            }
                        } catch (EOFException ex) {
                            /*
                             This means we've finished downloading the file since sink.write
                             will throw an EOFException when the file to be read is empty.
                            */
                        }
                    } else {
                        Log.e(TAG, "downloading " + font.getUrl() + " failed. No content to download.");
                        Log.e(TAG, "Status code: " + response.code());
                        failed = true;
                    }
                }
                finally {
                    if(source != null) {
                        source.close();
                    }
                    sink.close();
                    // This 'if' uses side effects to delete the File.
                    if(isCancelled() && !downloadFile.delete()) {
                        Log.e(TAG, "Could not delete file " + downloadFile);
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                failed = true;
            }
            return downloadFile;
        }

        @Override
        public void onProgressUpdate(Float... progress) {
            for(EmojiDownloadListener listener: listeners) {
                listener.onProgress(progress[0]);
            }
        }

        @Override
        public void onPostExecute(File downloadedFile) {
            if(!failed && downloadedFile.exists()) {
                for (EmojiDownloadListener listener : listeners) {
                    listener.onDownloaded(font);
                }
            }
            else {
                fail(downloadedFile);
            }
        }

        private void fail(File failedFile) {
            if(failedFile.exists() && !failedFile.delete()) {
                Log.e(TAG, "Could not delete file " + failedFile);
            }
            for(EmojiDownloadListener listener : listeners) {
                listener.onFailed();
            }
        }

        /**
         * This interfaced is used to get notified when a download has been finished
         */
        public interface EmojiDownloadListener {
            /**
             * Called after successfully finishing a download.
             * @param font The font related to this download. This will help identifying the download
             */
            void onDownloaded(EmojiCompatFont font);

            // TODO: Add functionality
            /**
             * Called when something went wrong with the download.
             * This one won't be called when the download has been cancelled though.
             */
            default void onFailed() {
                // Oh no! D:
            }

            /**
             * Called whenever the progress changed
             * @param Progress A value between 0 and 1 representing the current progress
             */
            default void onProgress(float Progress) {
                // ARE WE THERE YET?
            }
        }
    }

    @Override
    public String toString() {
        return display;
    }
}
