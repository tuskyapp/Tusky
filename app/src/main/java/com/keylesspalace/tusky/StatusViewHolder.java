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

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;

import java.net.URL;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StatusViewHolder extends RecyclerView.ViewHolder {
    private View container;
    private TextView displayName;
    private TextView username;
    private TextView sinceCreated;
    private TextView content;
    private NetworkImageView avatar;
    private ImageView boostedIcon;
    private TextView boostedByUsername;
    private ImageButton replyButton;
    private ImageButton reblogButton;
    private ImageButton favouriteButton;
    private ImageButton moreButton;
    private boolean favourited;
    private boolean reblogged;
    private NetworkImageView mediaPreview0;
    private NetworkImageView mediaPreview1;
    private NetworkImageView mediaPreview2;
    private NetworkImageView mediaPreview3;
    private View sensitiveMediaWarning;

    public StatusViewHolder(View itemView) {
        super(itemView);
        container = itemView.findViewById(R.id.status_container);
        displayName = (TextView) itemView.findViewById(R.id.status_display_name);
        username = (TextView) itemView.findViewById(R.id.status_username);
        sinceCreated = (TextView) itemView.findViewById(R.id.status_since_created);
        content = (TextView) itemView.findViewById(R.id.status_content);
        avatar = (NetworkImageView) itemView.findViewById(R.id.status_avatar);
        boostedIcon = (ImageView) itemView.findViewById(R.id.status_boosted_icon);
        boostedByUsername = (TextView) itemView.findViewById(R.id.status_boosted);
        replyButton = (ImageButton) itemView.findViewById(R.id.status_reply);
        reblogButton = (ImageButton) itemView.findViewById(R.id.status_reblog);
        favouriteButton = (ImageButton) itemView.findViewById(R.id.status_favourite);
        moreButton = (ImageButton) itemView.findViewById(R.id.status_more);
        reblogged = false;
        favourited = false;
        mediaPreview0 = (NetworkImageView) itemView.findViewById(R.id.status_media_preview_0);
        mediaPreview1 = (NetworkImageView) itemView.findViewById(R.id.status_media_preview_1);
        mediaPreview2 = (NetworkImageView) itemView.findViewById(R.id.status_media_preview_2);
        mediaPreview3 = (NetworkImageView) itemView.findViewById(R.id.status_media_preview_3);
        mediaPreview0.setDefaultImageResId(R.drawable.media_preview_unloaded);
        mediaPreview1.setDefaultImageResId(R.drawable.media_preview_unloaded);
        mediaPreview2.setDefaultImageResId(R.drawable.media_preview_unloaded);
        mediaPreview3.setDefaultImageResId(R.drawable.media_preview_unloaded);
        sensitiveMediaWarning = itemView.findViewById(R.id.status_sensitive_media_warning);
    }

    public void setDisplayName(String name) {
        displayName.setText(name);
    }

    public void setUsername(String name) {
        Context context = username.getContext();
        String format = context.getString(R.string.status_username_format);
        String usernameText = String.format(format, name);
        username.setText(usernameText);
    }

    public void setContent(Spanned content, final StatusActionListener listener) {
        // Redirect URLSpan's in the status content to the listener for viewing tag pages.
        SpannableStringBuilder builder = new SpannableStringBuilder(content);
        URLSpan[] urlSpans = content.getSpans(0, content.length(), URLSpan.class);
        for (URLSpan span : urlSpans) {
            int start = builder.getSpanStart(span);
            int end = builder.getSpanEnd(span);
            int flags = builder.getSpanFlags(span);
            CharSequence tag = builder.subSequence(start, end);
            if (tag.charAt(0) == '#') {
                final String viewTag = tag.subSequence(1, tag.length()).toString();
                ClickableSpan newSpan = new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        listener.onViewTag(viewTag);
                    }
                };
                builder.removeSpan(span);
                builder.setSpan(newSpan, start, end, flags);
            }
        }
        // Set the contents.
        this.content.setText(builder);
        // Make links clickable.
        this.content.setLinksClickable(true);
        this.content.setMovementMethod(LinkMovementMethod.getInstance());
    }

    public void setAvatar(String url) {
        Context context = avatar.getContext();
        ImageLoader imageLoader = VolleySingleton.getInstance(context).getImageLoader();
        avatar.setImageUrl(url, imageLoader);
        avatar.setDefaultImageResId(R.drawable.avatar_default);
        avatar.setErrorImageResId(R.drawable.avatar_error);
    }

    public void setCreatedAt(@Nullable Date createdAt) {
        String readout;
        if (createdAt != null) {
            long then = createdAt.getTime();
            long now = new Date().getTime();
            readout = DateUtils.getRelativeTimeSpanString(then, now);
        } else {
            readout = "?m"; // unknown minutes~
        }
        sinceCreated.setText(readout);
    }

    public void setRebloggedByUsername(String name) {
        Context context = boostedByUsername.getContext();
        String format = context.getString(R.string.status_boosted_format);
        String boostedText = String.format(format, name);
        boostedByUsername.setText(boostedText);
        boostedIcon.setVisibility(View.VISIBLE);
        boostedByUsername.setVisibility(View.VISIBLE);
    }

    public void hideRebloggedByUsername() {
        boostedIcon.setVisibility(View.GONE);
        boostedByUsername.setVisibility(View.GONE);
    }

    public void setReblogged(boolean reblogged) {
        this.reblogged = reblogged;
        if (!reblogged) {
            reblogButton.setImageResource(R.drawable.ic_reblog_off);
        } else {
            reblogButton.setImageResource(R.drawable.ic_reblog_on);
        }
    }

    public void disableReblogging() {
        reblogButton.setEnabled(false);
        reblogButton.setImageResource(R.drawable.ic_reblog_disabled);
    }

    public void setFavourited(boolean favourited) {
        this.favourited = favourited;
        if (!favourited) {
            favouriteButton.setImageResource(R.drawable.ic_favourite_off);
        } else {
            favouriteButton.setImageResource(R.drawable.ic_favourite_on);
        }
    }

    public void setMediaPreviews(final Status.MediaAttachment[] attachments,
                                 boolean sensitive, final StatusActionListener listener) {
        final NetworkImageView[] previews = {
                mediaPreview0,
                mediaPreview1,
                mediaPreview2,
                mediaPreview3
        };
        Context context = mediaPreview0.getContext();
        ImageLoader imageLoader = VolleySingleton.getInstance(context).getImageLoader();
        final int n = Math.min(attachments.length, Status.MAX_MEDIA_ATTACHMENTS);
        for (int i = 0; i < n; i++) {
            String previewUrl = attachments[i].getPreviewUrl();
            previews[i].setImageUrl(previewUrl, imageLoader);
            if (!sensitive) {
                previews[i].setVisibility(View.VISIBLE);
            } else {
                previews[i].setVisibility(View.GONE);
            }
            final String url = attachments[i].getUrl();
            final Status.MediaAttachment.Type type = attachments[i].getType();
            previews[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onViewMedia(url, type);
                }
            });
        }
        if (sensitive) {
            sensitiveMediaWarning.setVisibility(View.VISIBLE);
            sensitiveMediaWarning.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.setVisibility(View.GONE);
                    for (int i = 0; i < n; i++) {
                        previews[i].setVisibility(View.VISIBLE);
                    }
                    v.setOnClickListener(null);
                }
            });
        }
        // Hide any of the placeholder previews beyond the ones set.
        for (int i = n; i < Status.MAX_MEDIA_ATTACHMENTS; i++) {
            previews[i].setImageUrl(null, imageLoader);
            previews[i].setVisibility(View.GONE);
        }
    }

    public void hideSensitiveMediaWarning() {
        sensitiveMediaWarning.setVisibility(View.GONE);
    }

    public void setupButtons(final StatusActionListener listener, final int position) {

        replyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onReply(position);
            }
        });
        reblogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onReblog(!reblogged, position);
            }
        });
        favouriteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onFavourite(!favourited, position);
            }
        });
        moreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onMore(v, position);
            }
        });
        container.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onViewThread(position);
            }
        });
    }

    public void setupWithStatus(Status status, StatusActionListener listener, int position) {
        setDisplayName(status.getDisplayName());
        setUsername(status.getUsername());
        setCreatedAt(status.getCreatedAt());
        setContent(status.getContent(), listener);
        setAvatar(status.getAvatar());
        setReblogged(status.getReblogged());
        setFavourited(status.getFavourited());
        String rebloggedByUsername = status.getRebloggedByUsername();
        if (rebloggedByUsername == null) {
            hideRebloggedByUsername();
        } else {
            setRebloggedByUsername(rebloggedByUsername);
        }
        Status.MediaAttachment[] attachments = status.getAttachments();
        boolean sensitive = status.getSensitive();
        setMediaPreviews(attachments, sensitive, listener);
        /* A status without attachments is sometimes still marked sensitive, so it's necessary to
         * check both whether there are any attachments and if it's marked sensitive. */
        if (!sensitive || attachments.length == 0) {
            hideSensitiveMediaWarning();
        }
        setupButtons(listener, position);
        if (status.getVisibility() == Status.Visibility.PRIVATE) {
            disableReblogging();
        }
    }
}