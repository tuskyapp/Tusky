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
import androidx.annotation.Nullable;

import android.text.InputFilter;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.util.SmartLengthInputFilter;
import com.keylesspalace.tusky.viewdata.StatusViewData;
import com.squareup.picasso.Picasso;

import androidx.recyclerview.widget.RecyclerView;
import at.connyduck.sparkbutton.helpers.Utils;

public class StatusViewHolder extends StatusBaseViewHolder {
    private static final InputFilter[] COLLAPSE_INPUT_FILTER = new InputFilter[]{SmartLengthInputFilter.INSTANCE};
    private static final InputFilter[] NO_INPUT_FILTER = new InputFilter[0];

    private ImageView avatarReblog;
    private TextView rebloggedBar;
    private ToggleButton contentCollapseButton;

    StatusViewHolder(View itemView, boolean useAbsoluteTime) {
        super(itemView, useAbsoluteTime);
        avatarReblog = itemView.findViewById(R.id.status_avatar_reblog);
        rebloggedBar = itemView.findViewById(R.id.status_reblogged);
        contentCollapseButton = itemView.findViewById(R.id.button_toggle_content);
    }

    @Override
    void setAvatar(String url, @Nullable String rebloggedUrl) {
        super.setAvatar(url, rebloggedUrl);

        Context context = avatar.getContext();
        boolean hasReblog = rebloggedUrl != null && !rebloggedUrl.isEmpty();
        int padding = hasReblog ? Utils.dpToPx(context, 12) : 0;

        avatar.setPaddingRelative(0, 0, padding, padding);

        if (hasReblog) {
            avatarReblog.setVisibility(View.VISIBLE);
            Picasso.with(context)
                    .load(rebloggedUrl)
                    .placeholder(R.drawable.avatar_default)
                    .into(avatarReblog);
        } else {
            avatarReblog.setVisibility(View.GONE);
        }
    }

    @Override
    protected int getMediaPreviewHeight(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.status_media_preview_height);
    }

    @Override
    void setupWithStatus(StatusViewData.Concrete status, final StatusActionListener listener,
                         boolean mediaPreviewEnabled) {
        if(status == null) {
            showContent(false);
        } else {
            showContent(true);
            setupCollapsedState(status, listener);
            super.setupWithStatus(status, listener, mediaPreviewEnabled);

            String rebloggedByDisplayName = status.getRebloggedByUsername();
            if (rebloggedByDisplayName == null) {
                hideRebloggedByDisplayName();
            } else {
                setRebloggedByDisplayName(rebloggedByDisplayName);
            }

            rebloggedBar.setOnClickListener(v -> listener.onOpenReblog(getAdapterPosition()));

        }

    }

    private void setRebloggedByDisplayName(final String name) {
        Context context = rebloggedBar.getContext();
        String boostedText = context.getString(R.string.status_boosted_format, name);
        rebloggedBar.setText(boostedText);
        rebloggedBar.setVisibility(View.VISIBLE);
    }

    private void hideRebloggedByDisplayName() {
        if (rebloggedBar == null) {
            return;
        }
        rebloggedBar.setVisibility(View.GONE);
    }

    private void setupCollapsedState(final StatusViewData.Concrete status, final StatusActionListener listener) {
        /* input filter for TextViews have to be set before text */
        if (status.isCollapsible() && (status.isExpanded() || status.getSpoilerText() == null || status.getSpoilerText().isEmpty())) {
            contentCollapseButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION)
                    listener.onContentCollapsedChange(isChecked, position);
            });

            contentCollapseButton.setVisibility(View.VISIBLE);
            if (status.isCollapsed()) {
                contentCollapseButton.setChecked(true);
                content.setFilters(COLLAPSE_INPUT_FILTER);
            } else {
                contentCollapseButton.setChecked(false);
                content.setFilters(NO_INPUT_FILTER);
            }
        } else {
            contentCollapseButton.setVisibility(View.GONE);
            content.setFilters(NO_INPUT_FILTER);
        }
    }
}