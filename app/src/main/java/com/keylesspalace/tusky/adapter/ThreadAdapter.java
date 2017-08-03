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

import android.content.Context;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.util.CustomTabURLSpan;
import com.keylesspalace.tusky.viewdata.StatusViewData;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ThreadAdapter extends RecyclerView.Adapter {
    private static final int VIEW_TYPE_STATUS = 0;
    private static final int VIEW_TYPE_STATUS_DETAILED = 1;

    private List<StatusViewData> statuses;
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
        if (position == detailedStatusPosition) {
            StatusDetailedViewHolder holder = (StatusDetailedViewHolder) viewHolder;
            StatusViewData status = statuses.get(position);
            holder.setupWithStatus(status, statusActionListener, mediaPreviewEnabled);
        } else {
            StatusViewHolder holder = (StatusViewHolder) viewHolder;
            StatusViewData status = statuses.get(position);
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
        detailedStatusPosition = RecyclerView.NO_POSITION;
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
        detailedStatusPosition = RecyclerView.NO_POSITION;
        notifyDataSetChanged();
    }

    public void setItem(int position, StatusViewData status, boolean notifyAdapter) {
        statuses.set(position, status);
        if (notifyAdapter) {
            notifyItemChanged(position);
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

    private static class StatusDetailedViewHolder extends StatusViewHolder {
        private TextView reblogs;
        private TextView favourites;
        private TextView application;

        StatusDetailedViewHolder(View view) {
            super(view);
            reblogs = (TextView) view.findViewById(R.id.status_reblogs);
            favourites = (TextView) view.findViewById(R.id.status_favourites);
            application = (TextView) view.findViewById(R.id.status_application);
        }

        @Override
        protected void setCreatedAt(@Nullable Date createdAt) {
            if (createdAt != null) {
                DateFormat dateFormat = android.text.format.DateFormat.getMediumDateFormat(
                        timestamp.getContext());
                timestamp.setText(dateFormat.format(createdAt));
            } else {
                timestamp.setText("");
            }
        }

        private void setApplication(@Nullable Status.Application app) {
            if (app == null) {
                return;
            }
            if (app.website != null) {
                URLSpan span;
                Context context = application.getContext();
                boolean useCustomTabs = PreferenceManager.getDefaultSharedPreferences(context)
                        .getBoolean("customTabs", true);
                if (useCustomTabs) {
                    span = new CustomTabURLSpan(app.website);
                } else {
                    span = new URLSpan(app.website);
                }
                SpannableStringBuilder text = new SpannableStringBuilder(app.name);
                text.setSpan(span, 0, app.name.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                application.setText(text);
                application.setMovementMethod(LinkMovementMethod.getInstance());
            } else {
                application.setText(app.name);
            }
        }

        @Override
        void setupWithStatus(StatusViewData status, final StatusActionListener listener,
                             boolean mediaPreviewEnabled) {
            super.setupWithStatus(status, listener, mediaPreviewEnabled);
            reblogs.setText(status.getReblogsCount());
            favourites.setText(status.getFavouritesCount());
            setApplication(status.getApplication());
        }
    }
}
