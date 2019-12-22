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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.viewdata.StatusViewData;

import java.util.List;

public final class TimelineAdapter extends RecyclerView.Adapter {

    public void setUseBlurhash(boolean useBlurhash) {
        this.useBlurhash = useBlurhash;
    }

    public interface AdapterDataSource<T> {
        int getItemCount();

        T getItemAt(int pos);
    }

    private static final int VIEW_TYPE_STATUS = 0;
    private static final int VIEW_TYPE_PLACEHOLDER = 2;

    private final AdapterDataSource<StatusViewData> dataSource;
    private final StatusActionListener statusListener;
    private boolean mediaPreviewEnabled;
    private boolean useAbsoluteTime;
    private boolean showBotOverlay;
    private boolean animateAvatar;
    private boolean useBlurhash;

    public TimelineAdapter(AdapterDataSource<StatusViewData> dataSource,
                           StatusActionListener statusListener) {
        this.dataSource = dataSource;
        this.statusListener = statusListener;
        mediaPreviewEnabled = true;
        useAbsoluteTime = false;
        showBotOverlay = true;
        animateAvatar = false;
        useBlurhash = true;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        switch (viewType) {
            default:
            case VIEW_TYPE_STATUS: {
                View view = LayoutInflater.from(viewGroup.getContext())
                        .inflate(R.layout.item_status, viewGroup, false);
                return new StatusViewHolder(view, useAbsoluteTime);
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
        bindViewHolder(viewHolder,position,null);
    }


    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position, @NonNull List payloads) {
        bindViewHolder(viewHolder,position,payloads);
    }

    private void bindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position, @Nullable List payloads){
        StatusViewData status = dataSource.getItemAt(position);
        if (status instanceof StatusViewData.Placeholder) {
            PlaceholderViewHolder holder = (PlaceholderViewHolder) viewHolder;
            holder.setup(statusListener, ((StatusViewData.Placeholder) status).isLoading());
        } else if (status instanceof StatusViewData.Concrete) {
            StatusViewHolder holder = (StatusViewHolder) viewHolder;
            holder.setupWithStatus((StatusViewData.Concrete) status,
                    statusListener,
                    mediaPreviewEnabled,
                    showBotOverlay,
                    animateAvatar,
                    useBlurhash,
                    payloads != null && !payloads.isEmpty() ? payloads.get(0) : null);
        }
    }
    @Override
    public int getItemCount() {
        return dataSource.getItemCount();
    }

    @Override
    public int getItemViewType(int position) {
        if (dataSource.getItemAt(position) instanceof StatusViewData.Placeholder) {
            return VIEW_TYPE_PLACEHOLDER;
        } else {
            return VIEW_TYPE_STATUS;
        }
    }

    public void setMediaPreviewEnabled(boolean enabled) {
        mediaPreviewEnabled = enabled;
    }

    public void setUseAbsoluteTime(boolean useAbsoluteTime){
        this.useAbsoluteTime = useAbsoluteTime;
    }

    public boolean getMediaPreviewEnabled() {
        return mediaPreviewEnabled;
    }

    public void setShowBotOverlay(boolean showBotOverlay) {
        this.showBotOverlay = showBotOverlay;
    }

    public void setAnimateAvatar(boolean animateAvatar) {
        this.animateAvatar = animateAvatar;
    }

    @Override
    public long getItemId(int position) {
        return dataSource.getItemAt(position).getViewDataId();
    }
}
