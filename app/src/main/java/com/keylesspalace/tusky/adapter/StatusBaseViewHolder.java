package com.keylesspalace.tusky.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.widget.RecyclerView;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Attachment;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.util.CustomEmojiHelper;
import com.keylesspalace.tusky.util.DateUtils;
import com.keylesspalace.tusky.util.HtmlUtils;
import com.keylesspalace.tusky.util.LinkHelper;
import com.keylesspalace.tusky.util.ThemeUtils;
import com.keylesspalace.tusky.view.RoundedTransformation;
import com.keylesspalace.tusky.viewdata.StatusViewData;
import com.mikepenz.iconics.utils.Utils;
import com.squareup.picasso.Picasso;
import com.varunest.sparkbutton.SparkButton;
import com.varunest.sparkbutton.SparkEventListener;

import java.util.Date;
import java.util.List;

abstract class StatusBaseViewHolder extends RecyclerView.ViewHolder {
    private View container;
    private TextView displayName;
    private TextView username;
    private TextView content;
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
    private ImageView mediaOverlay0;
    private ImageView mediaOverlay1;
    private ImageView mediaOverlay2;
    private ImageView mediaOverlay3;
    private TextView sensitiveMediaWarning;
    private View sensitiveMediaShow;
    private TextView mediaLabel;
    private View contentWarningBar;
    private TextView contentWarningDescription;
    private ToggleButton contentWarningButton;

    ImageView avatar;
    TextView timestampInfo;

    StatusBaseViewHolder(View itemView) {
        super(itemView);
        container = itemView.findViewById(R.id.status_container);
        displayName = itemView.findViewById(R.id.status_display_name);
        username = itemView.findViewById(R.id.status_username);
        timestampInfo = itemView.findViewById(R.id.status_timestamp_info);
        content = itemView.findViewById(R.id.status_content);
        avatar = itemView.findViewById(R.id.status_avatar);
        replyButton = itemView.findViewById(R.id.status_reply);
        reblogButton = itemView.findViewById(R.id.status_reblog);
        favouriteButton = itemView.findViewById(R.id.status_favourite);
        moreButton = itemView.findViewById(R.id.status_more);
        reblogged = false;
        favourited = false;
        mediaPreview0 = itemView.findViewById(R.id.status_media_preview_0);
        mediaPreview1 = itemView.findViewById(R.id.status_media_preview_1);
        mediaPreview2 = itemView.findViewById(R.id.status_media_preview_2);
        mediaPreview3 = itemView.findViewById(R.id.status_media_preview_3);
        mediaOverlay0 = itemView.findViewById(R.id.status_media_overlay_0);
        mediaOverlay1 = itemView.findViewById(R.id.status_media_overlay_1);
        mediaOverlay2 = itemView.findViewById(R.id.status_media_overlay_2);
        mediaOverlay3 = itemView.findViewById(R.id.status_media_overlay_3);
        sensitiveMediaWarning = itemView.findViewById(R.id.status_sensitive_media_warning);
        sensitiveMediaShow = itemView.findViewById(R.id.status_sensitive_media_button);
        mediaLabel = itemView.findViewById(R.id.status_media_label);
        contentWarningBar = itemView.findViewById(R.id.status_content_warning_bar);
        contentWarningDescription = itemView.findViewById(R.id.status_content_warning_description);
        contentWarningButton = itemView.findViewById(R.id.status_content_warning_button);
    }

    protected abstract int getMediaPreviewHeight(Context context);

    private void setDisplayName(String name) {
        displayName.setText(name);
    }

    private void setUsername(String name) {
        Context context = username.getContext();
        String format = context.getString(R.string.status_username_format);
        String usernameText = String.format(format, name);
        username.setText(usernameText);
    }

    private void setContent(Spanned content, Status.Mention[] mentions, List<Status.Emoji> emojis,
                            StatusActionListener listener) {
        Spanned emojifiedText = CustomEmojiHelper.emojifyText(content, emojis, this.content);

        LinkHelper.setClickableText(this.content, emojifiedText, mentions, listener);
    }

    void setAvatar(String url, @Nullable String rebloggedUrl) {
        if (url.isEmpty()) {
            avatar.setImageResource(R.drawable.avatar_default);
        } else {
            Picasso.with(avatar.getContext())
                    .load(url)
                    .placeholder(R.drawable.avatar_default)
                    .transform(new RoundedTransformation(7, 0))
                    .into(avatar);
        }
    }

    protected void setCreatedAt(@Nullable Date createdAt) {
        // This is the visible timestampInfo.
        String readout;
        /* This one is for screen-readers. Frequently, they would mispronounce timestamps like "17m"
         * as 17 meters instead of minutes. */
        CharSequence readoutAloud;
        if (createdAt != null) {
            long then = createdAt.getTime();
            long now = new Date().getTime();
            readout = DateUtils.getRelativeTimeSpanString(timestampInfo.getContext(), then, now);
            readoutAloud = android.text.format.DateUtils.getRelativeTimeSpanString(then, now,
                    android.text.format.DateUtils.SECOND_IN_MILLIS,
                    android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE);
        } else {
            // unknown minutes~
            readout = "?m";
            readoutAloud = "? minutes";
        }
        timestampInfo.setText(readout);
        timestampInfo.setContentDescription(readoutAloud);
    }

    protected void showContent(boolean show) {
        if(show) {
            container.setVisibility(View.VISIBLE);
            container.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        } else {
            container.setVisibility(View.INVISIBLE);
            container.getLayoutParams().height = Utils.convertDpToPx(container.getContext(), 24);
        }
    }

    private void setIsReply(boolean isReply) {
        if(isReply) {
            replyButton.setImageResource(R.drawable.ic_reply_all_24dp);
        } else {
            replyButton.setImageResource(R.drawable.ic_reply_24dp);
        }

    }

    private void setReblogged(boolean reblogged) {
        this.reblogged = reblogged;
        reblogButton.setChecked(reblogged);
    }

    // This should only be called after setReblogged, in order to override the tint correctly.
    private void setRebloggingEnabled(boolean enabled, Status.Visibility visibility) {
        reblogButton.setEnabled(enabled);

        if (enabled) {
            int inactiveId = ThemeUtils.getDrawableId(reblogButton.getContext(),
                    R.attr.status_reblog_inactive_drawable, R.drawable.reblog_inactive_dark);
            reblogButton.setInactiveImage(inactiveId);
            reblogButton.setActiveImage(R.drawable.reblog_active);
        } else {
            int disabledId;
            if (visibility == Status.Visibility.DIRECT) {
                disabledId = ThemeUtils.getDrawableId(reblogButton.getContext(),
                        R.attr.status_reblog_direct_drawable, R.drawable.reblog_direct_dark);
            } else {
                disabledId = ThemeUtils.getDrawableId(reblogButton.getContext(),
                        R.attr.status_reblog_disabled_drawable, R.drawable.reblog_disabled_dark);
            }
            reblogButton.setInactiveImage(disabledId);
            reblogButton.setActiveImage(disabledId);
        }
    }

    private void setFavourited(boolean favourited) {
        this.favourited = favourited;
        favouriteButton.setChecked(favourited);
    }

    private void setMediaPreviews(final Attachment[] attachments, boolean sensitive,
                                  final StatusActionListener listener, boolean showingContent) {
        final ImageView[] previews = {
                mediaPreview0, mediaPreview1, mediaPreview2, mediaPreview3
        };
        final ImageView[] overlays = {
                mediaOverlay0, mediaOverlay1, mediaOverlay2, mediaOverlay3
        };
        Context context = mediaPreview0.getContext();

        int mediaPreviewUnloadedId =
                ThemeUtils.getDrawableId(itemView.getContext(), R.attr.media_preview_unloaded_drawable,
                        android.R.color.black);

        final int n = Math.min(attachments.length, Status.MAX_MEDIA_ATTACHMENTS);

        final String[] urls = new String[n];
        for (int i = 0; i < n; i++) {
            urls[i] = attachments[i].url;
        }

        for (int i = 0; i < n; i++) {
            String previewUrl = attachments[i].previewUrl;
            String description = attachments[i].description;

            if(TextUtils.isEmpty(description)) {
                previews[i].setContentDescription(context.getString(R.string.action_view_media));
            } else {
                previews[i].setContentDescription(description);
            }

            previews[i].setVisibility(View.VISIBLE);

            if (previewUrl == null || previewUrl.isEmpty()) {
                Picasso.with(context).load(mediaPreviewUnloadedId).into(previews[i]);
            } else {
                Picasso.with(context)
                        .load(previewUrl)
                        .placeholder(mediaPreviewUnloadedId)
                        .into(previews[i]);
            }

            final Attachment.Type type = attachments[i].type;
            if (type == Attachment.Type.VIDEO | type == Attachment.Type.GIFV) {
                overlays[i].setVisibility(View.VISIBLE);
            } else {
                overlays[i].setVisibility(View.GONE);
            }

            if (urls[i] == null || urls[i].isEmpty()) {
                previews[i].setOnClickListener(null);
            } else {
                final int urlIndex = i;
                previews[i].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onViewMedia(urls, urlIndex, type, v);
                    }
                });
            }

            if(n <= 2) {
                previews[0].getLayoutParams().height = getMediaPreviewHeight(context)*2;
                previews[1].getLayoutParams().height = getMediaPreviewHeight(context)*2;
            } else {
                previews[0].getLayoutParams().height = getMediaPreviewHeight(context);
                previews[1].getLayoutParams().height = getMediaPreviewHeight(context);
                previews[2].getLayoutParams().height = getMediaPreviewHeight(context);
                previews[3].getLayoutParams().height = getMediaPreviewHeight(context);
            }
        }

        String hiddenContentText;
        if(sensitive) {
            hiddenContentText = context.getString(R.string.status_sensitive_media_template,
                    context.getString(R.string.status_sensitive_media_title),
                    context.getString(R.string.status_sensitive_media_directions));

        } else {
            hiddenContentText = context.getString(R.string.status_sensitive_media_template,
                    context.getString(R.string.status_media_hidden_title),
                    context.getString(R.string.status_sensitive_media_directions));
        }

        sensitiveMediaWarning.setText(HtmlUtils.fromHtml(hiddenContentText));

        sensitiveMediaWarning.setVisibility(showingContent ? View.GONE : View.VISIBLE);
        sensitiveMediaShow.setVisibility(showingContent ? View.VISIBLE : View.GONE);
        sensitiveMediaShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onContentHiddenChange(false, getAdapterPosition());
                }
                v.setVisibility(View.GONE);
                sensitiveMediaWarning.setVisibility(View.VISIBLE);
            }
        });
        sensitiveMediaWarning.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onContentHiddenChange(true, getAdapterPosition());
                }
                v.setVisibility(View.GONE);
                sensitiveMediaShow.setVisibility(View.VISIBLE);
            }
        });


        // Hide any of the placeholder previews beyond the ones set.
        for (int i = n; i < Status.MAX_MEDIA_ATTACHMENTS; i++) {
            previews[i].setVisibility(View.GONE);
        }
    }

    @NonNull
    private static String getLabelTypeText(Context context, Attachment.Type type) {
        switch (type) {
            default:
            case IMAGE:
                return context.getString(R.string.status_media_images);
            case GIFV:
            case VIDEO:
                return context.getString(R.string.status_media_video);
        }
    }

    @DrawableRes
    private static int getLabelIcon(Attachment.Type type) {
        switch (type) {
            default:
            case IMAGE:
                return R.drawable.ic_photo_24dp;
            case GIFV:
            case VIDEO:
                return R.drawable.ic_videocam_24dp;
        }
    }

    private void setMediaLabel(Attachment[] attachments, boolean sensitive,
                               final StatusActionListener listener) {
        if (attachments.length == 0) {
            mediaLabel.setVisibility(View.GONE);
            return;
        }
        mediaLabel.setVisibility(View.VISIBLE);

        // Set the label's text.
        Context context = itemView.getContext();
        String labelText = getLabelTypeText(context, attachments[0].type);
        if (sensitive) {
            String sensitiveText = context.getString(R.string.status_sensitive_media_title);
            labelText += String.format(" (%s)", sensitiveText);
        }
        mediaLabel.setText(labelText);

        // Set the icon next to the label.
        int drawableId = getLabelIcon(attachments[0].type);
        Drawable drawable = AppCompatResources.getDrawable(context, drawableId);
        ThemeUtils.setDrawableTint(context, drawable, android.R.attr.textColorTertiary);
        mediaLabel.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);

        // Set the listener for the media view action.
        int n = Math.min(attachments.length, Status.MAX_MEDIA_ATTACHMENTS);
        final String[] urls = new String[n];
        for (int i = 0; i < n; i++) {
            urls[i] = attachments[i].url;
        }
        final Attachment.Type type = attachments[0].type;
        mediaLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onViewMedia(urls, 0, type, null);
            }
        });
    }

    private void hideSensitiveMediaWarning() {
        sensitiveMediaWarning.setVisibility(View.GONE);
        sensitiveMediaShow.setVisibility(View.GONE);
    }

    private void setSpoilerText(String spoilerText, List<Status.Emoji> emojis,
                                final boolean expanded, final StatusActionListener listener) {
        CharSequence emojiSpoiler =
                CustomEmojiHelper.emojifyString(spoilerText, emojis, contentWarningDescription);
        contentWarningDescription.setText(emojiSpoiler);
        contentWarningBar.setVisibility(View.VISIBLE);
        contentWarningButton.setChecked(expanded);
        contentWarningButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                contentWarningDescription.invalidate();
                if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onExpandedChange(isChecked, getAdapterPosition());
                }
                if (isChecked) {
                    content.setVisibility(View.VISIBLE);
                } else {
                    content.setVisibility(View.GONE);
                }
            }
        });
        if (expanded) {
            content.setVisibility(View.VISIBLE);
        } else {
            content.setVisibility(View.GONE);
        }
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
            public void onEventAnimationEnd(ImageView button, boolean buttonState) {
            }

            @Override
            public void onEventAnimationStart(ImageView button, boolean buttonState) {
            }
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
            public void onEventAnimationEnd(ImageView button, boolean buttonState) {
            }

            @Override
            public void onEventAnimationStart(ImageView button, boolean buttonState) {
            }
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

    void setupWithStatus(StatusViewData.Concrete status, final StatusActionListener listener,
                         boolean mediaPreviewEnabled) {
        setDisplayName(status.getUserFullName());
        setUsername(status.getNickname());
        setCreatedAt(status.getCreatedAt());
        setIsReply(status.getInReplyToId() != null);
        setContent(status.getContent(), status.getMentions(), status.getEmojis(), listener);
        setAvatar(status.getAvatar(), status.getRebloggedAvatar());
        setReblogged(status.isReblogged());
        setFavourited(status.isFavourited());
        Attachment[] attachments = status.getAttachments();
        boolean sensitive = status.isSensitive();
        if (mediaPreviewEnabled) {
            setMediaPreviews(attachments, sensitive, listener, status.isShowingContent());

            if (attachments.length == 0) {
                hideSensitiveMediaWarning();
//                videoIndicator.setVisibility(View.GONE);
            }
            // Hide the unused label.
            mediaLabel.setVisibility(View.GONE);
        } else {
            setMediaLabel(attachments, sensitive, listener);
            // Hide all unused views.
            mediaPreview0.setVisibility(View.GONE);
            mediaPreview1.setVisibility(View.GONE);
            mediaPreview2.setVisibility(View.GONE);
            mediaPreview3.setVisibility(View.GONE);
            hideSensitiveMediaWarning();
//            videoIndicator.setVisibility(View.GONE);
        }

        setupButtons(listener, status.getSenderId());
        setRebloggingEnabled(status.getRebloggingEnabled(), status.getVisibility());
        if (status.getSpoilerText() == null || status.getSpoilerText().isEmpty()) {
            hideSpoilerText();
        } else {
            setSpoilerText(status.getSpoilerText(), status.getEmojis(), status.isExpanded(), listener);
        }
    }


}
