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
import com.keylesspalace.tusky.interfaces.AdapterItemRemover;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.entity.Status;

import java.util.ArrayList;
import java.util.List;

public class ThreadAdapter extends RecyclerView.Adapter implements AdapterItemRemover {
    private List<Status> statuses;
    private StatusActionListener statusActionListener;
    private int statusIndex;

    public ThreadAdapter(StatusActionListener listener) {
        this.statusActionListener = listener;
        this.statuses = new ArrayList<>();
        this.statusIndex = 0;
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
        Status status = statuses.get(position);
        holder.setupWithStatus(status, statusActionListener);
    }

    @Override
    public int getItemCount() {
        return statuses.size();
    }

    public Status getItem(int position) {
        return statuses.get(position);
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

    public int setStatus(Status status) {
        if (statuses.size() > 0 && statuses.get(statusIndex).equals(status)) {
            // Do not add this status on refresh, it's already in there.
            statuses.set(statusIndex, status);
            return statusIndex;
        }
        int i = statusIndex;
        statuses.add(i, status);
        notifyItemInserted(i);
        return i;
    }

    public void setContext(List<Status> ancestors, List<Status> descendants) {
        Status mainStatus = null;

        // In case of refresh, remove old ancestors and descendants first. We'll remove all blindly,
        // as we have no guarantee on their order to be the same as before
        int oldSize = statuses.size();
        if (oldSize > 0) {
            mainStatus = statuses.get(statusIndex);
            statuses.clear();
            notifyItemRangeRemoved(0, oldSize);
        }

        // Insert newly fetched ancestors
        statusIndex = ancestors.size();
        statuses.addAll(0, ancestors);
        notifyItemRangeInserted(0, statusIndex);

        if (mainStatus != null) {
            // In case we needed to delete everything (which is way easier than deleting
            // everything except one), re-insert the remaining status here.
            statuses.add(statusIndex, mainStatus);
            notifyItemInserted(statusIndex);
        }

        // Insert newly fetched descendants
        int end = statuses.size();
        statuses.addAll(descendants);
        notifyItemRangeInserted(end, descendants.size());
    }
}
