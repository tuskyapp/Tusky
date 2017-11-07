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
import android.os.Build;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.view.RoundedTransformation;
import com.keylesspalace.tusky.viewdata.StatusViewData;
import com.squareup.picasso.Picasso;
import com.varunest.sparkbutton.helpers.Utils;

public class StatusViewHolder extends StatusBaseViewHolder {
    private ImageView avatarReblog;
    private View rebloggedBar;
    private TextView rebloggedByDisplayName;

    StatusViewHolder(View itemView) {
        super(itemView);
        avatarReblog = itemView.findViewById(R.id.status_avatar_reblog);
        rebloggedBar = itemView.findViewById(R.id.status_reblogged_bar);
        rebloggedByDisplayName = itemView.findViewById(R.id.status_reblogged);
    }

    @Override
    void setAvatar(String url, @Nullable String rebloggedUrl) {
        super.setAvatar(url, rebloggedUrl);

        Context context = avatar.getContext();
        boolean hasReblog = rebloggedUrl != null && !rebloggedUrl.isEmpty();
        int padding = hasReblog ? Utils.dpToPx(context, 12) : 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            avatar.setPaddingRelative(0, 0, padding, padding);
        } else {
            avatar.setPadding(0, 0, padding, padding);
        }

        if (hasReblog) {
            avatarReblog.setVisibility(View.VISIBLE);
            Picasso.with(context)
                    .load(rebloggedUrl)
                    .fit()
                    .transform(new RoundedTransformation(7, 0))
                    .into(avatarReblog);
        } else {
            avatarReblog.setVisibility(View.GONE);
        }
    }

    @Override
    void setupWithStatus(StatusViewData.Concrete status, final StatusActionListener listener,
                         boolean mediaPreviewEnabled) {
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
        rebloggedBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onOpenReblog(getAdapterPosition());
            }
        });
    }

    private void setRebloggedByDisplayName(String name) {
        Context context = rebloggedByDisplayName.getContext();
        String format = context.getString(R.string.status_boosted_format);
        String boostedText = String.format(format, name);
        rebloggedByDisplayName.setText(boostedText);
        rebloggedBar.setVisibility(View.VISIBLE);
    }

    private void hideRebloggedByDisplayName() {
        if (rebloggedBar == null) {
            return;
        }
        rebloggedBar.setVisibility(View.GONE);
    }
}