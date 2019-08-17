package com.keylesspalace.tusky.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Attachment;
import com.keylesspalace.tusky.entity.Attachment.Focus;
import com.keylesspalace.tusky.entity.Attachment.MetaData;
import com.keylesspalace.tusky.entity.Emoji;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.util.CustomEmojiHelper;
import com.keylesspalace.tusky.util.HtmlUtils;
import com.keylesspalace.tusky.util.ImageLoadingHelper;
import com.keylesspalace.tusky.util.LinkHelper;
import com.keylesspalace.tusky.util.ThemeUtils;
import com.keylesspalace.tusky.util.TimestampUtils;
import com.keylesspalace.tusky.view.MediaPreviewImageView;
import com.keylesspalace.tusky.viewdata.PollOptionViewData;
import com.keylesspalace.tusky.viewdata.PollViewData;
import com.keylesspalace.tusky.viewdata.PollViewDataKt;
import com.keylesspalace.tusky.viewdata.StatusViewData;
import com.mikepenz.iconics.utils.Utils;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import at.connyduck.sparkbutton.SparkButton;
import at.connyduck.sparkbutton.SparkEventListener;
import kotlin.collections.CollectionsKt;

public abstract class StatusBaseViewHolder extends RecyclerView.ViewHolder {
    public static class Key {
        public static final String KEY_CREATED = "created";
    }

    private TextView displayName;
    private TextView username;
    private ImageButton replyButton;
    private SparkButton reblogButton;
    private SparkButton favouriteButton;
    private ImageButton moreButton;
    protected MediaPreviewImageView[] mediaPreviews;
    private ImageView[] mediaOverlays;
    private TextView sensitiveMediaWarning;
    private View sensitiveMediaShow;
    protected TextView[] mediaLabels;
    private ToggleButton contentWarningButton;
    private ImageView avatarInset;

    public ImageView avatar;
    public TextView timestampInfo;
    public TextView content;
    public TextView contentWarningDescription;

    private RecyclerView pollOptions;
    private TextView pollDescription;
    private Button pollButton;

    private PollAdapter pollAdapter;

    private boolean useAbsoluteTime;
    private SimpleDateFormat shortSdf;
    private SimpleDateFormat longSdf;

    private final NumberFormat numberFormat = NumberFormat.getNumberInstance();

    protected int avatarRadius48dp;
    private int avatarRadius36dp;
    private int avatarRadius24dp;

    private final int mediaPreviewUnloadedId;

    protected StatusBaseViewHolder(View itemView,
                                   boolean useAbsoluteTime) {
        super(itemView);
        displayName = itemView.findViewById(R.id.status_display_name);
        username = itemView.findViewById(R.id.status_username);
        timestampInfo = itemView.findViewById(R.id.status_timestamp_info);
        content = itemView.findViewById(R.id.status_content);
        avatar = itemView.findViewById(R.id.status_avatar);
        replyButton = itemView.findViewById(R.id.status_reply);
        reblogButton = itemView.findViewById(R.id.status_inset);
        favouriteButton = itemView.findViewById(R.id.status_favourite);
        moreButton = itemView.findViewById(R.id.status_more);

        mediaPreviews = new MediaPreviewImageView[]{
                itemView.findViewById(R.id.status_media_preview_0),
                itemView.findViewById(R.id.status_media_preview_1),
                itemView.findViewById(R.id.status_media_preview_2),
                itemView.findViewById(R.id.status_media_preview_3)
        };
        mediaOverlays = new ImageView[]{
                itemView.findViewById(R.id.status_media_overlay_0),
                itemView.findViewById(R.id.status_media_overlay_1),
                itemView.findViewById(R.id.status_media_overlay_2),
                itemView.findViewById(R.id.status_media_overlay_3)
        };
        sensitiveMediaWarning = itemView.findViewById(R.id.status_sensitive_media_warning);
        sensitiveMediaShow = itemView.findViewById(R.id.status_sensitive_media_button);
        mediaLabels = new TextView[]{
                itemView.findViewById(R.id.status_media_label_0),
                itemView.findViewById(R.id.status_media_label_1),
                itemView.findViewById(R.id.status_media_label_2),
                itemView.findViewById(R.id.status_media_label_3)
        };
        contentWarningDescription = itemView.findViewById(R.id.status_content_warning_description);
        contentWarningButton = itemView.findViewById(R.id.status_content_warning_button);
        avatarInset = itemView.findViewById(R.id.status_avatar_inset);

        pollOptions = itemView.findViewById(R.id.status_poll_options);
        pollDescription = itemView.findViewById(R.id.status_poll_description);
        pollButton = itemView.findViewById(R.id.status_poll_button);

        pollAdapter = new PollAdapter();
        pollOptions.setAdapter(pollAdapter);
        pollOptions.setLayoutManager(new LinearLayoutManager(pollOptions.getContext()));
        ((DefaultItemAnimator) pollOptions.getItemAnimator()).setSupportsChangeAnimations(false);

        this.useAbsoluteTime = useAbsoluteTime;
        this.shortSdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        this.longSdf = new SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault());

        this.avatarRadius48dp = itemView.getContext().getResources().getDimensionPixelSize(R.dimen.avatar_radius_48dp);
        this.avatarRadius36dp = itemView.getContext().getResources().getDimensionPixelSize(R.dimen.avatar_radius_36dp);
        this.avatarRadius24dp = itemView.getContext().getResources().getDimensionPixelSize(R.dimen.avatar_radius_24dp);

        mediaPreviewUnloadedId = ThemeUtils.getDrawableId(itemView.getContext(),
                R.attr.media_preview_unloaded_drawable, android.R.color.black);
    }

    protected abstract int getMediaPreviewHeight(Context context);

    protected void setDisplayName(String name, List<Emoji> customEmojis) {
        CharSequence emojifiedName = CustomEmojiHelper.emojifyString(name, customEmojis, displayName);
        displayName.setText(emojifiedName);
    }

    protected void setUsername(String name) {
        Context context = username.getContext();
        String usernameText = context.getString(R.string.status_username_format, name);
        username.setText(usernameText);
    }

    public void toggleContentWarning() {
        contentWarningButton.toggle();
    }

    protected void setSpoilerAndContent(boolean expanded,
                                        @NonNull Spanned content,
                                        @Nullable String spoilerText,
                                        @Nullable Status.Mention[] mentions,
                                        @NonNull List<Emoji> emojis,
                                        final StatusActionListener listener) {
        if (TextUtils.isEmpty(spoilerText)) {
            contentWarningDescription.setVisibility(View.GONE);
            contentWarningButton.setVisibility(View.GONE);
            this.setTextVisible(true, content, mentions, emojis, listener);
        } else {
            CharSequence emojiSpoiler = CustomEmojiHelper.emojifyString(spoilerText, emojis, contentWarningDescription);
            contentWarningDescription.setText(emojiSpoiler);
            contentWarningDescription.setVisibility(View.VISIBLE);
            contentWarningButton.setVisibility(View.VISIBLE);
            contentWarningButton.setChecked(expanded);
            contentWarningButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onExpandedChange(isChecked, getAdapterPosition());
                }
                this.setTextVisible(isChecked, content, mentions, emojis, listener);
            });
            this.setTextVisible(expanded, content, mentions, emojis, listener);
        }
    }

    private void setTextVisible(boolean expanded,
                                Spanned content,
                                Status.Mention[] mentions,
                                List<Emoji> emojis,
                                final StatusActionListener listener) {
        if (expanded) {
            Spanned emojifiedText = CustomEmojiHelper.emojifyText(content, emojis, this.content);
            LinkHelper.setClickableText(this.content, emojifiedText, mentions, listener);
        } else {
            LinkHelper.setClickableMentions(this.content, mentions, listener);
        }
        if (TextUtils.isEmpty(this.content.getText())) {
            this.content.setVisibility(View.GONE);
        } else {
            this.content.setVisibility(View.VISIBLE);
        }
    }

    private void setAvatar(String url,
                           @Nullable String rebloggedUrl,
                           boolean isBot,
                           boolean showBotOverlay,
                           boolean animateAvatar) {

        int avatarRadius;
        if (TextUtils.isEmpty(rebloggedUrl)) {
            avatar.setPaddingRelative(0, 0, 0, 0);

            if (showBotOverlay && isBot) {
                avatarInset.setVisibility(View.VISIBLE);
                avatarInset.setBackgroundColor(0x50ffffff);
                Glide.with(avatarInset)
                        .load(R.drawable.ic_bot_24dp)
                        .into(avatarInset);

            } else {
                avatarInset.setVisibility(View.GONE);
            }

            avatarRadius = avatarRadius48dp;

        } else {
            int padding = Utils.convertDpToPx(avatar.getContext(), 12);
            avatar.setPaddingRelative(0, 0, padding, padding);

            avatarInset.setVisibility(View.VISIBLE);
            avatarInset.setBackground(null);
            ImageLoadingHelper.loadAvatar(rebloggedUrl, avatarInset, avatarRadius24dp, animateAvatar);

            avatarRadius = avatarRadius36dp;
        }

        ImageLoadingHelper.loadAvatar(url, avatar, avatarRadius, animateAvatar);

    }

    protected void setCreatedAt(@NonNull Date createdAt) {
        if (useAbsoluteTime) {
            timestampInfo.setText(getAbsoluteTime(createdAt));
        } else {
            long then = createdAt.getTime();
            long now = System.currentTimeMillis();
            String readout = TimestampUtils.getRelativeTimeSpanString(timestampInfo.getContext(), then, now);
            timestampInfo.setText(readout);
        }
    }

    private String getAbsoluteTime(@NonNull Date createdAt) {
        if (DateUtils.isToday(createdAt.getTime())) {
            return shortSdf.format(createdAt);
        } else {
            return longSdf.format(createdAt);
        }
    }

    private CharSequence getCreatedAtDescription(@NonNull Date createdAt) {
        if (useAbsoluteTime) {
            return getAbsoluteTime(createdAt);
        } else {
            /* This one is for screen-readers. Frequently, they would mispronounce timestamps like "17m"
             * as 17 meters instead of minutes. */

            long then = createdAt.getTime();
            long now = System.currentTimeMillis();
            return DateUtils.getRelativeTimeSpanString(then, now,
                    DateUtils.SECOND_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE);
        }
    }

    protected void setIsReply(boolean isReply) {
        if (isReply) {
            replyButton.setImageResource(R.drawable.ic_reply_all_24dp);
        } else {
            replyButton.setImageResource(R.drawable.ic_reply_24dp);
        }

    }

    private void setReblogged(boolean reblogged) {
        reblogButton.setChecked(reblogged);
    }

    // This should only be called after setReblogged, in order to override the tint correctly.
    private void setRebloggingEnabled(boolean enabled, Status.Visibility visibility) {
        reblogButton.setEnabled(enabled && visibility != Status.Visibility.PRIVATE);

        if (enabled) {
            int inactiveId;
            int activeId;
            if (visibility == Status.Visibility.PRIVATE) {
                inactiveId = ThemeUtils.getDrawableId(reblogButton.getContext(),
                        R.attr.status_reblog_disabled_drawable, R.drawable.reblog_private_dark);
                activeId = R.drawable.reblog_private_active;
            } else {
                inactiveId = ThemeUtils.getDrawableId(reblogButton.getContext(),
                        R.attr.status_reblog_inactive_drawable, R.drawable.reblog_inactive_dark);
                activeId = R.drawable.reblog_active;
            }
            reblogButton.setInactiveImage(inactiveId);
            reblogButton.setActiveImage(activeId);
        } else {
            int disabledId;
            if (visibility == Status.Visibility.DIRECT) {
                disabledId = ThemeUtils.getDrawableId(reblogButton.getContext(),
                        R.attr.status_reblog_direct_drawable, R.drawable.reblog_direct_dark);
            } else {
                disabledId = ThemeUtils.getDrawableId(reblogButton.getContext(),
                        R.attr.status_reblog_disabled_drawable, R.drawable.reblog_private_dark);
            }
            reblogButton.setInactiveImage(disabledId);
            reblogButton.setActiveImage(disabledId);
        }
    }

    protected void setFavourited(boolean favourited) {
        favouriteButton.setChecked(favourited);
    }

    private void loadImage(MediaPreviewImageView imageView, String previewUrl, String description,
                           MetaData meta) {
        if (TextUtils.isEmpty(previewUrl)) {
            Glide.with(imageView)
                    .load(mediaPreviewUnloadedId)
                    .centerInside()
                    .into(imageView);
        } else {
            Focus focus = meta != null ? meta.getFocus() : null;

            if (focus != null) { // If there is a focal point for this attachment:
                imageView.setFocalPoint(focus);

                Glide.with(imageView)
                        .load(previewUrl)
                        .placeholder(mediaPreviewUnloadedId)
                        .centerInside()
                        .addListener(imageView)
                        .into(imageView);
            } else {
                imageView.removeFocalPoint();

                Glide.with(imageView)
                        .load(previewUrl)
                        .placeholder(mediaPreviewUnloadedId)
                        .centerInside()
                        .into(imageView);
            }
        }
    }

    protected void setMediaPreviews(final List<Attachment> attachments, boolean sensitive,
                                    final StatusActionListener listener, boolean showingContent) {
        Context context = itemView.getContext();
        final int n = Math.min(attachments.size(), Status.MAX_MEDIA_ATTACHMENTS);

        for (int i = 0; i < n; i++) {
            String previewUrl = attachments.get(i).getPreviewUrl();
            String description = attachments.get(i).getDescription();
            MediaPreviewImageView imageView = mediaPreviews[i];

            imageView.setVisibility(View.VISIBLE);

            if (TextUtils.isEmpty(description)) {
                imageView.setContentDescription(imageView.getContext()
                        .getString(R.string.action_view_media));
            } else {
                imageView.setContentDescription(description);
            }

            if (!sensitive || showingContent) {
                loadImage(imageView, previewUrl, description, attachments.get(i).getMeta());
            } else {
                imageView.setImageResource(mediaPreviewUnloadedId);
            }

            final Attachment.Type type = attachments.get(i).getType();
            if (type == Attachment.Type.VIDEO || type == Attachment.Type.GIFV) {
                mediaOverlays[i].setVisibility(View.VISIBLE);
            } else {
                mediaOverlays[i].setVisibility(View.GONE);
            }

            setAttachmentClickListener(imageView, listener, i, attachments.get(i), true);
        }

        final int mediaPreviewHeight = getMediaPreviewHeight(context);

        if (n <= 2) {
            mediaPreviews[0].getLayoutParams().height = mediaPreviewHeight * 2;
            mediaPreviews[1].getLayoutParams().height = mediaPreviewHeight * 2;
        } else {
            mediaPreviews[0].getLayoutParams().height = mediaPreviewHeight;
            mediaPreviews[1].getLayoutParams().height = mediaPreviewHeight;
            mediaPreviews[2].getLayoutParams().height = mediaPreviewHeight;
            mediaPreviews[3].getLayoutParams().height = mediaPreviewHeight;
        }

        final String hiddenContentText;
        if (sensitive) {
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
        sensitiveMediaShow.setOnClickListener(v -> {
            if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                listener.onContentHiddenChange(false, getAdapterPosition());
            }
            v.setVisibility(View.GONE);
            sensitiveMediaWarning.setVisibility(View.VISIBLE);
        });
        sensitiveMediaWarning.setOnClickListener(v -> {
            if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                listener.onContentHiddenChange(true, getAdapterPosition());
            }
            v.setVisibility(View.GONE);
            sensitiveMediaShow.setVisibility(View.VISIBLE);
        });


        // Hide any of the placeholder previews beyond the ones set.
        for (int i = n; i < Status.MAX_MEDIA_ATTACHMENTS; i++) {
            mediaPreviews[i].setVisibility(View.GONE);
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

    protected void setMediaLabel(List<Attachment> attachments, boolean sensitive,
                                 final StatusActionListener listener, boolean showingContent) {
        Context context = itemView.getContext();
        for (int i = 0; i < mediaLabels.length; i++) {
            TextView mediaLabel = mediaLabels[i];
            if (i < attachments.size()) {
                Attachment attachment = attachments.get(i);
                mediaLabel.setVisibility(View.VISIBLE);

                if (sensitive && !showingContent) {
                    mediaLabel.setText(R.string.status_sensitive_media_title);
                } else {
                    mediaLabel.setText(getAttachmentDescription(context, attachment));
                }

                // Set the icon next to the label.
                int drawableId = getLabelIcon(attachments.get(0).getType());
                Drawable drawable = Objects.requireNonNull(context.getDrawable(drawableId));
                ThemeUtils.setDrawableTint(context, drawable, android.R.attr.textColorTertiary);
                mediaLabel.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);

                setAttachmentClickListener(mediaLabel, listener, i, attachment, false);
            } else {
                mediaLabel.setVisibility(View.GONE);
            }
        }
    }

    private void setAttachmentClickListener(View view, StatusActionListener listener,
                                            int index, Attachment attachment, boolean animateTransition) {
        view.setOnClickListener(v -> {
            int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                listener.onViewMedia(position, index, animateTransition ? v : null);
            }
        });
        view.setOnLongClickListener(v -> {
            CharSequence description = getAttachmentDescription(view.getContext(), attachment);
            Toast.makeText(view.getContext(), description, Toast.LENGTH_LONG).show();
            return true;
        });
    }

    private static CharSequence getAttachmentDescription(Context context, Attachment attachment) {
        if (TextUtils.isEmpty(attachment.getDescription())) {
            return context
                    .getString(R.string.description_status_media_no_description_placeholder);
        } else {
            return attachment.getDescription();
        }
    }

    protected void hideSensitiveMediaWarning() {
        sensitiveMediaWarning.setVisibility(View.GONE);
        sensitiveMediaShow.setVisibility(View.GONE);
    }

    protected void setupButtons(final StatusActionListener listener, final String accountId) {

        avatar.setOnClickListener(v -> listener.onViewAccount(accountId));
        replyButton.setOnClickListener(v -> {
            int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                listener.onReply(position);
            }
        });
        if (reblogButton != null) {
            reblogButton.setEventListener(new SparkEventListener() {
                @Override
                public void onEvent(ImageView button, boolean buttonState) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onReblog(buttonState, position);
                    }
                }

                @Override
                public void onEventAnimationEnd(ImageView button, boolean buttonState) {
                }

                @Override
                public void onEventAnimationStart(ImageView button, boolean buttonState) {
                }
            });
        }

        favouriteButton.setEventListener(new SparkEventListener() {
            @Override
            public void onEvent(ImageView button, boolean buttonState) {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onFavourite(buttonState, position);
                }
            }

            @Override
            public void onEventAnimationEnd(ImageView button, boolean buttonState) {
            }

            @Override
            public void onEventAnimationStart(ImageView button, boolean buttonState) {
            }
        });
        moreButton.setOnClickListener(v -> {
            int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                listener.onMore(v, position);
            }
        });
        /* Even though the content TextView is a child of the container, it won't respond to clicks
         * if it contains URLSpans without also setting its listener. The surrounding spans will
         * just eat the clicks instead of deferring to the parent listener, but WILL respond to a
         * listener directly on the TextView, for whatever reason. */
        View.OnClickListener viewThreadListener = v -> {
            int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                listener.onViewThread(position);
            }
        };
        content.setOnClickListener(viewThreadListener);
        itemView.setOnClickListener(viewThreadListener);
    }

    public void setupWithStatus(StatusViewData.Concrete status, final StatusActionListener listener,
                                boolean mediaPreviewEnabled, boolean showBotOverlay, boolean animateAvatar) {
        this.setupWithStatus(status, listener, mediaPreviewEnabled, showBotOverlay, animateAvatar, null);
    }

    protected void setupWithStatus(StatusViewData.Concrete status,
                                   final StatusActionListener listener,
                                   boolean mediaPreviewEnabled,
                                   boolean showBotOverlay,
                                   boolean animateAvatar,
                                   @Nullable Object payloads) {
        if (payloads == null) {
            setDisplayName(status.getUserFullName(), status.getAccountEmojis());
            setUsername(status.getNickname());
            setCreatedAt(status.getCreatedAt());
            setIsReply(status.getInReplyToId() != null);
            setAvatar(status.getAvatar(), status.getRebloggedAvatar(), status.isBot(), showBotOverlay, animateAvatar);
            setReblogged(status.isReblogged());
            setFavourited(status.isFavourited());
            List<Attachment> attachments = status.getAttachments();
            boolean sensitive = status.isSensitive();
            if (mediaPreviewEnabled) {
                setMediaPreviews(attachments, sensitive, listener, status.isShowingContent());

                if (attachments.size() == 0) {
                    hideSensitiveMediaWarning();
                }
                // Hide the unused label.
                for (TextView mediaLabel : mediaLabels) {
                    mediaLabel.setVisibility(View.GONE);
                }
            } else {
                setMediaLabel(attachments, sensitive, listener, status.isShowingContent());
                // Hide all unused views.
                mediaPreviews[0].setVisibility(View.GONE);
                mediaPreviews[1].setVisibility(View.GONE);
                mediaPreviews[2].setVisibility(View.GONE);
                mediaPreviews[3].setVisibility(View.GONE);
                hideSensitiveMediaWarning();
            }

            setupButtons(listener, status.getSenderId());
            setRebloggingEnabled(status.getRebloggingEnabled(), status.getVisibility());

            setSpoilerAndContent(status.isExpanded(), status.getContent(), status.getSpoilerText(), status.getMentions(), status.getStatusEmojis(), listener);

            setDescriptionForStatus(status);

            setupPoll(status.getPoll(), status.getStatusEmojis(), listener);

            // Workaround for RecyclerView 1.0.0 / androidx.core 1.0.0
            // RecyclerView tries to set AccessibilityDelegateCompat to null
            // but ViewCompat code replaces is with the default one. RecyclerView never
            // fetches another one from its delegate because it checks that it's set so we remove it
            // and let RecyclerView ask for a new delegate.
            itemView.setAccessibilityDelegate(null);
        } else {
            if (payloads instanceof List)
                for (Object item : (List) payloads) {
                    if (Key.KEY_CREATED.equals(item)) {
                        setCreatedAt(status.getCreatedAt());
                    }
                }

        }
    }

    private void setDescriptionForStatus(@NonNull StatusViewData.Concrete status) {
        Context context = itemView.getContext();

        String description = context.getString(R.string.description_status,
                status.getUserFullName(),
                getContentWarningDescription(context, status),
                (!status.isSensitive() || status.isExpanded() ? status.getContent() : ""),
                getCreatedAtDescription(status.getCreatedAt()),
                getReblogDescription(context, status),
                status.getNickname(),
                status.isReblogged() ? context.getString(R.string.description_status_reblogged) : "",
                status.isFavourited() ? context.getString(R.string.description_status_favourited) : "",
                getMediaDescription(context, status),
                getVisibilityDescription(context, status.getVisibility()),
                getFavsText(context, status.getFavouritesCount()),
                getReblogsText(context, status.getReblogsCount()),
                getPollDescription(context, status)
        );
        itemView.setContentDescription(description);
    }

    private static CharSequence getReblogDescription(Context context,
                                                     @NonNull StatusViewData.Concrete status) {
        String rebloggedUsername = status.getRebloggedByUsername();
        if (rebloggedUsername != null) {
            return context
                    .getString(R.string.status_boosted_format, rebloggedUsername);
        } else {
            return "";
        }
    }

    private static CharSequence getMediaDescription(Context context,
                                                    @NonNull StatusViewData.Concrete status) {
        if (status.getAttachments().isEmpty()) {
            return "";
        }
        StringBuilder mediaDescriptions = CollectionsKt.fold(
                status.getAttachments(),
                new StringBuilder(),
                (builder, a) -> {
                    if (a.getDescription() == null) {
                        String placeholder =
                                context.getString(R.string.description_status_media_no_description_placeholder);
                        return builder.append(placeholder);
                    } else {
                        builder.append("; ");
                        return builder.append(a.getDescription());
                    }
                });
        return context.getString(R.string.description_status_media, mediaDescriptions);
    }

    private static CharSequence getContentWarningDescription(Context context,
                                                             @NonNull StatusViewData.Concrete status) {
        if (!TextUtils.isEmpty(status.getSpoilerText())) {
            return context.getString(R.string.description_status_cw, status.getSpoilerText());
        } else {
            return "";
        }
    }

    private static CharSequence getVisibilityDescription(Context context, Status.Visibility visibility) {

        int resource;
        switch (visibility) {
            case PUBLIC:
                resource = R.string.description_visiblity_public;
                break;
            case UNLISTED:
                resource = R.string.description_visiblity_unlisted;
                break;
            case PRIVATE:
                resource = R.string.description_visiblity_private;
                break;
            case DIRECT:
                resource = R.string.description_visiblity_direct;
                break;
            default:
                return "";
        }
        return context.getString(resource);
    }

    private CharSequence getPollDescription(Context context,
                                            @NonNull StatusViewData.Concrete status) {
        PollViewData poll = status.getPoll();
        if (poll == null) {
            return "";
        } else {
            Object[] args = new CharSequence[5];
            List<PollOptionViewData> options = poll.getOptions();
            for (int i = 0; i < args.length; i++) {
                if (i < options.size()) {
                    int percent = PollViewDataKt.calculatePercent(options.get(i).getVotesCount(), poll.getVotesCount());
                    args[i] = HtmlUtils.fromHtml(context.getString(
                            R.string.poll_option_format,
                            percent,
                            options.get(i).getTitle()));
                } else {
                    args[i] = "";
                }
            }
            args[4] = getPollInfoText(System.currentTimeMillis(), poll, context);
            return context.getString(R.string.description_poll, args);
        }
    }

    protected CharSequence getFavsText(Context context, int count) {
        if (count > 0) {
            String countString = numberFormat.format(count);
            return HtmlUtils.fromHtml(context.getResources().getQuantityString(R.plurals.favs, count, countString));
        } else {
            return "";
        }
    }

    protected CharSequence getReblogsText(Context context, int count) {
        if (count > 0) {
            String countString = numberFormat.format(count);
            return HtmlUtils.fromHtml(context.getResources().getQuantityString(R.plurals.reblogs, count, countString));
        } else {
            return "";
        }
    }

    protected void setupPoll(PollViewData poll, List<Emoji> emojis, StatusActionListener listener) {
        if (poll == null) {

            pollOptions.setVisibility(View.GONE);

            pollDescription.setVisibility(View.GONE);

            pollButton.setVisibility(View.GONE);

        } else {
            long timestamp = System.currentTimeMillis();

            boolean expired = poll.getExpired() || (poll.getExpiresAt() != null && timestamp > poll.getExpiresAt().getTime());

            Context context = pollDescription.getContext();

            pollOptions.setVisibility(View.VISIBLE);

            if (expired || poll.getVoted()) {
                // no voting possible
                pollAdapter.setup(poll.getOptions(), poll.getVotesCount(), emojis, PollAdapter.RESULT);

                pollButton.setVisibility(View.GONE);
            } else {
                // voting possible
                pollAdapter.setup(poll.getOptions(), poll.getVotesCount(), emojis, poll.getMultiple() ? PollAdapter.MULTIPLE : PollAdapter.SINGLE);

                pollButton.setVisibility(View.VISIBLE);

                pollButton.setOnClickListener(v -> {

                    int position = getAdapterPosition();

                    if (position != RecyclerView.NO_POSITION) {

                        List<Integer> pollResult = pollAdapter.getSelected();

                        if (!pollResult.isEmpty()) {
                            listener.onVoteInPoll(position, pollResult);
                        }
                    }

                });
            }

            pollDescription.setVisibility(View.VISIBLE);
            pollDescription.setText(getPollInfoText(timestamp, poll, context));

        }
    }

    private CharSequence getPollInfoText(long timestamp, PollViewData poll, Context context) {
        String votes = numberFormat.format(poll.getVotesCount());
        String votesText = context.getResources().getQuantityString(R.plurals.poll_info_votes, poll.getVotesCount(), votes);
        CharSequence pollDurationInfo;
        if (poll.getExpired()) {
            pollDurationInfo = context.getString(R.string.poll_info_closed);
        } else if (poll.getExpiresAt() == null) {
            return votesText;
        } else {
            if (useAbsoluteTime) {
                pollDurationInfo = context.getString(R.string.poll_info_time_absolute, getAbsoluteTime(poll.getExpiresAt()));
            } else {
                String pollDuration = TimestampUtils.formatPollDuration(pollDescription.getContext(), poll.getExpiresAt().getTime(), timestamp);
                pollDurationInfo = context.getString(R.string.poll_info_time_relative, pollDuration);
            }
        }

        return pollDescription.getContext().getString(R.string.poll_info_format, votesText, pollDurationInfo);
    }

}
