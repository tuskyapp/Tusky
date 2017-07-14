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

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.viewdata.StatusViewData;

import java.util.ArrayList;
import java.util.List;

public class ThreadAdapter extends RecyclerView.Adapter {
    private List<StatusViewData> statuses;
    private StatusActionListener statusActionListener;
    private boolean mediaPreviewEnabled;

    public ThreadAdapter(StatusActionListener listener) {
        this.statusActionListener = listener;
        this.statuses = new ArrayList<>();
        mediaPreviewEnabled = true;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_status, parent, false);
        return new StatusViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        StatusViewHolder holder = (StatusViewHolder) viewHolder;
        StatusViewData status = statuses.get(position);
        holder.setupWithStatus(status,
                statusActionListener, mediaPreviewEnabled);
    }

    @Override
    public int getItemCount() {
        return statuses.size();
    }

    public void setStatuses(List<StatusViewData> statuses) {
        this.statuses.clear();
        this.statuses.addAll(statuses);
        notifyDataSetChanged();
    }

    public void addItem(int position, StatusViewData statusViewData) {
        statuses.add(position, statusViewData);
        notifyItemInserted(position);
    }

    public void clearItems() {
        int oldSize = statuses.size();
        statuses.clear();
        notifyItemRangeRemoved(0, oldSize);
    }

    public void addAll(int position, List<StatusViewData> statuses) {
        this.statuses.addAll(position, statuses);
        notifyItemRangeInserted(position, statuses.size());
    }

    public void addAll(List<StatusViewData> statuses) {
        int end = statuses.size();
        this.statuses.addAll(statuses);
        notifyItemRangeInserted(end, statuses.size());
    }

    public void clear() {
        statuses.clear();
        notifyDataSetChanged();
    }

    public void setItem(int position, StatusViewData status, boolean notifyAdapter) {
        statuses.set(position, status);
        if (notifyAdapter) notifyItemChanged(position);
    }

    public void setMediaPreviewEnabled(boolean enabled) {
        mediaPreviewEnabled = enabled;
    }
}
