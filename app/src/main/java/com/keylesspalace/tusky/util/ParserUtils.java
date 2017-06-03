package com.keylesspalace.tusky.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.URLUtil;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.util.List;

import static com.keylesspalace.tusky.util.StringUtils.QUOTE;

/**
 * Inspect and Get the information from an URL
 */
public class ParserUtils {
    private static final String TAG = "ParserUtils";
    private ParserListener parserListener;

    public ParserUtils(ParserListener parserListener) {
        this.parserListener = parserListener;
    }

    // ComposeActivity : EditText inside the onTextChanged
    public String getPastedURLText(Context context) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        String pasteData;
        if (clipboard.hasPrimaryClip()) {
            // get what is in the clipboard
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            pasteData = item.getText().toString();

            // If we share with an app, it's not only an url
            List<String> strings = StringUtils.extractUrl(pasteData);
            String url = strings.get(0); // we assume that the first url is the good one
            if (strings.size() > 0) {
                if (URLUtil.isValidUrl(url)) {
                    new ThreadHeaderInfo().execute(url);
                }
            }
        }
        return null;
    }

    public void putInClipboardManager(Context context, String string) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("", string);
        clipboard.setPrimaryClip(clip);
    }

    // parse the HTML page
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
            headerInfo.title = QUOTE + text.toUpperCase() + QUOTE;
        }
        if (!TextUtils.isEmpty(imageUrl)) {
            headerInfo.image = (imageUrl);
        }
        return headerInfo;
    }

    public interface ParserListener {
        void onReceiveHeaderInfo(HeaderInfo headerInfo);

        void onErrorHeaderInfo();
    }

    public class HeaderInfo {
        public String baseUrl;
        public String title;
        public String image;
    }

    private class ThreadHeaderInfo extends AsyncTask<String, Void, HeaderInfo> {
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
            if (headerInfo != null) {
                Log.i(TAG, "ThreadHeaderInfo#parsePageHeaderInfo() success." + headerInfo.title + " " + headerInfo.image);
                parserListener.onReceiveHeaderInfo(headerInfo);
            } else {
                parserListener.onErrorHeaderInfo();
            }
        }
    }
}
