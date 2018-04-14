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

import android.support.annotation.Nullable;
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
    private static final int VIEW_TYPE_STATUS = 0;
    private static final int VIEW_TYPE_STATUS_DETAILED = 1;

    private List<StatusViewData.Concrete> statuses;
    private StatusActionListener statusActionListener;
    private boolean mediaPreviewEnabled;
    private int detailedStatusPosition;

    public ThreadAdapter(StatusActionListener listener) {
        this.statusActionListener = listener;
        this.statuses = new ArrayList<>();
        mediaPreviewEnabled = true;
        detailedStatusPosition = RecyclerView.NO_POSITION;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            default:
            case VIEW_TYPE_STATUS: {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_status, parent, false);
                return new StatusViewHolder(view);
            }
            case VIEW_TYPE_STATUS_DETAILED: {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_status_detailed, parent, false);
                return new StatusDetailedViewHolder(view);
            }
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        StatusViewData.Concrete status = statuses.get(position);
        if (position == detailedStatusPosition) {
            StatusDetailedViewHolder holder = (StatusDetailedViewHolder) viewHolder;
            holder.setupWithStatus(status, statusActionListener, mediaPreviewEnabled);
        } else {
            StatusViewHolder holder = (StatusViewHolder) viewHolder;
            holder.setupWithStatus(status, statusActionListener, mediaPreviewEnabled);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position == detailedStatusPosition) {
            return VIEW_TYPE_STATUS_DETAILED;
        } else {
            return VIEW_TYPE_STATUS;
        }
    }

    @Override
    public int getItemCount() {
        return statuses.size();
    }

    public void setStatuses(List<StatusViewData.Concrete> statuses) {
        this.statuses.clear();
        this.statuses.addAll(statuses);
        notifyDataSetChanged();
    }

    public void addItem(int position, StatusViewData.Concrete statusViewData) {
        statuses.add(position, statusViewData);
        notifyItemInserted(position);
    }

    public void clearItems() {
        int oldSize = statuses.size();
        statuses.clear();
        detailedStatusPosition = RecyclerView.NO_POSITION;
        notifyItemRangeRemoved(0, oldSize);
    }

    public void addAll(int position, List<StatusViewData.Concrete> statuses) {
        this.statuses.addAll(position, statuses);
        notifyItemRangeInserted(position, statuses.size());
    }

    public void addAll(List<StatusViewData.Concrete> statuses) {
        int end = statuses.size();
        this.statuses.addAll(statuses);
        notifyItemRangeInserted(end, statuses.size());
    }

    public void clear() {
        statuses.clear();
        detailedStatusPosition = RecyclerView.NO_POSITION;
        notifyDataSetChanged();
    }

    public void setItem(int position, StatusViewData.Concrete status, boolean notifyAdapter) {
        statuses.set(position, status);
        if (notifyAdapter) {
            notifyItemChanged(position);
        }
    }

    @Nullable
    public StatusViewData.Concrete getItem(int position) {
        if (position != RecyclerView.NO_POSITION && position >= 0 && position < statuses.size()) {
            return statuses.get(position);
        } else {
            return null;
        }
    }

    public void setMediaPreviewEnabled(boolean enabled) {
        mediaPreviewEnabled = enabled;
    }

    public void setDetailedStatusPosition(int position) {
        if (position != detailedStatusPosition
                && detailedStatusPosition != RecyclerView.NO_POSITION) {
            int prior = detailedStatusPosition;
            detailedStatusPosition = position;
            notifyItemChanged(prior);
        } else {
            detailedStatusPosition = position;
        }
    }

    public int getDetailedStatusPosition() {
        return detailedStatusPosition;
    }
}
