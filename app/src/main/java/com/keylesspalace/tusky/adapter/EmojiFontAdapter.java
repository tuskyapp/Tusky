package com.keylesspalace.tusky.adapter;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.keylesspalace.tusky.EmojiPreference;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.EmojiCompatFont;
import com.pkmmte.view.CircularImageView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;

import static android.view.View.GONE;

/**
 * This adapter is used to provide the items in the emoji style picker
 */
public class EmojiFontAdapter extends RecyclerView.Adapter<EmojiFontAdapter.EmojiFontViewHolder>
        implements EmojiPreference.EmojiFontListener {

    private ArrayList<EmojiCompatFont> fonts;
    private EmojiCompatFont selected;
    private final ArrayList<RadioButton> radioButtons = new ArrayList<>();
    private final File dlDir;

    public EmojiFontAdapter(File fontList, File dlDir, EmojiCompatFont selected) {
        super();
        this.dlDir = dlDir;
        this.selected = selected;
        // Yes, this is an interface method which is called, but redundancy has to be reduced.
        onDownloaded(fontList);
    }

    private ArrayList<EmojiCompatFont> parseFonts(File fontList) throws FileNotFoundException {
        Gson gson = new Gson();
        Type emojiFontType = new TypeToken<ArrayList<EmojiCompatFont>>(){}.getType();
        ArrayList<EmojiCompatFont> fonts =
                gson.fromJson(new InputStreamReader(new FileInputStream(fontList)), emojiFontType);
        for(EmojiCompatFont font: fonts) {
            font.setBaseDirectory(dlDir);
        }
        return fonts;
    }

    @NonNull
    @Override
    public EmojiFontViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View item = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.emoji_pref_item, parent, false);
        radioButtons.add(item.findViewById(R.id.emoji_radio));
        return new EmojiFontViewHolder(item);
    }

    @Override
    public void onBindViewHolder(@NonNull EmojiFontViewHolder holder, int position) {
        holder.update(fonts.get(position));
    }

    @Override
    public int getItemCount() {
        return fonts.size();
    }

    public EmojiCompatFont getSelected() {
        return selected;
    }

    @Override
    public void onDownloaded(File emojiFont) {
        try {
            fonts = parseFonts(emojiFont);
            notifyDataSetChanged();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            if(fonts == null) {
                fonts = new ArrayList<>();
            }
        }
        fonts.add(EmojiCompatFont.SYSTEM_DEFAULT);
    }

    class EmojiFontViewHolder extends RecyclerView.ViewHolder implements EmojiCompatFont.Downloader.EmojiDownloadListener{
        private final CircularImageView thumb;
        private final TextView fontView;
        private final TextView subtitle;
        private final ImageButton downloadButton;
        private final ProgressBar downloadProgress;
        private final RadioButton radioButton;
        private final ImageButton cancelDownload;
        private EmojiCompatFont font;
        private Context applicationContext;

        EmojiFontViewHolder(View itemView) {
            super(itemView);
            this.thumb = itemView.findViewById(R.id.emojicompat_thumb);
            this.fontView = itemView.findViewById(R.id.emojicompat_name);
            this.subtitle = itemView.findViewById(R.id.emojicompat_subtitle);
            this.downloadButton = itemView.findViewById(R.id.download_emoji_button);
            this.downloadProgress = itemView.findViewById(R.id.emojicompat_progress);
            this.radioButton = itemView.findViewById(R.id.emoji_radio);
            this.cancelDownload = itemView.findViewById(R.id.cancel_emoji_download);
            this.applicationContext = itemView.getContext().getApplicationContext();
        }


        @Override
        public void onDownloaded(EmojiCompatFont font) {
            // First check if this is still the correct ViewHolder receiving this message
            if(this.font == font) {
                if (font.isDownloaded()) {
                    cancelDownload.setVisibility(View.GONE);
                    downloadProgress.setVisibility(View.GONE);
                    subtitle.setVisibility(View.VISIBLE);
                    radioButton.setVisibility(View.VISIBLE);
                    radioButton.toggle();
                }
            }
        }


        void update(EmojiCompatFont font) {
            this.font = font;
            fontView.setText(font.getDisplay(applicationContext));
            subtitle.setText(font.getSubtitle());
            try {
                if(!font.getThumb().exists()) {
                    font.downloadThumb((dlFont) ->
                    {
                        // This ViewHolder might have been recycled...
                        if(this.font == dlFont) {
                            try {
                                thumb.setImageBitmap(BitmapFactory.decodeFile(dlFont.getThumb().getAbsolutePath()));
                            } catch (FileNotFoundException e) {
                                // This is supposed to never happen
                                e.printStackTrace();
                            }
                        }
                    });
                }
                else {
                    thumb.setImageBitmap(BitmapFactory.decodeFile(font.getThumb().getAbsolutePath()));
                }
            }
            catch (FileNotFoundException ex) {
                // Okay. It's the standard font.
                thumb.setImageResource(R.drawable.ic_emoji_24dp);
            }

            downloadButton.setOnClickListener((View v) ->
            {
                try {
                    font.downloadFont(this);
                    downloadProgress.setVisibility(View.VISIBLE);
                    subtitle.setVisibility(GONE);
                    // TODO: Make the cancel button work
                    // cancelDownload.setVisibility(View.VISIBLE);
                    downloadButton.setVisibility(GONE);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            });

            if(font.isDownloaded()) {
                downloadButton.setVisibility(GONE);
                radioButton.setVisibility(View.VISIBLE);
            }

            radioButton.setOnClickListener((v) ->
            {
                try {
                    RadioButton button = (RadioButton) v;
                    if (button.isChecked()) {
                        selected = font;
                        for (RadioButton other : radioButtons) {
                            if (other != v) {
                                other.setChecked(false);
                            }
                        }
                    }
                }
                catch (ClassCastException ex) {
                    ex.printStackTrace();
                }
            });

            if(font.equals(selected)) {
                radioButton.setChecked(true);
                for (RadioButton other : radioButtons) {
                    if (other != radioButton) {
                        other.setChecked(false);
                    }
                }
            }
            else {
                radioButton.setChecked(false);
            }
        }

    }
}
