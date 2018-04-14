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
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.keylesspalace.tusky.R;

import java.util.ArrayList;
import java.util.List;

public class ReportAdapter extends RecyclerView.Adapter {
    public static class ReportStatus {
        String id;
        Spanned content;
        boolean checked;

        public ReportStatus(String id, Spanned content, boolean checked) {
            this.id = id;
            this.content = content;
            this.checked = checked;
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (this.id == null) {
                return this == other;
            } else if (!(other instanceof ReportStatus)) {
                return false;
            }
            ReportStatus status = (ReportStatus) other;
            return status.id.equals(this.id);
        }
    }

    private List<ReportStatus> statusList;

    public ReportAdapter() {
        super();
        statusList = new ArrayList<>();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_report_status, parent, false);
        return new ReportStatusViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        ReportStatusViewHolder holder = (ReportStatusViewHolder) viewHolder;
        ReportStatus status = statusList.get(position);
        holder.setupWithStatus(status);
    }

    @Override
    public int getItemCount() {
        return statusList.size();
    }

    public void addItem(ReportStatus status) {
        int end = statusList.size();
        statusList.add(status);
        notifyItemInserted(end);
    }

    public void addItems(List<ReportStatus> newStatuses) {
        int end = statusList.size();
        int added = 0;
        for (ReportStatus status : newStatuses) {
            if (!statusList.contains(status)) {
                statusList.add(status);
                added += 1;
            }
        }
        if (added > 0) {
            notifyItemRangeInserted(end, added);
        }
    }

    public String[] getCheckedStatusIds() {
        List<String> idList = new ArrayList<>();
        for (ReportStatus status : statusList) {
            if (status.checked) {
                idList.add(status.id);
            }
        }
        return idList.toArray(new String[0]);
    }

    private static class ReportStatusViewHolder extends RecyclerView.ViewHolder {
        private TextView content;
        private CheckBox checkBox;

        ReportStatusViewHolder(View view) {
            super(view);
            content = view.findViewById(R.id.report_status_content);
            checkBox = view.findViewById(R.id.report_status_check_box);
        }

        void setupWithStatus(final ReportStatus status) {
            content.setText(status.content);
            checkBox.setChecked(status.checked);
            checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    status.checked = isChecked;
                }
            });
        }
    }
}
