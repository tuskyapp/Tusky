package com.keylesspalace.tusky;

import android.app.AlarmManager;
import androidx.appcompat.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.keylesspalace.tusky.util.EmojiCompatFont;

import java.util.ArrayList;

/**
 * This Preference lets the user select their preferred emoji font
 */
public class EmojiPreference extends Preference {
    private static final String TAG = "EmojiPreference";
    private EmojiCompatFont selected, original;
    static final String FONT_PREFERENCE = "selected_emoji_font";
    private static final EmojiCompatFont[] FONTS = EmojiCompatFont.FONTS;
    // Please note that this array should be sorted in the same way as their fonts.
    private static final int[] viewIds = {
            R.id.item_nomoji,
            R.id.item_blobmoji,
            R.id.item_twemoji,
            R.id.item_notoemoji};

    private ArrayList<RadioButton> radioButtons = new ArrayList<>();


    public EmojiPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Find out which font is currently active
        this.selected = EmojiCompatFont.byId(PreferenceManager
                .getDefaultSharedPreferences(context)
                .getInt(FONT_PREFERENCE, 0));
        // We'll use this later to determine if anything has changed
        this.original = this.selected;

        setSummary(selected.getDisplay(context));
    }

    @Override
    protected void onClick() {

        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_emojicompat, null);

        for(int i = 0; i < viewIds.length; i++) {
            setupItem(view.findViewById(viewIds[i]), FONTS[i]);
        }

        new AlertDialog.Builder(getContext())
                .setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> onDialogOk())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setupItem(View container, EmojiCompatFont font) {
        Context context = container.getContext();

        TextView title = container.findViewById(R.id.emojicompat_name);
        TextView caption = container.findViewById(R.id.emojicompat_caption);
        ImageView thumb = container.findViewById(R.id.emojicompat_thumb);
        ImageButton download = container.findViewById(R.id.emojicompat_download);
        ImageButton cancel = container.findViewById(R.id.emojicompat_download_cancel);
        RadioButton radio = container.findViewById(R.id.emojicompat_radio);

        // Initialize all the views
        title.setText(font.getDisplay(context));
        caption.setText(font.getCaption(context));
        thumb.setImageDrawable(font.getThumb(context));

        // There needs to be a list of all the radio buttons in order to uncheck them when one is selected
        radioButtons.add(radio);

        updateItem(font, container);

        // Set actions
        download.setOnClickListener((downloadButton) ->
            startDownload(font, container));

        cancel.setOnClickListener((cancelButton) ->
            cancelDownload(font, container));

        radio.setOnClickListener((radioButton) ->
            select(font, (RadioButton) radioButton));

        container.setOnClickListener((containterView) ->
                select(font,
                        containterView.findViewById(R.id.emojicompat_radio
                        )));
    }

    private void startDownload(EmojiCompatFont font, View container) {
        ImageButton download         = container.findViewById(R.id.emojicompat_download);
        TextView caption        = container.findViewById(R.id.emojicompat_caption);

        ProgressBar progressBar = container.findViewById(R.id.emojicompat_progress);
        ImageButton cancel           = container.findViewById(R.id.emojicompat_download_cancel);

        // Switch to downloading style
        download.setVisibility(View.GONE);
        caption.setVisibility(View.INVISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        cancel.setVisibility(View.VISIBLE);


        font.downloadFont(getContext(), new EmojiCompatFont.Downloader.EmojiDownloadListener() {
            @Override
            public void onDownloaded(EmojiCompatFont font) {
                finishDownload(font, container);
            }

            @Override
            public void onProgress(float progress) {
                // The progress is returned as a float between 0 and 1
                progress *= progressBar.getMax();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    progressBar.setProgress((int) progress, true);
                }
                else {
                    progressBar.setProgress((int) progress);
                }
            }

            @Override
            public void onFailed() {
                Toast.makeText(getContext(), R.string.download_failed, Toast.LENGTH_SHORT).show();
                updateItem(font, container);
            }
        });
    }

    private void cancelDownload(EmojiCompatFont font, View container) {
        font.cancelDownload();
        updateItem(font, container);
    }

    private void finishDownload(EmojiCompatFont font, View container) {
        select(font, container.findViewById(R.id.emojicompat_radio));
        updateItem(font, container);
    }

    /**
     * Select a font both visually and logically
     * @param font The font to be selected
     * @param radio The radio button associated with it's visual item
     */
    private void select(EmojiCompatFont font, RadioButton radio) {
        selected = font;
        // Uncheck all the other buttons
        for(RadioButton other : radioButtons) {
            if(other != radio) {
                other.setChecked(false);
            }
        }
        radio.setChecked(true);
    }

    /**
     * Called when a "consistent" state is reached, i.e. it's not downloading the font
     * @param font The font to be displayed
     * @param container The ConstraintLayout containing the item
     */
    private void updateItem(EmojiCompatFont font, View container) {
        // Assignments
        ImageButton download      = container.findViewById(R.id.emojicompat_download);
        TextView caption          = container.findViewById(R.id.emojicompat_caption);

        ProgressBar progress = container.findViewById(R.id.emojicompat_progress);
        ImageButton cancel        = container.findViewById(R.id.emojicompat_download_cancel);

        RadioButton radio    = container.findViewById(R.id.emojicompat_radio);

        // There's no download going on
        progress.setVisibility(View.GONE);
        cancel.setVisibility(View.GONE);
        caption.setVisibility(View.VISIBLE);

        if(font.isDownloaded(getContext())) {
            // Make it selectable
            download.setVisibility(View.GONE);
            radio.setVisibility(View.VISIBLE);
            container.setClickable(true);
        }
        else {
            // Make it downloadable
            download.setVisibility(View.VISIBLE);
            radio.setVisibility(View.GONE);
            container.setClickable(false);
        }

        // Select it if necessary
        if(font == selected) {
            radio.setChecked(true);
        }
        else {
            radio.setChecked(false);
        }
    }


    /**
     * In order to be able to use this font later on, it needs to be saved first.
     */
    private void saveSelectedFont() {
        int index = selected.getId();
        Log.i(TAG, "saveSelectedFont: Font ID: " + index);
        // It's saved using the key FONT_PREFERENCE
        PreferenceManager
                .getDefaultSharedPreferences(getContext())
                .edit()
                .putInt(FONT_PREFERENCE, index)
                .apply();
        setSummary(selected.getDisplay(getContext()));
    }

    /**
     * That's it. The user doesn't want to switch between these amazing radio buttons anymore!
     * That means, the selected font can be saved (if the user hit OK)
     */
    private void onDialogOk() {
            saveSelectedFont();
            if(selected != original) {
                new AlertDialog.Builder(getContext())
                        .setTitle(R.string.restart_required)
                        .setMessage(R.string.restart_emoji)
                        .setNegativeButton(R.string.later, null)
                        .setPositiveButton(R.string.restart, ((dialog, which) -> {
                            // Restart the app
                            // From https://stackoverflow.com/a/17166729/5070653
                            Intent launchIntent = new Intent(getContext(), SplashActivity.class);
                            PendingIntent mPendingIntent = PendingIntent.getActivity(
                                    getContext(),
                                    // This is the codepoint of the party face emoji :D
                                    0x1f973,
                                    launchIntent,
                                    PendingIntent.FLAG_CANCEL_CURRENT);
                            AlarmManager mgr =
                                    (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
                            if (mgr != null) {
                                mgr.set(
                                        AlarmManager.RTC,
                                        System.currentTimeMillis() + 100,
                                        mPendingIntent);
                            }
                            System.exit(0);
                        })).show();
            }

    }

}
