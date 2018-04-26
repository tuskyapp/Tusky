package com.keylesspalace.tusky.adapter;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.EmojiCompatFont;

import java.io.File;
import java.util.ArrayList;

/**
 * This adapter is used to provide the items in the emoji style picker
 */
public class EmojiFontAdapter extends RecyclerView.Adapter<EmojiFontAdapter.EmojiFontViewHolder> {

    private ArrayList<EmojiCompatFont> fonts;

    public EmojiFontAdapter(File fontList) {
        super();
        fonts = parseFonts(fontList);
    }

    private ArrayList<EmojiCompatFont> parseFonts(File fontList) {
        return null;
    }

    @NonNull
    @Override
    public EmojiFontViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View item = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.emoji_pref_item, parent, false);
        return new EmojiFontViewHolder(item);
    }

    @Override
    public void onBindViewHolder(@NonNull EmojiFontViewHolder holder, int position) {

    }

    @Override
    public int getItemCount() {
        return 0;
    }

    class EmojiFontViewHolder extends RecyclerView.ViewHolder{

        EmojiFontViewHolder(View itemView) {
            super(itemView);
        }
    }
}
