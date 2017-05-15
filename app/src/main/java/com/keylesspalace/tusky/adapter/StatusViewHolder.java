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
import android.text.Spanned;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.view.RoundedTransformation;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.util.DateUtils;
import com.keylesspalace.tusky.util.LinkHelper;
import com.keylesspalace.tusky.util.ThemeUtils;
import com.squareup.picasso.Picasso;
import com.varunest.sparkbutton.SparkButton;
import com.varunest.sparkbutton.SparkEventListener;

import java.util.Date;

class StatusViewHolder extends RecyclerView.ViewHolder {
    private View container;
    private TextView displayName;
    private TextView username;
    private TextView sinceCreated;
    private TextView content;
    private ImageView avatar;
    private View rebloggedBar;
    private TextView rebloggedByDisplayName;
    private ImageButton replyButton;
    private SparkButton reblogButton;
    private SparkButton favouriteButton;
    private ImageButton moreButton;
    private boolean favourited;
    private boolean reblogged;
    private ImageView mediaPreview0;
    private ImageView mediaPreview1;
    private ImageView mediaPreview2;
    private ImageView mediaPreview3;
    private View sensitiveMediaWarning;
    private View contentWarningBar;
    private TextView contentWarningDescription;
    private ToggleButton contentWarningButton;

    StatusViewHolder(View itemView) {
        super(itemView);
        container = itemView.findViewById(R.id.status_container);
        displayName = (TextView) itemView.findViewById(R.id.status_display_name);
        username = (TextView) itemView.findViewById(R.id.status_username);
        sinceCreated = (TextView) itemView.findViewById(R.id.status_since_created);
        content = (TextView) itemView.findViewById(R.id.status_content);
        avatar = (ImageView) itemView.findViewById(R.id.status_avatar);
        rebloggedBar = itemView.findViewById(R.id.status_reblogged_bar);
        rebloggedByDisplayName = (TextView) itemView.findViewById(R.id.status_reblogged);
        replyButton = (ImageButton) itemView.findViewById(R.id.status_reply);
        reblogButton = (SparkButton) itemView.findViewById(R.id.status_reblog);
        favouriteButton = (SparkButton) itemView.findViewById(R.id.status_favourite);
        moreButton = (ImageButton) itemView.findViewById(R.id.status_more);
        reblogged = false;
        favourited = false;
        mediaPreview0 = (ImageView) itemView.findViewById(R.id.status_media_preview_0);
        mediaPreview1 = (ImageView) itemView.findViewById(R.id.status_media_preview_1);
        mediaPreview2 = (ImageView) itemView.findViewById(R.id.status_media_preview_2);
        mediaPreview3 = (ImageView) itemView.findViewById(R.id.status_media_preview_3);
        sensitiveMediaWarning = itemView.findViewById(R.id.status_sensitive_media_warning);
        contentWarningBar = itemView.findViewById(R.id.status_content_warning_bar);
        contentWarningDescription =
                (TextView) itemView.findViewById(R.id.status_content_warning_description);
        contentWarningButton =
                (ToggleButton) itemView.findViewById(R.id.status_content_warning_button);
    }

    private void setDisplayName(String name) {
        displayName.setText(name);
    }

    private void setUsername(String name) {
        Context context = username.getContext();
        String format = context.getString(R.string.status_username_format);
        String usernameText = String.format(format, name);
        username.setText(usernameText);
    }

    private void setContent(Spanned content, Status.Mention[] mentions,
                            StatusActionListener listener) {
        /* Redirect URLSpan's in the status content to the listener for viewing tag pages and
         * account pages. */
        Context context = this.content.getContext();
        boolean useCustomTabs = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("useCustomTabs", true);
        LinkHelper.setClickableText(this.content, content, mentions, useCustomTabs, listener);
    }

    private void setAvatar(String url) {
        if (url.isEmpty()) {
            return;
        }
        Context context = avatar.getContext();
        Picasso.with(context)
                .load(url)
                .placeholder(R.drawable.avatar_default)
                .error(R.drawable.avatar_error)
                .transform(new RoundedTransformation(7, 0))
                .into(avatar);
    }

    private void setCreatedAt(@Nullable Date createdAt) {
        // This is the visible timestamp.
        String readout;
        /* This one is for screen-readers. Frequently, they would mispronounce timestamps like "17m"
         * as 17 meters instead of minutes. */
        CharSequence readoutAloud;
        if (createdAt != null) {
            long then = createdAt.getTime();
            long now = new Date().getTime();
            readout = DateUtils.getRelativeTimeSpanString(then, now);
            readoutAloud = android.text.format.DateUtils.getRelativeTimeSpanString(then, now,
                    android.text.format.DateUtils.SECOND_IN_MILLIS,
                    android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE);
        } else {
            // unknown minutes~
            readout = "?m";
            readoutAloud = "? minutes";
        }
        sinceCreated.setText(readout);
        sinceCreated.setContentDescription(readoutAloud);
    }

    private void setRebloggedByDisplayName(String name) {
        Context context = rebloggedByDisplayName.getContext();
        String format = context.getString(R.string.status_boosted_format);
        String boostedText = String.format(format, name);
        rebloggedByDisplayName.setText(boostedText);
        rebloggedBar.setVisibility(View.VISIBLE);
    }

    private void hideRebloggedByDisplayName() {
        rebloggedBar.setVisibility(View.GONE);
    }

    private void setReblogged(boolean reblogged) {
        this.reblogged = reblogged;
        reblogButton.setChecked(reblogged);
    }

    /** This should only be called after setReblogged, in order to override the tint correctly. */
    private void setRebloggingEnabled(boolean enabled) {
        reblogButton.setEnabled(enabled);

        if (enabled) {
            int inactiveId = ThemeUtils.getDrawableId(reblogButton.getContext(),
                    R.attr.status_reblog_inactive_drawable, R.drawable.reblog_inactive_dark);
            reblogButton.setInactiveImage(inactiveId);
            reblogButton.setActiveImage(R.drawable.reblog_active);
        } else {
            int disabledId = ThemeUtils.getDrawableId(reblogButton.getContext(),
                    R.attr.status_reblog_disabled_drawable, R.drawable.reblog_disabled_dark);
            reblogButton.setInactiveImage(disabledId);
            reblogButton.setActiveImage(disabledId);
        }
    }

    private void setFavourited(boolean favourited) {
        this.favourited = favourited;
        favouriteButton.setChecked(favourited);
    }

    private void setMediaPreviews(final Status.MediaAttachment[] attachments,
                                  boolean sensitive, final StatusActionListener listener) {
        final ImageView[] previews = {
                mediaPreview0,
                mediaPreview1,
                mediaPreview2,
                mediaPreview3
        };
        Context context = mediaPreview0.getContext();

        int mediaPreviewUnloadedId = ThemeUtils.getDrawableId(itemView.getContext(),
                R.attr.media_preview_unloaded_drawable, android.R.color.black);

        final int n = Math.min(attachments.length, Status.MAX_MEDIA_ATTACHMENTS);

        for (int i = 0; i < n; i++) {
            String previewUrl = attachments[i].previewUrl;

            previews[i].setVisibility(View.VISIBLE);

            if(previewUrl == null || previewUrl.isEmpty()) {
                Picasso.with(context)
                        .load(mediaPreviewUnloadedId)
                        .into(previews[i]);
            } else {
                Picasso.with(context)
                        .load(previewUrl)
                        .placeholder(mediaPreviewUnloadedId)
                        .into(previews[i]);
            }

            final String url = attachments[i].url;
            final Status.MediaAttachment.Type type = attachments[i].type;

            if(url == null || url.isEmpty()) {
                previews[i].setOnClickListener(null);
            } else {
                previews[i].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onViewMedia(url, type);
                    }
                });
            }


        }

        if (sensitive) {
            sensitiveMediaWarning.setVisibility(View.VISIBLE);
            sensitiveMediaWarning.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.setVisibility(View.GONE);
                    v.setOnClickListener(null);
                }
            });
        }

        // Hide any of the placeholder previews beyond the ones set.
        for (int i = n; i < Status.MAX_MEDIA_ATTACHMENTS; i++) {
            previews[i].setVisibility(View.GONE);
        }
    }

    private void hideSensitiveMediaWarning() {
        sensitiveMediaWarning.setVisibility(View.GONE);
    }

    private void setSpoilerText(String spoilerText) {
        contentWarningDescription.setText(spoilerText);
        contentWarningBar.setVisibility(View.VISIBLE);
        content.setVisibility(View.GONE);
        contentWarningButton.setChecked(false);
        contentWarningButton.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            content.setVisibility(View.VISIBLE);
                        } else {
                            content.setVisibility(View.GONE);
                        }
                    }
                });
    }

    private void hideSpoilerText() {
        contentWarningBar.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
    }

    private void setupButtons(final StatusActionListener listener, final String accountId) {
        /* Originally position was passed through to all these listeners, but it caused several
         * bugs where other statuses in the list would be removed or added and cause the position
         * here to become outdated. So, getting the adapter position at the time the listener is
         * actually called is the appropriate solution. */
        avatar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onViewAccount(accountId);
            }
        });
        replyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onReply(position);
                }
            }
        });
        reblogButton.setEventListener(new SparkEventListener() {
                                          @Override
                                          public void onEvent(ImageView button, boolean buttonState) {
                                              int position = getAdapterPosition();
                                              if (position != RecyclerView.NO_POSITION) {
                                                  listener.onReblog(!reblogged, position);
                                              }
                                          }

                                          @Override
                                          public void onEventAnimationEnd(ImageView button, boolean buttonState) {}

                                          @Override
                                          public void onEventAnimationStart(ImageView button, boolean buttonState) {}
                                      });
        favouriteButton.setEventListener(new SparkEventListener() {
            @Override
            public void onEvent(ImageView button, boolean buttonState) {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onFavourite(!favourited, position);
                }
            }

            @Override
            public void onEventAnimationEnd(ImageView button, boolean buttonState) {}

            @Override
            public void onEventAnimationStart(ImageView button, boolean buttonState) {}
        });
        moreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onMore(v, position);
                }
            }
        });
        /* Even though the content TextView is a child of the container, it won't respond to clicks
         * if it contains URLSpans without also setting its listener. The surrounding spans will
         * just eat the clicks instead of deferring to the parent listener, but WILL respond to a
         * listener directly on the TextView, for whatever reason. */
        View.OnClickListener viewThreadListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onViewThread(position);
                }
            }
        };
        content.setOnClickListener(viewThreadListener);
        container.setOnClickListener(viewThreadListener);
    }

    void setupWithStatus(Status status, StatusActionListener listener) {
        Status realStatus = status.getActionableStatus();

        setDisplayName(realStatus.account.getDisplayName());
        setUsername(realStatus.account.username);
        setCreatedAt(realStatus.createdAt);
        setContent(realStatus.content, realStatus.mentions, listener);
        setAvatar(realStatus.account.avatar);
        setReblogged(realStatus.reblogged);
        setFavourited(realStatus.favourited);
        String rebloggedByDisplayName = status.account.getDisplayName();
        if (status.reblog == null) {
            hideRebloggedByDisplayName();
        } else {
            setRebloggedByDisplayName(rebloggedByDisplayName);
        }
        Status.MediaAttachment[] attachments = realStatus.attachments;
        boolean sensitive = realStatus.sensitive;
        setMediaPreviews(attachments, sensitive, listener);
        /* A status without attachments is sometimes still marked sensitive, so it's necessary to
         * check both whether there are any attachments and if it's marked sensitive. */
        if (!sensitive || attachments.length == 0) {
            hideSensitiveMediaWarning();
        }
        setupButtons(listener, realStatus.account.id);
        setRebloggingEnabled(status.rebloggingAllowed());
        if (realStatus.spoilerText.isEmpty()) {
            hideSpoilerText();
        } else {
            setSpoilerText(realStatus.spoilerText);
        }
    }
}