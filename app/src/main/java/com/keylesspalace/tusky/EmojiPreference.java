package com.keylesspalace.tusky;

import android.content.Context;
import android.os.AsyncTask;
import android.preference.DialogPreference;
import android.preference.PreferenceManager;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.google.gson.Gson;
import com.keylesspalace.tusky.adapter.EmojiFontAdapter;
import com.keylesspalace.tusky.entity.EmojiCompatFont;

import java.io.File;
import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

/**
 * This Preference lets the user select their preferred emoji font
 */
public class EmojiPreference extends DialogPreference {
    // TODO: Add a real URL
    //private static final String FONTS_URL = "https://tusky.github.io/fonts.json";
    private static final String FONTS_URL = "https://raw.githubusercontent.com/C1710/Tusky/emojiSettings/fonts.json";
    // These two Arrays contain the Views shown..
    // When the font list hasn't been loaded yet
    private View[] loading;
    // When the font list is loaded
    private View[] finished;
    // And these two Arrays contain the respective ids
    private static final int[] loadingIds = {R.id.emoji_loading_label, R.id.emoji_loading};
    private static final int[] finishedIds = {R.id.emoji_font_list, R.id.emoji_download_label};
    private RecyclerView fontRecycler;
    private Context context;
    private File emojiFolder;
    private EmojiCompatFont selected;
    static final String FONT_PREFERENCE = "selected_emoji_font";
    private EmojiFontAdapter adapter;

    public EmojiPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        emojiFolder = new File(context.getExternalFilesDir(null), "emoji");

        setDialogLayoutResource(R.layout.emojicompat_dialog);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
        setDialogIcon(null);
        selected = EmojiCompatFont.parseFont(PreferenceManager.getDefaultSharedPreferences(context).getString(FONT_PREFERENCE, ""));
        selected.setBaseDirectory(emojiFolder);
    }



    private void initRecycler(View view) {
        RecyclerView fontRecycler = view.findViewById(R.id.emoji_font_list);
        if(!emojiFolder.exists()) {
            emojiFolder.mkdirs();
        }
        File fontList = new File(emojiFolder, "fonts.json");
        adapter = new EmojiFontAdapter(fontList, emojiFolder, selected);
        //if(!fontList.exists()) {
            // Download fonts
            new FontListDownloader(adapter, this::onDownloaded).execute(fontList);
        //}
        fontRecycler.setItemAnimator(new DefaultItemAnimator());
        fontRecycler.setLayoutManager(new LinearLayoutManager(view.getContext()));
        fontRecycler.setAdapter(adapter);
        this.fontRecycler = fontRecycler;
    }

    private void onDownloaded(File emojiFont) {
        for (View view: loading) {
            view.setVisibility(View.GONE);
        }
        for (View view: finished) {
            view.setVisibility(View.VISIBLE);
        }
    }

    public interface EmojiFontListener {
        void onDownloaded(File emojiFont);
    }

    private static class FontListDownloader extends AsyncTask<File, Void, File> {
        private EmojiFontListener[] listeners;

        FontListDownloader(EmojiFontListener... listeners) {
            super();
            this.listeners = listeners;
        }

        @Override
        protected File doInBackground(File... files){
            File fontList = files[0];
            BufferedSink sink;
            try {
                if (!fontList.exists()) {
                    fontList.createNewFile();
                }
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(FONTS_URL)
                        .addHeader("Content-Type", "application/json")
                        .build();
                Response response = client.newCall(request).execute();
                sink = Okio.buffer(Okio.sink(fontList));
                try {
                    if (response.body() != null) {
                        sink.writeAll(response.body().source());
                    } else {
                        Log.e("FUCK", "downloadFonts: Source empty");
                    }
                }
                finally {
                    sink.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            return fontList;
        }

        @Override
        public void onPostExecute(File fontFile) {
            for(EmojiFontListener listener: listeners) {
                listener.onDownloaded(fontFile);
            }
        }
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        initRecycler(view);
        loading = new View[loadingIds.length];
        finished = new View[finishedIds.length];
        for(int i = 0; i < loadingIds.length; i++) {
            loading[i] = view.findViewById(loadingIds[i]);
        }
        for(int i = 0; i < finishedIds.length; i++) {
            finished[i] = view.findViewById(finishedIds[i]);
        }
    }

    private void pullSelectedFont() {
        try {
            selected = adapter.getSelected();
        }
        catch (NullPointerException ex) {
            // Since there does not seem to be an Adapter, we can't update the selected font...
            ex.printStackTrace();
        }
    }

    private void saveSelectedFont() {
        Gson gson = new Gson();
        String json = gson.toJson(selected);
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(FONT_PREFERENCE, json).apply();
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if(positiveResult) {
            pullSelectedFont();
            saveSelectedFont();
        }
        super.onDialogClosed(positiveResult);
    }

}
