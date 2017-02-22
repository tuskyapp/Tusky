/* Copyright 2017 Andrew Dawson
 *
 * This file is part of Tusky.
 *
 * Tusky is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky. If not, see
 * <http://www.gnu.org/licenses/>. */

package com.keylesspalace.tusky;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

class ThreadAdapter extends RecyclerView.Adapter implements AdapterItemRemover {
    private List<Status> statuses;
    private StatusActionListener statusActionListener;
    private int statusIndex;

    ThreadAdapter(StatusActionListener listener) {
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
        holder.setupWithStatus(status, statusActionListener, position);
    }

    @Override
    public int getItemCount() {
        return statuses.size();
    }

    Status getItem(int position) {
        return statuses.get(position);
    }

    public void removeItem(int position) {
        statuses.remove(position);
        notifyItemRemoved(position);
    }

    int insertStatus(Status status) {
        int i = statusIndex;
        statuses.add(i, status);
        notifyItemInserted(i);
        return i;
    }

    void addAncestors(List<Status> ancestors) {
        statusIndex = ancestors.size();
        statuses.addAll(0, ancestors);
        notifyItemRangeInserted(0, statusIndex);
    }

    void addDescendants(List<Status> descendants) {
        int end = statuses.size();
        statuses.addAll(descendants);
        notifyItemRangeInserted(end, descendants.size());
    }
}
