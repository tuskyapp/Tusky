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
import com.keylesspalace.tusky.interfaces.AdapterItemRemover;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.entity.Status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class TimelineAdapter extends RecyclerView.Adapter implements AdapterItemRemover {
    private static final int VIEW_TYPE_STATUS = 0;
    private static final int VIEW_TYPE_FOOTER = 1;

    private List<Status> statuses;
    private StatusActionListener statusListener;
    private FooterViewHolder.State footerState;
    private boolean mediaPreviewEnabled;
    private String topId;
    private String bottomId;

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
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        if (position < statuses.size()) {
            StatusViewHolder holder = (StatusViewHolder) viewHolder;
            Status status = statuses.get(position);
            holder.setupWithStatus(status, statusListener, mediaPreviewEnabled);
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
            return VIEW_TYPE_STATUS;
        }
    }

    @Override
    public void removeItem(int position) {
        statuses.remove(position);
        notifyItemRemoved(position);
    }

    @Override
    public void removeAllByAccountId(String accountId) {
        for (int i = 0; i < statuses.size();) {
            Status status = statuses.get(i);
            if (accountId.equals(status.account.id)) {
                statuses.remove(i);
                notifyItemRemoved(i);
            } else {
                i += 1;
            }
        }
    }

    public void update(@Nullable List<Status> newStatuses, @Nullable String fromId,
            @Nullable String uptoId) {
        if (newStatuses == null || newStatuses.isEmpty()) {
            return;
        }
        if (fromId != null) {
            bottomId = fromId;
        }
        if (uptoId != null) {
            topId = uptoId;
        }
        if (statuses.isEmpty()) {
            // This construction removes duplicates.
            statuses = new ArrayList<>(new HashSet<>(newStatuses));
        } else {
            int index = statuses.indexOf(newStatuses.get(newStatuses.size() - 1));
            for (int i = 0; i < index; i++) {
                statuses.remove(0);
            }
            int newIndex = newStatuses.indexOf(statuses.get(0));
            if (newIndex == -1) {
                statuses.addAll(0, newStatuses);
            } else {
                statuses.addAll(0, newStatuses.subList(0, newIndex));
            }
        }
        notifyDataSetChanged();
    }

    public void addItems(List<Status> newStatuses, @Nullable String fromId) {
        if (fromId != null) {
            bottomId = fromId;
        }
        int end = statuses.size();
        Status last = statuses.get(end - 1);
        if (last != null && !findStatus(newStatuses, last.id)) {
            statuses.addAll(newStatuses);
            notifyItemRangeInserted(end, newStatuses.size());
        }
    }

    private static boolean findStatus(List<Status> statuses, String id) {
        for (Status status : statuses) {
            if (status.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        statuses.clear();
        notifyDataSetChanged();
    }

    @Nullable
    public Status getItem(int position) {
        if (position >= 0 && position < statuses.size()) {
            return statuses.get(position);
        }
        return null;
    }

    public void setFooterState(FooterViewHolder.State newFooterState) {
        footerState = newFooterState;
    }

    public void setMediaPreviewEnabled(boolean enabled) {
        mediaPreviewEnabled = enabled;
    }

    @Nullable
    public String getBottomId() {
        return bottomId;
    }

    @Nullable
    public String getTopId() {
        return topId;
    }
}
