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

public class TimelineAdapter extends RecyclerView.Adapter {
    private static final int VIEW_TYPE_STATUS = 0;
    private static final int VIEW_TYPE_FOOTER = 1;
    private static final int VIEW_TYPE_PLACEHOLDER = 2;

    private List<StatusViewData> statuses;
    private StatusActionListener statusListener;
    private FooterViewHolder.State footerState;
    private boolean mediaPreviewEnabled;

    public TimelineAdapter(StatusActionListener statusListener) {
        super();
        statuses = new ArrayList<>();
        this.statusListener = statusListener;
        footerState = FooterViewHolder.State.END;
        mediaPreviewEnabled = true;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        switch (viewType) {
            default:
            case VIEW_TYPE_STATUS: {
                View view = LayoutInflater.from(viewGroup.getContext())
                        .inflate(R.layout.item_status, viewGroup, false);
                return new StatusViewHolder(view);
            }
            case VIEW_TYPE_FOOTER: {
                View view = LayoutInflater.from(viewGroup.getContext())
                        .inflate(R.layout.item_footer, viewGroup, false);
                return new FooterViewHolder(view);
            }
            case VIEW_TYPE_PLACEHOLDER: {
                View view = LayoutInflater.from(viewGroup.getContext())
                        .inflate(R.layout.item_status_placeholder, viewGroup, false);
                return new PlaceholderViewHolder(view);
            }
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        if (position < statuses.size()) {
            StatusViewData status = statuses.get(position);
            if (status instanceof StatusViewData.Placeholder) {
                PlaceholderViewHolder holder = (PlaceholderViewHolder) viewHolder;
                holder.setup(!((StatusViewData.Placeholder) status).isLoading(), statusListener);
            } else {

                StatusViewHolder holder = (StatusViewHolder) viewHolder;
                holder.setupWithStatus((StatusViewData.Concrete) status,
                        statusListener, mediaPreviewEnabled);
            }

        } else {
            FooterViewHolder holder = (FooterViewHolder) viewHolder;
            holder.setState(footerState);
        }
    }

    @Override
    public int getItemCount() {
        return statuses.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == statuses.size()) {
            return VIEW_TYPE_FOOTER;
        } else {
            if (statuses.get(position) instanceof StatusViewData.Placeholder) {
                return VIEW_TYPE_PLACEHOLDER;
            } else {
                return VIEW_TYPE_STATUS;
            }
        }
    }

    public void update(@Nullable List<StatusViewData> newStatuses) {
        if (newStatuses == null || newStatuses.isEmpty()) {
            return;
        }
        statuses.clear();
        statuses.addAll(newStatuses);
        notifyDataSetChanged();
    }

    public void addItems(List<StatusViewData> newStatuses) {
        statuses.addAll(newStatuses);
        notifyItemRangeInserted(statuses.size(), newStatuses.size());
    }

    public void changeItem(int position, StatusViewData newData, boolean notifyAdapter) {
        statuses.set(position, newData);
        if (notifyAdapter) notifyItemChanged(position);
    }

    public void clear() {
        statuses.clear();
        notifyDataSetChanged();
    }

    public void setFooterState(FooterViewHolder.State newFooterState) {
        FooterViewHolder.State oldValue = footerState;
        footerState = newFooterState;
        if (footerState != oldValue) {
            notifyItemChanged(statuses.size());
        }
    }

    public void setMediaPreviewEnabled(boolean enabled) {
        mediaPreviewEnabled = enabled;
    }
}
