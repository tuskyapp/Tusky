package com.keylesspalace.tusky.adapter;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.keylesspalace.tusky.EmojiPreference;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.util.EmojiCompatFont;

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

    // All fonts available in this list
    private ArrayList<EmojiCompatFont> fonts = new ArrayList<>();
    // The currently selected font which will be chosen when clicking OK
    private EmojiCompatFont selected;
    // All Radio buttons.
    // Since a RadioGroup can't be used, unchecking all the other radio buttons needs to be done manually.
    private final ArrayList<RadioButton> radioButtons = new ArrayList<>();
    // The directory containing all the emoji files
    private final File dlDir;

    /**
     * Create a new EmojiFontAdapter to fill the selector GUI
     * @param dlDir The directory with all the emoji files
     * @param selected the currently selected emoji font (maybe system default?)
     */
    public EmojiFontAdapter(File dlDir, EmojiCompatFont selected) {
        super();
        this.dlDir = dlDir;
        this.selected = selected;
    }

    /**
     * Load a list of fonts from a JSON file
     * @param fontList the file containing the JSON document
     * @return An ArrayList containing the EmojiCompatFonts represented in the JSON file
     * @throws FileNotFoundException Thrown if the JSON file can't be found
     */
    private ArrayList<EmojiCompatFont> parseFonts(File fontList) throws FileNotFoundException {
        // Use Gson
        Gson gson = new Gson();
        // This is actually copied from their user guide
        Type emojiFontType = new TypeToken<ArrayList<EmojiCompatFont>>(){}.getType();
        ArrayList<EmojiCompatFont> fonts;
        try {
            fonts =
                    gson.fromJson(new InputStreamReader(new FileInputStream(fontList)), emojiFontType);
        }
        catch (JsonSyntaxException ex) {
            ex.printStackTrace();
            return new ArrayList<>();
        }
        // The font objects don't have their base directory yet...
        for(EmojiCompatFont font: fonts) {
            font.setBaseDirectory(dlDir);
        }
        // That's it!
        return fonts;
    }

    /**
     * Create a new skeleton for an entry
     * @param parent Probably the RecyclerView using this adapter
     * @param viewType ????
     * @return An empty font entry
     */
    @NonNull
    @Override
    public EmojiFontViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View item = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_emoji_pref, parent, false);
        // All radio buttons need to be stored to uncheck them later
        radioButtons.add(item.findViewById(R.id.emoji_radio));
        return new EmojiFontViewHolder(item);
    }

    @Override
    public void onBindViewHolder(@NonNull EmojiFontViewHolder holder, int position) {
        // Okay. Just change the content to represent this new font
        holder.update(fonts.get(position));
    }

    @Override
    public int getItemCount() {
        return fonts.size();
    }

    public EmojiCompatFont getSelected() {
        return selected;
    }

    /**
     * This one is called when we finished downloading the JSON file or after creating this Adapter
     * @param emojiFont the font list file
     */
    @Override
    public void onDownloaded(File emojiFont) {
        try {
            // Assign the global var
            fonts = parseFonts(emojiFont);
            // Update the RecyclerView's content
            notifyDataSetChanged();
        } catch (FileNotFoundException e) {
            // Oh no - something went wrong! (It's probably the download that hasn't finished yet)
            e.printStackTrace();
            // In order to prevent another crash, the list will simply be empty
            if(fonts == null) {
                fonts = new ArrayList<>();
            }
        }
        // The system default option isn't included by default. So we'll do this here
        fonts.add(EmojiCompatFont.SYSTEM_DEFAULT);
    }

    /**
     * This class got a little big, but it's the interface between the GUI and the backend.
     */
    class EmojiFontViewHolder extends RecyclerView.ViewHolder implements EmojiCompatFont.Downloader.EmojiDownloadListener{
        // This is where we'll see the thumbnail picture
        private final ImageView thumb;
        // This is the font's title view
        private final TextView fontView;
        // The caption
        private final TextView subtitle;
        // The button to download the font (it's not shown after the download has begun)
        private final ImageButton downloadButton;

        // These two are only shown during the download
        // This button is supposed to be cklicked when the user decides to cancel the download
        // TODO: It's currently disabled
        private final ImageButton cancelDownload;
        // This is the progress bar indicating that the download is still ongoing.
        // TODO: Update progress
        private final ProgressBar downloadProgress;

        // This button is only shown when the download has been finished.
        // It can be selected to select a font
        private final RadioButton radioButton;

        // The font which is represented by this entry
        private EmojiCompatFont font;
        // The Application's context is (only) needed to translate the "System default" string
        private final Context applicationContext;

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


        /**
         * This method is called when the font has been downloaded
         * @param font The font related to this download. This will help identifying the download
         */
        @Override
        public void onDownloaded(EmojiCompatFont font) {
            // First check if this is still the correct ViewHolder receiving this message
            if(this.font == font) {
                if (font.isDownloaded()) {
                    // We don't need the option to cancel the download anymore
                    cancelDownload.setVisibility(View.GONE);
                    // We don't need to see the progress anymore
                    downloadProgress.setVisibility(View.GONE);
                    // We're interested in the caption again!
                    subtitle.setVisibility(View.VISIBLE);
                    // We're interested in selecting the font!
                    radioButton.setVisibility(View.VISIBLE);
                    // Since the user just downloaded this file, they'll probably want to choose it as well.
                    radioButton.callOnClick();
                }
            }
        }


        /**
         * Update this ViewHolder to represent another font
         * @param font The EmojiCompatFont represented by this ViewHolder
         */
        void update(EmojiCompatFont font) {
            // Exchange the font
            this.font = font;
            // Set the title and cation
            fontView.setText(font.getDisplay(applicationContext));
            subtitle.setText(font.getSubtitle());
            // Now it's time to display the thumbnail picture
            try {
                // It isn't downloaded yet. So we'll do that now
                // TODO: Don't download if data saving is enabled
                if(!font.getThumb().exists()) {
                    // Luckily the font can do this for us!
                    font.downloadThumb((dlFont) ->
                    {
                        // This ViewHolder might have been recycled...
                        if(this.font == dlFont) {
                            // Okay, this ViewHolder still displays the same font
                            try {
                                // Load it!
                                thumb.setImageBitmap(BitmapFactory.decodeFile(dlFont.getThumb().getAbsolutePath()));
                            } catch (FileNotFoundException e) {
                                // This is supposed to never happen
                                e.printStackTrace();
                            }
                        }
                    });
                }
                else {
                    // We've already downloaded the thumbnail image
                    // We can set it right away
                    thumb.setImageBitmap(BitmapFactory.decodeFile(font.getThumb().getAbsolutePath()));
                }
            }
            catch (FileNotFoundException ex) {
                // Okay. It's the standard font.
                // This means a generic Drawable is used
                thumb.setImageResource(R.drawable.ic_emoji_24dp);
            }

            // Downloads should be initializable
            downloadButton.setOnClickListener((View v) ->
            {
                try {
                    // Download the font
                    font.downloadFont(this);
                    // Switch to a loading perspective
                    downloadProgress.setVisibility(View.VISIBLE);
                    subtitle.setVisibility(GONE);
                    // TODO: Make the cancel button work
                    cancelDownload.setVisibility(View.VISIBLE);
                    cancelDownload.setOnClickListener((cancel) -> {
                        font.cancelDownload();
                        downloadButton.setVisibility(View.VISIBLE);
                        subtitle.setVisibility(View.VISIBLE);
                        downloadProgress.setVisibility(GONE);
                        cancel.setVisibility(GONE);
                    });
                    downloadButton.setVisibility(GONE);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            });

            // Well, is the font already available? We can just show it right away!
            if(font.isDownloaded()) {
                // We don't need the download button anymore
                downloadButton.setVisibility(GONE);
                // But we need the selection button
                radioButton.setVisibility(View.VISIBLE);
            }

            // Selecting the radio button is supposed to do something too...
            radioButton.setOnClickListener((v) ->
            {
                try {
                    RadioButton button = (RadioButton) v;
                    if (button.isChecked()) {
                        // Select the new font
                        selected = font;
                        // Uncheck the other fonts
                        for (RadioButton other : radioButtons) {
                            if (other != v) {
                                other.setChecked(false);
                            }
                        }
                    }
                }
                catch (ClassCastException ex) {
                    // Wait, what?! This radio button was not a radio button??
                    // But... but... that's impossible!
                    ex.printStackTrace();
                }
            });

            // Oh, we're just loading the currently selected font? Mark it as selected!
            if(font.equals(selected)) {
                // Check
                radioButton.setChecked(true);
                // Uncheck all the others
                for (RadioButton other : radioButtons) {
                    if (other != radioButton) {
                        other.setChecked(false);
                    }
                }
            }
            else {
                // Meh, it's not the selected font. Uncheck it.
                radioButton.setChecked(false);
            }
        }

    }
}
