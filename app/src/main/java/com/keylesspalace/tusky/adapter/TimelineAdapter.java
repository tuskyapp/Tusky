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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.recyclerview.extensions.AsyncListDiffer;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.viewdata.StatusViewData;

import java.util.ArrayList;
import java.util.List;

public final class TimelineAdapter extends RecyclerView.Adapter {
    private static final int VIEW_TYPE_STATUS = 0;
    private static final int VIEW_TYPE_PLACEHOLDER = 2;

    private List<StatusViewData> statuses;
    private StatusActionListener statusListener;
    private boolean mediaPreviewEnabled;

    public TimelineAdapter(StatusActionListener statusListener) {
        super();
        statuses = new ArrayList<>();
        this.statusListener = statusListener;
        mediaPreviewEnabled = true;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        switch (viewType) {
            default:
            case VIEW_TYPE_STATUS: {
                View view = LayoutInflater.from(viewGroup.getContext())
                        .inflate(R.layout.item_status, viewGroup, false);
                return new StatusViewHolder(view);
            }
            case VIEW_TYPE_PLACEHOLDER: {
                View view = LayoutInflater.from(viewGroup.getContext())
                        .inflate(R.layout.item_status_placeholder, viewGroup, false);
                return new PlaceholderViewHolder(view);
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        StatusViewData status = statuses.get(position);
        if (status instanceof StatusViewData.Placeholder) {
            PlaceholderViewHolder holder = (PlaceholderViewHolder) viewHolder;
            holder.setup(!((StatusViewData.Placeholder) status).isLoading(),
                    statusListener, ((StatusViewData.Placeholder) status).isLoading());
        } else {
            StatusViewHolder holder = (StatusViewHolder) viewHolder;
            holder.setupWithStatus((StatusViewData.Concrete) status,
                    statusListener, mediaPreviewEnabled);
        }
    }

    @Override
    public int getItemCount() {
        return statuses.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (statuses.get(position) instanceof StatusViewData.Placeholder) {
            return VIEW_TYPE_PLACEHOLDER;
        } else {
            return VIEW_TYPE_STATUS;
        }
    }

    public void update(@Nullable List<StatusViewData> newStatuses) {
        if (newStatuses == null || newStatuses.isEmpty()) {
            return;
        }
        statuses.clear();
        statuses.addAll(newStatuses);
    }

    public void clear() {
        statuses.clear();
        notifyDataSetChanged();
    }

    public void setMediaPreviewEnabled(boolean enabled) {
        mediaPreviewEnabled = enabled;
    }

    @Override
    public long getItemId(int position) {
        if (position >= statuses.size()) {
            return 0;
        }
        return statuses.get(position).getViewDataId();
    }
}
