package com.keylesspalace.tusky;

import android.content.Context;
import android.preference.DialogPreference;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;

import com.keylesspalace.tusky.adapter.EmojiFontAdapter;

import java.io.File;

/**
 * This Preference lets the user select their preferred emoji font
 */
public class EmojiPreference extends DialogPreference {
    // TODO: Add a real URL
    private static final String fontListUrl = "tusky.github.io";
    private RecyclerView fontRecycler;
    private Context context;

    public EmojiPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;

        setDialogLayoutResource(R.layout.emojicompat_dialog);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
        setDialogIcon(null);
        initRecycler();
    }

    private void initRecycler() {
        File fontList = new File(context.getExternalFilesDir(null), "emoji/fonts.json");
        if(!fontList.exists()) {
            // Download fonts
        }
        fontRecycler.setAdapter(new EmojiFontAdapter(fontList));
    }

    @Override
    protected void onBindDialogView(View view) {
        fontRecycler = view.findViewById(R.id.emoji_font_list);
        super.onBindDialogView(view);
    }
}
