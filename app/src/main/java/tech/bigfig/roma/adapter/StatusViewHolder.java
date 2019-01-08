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
import androidx.annotation.Nullable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import tech.bigfig.roma.R;
import tech.bigfig.roma.interfaces.StatusActionListener;
import tech.bigfig.roma.viewdata.StatusViewData;
import com.squareup.picasso.Picasso;

import at.connyduck.sparkbutton.helpers.Utils;

public class StatusViewHolder extends StatusBaseViewHolder {
    private ImageView avatarReblog;
    private TextView rebloggedBar;

    StatusViewHolder(View itemView, boolean useAbsoluteTime) {
        super(itemView, useAbsoluteTime);
        avatarReblog = itemView.findViewById(R.id.status_avatar_reblog);
        rebloggedBar = itemView.findViewById(R.id.status_reblogged);
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
            super.setupWithStatus(status, listener, mediaPreviewEnabled);

            String rebloggedByDisplayName = status.getRebloggedByUsername();
            if (rebloggedByDisplayName == null) {
                hideRebloggedByDisplayName();
            } else {
                setRebloggedByDisplayName(rebloggedByDisplayName);
            }

            // I think it's not efficient to create new object every time we bind a holder.
            // More efficient approach would be creating View.OnClickListener during holder creation
            // and storing StatusActionListener in a variable after binding.
            rebloggedBar.setOnClickListener(v -> listener.onOpenReblog(getAdapterPosition()));
        }
    }

    private void setRebloggedByDisplayName(String name) {
        Context context = rebloggedBar.getContext();
        String repostedText = context.getString(R.string.status_reposted_format, name);
        rebloggedBar.setText(repostedText);
        rebloggedBar.setVisibility(View.VISIBLE);
    }

    private void hideRebloggedByDisplayName() {
        if (rebloggedBar == null) {
            return;
        }
        rebloggedBar.setVisibility(View.GONE);
    }
}