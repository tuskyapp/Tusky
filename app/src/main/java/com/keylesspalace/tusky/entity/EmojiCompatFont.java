package com.keylesspalace.tusky.entity;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.Gson;
import com.keylesspalace.tusky.FileEmojiCompatConfig;
import com.keylesspalace.tusky.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

public class EmojiCompatFont {
    private String name, display, subtitle, img, url, src;
    public static final EmojiCompatFont SYSTEM_DEFAULT =
            new EmojiCompatFont("system-default",
                    "System Default",
                    "",
                    "",
                    "");
    private File baseDirectory;

    public EmojiCompatFont(String name,
                           String display,
                           String subtitle,
                           String img,
                           String src) {
        this.name = name;
        this.display = display;
        this.subtitle = subtitle;
        this.img = img;
        this.src = src;
    }

    public EmojiCompatFont(String name,
                           String display,
                           String subtitle,
                           String img,
                           String src,
                           File baseDirectory) {
       this(name, display, subtitle, img, src);
       this.baseDirectory = baseDirectory;
    }

    public EmojiCompatFont() {
        this("","","","","");
    }

    public static EmojiCompatFont parseFont(String FontDefinition) {
        if(FontDefinition.equals("")) {
            return EmojiCompatFont.SYSTEM_DEFAULT;
        }
        else {
            Gson gson = new Gson();
            EmojiCompatFont font = gson.fromJson(FontDefinition, EmojiCompatFont.class);
            return font;
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
     *
     * @return The thumbnail file of this font.
     * @throws FileNotFoundException If this is the default font, you'll get a FileNotFoundException
     */
    public File getThumb() throws FileNotFoundException{
        if(this != SYSTEM_DEFAULT) {
            return new File(baseDirectory, this.getName() + ".png");
        }
        else {
            throw new FileNotFoundException("The system default font needs special behavior");
        }
    }

    public File getFont() throws FileNotFoundException {
        if(this != SYSTEM_DEFAULT) {
            return new File(baseDirectory, this.getName() + ".ttf");
        }
        else {
            throw new FileNotFoundException("The system default font needs special behavior");
        }
    }

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

    public boolean isDownloaded() {
        try {
            return getFont().exists();
        }
        catch (FileNotFoundException ex) {
            // This means the system standard font is selected
            return true;
        }
    }

    public void downloadThumb(Downloader.EmojiDownloadListener... listeners) throws FileNotFoundException {
        new Downloader(this, getImg(), "image/png", listeners).execute(getThumb());
    }

    public void downloadFont(Downloader.EmojiDownloadListener... listeners) throws FileNotFoundException {
        new Downloader(this, getUrl(), "application/woff", listeners).execute(getFont());
    }

    public static class Downloader extends AsyncTask<File, Void, File> {
        private final EmojiDownloadListener[] listeners;
        private final String url;
        private final String mimetype;
        private final EmojiCompatFont font;
        private static final String TAG = "Emoji-Font Downloader";

        Downloader(EmojiCompatFont font, String url, String mimetype, EmojiDownloadListener... listeners) {
            super();
            this.listeners = listeners;
            this.url = url;
            this.mimetype = mimetype;
            this.font = font;
        }

        @Override
        protected File doInBackground(File... files){
            File downloadFile = files[0];
            BufferedSink sink;
            try {
                if (!downloadFile.exists()) {
                    downloadFile.createNewFile();
                }
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(url)
                        .addHeader("Content-Type", mimetype)
                        .build();
                Response response = client.newCall(request).execute();
                sink = Okio.buffer(Okio.sink(downloadFile));
                try {
                    if (response.body() != null) {
                        sink.writeAll(response.body().source());
                    } else {
                        Log.e(TAG, "downloading " + url + " failed. No content to download.");
                    }
                }
                finally {
                    sink.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            return downloadFile;
        }

        @Override
        public void onPostExecute(File downloadedFile) {
            if(downloadedFile.exists()) {
                for (EmojiDownloadListener listener : listeners) {
                    listener.onDownloaded(font);
                }
            }
        }

        public interface EmojiDownloadListener {
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
