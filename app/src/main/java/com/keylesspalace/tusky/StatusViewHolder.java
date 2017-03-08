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
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

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
            final StatusActionListener listener) {
        /* Redirect URLSpan's in the status content to the listener for viewing tag pages and
         * account pages. */
        SpannableStringBuilder builder = new SpannableStringBuilder(content);
        URLSpan[] urlSpans = content.getSpans(0, content.length(), URLSpan.class);
        for (URLSpan span : urlSpans) {
            int start = builder.getSpanStart(span);
            int end = builder.getSpanEnd(span);
            int flags = builder.getSpanFlags(span);
            CharSequence text = builder.subSequence(start, end);
            if (text.charAt(0) == '#') {
                final String tag = text.subSequence(1, text.length()).toString();
                ClickableSpan newSpan = new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        listener.onViewTag(tag);
                    }
                };
                builder.removeSpan(span);
                builder.setSpan(newSpan, start, end, flags);
            } else if (text.charAt(0) == '@') {
                final String accountUsername = text.subSequence(1, text.length()).toString();
                String id = null;
                for (Status.Mention mention: mentions) {
                    if (mention.getUsername().equals(accountUsername)) {
                        id = mention.getId();
                    }
                }
                if (id != null) {
                    final String accountId = id;
                    ClickableSpan newSpan = new ClickableSpan() {
                        @Override
                        public void onClick(View widget) {
                            listener.onViewAccount(accountId);
                        }
                    };
                    builder.removeSpan(span);
                    builder.setSpan(newSpan, start, end, flags);
                }
            }
        }
        // Set the contents.
        this.content.setText(builder);
        // Make links clickable.
        this.content.setLinksClickable(true);
        this.content.setMovementMethod(LinkMovementMethod.getInstance());
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
                .into(avatar);
    }

    private void setCreatedAt(@Nullable Date createdAt) {
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
            String previewUrl = attachments[i].getPreviewUrl();

            previews[i].setVisibility(View.VISIBLE);

            Picasso.with(context)
                .load(previewUrl)
                .placeholder(mediaPreviewUnloadedId)
                .into(previews[i]);

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
                listener.onReply(getAdapterPosition());
            }
        });
        reblogButton.setEventListener(new SparkEventListener() {
            @Override
            public void onEvent(ImageView button, boolean buttonState) {
                listener.onReblog(!reblogged, getAdapterPosition());
            }
        });
        favouriteButton.setEventListener(new SparkEventListener() {
            @Override
            public void onEvent(ImageView button, boolean buttonState) {
                listener.onFavourite(!favourited, getAdapterPosition());
            }
        });
        moreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onMore(v, getAdapterPosition());
            }
        });
        /* Even though the content TextView is a child of the container, it won't respond to clicks
         * if it contains URLSpans without also setting its listener. The surrounding spans will
         * just eat the clicks instead of deferring to the parent listener, but WILL respond to a
         * listener directly on the TextView, for whatever reason. */
        View.OnClickListener viewThreadListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onViewThread(getAdapterPosition());
            }
        };
        content.setOnClickListener(viewThreadListener);
        container.setOnClickListener(viewThreadListener);
    }

    void setupWithStatus(Status status, StatusActionListener listener) {
        setDisplayName(status.getDisplayName());
        setUsername(status.getUsername());
        setCreatedAt(status.getCreatedAt());
        setContent(status.getContent(), status.getMentions(), listener);
        setAvatar(status.getAvatar());
        setReblogged(status.getReblogged());
        setFavourited(status.getFavourited());
        String rebloggedByDisplayName = status.getRebloggedByDisplayName();
        if (rebloggedByDisplayName == null) {
            hideRebloggedByDisplayName();
        } else {
            setRebloggedByDisplayName(rebloggedByDisplayName);
        }
        Status.MediaAttachment[] attachments = status.getAttachments();
        boolean sensitive = status.getSensitive();
        setMediaPreviews(attachments, sensitive, listener);
        /* A status without attachments is sometimes still marked sensitive, so it's necessary to
         * check both whether there are any attachments and if it's marked sensitive. */
        if (!sensitive || attachments.length == 0) {
            hideSensitiveMediaWarning();
        }
        setupButtons(listener, status.getAccountId());
        setRebloggingEnabled(status.getVisibility() != Status.Visibility.PRIVATE);
        if (status.getSpoilerText().isEmpty()) {
            hideSpoilerText();
        } else {
            setSpoilerText(status.getSpoilerText());
        }
    }
}