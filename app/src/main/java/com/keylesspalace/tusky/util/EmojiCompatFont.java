package com.keylesspalace.tusky.util;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.Gson;
import com.keylesspalace.tusky.FileEmojiCompatConfig;
import com.keylesspalace.tusky.R;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

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
    // These are the items which are also present in the JSON files
    private final String name, display, subtitle, img, url, src;
    private AsyncTask fontDownloader;
    /**
     * This is a special font as it's basically no EmojiCompat font.
     * Whenever the "system font" is used, another font called 'NoEmojiCompat.ttf' is used which
     * is available in the assets folder.
     */
    public static final EmojiCompatFont SYSTEM_DEFAULT =
            new EmojiCompatFont("system-default",
                    "System Default",
                    "",
                    "",
                    "",
                    "");
    // The directory where the necessary files are/will be stored.
    // This is usually the [internal storage]/Android/com.keylessplace.tusky/files/emoji folder
    private File baseDirectory;

    /**
     * Create a new font
     * @param name The unique name of this font. It will also be used for the files created
     * @param display The name which is displayed in the emoji style picker.
     * @param subtitle A caption which is also shown.
     * @param img The URL of a thumbnail image
     * @param url The URL of the TTF file
     * @param src The URL of the GitHub repo of the font's project
     */
    public EmojiCompatFont(String name,
                           String display,
                           String subtitle,
                           String img,
                           String url,
                           String src) {
        this.name = name;
        this.display = display;
        this.subtitle = subtitle;
        this.img = img;
        this.url = url;
        this.src = src;
    }

    public EmojiCompatFont() {
        this("", "", "", "", "", "");
    }


    /**
     * Create a new font
     * @param name The unique name of this font. It will also be used for the files created
     * @param display The name which is displayed in the emoji style picker.
     * @param subtitle A caption which is also shown.
     * @param img The URL of a thumbnail image
     * @param src The URL of the GitHub repo of the font's project
     * @param baseDirectory The directory containing this font
     */
    public EmojiCompatFont(String name,
                           String display,
                           String subtitle,
                           String img,
                           String url,
                           String src,
                           File baseDirectory) {
       this(name, display, subtitle, img, url, src);
       this.baseDirectory = baseDirectory;
    }


    /**
     * This method creates a new EmojiCompatFont by parsing a JSON file.
     * JSON is used to persistently store an EmojiCompat font.
     * A baseDirectory has still to be added though.
     * @param FontDefinition the JSON String
     * @return the newly created font (or the null-font if "" was passed)
     */
    public static EmojiCompatFont parseFont(String FontDefinition) {
        // If nothing is passed, return the system default
        if(FontDefinition.equals("")) {
            return EmojiCompatFont.SYSTEM_DEFAULT;
        }
        else {
            // Parse using Gson
            Gson gson = new Gson();
            return gson.fromJson(FontDefinition, EmojiCompatFont.class);
        }
    }

    public String getName() {
        return name;
    }

    public String getDisplay() {
        return display;
    }

    public String getDisplay(Context context) {
        return this != SYSTEM_DEFAULT ? display : context.getString(R.string.system_default);
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getImg() {
        return img;
    }

    public String getUrl() {
        return url;
    }

    public String getSrc() {
        return src;
    }

    public void setBaseDirectory(File baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    // TODO: Add the option to use other thumbnail formats than PNG
    /**
     * This method will return the thumbnail's file (regardless of its existence).
     * If you try to call it on SYSTEM_DEFAULT though, you'll get a FileNotFoundException.
     * @return The thumbnail file of this font.
     * @throws FileNotFoundException If this is the default font, you'll get a FileNotFoundException
     */
    public File getThumb() throws FileNotFoundException{
        if(this != SYSTEM_DEFAULT) {
            // Return the thumbnail file as a File.
            return new File(baseDirectory, this.getName() + ".png");
        }
        else {
            // TODO: There might be a better solution. Maybe implementing an own Exception?
            throw new FileNotFoundException("The system default font needs special behavior");
        }
    }

    /**
     * This method will return the actual font file (regardless of its existence).
     * If you try to call it on SYSTEM_DEFAULT though, you'll get a FileNotFoundException.
     * @return The font (TTF) file.
     * @throws FileNotFoundException If this is the default font, you'll get a FileNotFoundException
     */
    public File getFont() throws FileNotFoundException {
        if(this != SYSTEM_DEFAULT) {
            return new File(baseDirectory, this.getName() + ".ttf");
        }
        else {
            throw new FileNotFoundException("The system default font needs special behavior");
        }
    }

    /**
     * This method will create a new FileEmojiCompatConfig for you - if it is available.
     * Otherwise it will just use the default font.
     * @param context The App context is needed to provide a fallback if anything goes wrong.
     * @return a new FileEmojiCompat ready to initialize EmojiCompat
     */
    public FileEmojiCompatConfig getConfig(Context context) {
        try {
            return new FileEmojiCompatConfig(context, getFont());
        } catch (FileNotFoundException e) {
            /*
             If you choose the system's default font, this will simply cause an Exception
             inside FileEmojiCompatConfig to use the system font.
             This is a.... let's say a strange solution but it's simple and it works!
            */
            return new FileEmojiCompatConfig(context, (File) null);
        }
    }

    /**
     * Shows if the font file has been downloaded already
     * @return If the font file (TTF) has been already downloaded.
     *      Will return true for SYSTEM_DEFAULT
     */
    public boolean isDownloaded() {
        try {
            // Test if the file itself exists.
            return getFont().exists();
        }
        // Since getFont throws a FileNotFoundError...
        catch (FileNotFoundException ex) {
            // ...this means the system standard font is selected
            return true;
        }
    }

    /**
     * Downloads the thumbnail for this font
     * @param listeners The listeners which will be notified when the download has been finished
     * @throws FileNotFoundException This is thrown when attempting to download for SYSTEM_DEFAULT
     */
    public void downloadThumb(Downloader.EmojiDownloadListener... listeners) throws FileNotFoundException {
        new Downloader(this, getImg(), "image/png", listeners).execute(getThumb());
    }

    /**
     * Downloads the TTF file for this font
     * @param listeners The listeners which will be notified when the download has been finished
     * @throws FileNotFoundException This is thrown when attempting to download for SYSTEM_DEFAULT
     */
    public void downloadFont(Downloader.EmojiDownloadListener... listeners) throws FileNotFoundException {
        fontDownloader = new Downloader(
                this,
                getUrl(),
                "application/woff",
                listeners)
                .execute(getFont());
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
     * This class is used to easily manage file downloads for a font.
     */
    public static class Downloader extends AsyncTask<File, Void, File> {
        // All interested objects/methods
        private final EmojiDownloadListener[] listeners;
        // The URL of the source file
        private final String url;
        // The MIME-Type which might be unnecessary
        private final String mimetype;
        // The font belonging to this download
        private final EmojiCompatFont font;
        private static final String TAG = "Emoji-Font Downloader";
        private static long BUFFER_SIZE = 2048;

        Downloader(EmojiCompatFont font, String url, String mimetype, EmojiDownloadListener... listeners) {
            super();
            this.listeners = listeners;
            this.url = url;
            this.mimetype = mimetype;
            this.font = font;
        }

        @Override
        protected File doInBackground(File... files){
            // Only download to one file...
            File downloadFile = files[0];
            try {
                // It is possible (and very likely) that the file does not exist yet
                if (!downloadFile.exists()) {
                    downloadFile.createNewFile();
                }
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(url)
                        .addHeader("Content-Type", mimetype)
                        .build();
                Response response = client.newCall(request).execute();
                BufferedSink sink = Okio.buffer(Okio.sink(downloadFile));
                Source source = null;
                try {
                    // Download!
                    if (response.body() != null) {
                        source = response.body().source();
                        try {
                            while (!isCancelled()) {
                                    sink.write(response.body().source(), BUFFER_SIZE);
                            }
                        } catch (EOFException ex) {
                                /*
                                 This means we've finished downloading the file since sink.write
                                 will throw an EOFException when the file to be read is empty.
                                */
                        }
                    } else {
                        Log.e(TAG, "downloading " + url + " failed. No content to download.");
                    }
                }
                finally {
                    if(source != null) {
                        source.close();
                    }
                    sink.close();
                    // This if uses side effects to delete the File.
                    if(isCancelled() && !downloadFile.delete()) {
                        Log.e(TAG, "Could not delete file " + downloadFile + ".");
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            return downloadFile;
        }

        @Override
        public void onPostExecute(File downloadedFile) {
            // So we've finished the download. We'll notify our listeners
            if(downloadedFile.exists()) {
                // But only if we actually managed to download the files
                // TODO: Notify when downloading the file failed
                for (EmojiDownloadListener listener : listeners) {
                    listener.onDownloaded(font);
                }
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
        }
    }

    /**
     * Two EmojiCompatFonts are declared equal if they have the same (unique) name.
     * @param other The other EmojiCompatFont
     * @return true, if both have the same name
     */
    @Override
    public boolean equals(Object other) {
        return other.getClass() == this.getClass()
                && ((EmojiCompatFont) other).getName().equals(this.getName());
    }
}
