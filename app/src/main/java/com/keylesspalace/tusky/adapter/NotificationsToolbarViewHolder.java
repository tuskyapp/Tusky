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

import android.view.View;
import android.widget.Button;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.interfaces.StatusActionListener;

import androidx.recyclerview.widget.RecyclerView;

public final class NotificationsToolbarViewHolder extends RecyclerView.ViewHolder {

    private Button clearNotifications;
    private Button showFilter;

    NotificationsToolbarViewHolder(View itemView) {
        super(itemView);
        clearNotifications = itemView.findViewById(R.id.buttonClear);
        showFilter = itemView.findViewById(R.id.buttonFilter);
    }

    public void setup(final NotificationsAdapter.NotificationActionListener listener, boolean isItemsExists) {
        showFilter.setVisibility(View.VISIBLE);
        clearNotifications.setVisibility(isItemsExists ? View.VISIBLE : View.GONE);
        if (listener != null) {
            showFilter.setOnClickListener(v -> listener.onShowFilterClick(showFilter));
            clearNotifications.setOnClickListener(v -> listener.onClearClick());
        }
    }
}