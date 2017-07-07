/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.adapter;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.db.TootEntity;

import java.util.ArrayList;
import java.util.List;

public class SavedTootAdapter extends RecyclerView.Adapter {
    private List<TootEntity> list;
    private SavedTootAction handler;

    public SavedTootAdapter(Context context) {
        super();
        list = new ArrayList<>();
        handler = (SavedTootAction) context;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_saved_toot, parent, false);
        return new TootViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        TootViewHolder holder = (TootViewHolder) viewHolder;
        holder.bind(position, getItem(position));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public void setItems(List<TootEntity> newToot) {
        list = new ArrayList<>();
        list.addAll(newToot);
    }

    public void addItems(List<TootEntity> newToot) {
        int end = list.size();
        list.addAll(newToot);
        notifyItemRangeInserted(end, newToot.size());
    }

    @Nullable
    public TootEntity removeItem(int position) {
        if (position < 0 || position >= list.size()) {
            return null;
        }
        TootEntity toot = list.remove(position);
        notifyItemRemoved(position);
        return toot;
    }

    public TootEntity getItem(int position) {
        if (position >= 0 && position < list.size()) {
            return list.get(position);
        }
        return null;
    }

    // handler saved toot
    public interface SavedTootAction {
        void delete(int position, TootEntity item);

        void click(int position, TootEntity item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView mTextView;

        public ViewHolder(TextView v) {
            super(v);
            mTextView = v;
        }
    }

    private class TootViewHolder extends RecyclerView.ViewHolder {
        View view;
        TextView content;
        ImageButton suppr;

        TootViewHolder(View view) {
            super(view);
            this.view = view;
            this.content = (TextView) view.findViewById(R.id.content);
            this.suppr = (ImageButton) view.findViewById(R.id.suppr);
        }

        void bind(final int position, final TootEntity item) {
            if (item != null) {
                if (!TextUtils.isEmpty(item.getText()))
                    content.setText(item.getText());
                else
                    content.setText("");
                suppr.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        handler.delete(position, item);
                    }
                });
                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        handler.click(position, item);
                    }
                });
            }
        }
    }
}
