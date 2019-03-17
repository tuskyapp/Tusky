/* Copyright 2019 kyori19
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.ScheduledStatus;

import java.util.ArrayList;
import java.util.List;

public class ScheduledTootAdapter extends RecyclerView.Adapter {
    private List<ScheduledStatus> list;
    private ScheduledTootAction handler;

    public ScheduledTootAdapter(Context context) {
        super();
        list = new ArrayList<>();
        handler = (ScheduledTootAction) context;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_scheduled_toot, parent, false);
        return new TootViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        TootViewHolder holder = (TootViewHolder) viewHolder;
        holder.bind(getItem(position));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public void setItems(List<ScheduledStatus> newToot) {
        list = new ArrayList<>();
        list.addAll(newToot);
    }

    @Nullable
    public ScheduledStatus removeItem(int position) {
        if (position < 0 || position >= list.size()) {
            return null;
        }
        ScheduledStatus toot = list.remove(position);
        notifyItemRemoved(position);
        return toot;
    }

    private ScheduledStatus getItem(int position) {
        if (position >= 0 && position < list.size()) {
            return list.get(position);
        }
        return null;
    }

    public interface ScheduledTootAction {
        void edit(int position, ScheduledStatus item);

        void delete(int position, ScheduledStatus item);
    }

    private class TootViewHolder extends RecyclerView.ViewHolder {
        View view;
        TextView text;
        ImageButton edit;
        ImageButton delete;

        TootViewHolder(View view) {
            super(view);
            this.view = view;
            this.text = view.findViewById(R.id.text);
            this.edit = view.findViewById(R.id.edit);
            this.delete = view.findViewById(R.id.delete);
        }

        void bind(final ScheduledStatus item) {
            edit.setEnabled(true);
            delete.setEnabled(true);

            if (item != null) {
                text.setText(item.getParams().getText());

                edit.setOnClickListener(v -> {
                    v.setEnabled(false);
                    handler.edit(getAdapterPosition(), item);
                });

                delete.setOnClickListener(v -> {
                    v.setEnabled(false);
                    handler.delete(getAdapterPosition(), item);
                });
            }
        }
    }
}
