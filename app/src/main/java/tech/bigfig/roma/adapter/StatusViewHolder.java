/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma.adapter;

import android.content.Context;
import android.text.InputFilter;
import android.view.View;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.bumptech.glide.Glide;
import tech.bigfig.roma.R;
import tech.bigfig.roma.interfaces.StatusActionListener;
import tech.bigfig.roma.util.SmartLengthInputFilter;
import tech.bigfig.roma.viewdata.StatusViewData;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import at.connyduck.sparkbutton.helpers.Utils;

public class StatusViewHolder extends StatusBaseViewHolder {
    private static final InputFilter[] COLLAPSE_INPUT_FILTER = new InputFilter[]{SmartLengthInputFilter.INSTANCE};
    private static final InputFilter[] NO_INPUT_FILTER = new InputFilter[0];

    private TextView statusInfo;
    private ToggleButton contentCollapseButton;

    StatusViewHolder(View itemView, boolean useAbsoluteTime) {
        super(itemView, useAbsoluteTime);
        statusInfo = itemView.findViewById(R.id.status_info);
        contentCollapseButton = itemView.findViewById(R.id.button_toggle_content);
    }

    @Override
    protected void setAvatar(String url, @Nullable String rebloggedUrl, boolean isBot) {
        super.setAvatar(url, rebloggedUrl, isBot);
        Context context = avatar.getContext();

        boolean hasReblog = rebloggedUrl != null && !rebloggedUrl.isEmpty();
        int padding = hasReblog ? Utils.dpToPx(context, 12) : 0;

        avatar.setPaddingRelative(0, 0, padding, padding);

        if (hasReblog) {
            avatarInset.setVisibility(View.VISIBLE);
            Glide.with(context)
                    .load(rebloggedUrl)
                    .placeholder(R.drawable.avatar_default)
                    .into(avatarInset);
        }
    }

    @Override
    protected int getMediaPreviewHeight(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.status_media_preview_height);
    }

    @Override
    protected void setupWithStatus(StatusViewData.Concrete status, final StatusActionListener listener,
                                   boolean mediaPreviewEnabled, @Nullable Object payloads) {
        if (status == null || payloads == null) {
            if (status == null) {
                showContent(false);
            } else {
                showContent(true);
                setupCollapsedState(status, listener);
                super.setupWithStatus(status, listener, mediaPreviewEnabled, null);

                String rebloggedByDisplayName = status.getRebloggedByUsername();
                if (rebloggedByDisplayName == null) {
                    hideStatusInfo();
                } else {
                    setRebloggedByDisplayName(rebloggedByDisplayName);
                    statusInfo.setOnClickListener(v -> listener.onOpenReblog(getAdapterPosition()));
                }

            }
        } else {
            super.setupWithStatus(status, listener, mediaPreviewEnabled, payloads);
        }
    }

    private void setRebloggedByDisplayName(final String name) {
        Context context = statusInfo.getContext();
        String repostedText = context.getString(R.string.status_reposted_format, name);
        statusInfo.setText(repostedText);
        statusInfo.setVisibility(View.VISIBLE);
    }

    // don't use this on the same ViewHolder as setRebloggedByDisplayName, will cause recycling issues as paddings are changed
    void setPollInfo(final boolean ownPoll) {
        statusInfo.setText(ownPoll ? R.string.poll_ended_created : R.string.poll_ended_voted);
        statusInfo.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_poll_24dp, 0, 0, 0);
        statusInfo.setCompoundDrawablePadding(Utils.dpToPx(statusInfo.getContext(), 10));
        statusInfo.setPaddingRelative(Utils.dpToPx(statusInfo.getContext(), 28), 0, 0, 0);
        statusInfo.setVisibility(View.VISIBLE);
    }

    void hideStatusInfo() {
        if (statusInfo == null) {
            return;
        }
        statusInfo.setVisibility(View.GONE);
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
