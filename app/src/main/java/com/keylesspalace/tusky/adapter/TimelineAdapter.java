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
import java.util.List;

public class TimelineAdapter extends RecyclerView.Adapter implements AdapterItemRemover {
    private static final int VIEW_TYPE_STATUS = 0;
    private static final int VIEW_TYPE_FOOTER = 1;

    public enum FooterState {
        EMPTY,
        END,
        LOADING
    }

    private List<Status> statuses;
    private StatusActionListener statusListener;
    private FooterState footerState = FooterState.END;

    public TimelineAdapter(StatusActionListener statusListener) {
        super();
        statuses = new ArrayList<>();
        this.statusListener = statusListener;
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
                View view;
                switch (footerState) {
                    default:
                    case LOADING:
                        view = LayoutInflater.from(viewGroup.getContext())
                                .inflate(R.layout.item_footer, viewGroup, false);
                        break;
                    case END: {
                        view = LayoutInflater.from(viewGroup.getContext())
                                .inflate(R.layout.item_footer_end, viewGroup, false);
                        break;
                    }
                    case EMPTY: {
                        view = LayoutInflater.from(viewGroup.getContext())
                                .inflate(R.layout.item_footer_empty, viewGroup, false);
                        break;
                    }
                }
                return new FooterViewHolder(view);
            }
        }
    }

    public void setFooterState(FooterState newFooterState) {
        FooterState oldValue = footerState;
        footerState = newFooterState;
        if (footerState != oldValue) {
            notifyItemChanged(statuses.size());
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        if (position < statuses.size()) {
            StatusViewHolder holder = (StatusViewHolder) viewHolder;
            Status status = statuses.get(position);
            holder.setupWithStatus(status, statusListener);
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

    public void update(List<Status> newStatuses) {
        if (newStatuses == null || newStatuses.isEmpty()) {
            return;
        }
        if (statuses.isEmpty()) {
            statuses = newStatuses;
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

    public void addItems(List<Status> newStatuses) {
        int end = statuses.size();
        statuses.addAll(newStatuses);
        notifyItemRangeInserted(end, newStatuses.size());
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
}
