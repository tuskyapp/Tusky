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

package com.keylesspalace.tusky.components.timeline;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.adapter.PlaceholderViewHolder;
import com.keylesspalace.tusky.adapter.StatusViewHolder;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.util.StatusDisplayOptions;
import com.keylesspalace.tusky.viewdata.StatusViewData;

import java.util.List;

public final class TimelineAdapter extends RecyclerView.Adapter {

    public interface AdapterDataSource<T> {
        int getItemCount();

        T getItemAt(int pos);
    }

    private static final int VIEW_TYPE_STATUS = 0;
    private static final int VIEW_TYPE_PLACEHOLDER = 2;

    private final AdapterDataSource<StatusViewData> dataSource;
    private StatusDisplayOptions statusDisplayOptions;
    private final StatusActionListener statusListener;

    public TimelineAdapter(AdapterDataSource<StatusViewData> dataSource,
                           StatusDisplayOptions statusDisplayOptions,
                           StatusActionListener statusListener) {
        this.dataSource = dataSource;
        this.statusDisplayOptions = statusDisplayOptions;
        this.statusListener = statusListener;
    }

    public boolean getMediaPreviewEnabled() {
        return statusDisplayOptions.mediaPreviewEnabled();
    }

    public void setMediaPreviewEnabled(boolean mediaPreviewEnabled) {
        this.statusDisplayOptions = statusDisplayOptions.copy(
                statusDisplayOptions.animateAvatars(),
                mediaPreviewEnabled,
                statusDisplayOptions.useAbsoluteTime(),
                statusDisplayOptions.showBotOverlay(),
                statusDisplayOptions.useBlurhash(),
                statusDisplayOptions.cardViewMode(),
                statusDisplayOptions.confirmReblogs(),
                statusDisplayOptions.confirmFavourites(),
                statusDisplayOptions.hideStats(),
                statusDisplayOptions.animateEmojis()
        );
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
        bindViewHolder(viewHolder, position, null);
    }


    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position, @NonNull List payloads) {
        bindViewHolder(viewHolder, position, payloads);
    }

    private void bindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position, @Nullable List payloads) {
        StatusViewData status = dataSource.getItemAt(position);
        if (status instanceof StatusViewData.Placeholder) {
            PlaceholderViewHolder holder = (PlaceholderViewHolder) viewHolder;
            holder.setup(statusListener, ((StatusViewData.Placeholder) status).isLoading());
        } else if (status instanceof StatusViewData.Concrete) {
            StatusViewHolder holder = (StatusViewHolder) viewHolder;
            holder.setupWithStatus((StatusViewData.Concrete) status,
                    statusListener,
                    statusDisplayOptions,
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

    @Override
    public long getItemId(int position) {
        return dataSource.getItemAt(position).getViewDataId();
    }
}
