package com.keylesspalace.tusky.adapter;

import static com.keylesspalace.tusky.viewdata.PollViewDataKt.buildDescription;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.ViewMediaActivity;
import com.keylesspalace.tusky.entity.Attachment;
import com.keylesspalace.tusky.entity.Attachment.Focus;
import com.keylesspalace.tusky.entity.Attachment.MetaData;
import com.keylesspalace.tusky.entity.Card;
import com.keylesspalace.tusky.entity.Emoji;
import com.keylesspalace.tusky.entity.Filter;
import com.keylesspalace.tusky.entity.FilterResult;
import com.keylesspalace.tusky.entity.HashTag;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.util.AbsoluteTimeFormatter;
import com.keylesspalace.tusky.util.AttachmentHelper;
import com.keylesspalace.tusky.util.CardViewMode;
import com.keylesspalace.tusky.util.CompositeWithOpaqueBackground;
import com.keylesspalace.tusky.util.CustomEmojiHelper;
import com.keylesspalace.tusky.util.ImageLoadingHelper;
import com.keylesspalace.tusky.util.LinkHelper;
import com.keylesspalace.tusky.util.NumberUtils;
import com.keylesspalace.tusky.util.StatusDisplayOptions;
import com.keylesspalace.tusky.util.TimestampUtils;
import com.keylesspalace.tusky.util.TouchDelegateHelper;
import com.keylesspalace.tusky.view.MediaPreviewImageView;
import com.keylesspalace.tusky.view.MediaPreviewLayout;
import com.keylesspalace.tusky.viewdata.PollOptionViewData;
import com.keylesspalace.tusky.viewdata.PollViewData;
import com.keylesspalace.tusky.viewdata.PollViewDataKt;
import com.keylesspalace.tusky.viewdata.StatusViewData;

import java.text.NumberFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import at.connyduck.sparkbutton.SparkButton;
import at.connyduck.sparkbutton.helpers.Utils;
import kotlin.collections.CollectionsKt;

public abstract class StatusBaseViewHolder extends RecyclerView.ViewHolder {
    public static class Key {
        public static final String KEY_CREATED = "created";
    }

    private final String TAG = "StatusBaseViewHolder";

    private final TextView displayName;
    private final TextView username;
    private final ImageButton replyButton;
    private final TextView replyCountLabel;
    private final SparkButton reblogButton;
    private final SparkButton favouriteButton;
    private final SparkButton bookmarkButton;
    private final ImageButton moreButton;
    private final ConstraintLayout mediaContainer;
    protected final MediaPreviewLayout mediaPreview;
    private final TextView sensitiveMediaWarning;
    private final View sensitiveMediaShow;
    protected final TextView[] mediaLabels;
    protected final CharSequence[] mediaDescriptions;
    private final MaterialButton contentWarningButton;
    private final ImageView avatarInset;

    public final ImageView avatar;
    public final TextView metaInfo;
    public final TextView content;
    public final TextView contentWarningDescription;

    private final RecyclerView pollOptions;
    private final TextView pollDescription;
    private final Button pollButton;

    private final LinearLayout cardView;
    private final LinearLayout cardInfo;
    private final ShapeableImageView cardImage;
    private final TextView cardTitle;
    private final TextView cardDescription;
    private final TextView cardUrl;
    private final PollAdapter pollAdapter;
    protected final LinearLayout filteredPlaceholder;
    protected final TextView filteredPlaceholderLabel;
    protected final Button filteredPlaceholderShowButton;
    protected final ConstraintLayout statusContainer;

    private final NumberFormat numberFormat = NumberFormat.getNumberInstance();
    private final AbsoluteTimeFormatter absoluteTimeFormatter = new AbsoluteTimeFormatter();

    protected final int avatarRadius48dp;
    private final int avatarRadius36dp;
    private final int avatarRadius24dp;

    private final Drawable mediaPreviewUnloaded;

    protected StatusBaseViewHolder(@NonNull View itemView) {
        super(itemView);
        displayName = itemView.findViewById(R.id.status_display_name);
        username = itemView.findViewById(R.id.status_username);
        metaInfo = itemView.findViewById(R.id.status_meta_info);
        content = itemView.findViewById(R.id.status_content);
        avatar = itemView.findViewById(R.id.status_avatar);
        replyButton = itemView.findViewById(R.id.status_reply);
        replyCountLabel = itemView.findViewById(R.id.status_replies);
        reblogButton = itemView.findViewById(R.id.status_inset);
        favouriteButton = itemView.findViewById(R.id.status_favourite);
        bookmarkButton = itemView.findViewById(R.id.status_bookmark);
        moreButton = itemView.findViewById(R.id.status_more);

        mediaContainer = itemView.findViewById(R.id.status_media_preview_container);
        mediaContainer.setClipToOutline(true);
        mediaPreview = itemView.findViewById(R.id.status_media_preview);

        sensitiveMediaWarning = itemView.findViewById(R.id.status_sensitive_media_warning);
        sensitiveMediaShow = itemView.findViewById(R.id.status_sensitive_media_button);
        mediaLabels = new TextView[]{
                itemView.findViewById(R.id.status_media_label_0),
                itemView.findViewById(R.id.status_media_label_1),
                itemView.findViewById(R.id.status_media_label_2),
                itemView.findViewById(R.id.status_media_label_3)
        };
        mediaDescriptions = new CharSequence[mediaLabels.length];
        contentWarningDescription = itemView.findViewById(R.id.status_content_warning_description);
        contentWarningButton = itemView.findViewById(R.id.status_content_warning_button);
        avatarInset = itemView.findViewById(R.id.status_avatar_inset);

        pollOptions = itemView.findViewById(R.id.status_poll_options);
        pollDescription = itemView.findViewById(R.id.status_poll_description);
        pollButton = itemView.findViewById(R.id.status_poll_button);

        cardView = itemView.findViewById(R.id.status_card_view);
        cardInfo = itemView.findViewById(R.id.card_info);
        cardImage = itemView.findViewById(R.id.card_image);
        cardTitle = itemView.findViewById(R.id.card_title);
        cardDescription = itemView.findViewById(R.id.card_description);
        cardUrl = itemView.findViewById(R.id.card_link);

        filteredPlaceholder = itemView.findViewById(R.id.status_filtered_placeholder);
        filteredPlaceholderLabel = itemView.findViewById(R.id.status_filter_label);
        filteredPlaceholderShowButton = itemView.findViewById(R.id.status_filter_show_anyway);
        statusContainer = itemView.findViewById(R.id.status_container);

        pollAdapter = new PollAdapter();
        pollOptions.setAdapter(pollAdapter);
        pollOptions.setLayoutManager(new LinearLayoutManager(pollOptions.getContext()));
        ((DefaultItemAnimator) pollOptions.getItemAnimator()).setSupportsChangeAnimations(false);

        this.avatarRadius48dp = itemView.getContext().getResources().getDimensionPixelSize(R.dimen.avatar_radius_48dp);
        this.avatarRadius36dp = itemView.getContext().getResources().getDimensionPixelSize(R.dimen.avatar_radius_36dp);
        this.avatarRadius24dp = itemView.getContext().getResources().getDimensionPixelSize(R.dimen.avatar_radius_24dp);

        mediaPreviewUnloaded = new ColorDrawable(MaterialColors.getColor(itemView, R.attr.colorBackgroundAccent));

        TouchDelegateHelper.expandTouchSizeToFillRow((ViewGroup) itemView, CollectionsKt.listOfNotNull(replyButton, reblogButton, favouriteButton, bookmarkButton, moreButton));
    }

    protected void setDisplayName(@NonNull String name, @Nullable List<Emoji> customEmojis, @NonNull StatusDisplayOptions statusDisplayOptions) {
        CharSequence emojifiedName = CustomEmojiHelper.emojify(
                name, customEmojis, displayName, statusDisplayOptions.animateEmojis()
        );
        displayName.setText(emojifiedName);
    }

    protected void setUsername(@Nullable String name) {
        Context context = username.getContext();
        String usernameText = context.getString(R.string.post_username_format, name);
        username.setText(usernameText);
    }

    public void toggleContentWarning() {
        contentWarningButton.performClick();
    }

    protected void setSpoilerAndContent(@NonNull StatusViewData.Concrete status,
                                        @NonNull StatusDisplayOptions statusDisplayOptions,
                                        final @NonNull StatusActionListener listener) {

        Status actionable = status.getActionable();
        String spoilerText = actionable.getSpoilerText();
        List<Emoji> emojis = actionable.getEmojis();

        boolean sensitive = !TextUtils.isEmpty(spoilerText);
        boolean expanded = status.isExpanded();

        if (sensitive) {
            CharSequence emojiSpoiler = CustomEmojiHelper.emojify(
                    spoilerText, emojis, contentWarningDescription, statusDisplayOptions.animateEmojis()
            );
            contentWarningDescription.setText(emojiSpoiler);
            contentWarningDescription.setVisibility(View.VISIBLE);
            contentWarningButton.setVisibility(View.VISIBLE);
            setContentWarningButtonText(expanded);
            contentWarningButton.setOnClickListener(view -> toggleExpandedState(true, !expanded, status, statusDisplayOptions, listener));
            this.setTextVisible(true, expanded, status, statusDisplayOptions, listener);
        } else {
            contentWarningDescription.setVisibility(View.GONE);
            contentWarningButton.setVisibility(View.GONE);
            this.setTextVisible(false, true, status, statusDisplayOptions, listener);
        }
    }

    private void setContentWarningButtonText(boolean expanded) {
        if (expanded) {
            contentWarningButton.setText(R.string.post_content_warning_show_less);
        } else {
            contentWarningButton.setText(R.string.post_content_warning_show_more);
        }
    }

    protected void toggleExpandedState(boolean sensitive,
                                       boolean expanded,
                                       @NonNull final StatusViewData.Concrete status,
                                       @NonNull final StatusDisplayOptions statusDisplayOptions,
                                       @NonNull final StatusActionListener listener) {

        contentWarningDescription.invalidate();
        int adapterPosition = getBindingAdapterPosition();
        if (adapterPosition != RecyclerView.NO_POSITION) {
            listener.onExpandedChange(expanded, adapterPosition);
        }
        setContentWarningButtonText(expanded);

        this.setTextVisible(sensitive, expanded, status, statusDisplayOptions, listener);

        setupCard(status, expanded, statusDisplayOptions.cardViewMode(), statusDisplayOptions, listener);
    }

    private void setTextVisible(boolean sensitive,
                                boolean expanded,
                                @NonNull final StatusViewData.Concrete status,
                                @NonNull final StatusDisplayOptions statusDisplayOptions,
                                final StatusActionListener listener) {

        Status actionable = status.getActionable();
        Spanned content = status.getContent();
        List<Status.Mention> mentions = actionable.getMentions();
        List<HashTag> tags =actionable.getTags();
        List<Emoji> emojis = actionable.getEmojis();
        PollViewData poll = PollViewDataKt.toViewData(actionable.getPoll());

        if (expanded) {
            CharSequence emojifiedText = CustomEmojiHelper.emojify(content, emojis, this.content, statusDisplayOptions.animateEmojis());
            LinkHelper.setClickableText(this.content, emojifiedText, mentions, tags, listener);
            for (int i = 0; i < mediaLabels.length; ++i) {
                updateMediaLabel(i, sensitive, true);
            }
            if (poll != null) {
                setupPoll(poll, emojis, statusDisplayOptions, listener);
            } else {
                hidePoll();
            }
        } else {
            hidePoll();
            LinkHelper.setClickableMentions(this.content, mentions, listener);
        }
        if (TextUtils.isEmpty(this.content.getText())) {
            this.content.setVisibility(View.GONE);
        } else {
            this.content.setVisibility(View.VISIBLE);
        }
    }

    private void hidePoll() {
        pollButton.setVisibility(View.GONE);
        pollDescription.setVisibility(View.GONE);
        pollOptions.setVisibility(View.GONE);
    }

    private void setAvatar(String url,
                          @Nullable String rebloggedUrl,
                           boolean isBot,
                           StatusDisplayOptions statusDisplayOptions) {

        int avatarRadius;
        if (TextUtils.isEmpty(rebloggedUrl)) {
            avatar.setPaddingRelative(0, 0, 0, 0);

            if (statusDisplayOptions.showBotOverlay() && isBot) {
                avatarInset.setVisibility(View.VISIBLE);
                Glide.with(avatarInset)
                        .load(R.drawable.bot_badge)
                        .into(avatarInset);
            } else {
                avatarInset.setVisibility(View.GONE);
            }

            avatarRadius = avatarRadius48dp;

        } else {
            int padding = Utils.dpToPx(avatar.getContext(), 12);
            avatar.setPaddingRelative(0, 0, padding, padding);

            avatarInset.setVisibility(View.VISIBLE);
            avatarInset.setBackground(null);
            ImageLoadingHelper.loadAvatar(rebloggedUrl, avatarInset, avatarRadius24dp,
                    statusDisplayOptions.animateAvatars(), null);

            avatarRadius = avatarRadius36dp;
        }

        ImageLoadingHelper.loadAvatar(
            url,
            avatar,
            avatarRadius,
            statusDisplayOptions.animateAvatars(),
            Collections.singletonList(new CompositeWithOpaqueBackground(MaterialColors.getColor(avatar, android.R.attr.colorBackground)))
        );
    }

    protected void setMetaData(@NonNull StatusViewData.Concrete statusViewData, @NonNull StatusDisplayOptions statusDisplayOptions, @NonNull StatusActionListener listener) {

        Status status = statusViewData.getActionable();
        Date createdAt = status.getCreatedAt();
        Date editedAt = status.getEditedAt();

        String timestampText;
        if (statusDisplayOptions.useAbsoluteTime()) {
            timestampText = absoluteTimeFormatter.format(createdAt, true);
        } else {
            if (createdAt == null) {
                timestampText = "?m";
            } else {
                long then = createdAt.getTime();
                long now = System.currentTimeMillis();
                timestampText = TimestampUtils.getRelativeTimeSpanString(metaInfo.getContext(), then, now);
            }
        }

        if (editedAt != null) {
            timestampText = metaInfo.getContext().getString(R.string.post_timestamp_with_edited_indicator, timestampText);
        }
        metaInfo.setText(timestampText);
    }

    private CharSequence getCreatedAtDescription(Date createdAt,
                                                 StatusDisplayOptions statusDisplayOptions) {
        if (statusDisplayOptions.useAbsoluteTime()) {
            return absoluteTimeFormatter.format(createdAt, true);
        } else {
            /* This one is for screen-readers. Frequently, they would mispronounce timestamps like "17m"
             * as 17 meters instead of minutes. */

            if (createdAt == null) {
                return "? minutes";
            } else {
                long then = createdAt.getTime();
                long now = System.currentTimeMillis();
                return DateUtils.getRelativeTimeSpanString(then, now,
                        DateUtils.SECOND_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE);
            }
        }
    }

    protected void setIsReply(boolean isReply) {
        if (isReply) {
            replyButton.setImageResource(R.drawable.ic_reply_all_24dp);
        } else {
            replyButton.setImageResource(R.drawable.ic_reply_24dp);
        }

    }

    protected void setReplyCount(int repliesCount, boolean fullStats) {
        // This label only exists in the non-detailed view (to match the web ui)
        if (replyCountLabel == null) return;

        if (fullStats) {
            replyCountLabel.setText(NumberUtils.formatNumber(repliesCount, 1000));
            return;
        }

        // Show "0", "1", or "1+" for replies otherwise, so the user knows if there is a thread
        // that they can click through to read.
        replyCountLabel.setText((repliesCount > 1 ? replyCountLabel.getContext().getString(R.string.status_count_one_plus) : Integer.toString(repliesCount)));
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
                inactiveId = R.drawable.ic_reblog_private_24dp;
                activeId = R.drawable.ic_reblog_private_active_24dp;
            } else {
                inactiveId = R.drawable.ic_reblog_24dp;
                activeId = R.drawable.ic_reblog_active_24dp;
            }
            reblogButton.setInactiveImage(inactiveId);
            reblogButton.setActiveImage(activeId);
        } else {
            int disabledId;
            if (visibility == Status.Visibility.DIRECT) {
                disabledId = R.drawable.ic_reblog_direct_24dp;
            } else {
                disabledId = R.drawable.ic_reblog_private_24dp;
            }
            reblogButton.setInactiveImage(disabledId);
            reblogButton.setActiveImage(disabledId);
        }
    }

    protected void setFavourited(boolean favourited) {
        favouriteButton.setChecked(favourited);
    }

    protected void setBookmarked(boolean bookmarked) {
        bookmarkButton.setChecked(bookmarked);
    }

    private BitmapDrawable decodeBlurHash(String blurhash) {
        return ImageLoadingHelper.decodeBlurHash(this.avatar.getContext(), blurhash);
    }

    private void loadImage(MediaPreviewImageView imageView,
                           @Nullable String previewUrl,
                           @Nullable MetaData meta,
                           @Nullable String blurhash) {

        Drawable placeholder = blurhash != null ? decodeBlurHash(blurhash) : mediaPreviewUnloaded;

        if (TextUtils.isEmpty(previewUrl)) {
            imageView.removeFocalPoint();

            Glide.with(imageView)
                    .load(placeholder)
                    .centerInside()
                    .into(imageView);
        } else {
            Focus focus = meta != null ? meta.getFocus() : null;

            if (focus != null) { // If there is a focal point for this attachment:
                imageView.setFocalPoint(focus);

                Glide.with(imageView.getContext())
                        .load(previewUrl)
                        .placeholder(placeholder)
                        .centerInside()
                        .addListener(imageView)
                        .into(imageView);
            } else {
                imageView.removeFocalPoint();

                Glide.with(imageView)
                        .load(previewUrl)
                        .placeholder(placeholder)
                        .centerInside()
                        .into(imageView);
            }
        }
    }

    protected void setMediaPreviews(
            final @NonNull List<Attachment> attachments,
            boolean sensitive,
            final @NonNull StatusActionListener listener,
            boolean showingContent,
            boolean useBlurhash
    ) {

        mediaPreview.setVisibility(View.VISIBLE);
        mediaPreview.setAspectRatios(AttachmentHelper.aspectRatios(attachments));

        mediaPreview.forEachIndexed((i, imageView, descriptionIndicator) -> {
            Attachment attachment = attachments.get(i);
            String previewUrl = attachment.getPreviewUrl();
            String description = attachment.getDescription();
            boolean hasDescription = !TextUtils.isEmpty(description);

            if (hasDescription) {
                imageView.setContentDescription(description);
            } else {
                imageView.setContentDescription(imageView.getContext().getString(R.string.action_view_media));
            }

            loadImage(
                    imageView,
                    showingContent ? previewUrl : null,
                    attachment.getMeta(),
                    useBlurhash ? attachment.getBlurhash() : null
            );

            final Attachment.Type type = attachment.getType();
            if (showingContent && (type == Attachment.Type.VIDEO || type == Attachment.Type.GIFV)) {
                imageView.setForeground(ContextCompat.getDrawable(itemView.getContext(), R.drawable.play_indicator_overlay));
            } else {
                imageView.setForeground(null);
            }

            setAttachmentClickListener(imageView, listener, i, attachment, true);

            if (sensitive) {
                sensitiveMediaWarning.setText(R.string.post_sensitive_media_title);
            } else {
                sensitiveMediaWarning.setText(R.string.post_media_hidden_title);
            }

            sensitiveMediaWarning.setVisibility(showingContent ? View.GONE : View.VISIBLE);
            sensitiveMediaShow.setVisibility(showingContent ? View.VISIBLE : View.GONE);

            descriptionIndicator.setVisibility(hasDescription && showingContent ? View.VISIBLE : View.GONE);

            sensitiveMediaShow.setOnClickListener(v -> {
                if (getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onContentHiddenChange(false, getBindingAdapterPosition());
                }
                v.setVisibility(View.GONE);
                sensitiveMediaWarning.setVisibility(View.VISIBLE);
                descriptionIndicator.setVisibility(View.GONE);
            });
            sensitiveMediaWarning.setOnClickListener(v -> {
                if (getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onContentHiddenChange(true, getBindingAdapterPosition());
                }
                v.setVisibility(View.GONE);
                sensitiveMediaShow.setVisibility(View.VISIBLE);
                descriptionIndicator.setVisibility(hasDescription ? View.VISIBLE : View.GONE);
            });

            return null;
        });
    }

    @DrawableRes
    private static int getLabelIcon(Attachment.Type type) {
        switch (type) {
            case IMAGE:
                return R.drawable.ic_photo_24dp;
            case GIFV:
            case VIDEO:
                return R.drawable.ic_videocam_24dp;
            case AUDIO:
                return R.drawable.ic_music_box_24dp;
            default:
                return R.drawable.ic_attach_file_24dp;
        }
    }

    private void updateMediaLabel(int index, boolean sensitive, boolean showingContent) {
        Context context = itemView.getContext();
        CharSequence label = (sensitive && !showingContent) ?
                context.getString(R.string.post_sensitive_media_title) :
                mediaDescriptions[index];
        mediaLabels[index].setText(label);
    }

    protected void setMediaLabel(@NonNull List<Attachment> attachments, boolean sensitive,
                                 final @NonNull StatusActionListener listener, boolean showingContent) {
        Context context = itemView.getContext();
        for (int i = 0; i < mediaLabels.length; i++) {
            TextView mediaLabel = mediaLabels[i];
            if (i < attachments.size()) {
                Attachment attachment = attachments.get(i);
                mediaLabel.setVisibility(View.VISIBLE);
                mediaDescriptions[i] = AttachmentHelper.getFormattedDescription(attachment, context);
                updateMediaLabel(i, sensitive, showingContent);

                // Set the icon next to the label.
                int drawableId = getLabelIcon(attachments.get(0).getType());
                mediaLabel.setCompoundDrawablesWithIntrinsicBounds(drawableId, 0, 0, 0);

                setAttachmentClickListener(mediaLabel, listener, i, attachment, false);
            } else {
                mediaLabel.setVisibility(View.GONE);
            }
        }
    }

    private void setAttachmentClickListener(View view, @NonNull StatusActionListener listener,
                                            int index, Attachment attachment, boolean animateTransition) {
        view.setOnClickListener(v -> {
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                if (sensitiveMediaWarning.getVisibility() == View.VISIBLE) {
                    listener.onContentHiddenChange(true, getBindingAdapterPosition());
                } else {
                    listener.onViewMedia(position, index, animateTransition ? v : null);
                }
            }
        });
        view.setOnLongClickListener(v -> {
            CharSequence description = AttachmentHelper.getFormattedDescription(attachment, view.getContext());
            Toast.makeText(view.getContext(), description, Toast.LENGTH_LONG).show();
            return true;
        });
    }

    protected void hideSensitiveMediaWarning() {
        sensitiveMediaWarning.setVisibility(View.GONE);
        sensitiveMediaShow.setVisibility(View.GONE);
    }

    protected void setupButtons(final @NonNull StatusActionListener listener,
                                final @NonNull String accountId,
                                final @Nullable String statusContent,
                                @NonNull StatusDisplayOptions statusDisplayOptions) {
        View.OnClickListener profileButtonClickListener = button -> listener.onViewAccount(accountId);

        avatar.setOnClickListener(profileButtonClickListener);
        displayName.setOnClickListener(profileButtonClickListener);

        replyButton.setOnClickListener(v -> {
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                listener.onReply(position);
            }
        });


        if (reblogButton != null) {
            reblogButton.setEventListener((button, buttonState) -> {
                // return true to play animation
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    if (statusDisplayOptions.confirmReblogs()) {
                        showConfirmReblog(listener, buttonState, position);
                        return false;
                    } else {
                        listener.onReblog(!buttonState, position);
                        return true;
                    }
                } else {
                    return false;
                }
            });
        }


        favouriteButton.setEventListener((button, buttonState) -> {
            // return true to play animation
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                if (statusDisplayOptions.confirmFavourites()) {
                    showConfirmFavourite(listener, buttonState, position);
                    return false;
                } else {
                    listener.onFavourite(!buttonState, position);
                    return true;
                }
            } else {
                return true;
            }
        });

        bookmarkButton.setEventListener((button, buttonState) -> {
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                listener.onBookmark(!buttonState, position);
            }
            return true;
        });

        moreButton.setOnClickListener(v -> {
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                listener.onMore(v, position);
            }
        });
        /* Even though the content TextView is a child of the container, it won't respond to clicks
         * if it contains URLSpans without also setting its listener. The surrounding spans will
         * just eat the clicks instead of deferring to the parent listener, but WILL respond to a
         * listener directly on the TextView, for whatever reason. */
        View.OnClickListener viewThreadListener = v -> {
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                listener.onViewThread(position);
            }
        };
        content.setOnClickListener(viewThreadListener);
        itemView.setOnClickListener(viewThreadListener);
    }

    private void showConfirmReblog(StatusActionListener listener,
                                   boolean buttonState,
                                   int position) {
        PopupMenu popup = new PopupMenu(itemView.getContext(), reblogButton);
        popup.inflate(R.menu.status_reblog);
        Menu menu = popup.getMenu();
        if (buttonState) {
            menu.findItem(R.id.menu_action_reblog).setVisible(false);
        } else {
            menu.findItem(R.id.menu_action_unreblog).setVisible(false);
        }
        popup.setOnMenuItemClickListener(item -> {
            listener.onReblog(!buttonState, position);
            if(!buttonState) {
                reblogButton.playAnimation();
                reblogButton.setChecked(true);
            }
            return true;
        });
        popup.show();
    }

    private void showConfirmFavourite(StatusActionListener listener,
                                      boolean buttonState,
                                      int position) {
        PopupMenu popup = new PopupMenu(itemView.getContext(), favouriteButton);
        popup.inflate(R.menu.status_favourite);
        Menu menu = popup.getMenu();
        if (buttonState) {
            menu.findItem(R.id.menu_action_favourite).setVisible(false);
        } else {
            menu.findItem(R.id.menu_action_unfavourite).setVisible(false);
        }
        popup.setOnMenuItemClickListener(item -> {
            listener.onFavourite(!buttonState, position);
            if(!buttonState) {
                favouriteButton.playAnimation();
                favouriteButton.setChecked(true);
            }
            return true;
        });
        popup.show();
    }

    public void setupWithStatus(@NonNull StatusViewData.Concrete status, final @NonNull StatusActionListener listener,
                                @NonNull StatusDisplayOptions statusDisplayOptions) {
        this.setupWithStatus(status, listener, statusDisplayOptions, null);
    }

    public void setupWithStatus(@NonNull StatusViewData.Concrete status,
                                @NonNull final StatusActionListener listener,
                                @NonNull StatusDisplayOptions statusDisplayOptions,
                                @Nullable Object payloads) {
        if (payloads == null) {
            Status actionable = status.getActionable();
            setDisplayName(actionable.getAccount().getName(), actionable.getAccount().getEmojis(), statusDisplayOptions);
            setUsername(actionable.getAccount().getUsername());
            setMetaData(status, statusDisplayOptions, listener);
            setIsReply(actionable.getInReplyToId() != null);
            setReplyCount(actionable.getRepliesCount(), statusDisplayOptions.showStatsInline());
            setAvatar(actionable.getAccount().getAvatar(), status.getRebloggedAvatar(),
                    actionable.getAccount().getBot(), statusDisplayOptions);
            setReblogged(actionable.getReblogged());
            setFavourited(actionable.getFavourited());
            setBookmarked(actionable.getBookmarked());
            List<Attachment> attachments = actionable.getAttachments();
            boolean sensitive = actionable.getSensitive();
            if (statusDisplayOptions.mediaPreviewEnabled() && hasPreviewableAttachment(attachments)) {
                setMediaPreviews(attachments, sensitive, listener, status.isShowingContent(), statusDisplayOptions.useBlurhash());

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
                mediaPreview.setVisibility(View.GONE);
                hideSensitiveMediaWarning();
            }

            setupCard(status, status.isExpanded(), statusDisplayOptions.cardViewMode(), statusDisplayOptions, listener);

            setupButtons(listener, actionable.getAccount().getId(), status.getContent().toString(),
                    statusDisplayOptions);
            setRebloggingEnabled(actionable.rebloggingAllowed(), actionable.getVisibility());

            setSpoilerAndContent(status, statusDisplayOptions, listener);

            setupFilterPlaceholder(status, listener, statusDisplayOptions);

            setDescriptionForStatus(status, statusDisplayOptions);

            // Workaround for RecyclerView 1.0.0 / androidx.core 1.0.0
            // RecyclerView tries to set AccessibilityDelegateCompat to null
            // but ViewCompat code replaces is with the default one. RecyclerView never
            // fetches another one from its delegate because it checks that it's set so we remove it
            // and let RecyclerView ask for a new delegate.
            itemView.setAccessibilityDelegate(null);
        } else {
            if (payloads instanceof List)
                for (Object item : (List<?>) payloads) {
                    if (Key.KEY_CREATED.equals(item)) {
                        setMetaData(status, statusDisplayOptions, listener);
                    }
                }

        }
    }

    private void setupFilterPlaceholder(StatusViewData.Concrete status, StatusActionListener listener, StatusDisplayOptions displayOptions) {
        if (status.getFilterAction() != Filter.Action.WARN) {
            showFilteredPlaceholder(false);
            return;
        }

        showFilteredPlaceholder(true);

        Filter matchedFilter = null;

        for (FilterResult result : status.getActionable().getFiltered()) {
            Filter filter = result.getFilter();
            if (filter.getAction() == Filter.Action.WARN) {
                matchedFilter = filter;
                break;
            }
        }

        filteredPlaceholderLabel.setText(itemView.getContext().getString(R.string.status_filter_placeholder_label_format, matchedFilter.getTitle()));
        filteredPlaceholderShowButton.setOnClickListener(view -> listener.clearWarningAction(getBindingAdapterPosition()));
    }

    protected static boolean hasPreviewableAttachment(@NonNull List<Attachment> attachments) {
        for (Attachment attachment : attachments) {
            if (attachment.getType() == Attachment.Type.AUDIO || attachment.getType() == Attachment.Type.UNKNOWN) {
                return false;
            }
        }
        return true;
    }

    private void setDescriptionForStatus(@NonNull StatusViewData.Concrete status,
                                         StatusDisplayOptions statusDisplayOptions) {
        Context context = itemView.getContext();
        Status actionable = status.getActionable();

        String description = context.getString(R.string.description_status,
                actionable.getAccount().getDisplayName(),
                getContentWarningDescription(context, status),
                (TextUtils.isEmpty(actionable.getSpoilerText()) || !actionable.getSensitive() || status.isExpanded() ? status.getContent() : ""),
                getCreatedAtDescription(actionable.getCreatedAt(), statusDisplayOptions),
                actionable.getEditedAt() != null ? context.getString(R.string.description_post_edited) : "",
                getReblogDescription(context, status),
                actionable.getAccount().getUsername(),
                actionable.getReblogged() ? context.getString(R.string.description_post_reblogged) : "",
                actionable.getFavourited() ? context.getString(R.string.description_post_favourited) : "",
                actionable.getBookmarked() ? context.getString(R.string.description_post_bookmarked) : "",
                getMediaDescription(context, status),
                getVisibilityDescription(context, actionable.getVisibility()),
                getFavsText(context, actionable.getFavouritesCount()),
                getReblogsText(context, actionable.getReblogsCount()),
                getPollDescription(status, context, statusDisplayOptions)
        );
        itemView.setContentDescription(description);
    }

    private static CharSequence getReblogDescription(Context context,
                                                     @NonNull StatusViewData.Concrete status) {
        Status reblog = status.getRebloggingStatus();
        if (reblog != null) {
            return context
                    .getString(R.string.post_boosted_format, reblog.getAccount().getUsername());
        } else {
            return "";
        }
    }

    private static CharSequence getMediaDescription(Context context,
                                                    @NonNull StatusViewData.Concrete status) {
        if (status.getActionable().getAttachments().isEmpty()) {
            return "";
        }
        StringBuilder mediaDescriptions = CollectionsKt.fold(
                status.getActionable().getAttachments(),
                new StringBuilder(),
                (builder, a) -> {
                    if (a.getDescription() == null) {
                        String placeholder =
                                context.getString(R.string.description_post_media_no_description_placeholder);
                        return builder.append(placeholder);
                    } else {
                        builder.append("; ");
                        return builder.append(a.getDescription());
                    }
                });
        return context.getString(R.string.description_post_media, mediaDescriptions);
    }

    private static CharSequence getContentWarningDescription(Context context,
                                                             @NonNull StatusViewData.Concrete status) {
        if (!TextUtils.isEmpty(status.getActionable().getSpoilerText())) {
            return context.getString(R.string.description_post_cw, status.getActionable().getSpoilerText());
        } else {
            return "";
        }
    }

    @NonNull
    protected static CharSequence getVisibilityDescription(@NonNull Context context, @Nullable Status.Visibility visibility) {

        if (visibility == null) {
            return "";
        }

        int resource;
        switch (visibility) {
            case PUBLIC:
                resource = R.string.description_visibility_public;
                break;
            case UNLISTED:
                resource = R.string.description_visibility_unlisted;
                break;
            case PRIVATE:
                resource = R.string.description_visibility_private;
                break;
            case DIRECT:
                resource = R.string.description_visibility_direct;
                break;
            default:
                return "";
        }
        return context.getString(resource);
    }

    private CharSequence getPollDescription(@NonNull StatusViewData.Concrete status,
                                            Context context,
                                            StatusDisplayOptions statusDisplayOptions) {
        PollViewData poll = PollViewDataKt.toViewData(status.getActionable().getPoll());
        if (poll == null) {
            return "";
        } else {
            Object[] args = new CharSequence[5];
            List<PollOptionViewData> options = poll.getOptions();
            for (int i = 0; i < args.length; i++) {
                if (i < options.size()) {
                    int percent = PollViewDataKt.calculatePercent(options.get(i).getVotesCount(), poll.getVotersCount(), poll.getVotesCount());
                    args[i] = buildDescription(options.get(i).getTitle(), percent, options.get(i).getVoted(), context);
                } else {
                    args[i] = "";
                }
            }
            args[4] = getPollInfoText(System.currentTimeMillis(), poll, statusDisplayOptions,
                    context);
            return context.getString(R.string.description_poll, args);
        }
    }

    @NonNull
    protected CharSequence getFavsText(@NonNull Context context, int count) {
        if (count > 0) {
            String countString = numberFormat.format(count);
            return HtmlCompat.fromHtml(context.getResources().getQuantityString(R.plurals.favs, count, countString), HtmlCompat.FROM_HTML_MODE_LEGACY);
        } else {
            return "";
        }
    }

    @NonNull
    protected CharSequence getReblogsText(@NonNull Context context, int count) {
        if (count > 0) {
            String countString = numberFormat.format(count);
            return HtmlCompat.fromHtml(context.getResources().getQuantityString(R.plurals.reblogs, count, countString), HtmlCompat.FROM_HTML_MODE_LEGACY);
        } else {
            return "";
        }
    }

    private void setupPoll(PollViewData poll, List<Emoji> emojis,
                           StatusDisplayOptions statusDisplayOptions,
                           StatusActionListener listener) {
        long timestamp = System.currentTimeMillis();

        boolean expired = poll.getExpired() || (poll.getExpiresAt() != null && timestamp > poll.getExpiresAt().getTime());

        Context context = pollDescription.getContext();

        pollOptions.setVisibility(View.VISIBLE);

        if (expired || poll.getVoted()) {
            // no voting possible
            View.OnClickListener viewThreadListener = v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onViewThread(position);
                }
            };
            pollAdapter.setup(
                    poll.getOptions(),
                    poll.getVotesCount(),
                    poll.getVotersCount(),
                    emojis,
                    PollAdapter.RESULT,
                    viewThreadListener,
                    statusDisplayOptions.animateEmojis()
            );

            pollButton.setVisibility(View.GONE);
        } else {
            // voting possible
            pollAdapter.setup(
                    poll.getOptions(),
                    poll.getVotesCount(),
                    poll.getVotersCount(),
                    emojis,
                    poll.getMultiple() ? PollAdapter.MULTIPLE : PollAdapter.SINGLE,
                    null,
                    statusDisplayOptions.animateEmojis()
            );

            pollButton.setVisibility(View.VISIBLE);

            pollButton.setOnClickListener(v -> {

                int position = getBindingAdapterPosition();

                if (position != RecyclerView.NO_POSITION) {

                    List<Integer> pollResult = pollAdapter.getSelected();

                    if (!pollResult.isEmpty()) {
                        listener.onVoteInPoll(position, pollResult);
                    }
                }

            });
        }

        pollDescription.setVisibility(View.VISIBLE);
        pollDescription.setText(getPollInfoText(timestamp, poll, statusDisplayOptions, context));
    }

    private CharSequence getPollInfoText(long timestamp, PollViewData poll,
                                         StatusDisplayOptions statusDisplayOptions,
                                         Context context) {
        String votesText;
        if (poll.getVotersCount() == null) {
            String voters = numberFormat.format(poll.getVotesCount());
            votesText = context.getResources().getQuantityString(R.plurals.poll_info_votes, poll.getVotesCount(), voters);
        } else {
            String voters = numberFormat.format(poll.getVotersCount());
            votesText = context.getResources().getQuantityString(R.plurals.poll_info_people, poll.getVotersCount(), voters);
        }
        CharSequence pollDurationInfo;
        if (poll.getExpired()) {
            pollDurationInfo = context.getString(R.string.poll_info_closed);
        } else if (poll.getExpiresAt() == null) {
            return votesText;
        } else {
            if (statusDisplayOptions.useAbsoluteTime()) {
                pollDurationInfo = context.getString(R.string.poll_info_time_absolute, absoluteTimeFormatter.format(poll.getExpiresAt(), false));
            } else {
                pollDurationInfo = TimestampUtils.formatPollDuration(pollDescription.getContext(), poll.getExpiresAt().getTime(), timestamp);
            }
        }

        return pollDescription.getContext().getString(R.string.poll_info_format, votesText, pollDurationInfo);
    }

    protected void setupCard(
            final @NonNull StatusViewData.Concrete status,
            boolean expanded,
            final @NonNull CardViewMode cardViewMode,
            final @NonNull StatusDisplayOptions statusDisplayOptions,
            final @NonNull StatusActionListener listener
    ) {
        if (cardView == null) {
            return;
        }

        final Status actionable = status.getActionable();
        final Card card = actionable.getCard();

        if (cardViewMode != CardViewMode.NONE &&
            actionable.getAttachments().size() == 0 &&
            actionable.getPoll() == null &&
            card != null &&
            !TextUtils.isEmpty(card.getUrl()) &&
            (!actionable.getSensitive() || expanded) &&
            (!status.isCollapsible() || !status.isCollapsed())) {

            cardView.setVisibility(View.VISIBLE);
            cardTitle.setText(card.getTitle());
            if (TextUtils.isEmpty(card.getDescription()) && TextUtils.isEmpty(card.getAuthorName())) {
                cardDescription.setVisibility(View.GONE);
            } else {
                cardDescription.setVisibility(View.VISIBLE);
                if (TextUtils.isEmpty(card.getDescription())) {
                    cardDescription.setText(card.getAuthorName());
                } else {
                    cardDescription.setText(card.getDescription());
                }
            }

            cardUrl.setText(card.getUrl());

            // Statuses from other activitypub sources can be marked sensitive even if there's no media,
            // so let's blur the preview in that case
            // If media previews are disabled, show placeholder for cards as well
            if (statusDisplayOptions.mediaPreviewEnabled() && !actionable.getSensitive() && !TextUtils.isEmpty(card.getImage())) {

                int radius = cardImage.getContext().getResources()
                        .getDimensionPixelSize(R.dimen.card_radius);
                ShapeAppearanceModel.Builder cardImageShape = ShapeAppearanceModel.builder();

                if (card.getWidth() > card.getHeight()) {
                    cardView.setOrientation(LinearLayout.VERTICAL);

                    cardImage.getLayoutParams().height = cardImage.getContext().getResources()
                            .getDimensionPixelSize(R.dimen.card_image_vertical_height);
                    cardImage.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
                    cardInfo.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                    cardInfo.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
                    cardImageShape.setTopLeftCorner(CornerFamily.ROUNDED, radius);
                    cardImageShape.setTopRightCorner(CornerFamily.ROUNDED, radius);
                } else {
                    cardView.setOrientation(LinearLayout.HORIZONTAL);
                    cardImage.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                    cardImage.getLayoutParams().width = cardImage.getContext().getResources()
                            .getDimensionPixelSize(R.dimen.card_image_horizontal_width);
                    cardInfo.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    cardInfo.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
                    cardImageShape.setTopLeftCorner(CornerFamily.ROUNDED, radius);
                    cardImageShape.setBottomLeftCorner(CornerFamily.ROUNDED, radius);
                }

                cardImage.setShapeAppearanceModel(cardImageShape.build());

                cardImage.setScaleType(ImageView.ScaleType.CENTER_CROP);

                RequestBuilder<Drawable> builder = Glide.with(cardImage.getContext())
                        .load(card.getImage())
                        .dontTransform();
                if (statusDisplayOptions.useBlurhash() && !TextUtils.isEmpty(card.getBlurhash())) {
                    builder = builder.placeholder(decodeBlurHash(card.getBlurhash()));
                }
                builder.into(cardImage);
            } else if (statusDisplayOptions.useBlurhash() && !TextUtils.isEmpty(card.getBlurhash())) {
                int radius = cardImage.getContext().getResources()
                        .getDimensionPixelSize(R.dimen.card_radius);

                cardView.setOrientation(LinearLayout.HORIZONTAL);
                cardImage.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                cardImage.getLayoutParams().width = cardImage.getContext().getResources()
                        .getDimensionPixelSize(R.dimen.card_image_horizontal_width);
                cardInfo.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                cardInfo.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;

                ShapeAppearanceModel cardImageShape = ShapeAppearanceModel.builder()
                        .setTopLeftCorner(CornerFamily.ROUNDED, radius)
                        .setBottomLeftCorner(CornerFamily.ROUNDED, radius)
                        .build();
                cardImage.setShapeAppearanceModel(cardImageShape);

                cardImage.setScaleType(ImageView.ScaleType.CENTER_CROP);

                Glide.with(cardImage.getContext())
                        .load(decodeBlurHash(card.getBlurhash()))
                        .dontTransform()
                        .into(cardImage);
            } else {
                cardView.setOrientation(LinearLayout.HORIZONTAL);
                cardImage.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                cardImage.getLayoutParams().width = cardImage.getContext().getResources()
                        .getDimensionPixelSize(R.dimen.card_image_horizontal_width);
                cardInfo.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                cardInfo.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;

                cardImage.setShapeAppearanceModel(new ShapeAppearanceModel());

                cardImage.setScaleType(ImageView.ScaleType.CENTER);

                Glide.with(cardImage.getContext())
                        .load(R.drawable.card_image_placeholder)
                        .into(cardImage);
            }

            View.OnClickListener visitLink = v -> listener.onViewUrl(card.getUrl());

            cardView.setOnClickListener(visitLink);
            // View embedded photos in our image viewer instead of opening the browser
            cardImage.setOnClickListener(card.getType().equals(Card.TYPE_PHOTO) && !TextUtils.isEmpty(card.getEmbedUrl()) ?
                    v -> cardView.getContext().startActivity(ViewMediaActivity.newSingleImageIntent(cardView.getContext(), card.getEmbedUrl())) :
                    visitLink);

            cardView.setClipToOutline(true);
        } else {
            cardView.setVisibility(View.GONE);
        }
    }

    public void showStatusContent(boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        avatar.setVisibility(visibility);
        avatarInset.setVisibility(visibility);
        displayName.setVisibility(visibility);
        username.setVisibility(visibility);
        metaInfo.setVisibility(visibility);
        contentWarningDescription.setVisibility(visibility);
        contentWarningButton.setVisibility(visibility);
        content.setVisibility(visibility);
        cardView.setVisibility(visibility);
        mediaContainer.setVisibility(visibility);
        pollOptions.setVisibility(visibility);
        pollButton.setVisibility(visibility);
        pollDescription.setVisibility(visibility);
        replyButton.setVisibility(visibility);
        reblogButton.setVisibility(visibility);
        favouriteButton.setVisibility(visibility);
        bookmarkButton.setVisibility(visibility);
        moreButton.setVisibility(visibility);
    }

    public void showFilteredPlaceholder(boolean show) {
        if (statusContainer != null) {
            statusContainer.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        if (filteredPlaceholder != null) {
            filteredPlaceholder.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }
}
