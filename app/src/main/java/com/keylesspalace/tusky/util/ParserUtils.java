package com.keylesspalace.tusky.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.URLUtil;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.lang.ref.WeakReference;
import java.util.List;

import static com.keylesspalace.tusky.util.StringUtils.QUOTE;

/**
 * Inspect and get the information from a URL.
 */
public final class ParserUtils {
    private static final String TAG = "ParserUtils";

    public static void getUrlInfo(String urlString, ParserListener listener) {
        if (!URLUtil.isValidUrl(urlString)) {
            Log.e(TAG,
                    "Inavlid URL passed to getUrlInfo: " + urlString);
        }
        new ThreadHeaderInfo(listener).execute(urlString);
    }


    public static @Nullable
    String getClipboardUrl(Context context) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            String pasteData = item.getText().toString();

            // If we share with an app, it's not only an url
            List<String> strings = StringUtils.extractUrl(pasteData);
            if (strings.size() > 0) {
                String url = strings.get(0); // we assume that the first url is the good one
                if (URLUtil.isValidUrl(url)) {
                    return url;
                }
            }
        }
        return null;
    }

    public static void putInClipboardManager(Context context, String string) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("", string);
        clipboard.setPrimaryClip(clip);
    }

    public interface ParserListener {
        void onReceiveHeaderInfo(HeaderInfo headerInfo);

        void onErrorHeaderInfo();
    }

    private ParserUtils() {
    }

    public static class HeaderInfo {
        public String baseUrl;
        public String title;
        public String image;
    }

    private static class ThreadHeaderInfo extends AsyncTask<String, Void, HeaderInfo> {

        private WeakReference<ParserListener> parserListener;

        ThreadHeaderInfo(ParserListener parserListener) {
            this.parserListener = new WeakReference<ParserListener>(parserListener);
        }

        protected HeaderInfo doInBackground(String... urls) {
            try {
                String url = urls[0];
                return parsePageHeaderInfo(url);
            } catch (Exception e) {
                Log.e(TAG, "ThreadHeaderInfo#parsePageHeaderInfo() failed." + e.getMessage());
                return null;
            }
        }

        protected void onPostExecute(HeaderInfo headerInfo) {
            ParserListener listener = parserListener.get();
            if (listener == null) return;
            if (headerInfo != null) {
                Log.i(TAG,
                        "ThreadHeaderInfo#parsePageHeaderInfo() success." + headerInfo.title +
                                " " + headerInfo.image);
                listener.onReceiveHeaderInfo(headerInfo);
            } else {
                listener.onErrorHeaderInfo();
            }
        }

        /**
         * parse the HTML page
         */
        private HeaderInfo parsePageHeaderInfo(String urlStr) throws Exception {
            Connection con = Jsoup.connect(urlStr);
            HeaderInfo headerInfo = new HeaderInfo();
            con.userAgent(HttpConnection.DEFAULT_UA);
            Document doc = con.get();

            // get info
            String text;
            Elements metaOgTitle = doc.select("meta[property=og:title]");
            if (metaOgTitle != null) {
                text = metaOgTitle.attr("content");
            } else {
                text = doc.title();
            }

            String imageUrl = null;
            Elements metaOgImage = doc.select("meta[property=og:image]");
            if (metaOgImage != null) {
                imageUrl = metaOgImage.attr("content");
            }

            // set info
            headerInfo.baseUrl = urlStr;
            if (!TextUtils.isEmpty(text)) {
                headerInfo.title = QUOTE + text + QUOTE;
            }
            if (!TextUtils.isEmpty(imageUrl)) {
                headerInfo.image = (imageUrl);
            }
            return headerInfo;
        }
    }
}
