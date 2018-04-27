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
    // The font list will be downloaded from this URL
    private static final String FONTS_URL = "https://raw.githubusercontent.com/C1710/Tusky/emojiSettings/fonts.json";
    // These two Arrays contain the Views shown...
    // ...when the font list hasn't been loaded yet
    private View[] loading;
    // ...when the font list is loaded
    private View[] finished;
    // And these two Arrays contain the respective ids
    private static final int[] loadingIds = {R.id.emoji_loading_label, R.id.emoji_loading};
    private static final int[] finishedIds = {R.id.emoji_font_list, R.id.emoji_download_label};
    // We'll need a Context to get some String resources. Thanks, Android!
    private Context context;
    // This is where the emoji fonts are stored
    private File emojiFolder;
    // TODO: It might be possible that you could use a more lightweight solution...
    // Which font is the selected one?
    private EmojiCompatFont selected;
    // The key of this preference. It will also be used when loading it at the start
    static final String FONT_PREFERENCE = "selected_emoji_font";
    // In order to be able to retrieve the newly selected font, we'll need to have a reference to
    // the adapter which is used to select the font.
    private EmojiFontAdapter adapter;


    public EmojiPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        // We'll need this context...
        this.context = context;
        // 1. to get the directory where the emoji files are stored
        emojiFolder = new File(context.getExternalFilesDir(null), "emoji");
        // 2. We'll need it later to translate one String

        // Set the content of the dialog
        setDialogLayoutResource(R.layout.emojicompat_dialog);
        // This should be pretty straightforward...
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
        // No icons for you!
        setDialogIcon(null);
        // In order to know which font has been selected, we need to load it first.
        selected = EmojiCompatFont.parseFont(
                PreferenceManager
                        .getDefaultSharedPreferences(context)
                        .getString(FONT_PREFERENCE, ""));
        // TODO: Save the directory in JSON
        // Since the directory is not stored in the JSON, we'll need to manually add a directory
        // in order to let it find its files
        selected.setBaseDirectory(emojiFolder);
    }


    /**
     * Since configuring these RecyclerViews can be very complex, it's useful to do this in a seperate
     * method...
     * This one is the RecyclerView containing the Emoji entries btw.
     * @param view The View containing this RecyclerView
     */
    private void initRecycler(View view) {
        // Which one is it?
        RecyclerView fontRecycler = view.findViewById(R.id.emoji_font_list);
        // It might be possible that the directory containing the EmojiCompat files does not exist yet...
        if(!emojiFolder.exists()) {
            // If not, create it!
            emojiFolder.mkdirs();
        }
        // This is the font list which stores all the necessary information on the emoji fonts.
        File fontList = new File(emojiFolder, "fonts.json");
        // The Adapter will use this list to well... list these fonts.
        adapter = new EmojiFontAdapter(fontList, emojiFolder, selected);
        // We don't need to download the list again and again.
        // TODO: Check if it's useful to download the list again. (i.e. if an update was made)
        if(!fontList.exists()) {
            // Download fonts
            new FontListDownloader(adapter, this::onDownloaded).execute(fontList);
        }
        // Configure the RecyclerView
        fontRecycler.setItemAnimator(new DefaultItemAnimator());
        fontRecycler.setLayoutManager(new LinearLayoutManager(view.getContext()));
        fontRecycler.setAdapter(adapter);
    }

    /**
     * This method is called when we finished downloading our font list.
     * @param emojiFont The File containing the JSON file with the font list in it
     */
    private void onDownloaded(File emojiFont) {
        // So we're ready to show the actual content! No more loading screens*! (*almost)
        for (View view: loading) {
            view.setVisibility(View.GONE);
        }
        for (View view: finished) {
            view.setVisibility(View.VISIBLE);
        }
    }

    /**
     * This interface is used to let objects get notified when the font list has been downloaded.
     */
    public interface EmojiFontListener {
        void onDownloaded(File emojiFont);
        /*
        TODO: Maybe add something if downloading went wrong.
        The only problem about this would be that Lambda expressions couldn't be used anymore :'(
        */
    }

    /**
     * This task bundles all the stuff required to download a new emoji file list
     */
    private static class FontListDownloader extends AsyncTask<File, Void, File> {
        // All the objects interested about (?) this download
        private EmojiFontListener[] listeners;

        FontListDownloader(EmojiFontListener... listeners) {
            super();
            this.listeners = listeners;
        }

        @Override
        protected File doInBackground(File... files){
            // This is (mostly) shamelessly copied from StackOverflow...
            // We just want to download to ONE file...
            File fontList = files[0];
            // This will be used to write the downloaded content
            BufferedSink sink;
            try {
                // There's probably no file as we first need to download it here!
                if (!fontList.exists()) {
                    fontList.createNewFile();
                }
                OkHttpClient client = new OkHttpClient();
                // Build a request to download this list.
                Request request = new Request.Builder().url(FONTS_URL)
                        .addHeader("Content-Type", "application/json")
                        .build();
                // Start download
                Response response = client.newCall(request).execute();
                // Open up the writing part
                sink = Okio.buffer(Okio.sink(fontList));
                try {
                    if (response.body() != null) {
                        // GOGOGO! DOWNLOAD!
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
            // Finished!
            return fontList;
        }

        @Override
        public void onPostExecute(File fontFile) {
            // So we're ready to notify our customers... LISTENERS
            for(EmojiFontListener listener: listeners) {
                listener.onDownloaded(fontFile);
            }
        }
    }

    /**
     * This method is called when the dialog is created
     * @param view The container with all these wounderful Views
     */
    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        // Okay. We can initialize our emoji font list
        initRecycler(view);
        // We'll assign the Views to two groups in order to make it easier to
        // go from Loading... to You'll need to download even more!
        loading = new View[loadingIds.length];
        finished = new View[finishedIds.length];
        for(int i = 0; i < loadingIds.length; i++) {
            loading[i] = view.findViewById(loadingIds[i]);
        }
        for(int i = 0; i < finishedIds.length; i++) {
            finished[i] = view.findViewById(finishedIds[i]);
        }
    }

    /**
     * This one copies the currently selected font from the adapter.
     * It should be usually called when clicking OK
     */
    private void pullSelectedFont() {
        try {
            selected = adapter.getSelected();
        }
        catch (NullPointerException ex) {
            // Since there does not seem to be an Adapter, we can't update the selected font...
            ex.printStackTrace();
        }
    }

    /**
     * In order to be able to use this font later on, it needs to be saved first.
     */
    private void saveSelectedFont() {
        // The configuration is saved using JSON which can be easily encoded by using Gson.
        Gson gson = new Gson();
        String json = gson.toJson(selected);
        // It's saved using the key FONT_PREFERENCE
        PreferenceManager
                .getDefaultSharedPreferences(context)
                .edit()
                .putString(FONT_PREFERENCE, json)
                .apply();
    }

    /**
     * That's it. The user doesn't want to switch between these amazing radio buttons anymore!
     * That means, the selected font can be saved (if the user hit OK)
     * @param positiveResult if OK has been selected.
     */
    @Override
    public void onDialogClosed(boolean positiveResult) {
        if(positiveResult) {
            // TODO: It might be better to not store the newly selected font in this class/object...
            // Get it
            pullSelectedFont();
            // save it
            saveSelectedFont();
        }
    }

}
