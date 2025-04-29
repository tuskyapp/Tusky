package com.keylesspalace.tusky.adapter;

import static com.keylesspalace.tusky.viewdata.PollViewDataKt.buildDescription;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.TooltipCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.text.HtmlCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.ViewMediaActivity;
import com.keylesspalace.tusky.entity.Attachment;
import com.keylesspalace.tusky.entity.Attachment.Focus;
import com.keylesspalace.tusky.entity.Attachment.MetaData;
import com.keylesspalace.tusky.entity.Filter;
import com.keylesspalace.tusky.entity.PreviewCard;
import com.keylesspalace.tusky.entity.Emoji;
import com.keylesspalace.tusky.entity.HashTag;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.entity.TimelineAccount;
import com.keylesspalace.tusky.entity.Translation;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.util.AbsoluteTimeFormatter;
import com.keylesspalace.tusky.util.AttachmentHelper;
import com.keylesspalace.tusky.util.BlurhashDrawable;
import com.keylesspalace.tusky.util.CardViewMode;
import com.keylesspalace.tusky.util.CompositeWithOpaqueBackground;
import com.keylesspalace.tusky.util.CustomEmojiHelper;
import com.keylesspalace.tusky.util.ImageLoadingHelper;
import com.keylesspalace.tusky.util.LinkHelper;
import com.keylesspalace.tusky.util.LocaleUtilsKt;
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
import com.keylesspalace.tusky.viewdata.TranslationViewData;

import java.text.NumberFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import at.connyduck.sparkbutton.SparkButton;
import at.connyduck.sparkbutton.helpers.Utils;
import kotlin.Unit;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.functions.Function0;

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
    protected final ConstraintLayout mediaContainer;
    protected final MediaPreviewLayout mediaPreview;
    private final TextView sensitiveMediaWarning;
    private final View sensitiveMediaShow;
    protected final TextView[] mediaLabels;
    protected final MaterialCardView[] mediaLabelContainers;
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
    private final Button pollResultsButton;

    private final MaterialCardView cardView;
    private final LinearLayout cardLayout;
    private final ShapeableImageView cardImage;
    private final TextView cardTitle;
    private final TextView cardMetadata;
    private final TextView cardAuthor;
    private final TextView cardAuthorButton;

    private final PollAdapter pollAdapter;
    protected final ConstraintLayout statusContainer;
    private final TextView translationStatusView;
    private final Button untranslateButton;
    private final TextView trailingHashtagView;


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
        mediaLabelContainers = new MaterialCardView[]{
            itemView.findViewById(R.id.status_media_label_container_0),
            itemView.findViewById(R.id.status_media_label_container_1),
            itemView.findViewById(R.id.status_media_label_container_2),
            itemView.findViewById(R.id.status_media_label_container_3)
        };
        mediaDescriptions = new CharSequence[mediaLabels.length];
        contentWarningDescription = itemView.findViewById(R.id.status_content_warning_description);
        contentWarningButton = itemView.findViewById(R.id.status_content_warning_button);
        avatarInset = itemView.findViewById(R.id.status_avatar_inset);

        pollOptions = itemView.findViewById(R.id.status_poll_options);
        pollDescription = itemView.findViewById(R.id.status_poll_description);
        pollButton = itemView.findViewById(R.id.status_poll_button);
        pollResultsButton = itemView.findViewById(R.id.status_poll_results_button);

        cardView = itemView.findViewById(R.id.status_card_view);
        cardLayout = itemView.findViewById(R.id.status_card_layout);
        cardImage = itemView.findViewById(R.id.card_image);
        cardTitle = itemView.findViewById(R.id.card_title);
        cardMetadata = itemView.findViewById(R.id.card_metadata);
        cardAuthor = itemView.findViewById(R.id.card_author);
        cardAuthorButton = itemView.findViewById(R.id.card_author_button);

        statusContainer = itemView.findViewById(R.id.status_container);

        pollAdapter = new PollAdapter();
        pollOptions.setAdapter(pollAdapter);
        pollOptions.setLayoutManager(new LinearLayoutManager(pollOptions.getContext()));
        ((DefaultItemAnimator) pollOptions.getItemAnimator()).setSupportsChangeAnimations(false);

        translationStatusView = itemView.findViewById(R.id.status_translation_status);
        untranslateButton = itemView.findViewById(R.id.status_button_untranslate);
        trailingHashtagView = itemView.findViewById(R.id.status_trailing_hashtags_content);

        this.avatarRadius48dp = itemView.getContext().getResources().getDimensionPixelSize(R.dimen.avatar_radius_48dp);
        this.avatarRadius36dp = itemView.getContext().getResources().getDimensionPixelSize(R.dimen.avatar_radius_36dp);
        this.avatarRadius24dp = itemView.getContext().getResources().getDimensionPixelSize(R.dimen.avatar_radius_24dp);

        mediaPreviewUnloaded = new ColorDrawable(MaterialColors.getColor(itemView, R.attr.colorBackgroundAccent));

        TouchDelegateHelper.expandTouchSizeToFillRow((ViewGroup) itemView, CollectionsKt.listOfNotNull(replyButton, reblogButton, favouriteButton, bookmarkButton, moreButton));
    }

    protected void setDisplayName(@NonNull String name, @NonNull List<Emoji> customEmojis, @NonNull StatusDisplayOptions statusDisplayOptions) {
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
        String spoilerText = status.getSpoilerText();
        List<Emoji> emojis = actionable.getEmojis();

        boolean sensitive = !TextUtils.isEmpty(spoilerText);
        boolean expanded = status.isExpanded();

        if (sensitive) {
            CharSequence emojiSpoiler = CustomEmojiHelper.emojify(
                spoilerText, emojis, contentWarningDescription, statusDisplayOptions.animateEmojis()
            );
            contentWarningDescription.setText(emojiSpoiler);
            contentWarningDescription.setVisibility(View.VISIBLE);
            boolean hasContent = !TextUtils.isEmpty(status.getContent());
            if (hasContent) {
                contentWarningButton.setVisibility(View.VISIBLE);
                setContentWarningButtonText(expanded);
                contentWarningButton.setOnClickListener(view -> toggleExpandedState(true, !expanded, status, statusDisplayOptions, listener));
            } else {
                contentWarningButton.setVisibility(View.GONE);
            }
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

        setupCard(status, expanded, !status.isShowingContent(), statusDisplayOptions.cardViewMode(), statusDisplayOptions, listener);
    }

    private void setTextVisible(boolean sensitive,
                                boolean expanded,
                                @NonNull final StatusViewData.Concrete status,
                                @NonNull final StatusDisplayOptions statusDisplayOptions,
                                final StatusActionListener listener) {

        Status actionable = status.getActionable();
        Spanned content = status.getContent();
        List<Status.Mention> mentions = actionable.getMentions();
        List<HashTag> tags = actionable.getTags();
        List<Emoji> emojis = actionable.getEmojis();
        PollViewData poll = PollViewDataKt.toViewData(status.getPoll());

        if (expanded) {
            CharSequence emojifiedText = CustomEmojiHelper.emojify(content, emojis, this.content, statusDisplayOptions.animateEmojis());
            LinkHelper.setClickableText(this.content, emojifiedText, mentions, tags, listener, this.trailingHashtagView);
            if (trailingHashtagView != null && status.isCollapsible() && status.isCollapsed()) {
                trailingHashtagView.setVisibility(View.GONE);
            }
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
            if (trailingHashtagView != null) {
                trailingHashtagView.setVisibility(View.GONE);
            }
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
        pollResultsButton.setVisibility(View.GONE);
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

    protected void setReplyButtonImage(boolean isReply) {
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
                inactiveId = R.drawable.ic_lock_24dp;
                activeId = R.drawable.ic_lock_24dp_filled;
            } else {
                inactiveId = R.drawable.ic_repeat_24dp;
                activeId = R.drawable.ic_repeat_active_24dp;
            }
            reblogButton.setInactiveImage(inactiveId);
            reblogButton.setActiveImage(activeId);
        } else {
            int disabledId;
            if (visibility == Status.Visibility.DIRECT) {
                disabledId = R.drawable.ic_mail_24dp;
            } else {
                disabledId = R.drawable.ic_lock_24dp;
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
        return new BlurhashDrawable(this.avatar.getContext(), blurhash);
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
        boolean useBlurhash,
        Filter filter
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
                imageView.setForegroundGravity(Gravity.CENTER);
                imageView.setForeground(AppCompatResources.getDrawable(itemView.getContext(), R.drawable.play_indicator));
            } else {
                imageView.setForeground(null);
            }

            final CharSequence formattedDescription = AttachmentHelper.getFormattedDescription(attachment, imageView.getContext());
            setAttachmentClickListener(imageView, listener, i, formattedDescription, true);

            if (filter != null) {
                sensitiveMediaWarning.setText(sensitiveMediaWarning.getContext().getString(R.string.status_filter_placeholder_label_format, filter.getTitle()));
            } else if (sensitive) {
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
        return switch (type) {
            case IMAGE -> R.drawable.ic_image_24dp;
            case GIFV -> R.drawable.ic_gif_box_24dp;
            case VIDEO -> R.drawable.ic_slideshow_24dp;
            case AUDIO -> R.drawable.ic_music_box_24dp;
            default -> R.drawable.ic_attach_file_24dp;
        };
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
                mediaLabelContainers[i].setVisibility(View.VISIBLE);
                mediaDescriptions[i] = AttachmentHelper.getFormattedDescription(attachment, context);
                updateMediaLabel(i, sensitive, showingContent);

                // Set the icon next to the label.
                int drawableId = getLabelIcon(attachments.get(0).getType());
                mediaLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(drawableId, 0, 0, 0);

                setAttachmentClickListener(mediaLabel, listener, i, mediaDescriptions[i], false);
            } else {
                mediaLabelContainers[i].setVisibility(View.GONE);
            }
        }
    }

    private void setAttachmentClickListener(@NonNull View view, @NonNull StatusActionListener listener,
                                            int index, CharSequence description, boolean animateTransition) {
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
        TooltipCompat.setTooltipText(view, description);
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
                    listener.onReblog(!buttonState, position, null, () -> {
                        if (!buttonState) { reblogButton.playAnimation(); }
                        reblogButton.setChecked(!buttonState);
                        return Unit.INSTANCE;
                    });
                }
                return false;
            });
        }


        favouriteButton.setEventListener((button, buttonState) -> {
            // return true to play animation
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                listener.onFavourite(!buttonState, position, () -> {
                    if (!buttonState) { favouriteButton.playAnimation(); }
                    favouriteButton.setChecked(!buttonState);
                    return Unit.INSTANCE;
                });
            }
            return false;
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

    public void setupWithStatus(@NonNull StatusViewData.Concrete status,
                                @NonNull final StatusActionListener listener,
                                @NonNull StatusDisplayOptions statusDisplayOptions,
                                @NonNull List<Object> payloads,
                                final boolean showStatusInfo) {
        if (payloads.isEmpty()) {
            Status actionable = status.getActionable();
            setDisplayName(actionable.getAccount().getName(), actionable.getAccount().getEmojis(), statusDisplayOptions);
            setUsername(actionable.getAccount().getUsername());
            setMetaData(status, statusDisplayOptions, listener);
            setReplyButtonImage(actionable.isReply());
            setReplyCount(actionable.getRepliesCount(), statusDisplayOptions.showStatsInline());
            setAvatar(actionable.getAccount().getAvatar(), status.getRebloggedAvatar(),
                actionable.getAccount().getBot(), statusDisplayOptions);
            setReblogged(actionable.getReblogged());
            setFavourited(actionable.getFavourited());
            setBookmarked(actionable.getBookmarked());
            List<Attachment> attachments = status.getAttachments();
            boolean sensitive = actionable.getSensitive();
            if (attachments.isEmpty()) {
                mediaContainer.setVisibility(View.GONE);
            } else if (statusDisplayOptions.mediaPreviewEnabled() && hasPreviewableAttachment(attachments)) {
                mediaContainer.setVisibility(View.VISIBLE);

                setMediaPreviews(attachments, sensitive, listener, status.isShowingContent(), statusDisplayOptions.useBlurhash(), status.getFilter());

                if (attachments.isEmpty()) {
                    hideSensitiveMediaWarning();
                }
                // Hide the unused label.
                for (MaterialCardView mediaLabelContainer : mediaLabelContainers) {
                    mediaLabelContainer.setVisibility(View.GONE);
                }
            } else {
                mediaContainer.setVisibility(View.VISIBLE);

                setMediaLabel(attachments, sensitive, listener, status.isShowingContent());
                // Hide all unused views.
                mediaPreview.setVisibility(View.GONE);
                hideSensitiveMediaWarning();
            }

            setupCard(status, status.isExpanded(), !status.isShowingContent(), statusDisplayOptions.cardViewMode(), statusDisplayOptions, listener);

            setupButtons(listener, actionable.getAccount().getId(), status.getContent().toString(),
                statusDisplayOptions);

            setTranslationStatus(status, listener);

            setRebloggingEnabled(actionable.isRebloggingAllowed(), actionable.getVisibility());

            setSpoilerAndContent(status, statusDisplayOptions, listener);

            setDescriptionForStatus(status, statusDisplayOptions);

            // Workaround for RecyclerView 1.0.0 / androidx.core 1.0.0
            // RecyclerView tries to set AccessibilityDelegateCompat to null
            // but ViewCompat code replaces is with the default one. RecyclerView never
            // fetches another one from its delegate because it checks that it's set so we remove it
            // and let RecyclerView ask for a new delegate.
            itemView.setAccessibilityDelegate(null);
        } else {
            for (Object item : payloads) {
                if (Key.KEY_CREATED.equals(item)) {
                    setMetaData(status, statusDisplayOptions, listener);
                    if (status.getStatus().getCard() != null && status.getStatus().getCard().getPublishedAt() != null) {
                        // there is a preview card showing the published time, we need to refresh it as well
                        setupCard(status, status.isExpanded(), !status.isShowingContent(), statusDisplayOptions.cardViewMode(), statusDisplayOptions, listener);
                    }
                    break;
                }
            }
        }
    }

    private void setTranslationStatus(StatusViewData.Concrete status, StatusActionListener listener) {
        var translationViewData = status.getTranslation();
        if (translationViewData != null) {
            if (translationViewData instanceof TranslationViewData.Loaded) {
                Translation translation = ((TranslationViewData.Loaded) translationViewData).getData();
                translationStatusView.setVisibility(View.VISIBLE);
                var langName = LocaleUtilsKt.localeNameForUntrustedISO639LangCode(translation.getDetectedSourceLanguage());
                translationStatusView.setText(translationStatusView.getContext().getString(R.string.label_translated, langName, translation.getProvider()));
                untranslateButton.setVisibility(View.VISIBLE);
                untranslateButton.setOnClickListener((v) -> listener.onUntranslate(getBindingAdapterPosition()));
            } else {
                translationStatusView.setVisibility(View.VISIBLE);
                translationStatusView.setText(R.string.label_translating);
                untranslateButton.setVisibility(View.GONE);
                untranslateButton.setOnClickListener(null);
            }
        } else {
            translationStatusView.setVisibility(View.GONE);
            untranslateButton.setVisibility(View.GONE);
            untranslateButton.setOnClickListener(null);
        }
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
            // 1 display_name
            actionable.getAccount().getDisplayName(),
            // 2 CW?
            getContentWarningDescription(context, status),
            // 3 content?
            (TextUtils.isEmpty(status.getSpoilerText()) || !actionable.getSensitive() || status.isExpanded() ? status.getContent() : ""),
            // 4 date
            getCreatedAtDescription(actionable.getCreatedAt(), statusDisplayOptions),
            // 5 edited?
            actionable.getEditedAt() != null ? context.getString(R.string.description_post_edited) : "",
            // 6 reposted_by?
            getReblogDescription(context, status),
            // 7 username
            actionable.getAccount().getUsername(),
            // 8 reposted
            actionable.getReblogged() ? context.getString(R.string.description_post_reblogged) : "",
            // 9 favorited
            actionable.getFavourited() ? context.getString(R.string.description_post_favourited) : "",
            // 10 bookmarked
            actionable.getBookmarked() ? context.getString(R.string.description_post_bookmarked) : "",
            // 11 media
            getMediaDescription(context, status),
            // 12 visibility
            getVisibilityDescription(context, actionable.getVisibility()),
            // 13 fav_number
            getFavsText(context, actionable.getFavouritesCount()),
            // 14 reblog_number
            getReblogsText(context, actionable.getReblogsCount()),
            // 15 poll?
            getPollDescription(status, context, statusDisplayOptions),
            // 16 translated?
            getTranslatedDescription(context, status.getTranslation())
        );
        itemView.setContentDescription(description);
    }

    private String getTranslatedDescription(Context context, TranslationViewData translationViewData) {
        if (translationViewData == null) {
            return "";
        } else if (translationViewData instanceof TranslationViewData.Loading) {
            return context.getString(R.string.label_translating);
        } else {
            Translation translation = ((TranslationViewData.Loaded) translationViewData).getData();
            var langName = LocaleUtilsKt.localeNameForUntrustedISO639LangCode(translation.getDetectedSourceLanguage());
            return context.getString(R.string.label_translated, langName, translation.getProvider());
        }
    }

    private static CharSequence getReblogDescription(Context context,
                                                     @NonNull StatusViewData.Concrete status) {
        @Nullable
        Status reblog = status.getRebloggingStatus();
        if (reblog != null) {
            return context
                .getString(R.string.post_boosted_format, reblog.getAccount().getUsername());
        } else {
            return "";
        }
    }

    private static CharSequence getMediaDescription(Context context,
                                                    @NonNull StatusViewData.Concrete viewData) {
        if (viewData.getAttachments().isEmpty()) {
            return "";
        }
        StringBuilder mediaDescriptions = CollectionsKt.fold(
            viewData.getAttachments(),
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
        if (!TextUtils.isEmpty(status.getSpoilerText())) {
            return context.getString(R.string.description_post_cw, status.getSpoilerText());
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
        PollViewData poll = PollViewDataKt.toViewData(status.getPoll());
        if (poll == null) {
            return "";
        } else {
            Object[] args = new CharSequence[5];
            List<PollOptionViewData> options = poll.getOptions();
            for (int i = 0; i < args.length; i++) {
                if (i < options.size()) {
                    int percent = PollViewDataKt.calculatePercent(options.get(i).getVotesCount(), poll.getVotersCount(), poll.getVotesCount());
                    args[i] = buildDescription(options.get(i).getTitle(), percent, options.get(i).getVoted(), context, null);
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
        String countString = numberFormat.format(count);
        return HtmlCompat.fromHtml(context.getResources().getQuantityString(R.plurals.favs, count, countString), HtmlCompat.FROM_HTML_MODE_LEGACY);
    }

    @NonNull
    protected CharSequence getReblogsText(@NonNull Context context, int count) {
        String countString = numberFormat.format(count);
        return HtmlCompat.fromHtml(context.getResources().getQuantityString(R.plurals.reblogs, count, countString), HtmlCompat.FROM_HTML_MODE_LEGACY);
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
            pollResultsButton.setVisibility(View.GONE);
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
            pollResultsButton.setVisibility(View.VISIBLE);

            pollButton.setOnClickListener(v -> {

                int position = getBindingAdapterPosition();

                if (position != RecyclerView.NO_POSITION) {

                    List<Integer> pollResult = pollAdapter.getSelected();

                    if (!pollResult.isEmpty()) {
                        listener.onVoteInPoll(position, pollResult);
                    }
                }

            });

            pollResultsButton.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();

                if (position != RecyclerView.NO_POSITION) {
                    listener.onShowPollResults(position);
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
        boolean blurMedia,
        final @NonNull CardViewMode cardViewMode,
        final @NonNull StatusDisplayOptions statusDisplayOptions,
        final @NonNull StatusActionListener listener
    ) {
        if (cardView == null) {
            return;
        }

        final Context context = cardView.getContext();

        final Status actionable = status.getActionable();
        final PreviewCard card = actionable.getCard();

        if (cardViewMode != CardViewMode.NONE &&
            actionable.getAttachments().isEmpty() &&
            actionable.getPoll() == null &&
            card != null &&
            !TextUtils.isEmpty(card.getUrl()) &&
            (TextUtils.isEmpty(actionable.getSpoilerText()) || expanded) &&
            (!status.isCollapsible() || !status.isCollapsed())) {

            cardView.setVisibility(View.VISIBLE);
            cardTitle.setText(card.getTitle());

            String providerName = card.getProviderName();
            if (TextUtils.isEmpty(providerName)) {
                providerName = Uri.parse(card.getUrl()).getHost();
            }

            if (TextUtils.isEmpty(providerName)) {
                cardMetadata.setVisibility(View.GONE);
            } else {
                cardMetadata.setVisibility(View.VISIBLE);
                if (card.getPublishedAt() == null) {
                    cardMetadata.setText(providerName);
                } else {
                    String metadataJoiner = context.getString(R.string.metadata_joiner);
                    cardMetadata.setText(providerName + metadataJoiner + TimestampUtils.getRelativeTimeSpanString(context, card.getPublishedAt().getTime(), System.currentTimeMillis()));
                }
            }

            String cardAuthorName;
            final TimelineAccount cardAuthorAccount;
            if (card.getAuthors().isEmpty()) {
                cardAuthorAccount = null;
                cardAuthorName = card.getAuthorName();
            } else {
                cardAuthorName = card.getAuthors().get(0).getName();
                cardAuthorAccount = card.getAuthors().get(0).getAccount();
                if (cardAuthorAccount != null) {
                    cardAuthorName = cardAuthorAccount.getName();
                }
            }

            final boolean hasNoAuthorName = TextUtils.isEmpty(cardAuthorName);

            if (hasNoAuthorName && TextUtils.isEmpty(card.getDescription())) {
                cardAuthor.setVisibility(View.GONE);
                cardAuthorButton.setVisibility(View.GONE);
            } else if (hasNoAuthorName) {
                cardAuthor.setVisibility(View.VISIBLE);
                cardAuthor.setText(card.getDescription());
                cardAuthorButton.setVisibility(View.GONE);
            } else if (cardAuthorAccount == null) {
                cardAuthor.setVisibility(View.VISIBLE);
                cardAuthor.setText(context.getString(R.string.preview_card_by_author, cardAuthorName));
                cardAuthorButton.setVisibility(View.GONE);
            } else {
                cardAuthorButton.setVisibility(View.VISIBLE);
                final String buttonText = context.getString(R.string.preview_card_more_by_author, cardAuthorName);
                final CharSequence emojifiedButtonText = CustomEmojiHelper.emojify(buttonText, cardAuthorAccount.getEmojis(), cardAuthorButton, statusDisplayOptions.animateEmojis());
                cardAuthorButton.setText(emojifiedButtonText);
                cardAuthorButton.setOnClickListener(v-> listener.onViewAccount(cardAuthorAccount.getId()));
                cardAuthor.setVisibility(View.GONE);
            }

            // Statuses from other activitypub sources can be marked sensitive even if there's no media,
            // so let's blur the preview in that case
            // If media previews are disabled, show placeholder for cards as well
            if (statusDisplayOptions.mediaPreviewEnabled() && !blurMedia && !actionable.getSensitive() && !TextUtils.isEmpty(card.getImage())) {

                int radius = context.getResources().getDimensionPixelSize(R.dimen.inner_card_radius);
                ShapeAppearanceModel.Builder cardImageShape = ShapeAppearanceModel.builder();

                if (card.getWidth() > card.getHeight()) {
                    cardLayout.setOrientation(LinearLayout.VERTICAL);
                    cardImage.getLayoutParams().height = cardImage.getContext().getResources()
                        .getDimensionPixelSize(R.dimen.card_image_vertical_height);
                    cardImage.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
                    cardImageShape.setTopLeftCorner(CornerFamily.ROUNDED, radius);
                    cardImageShape.setTopRightCorner(CornerFamily.ROUNDED, radius);
                } else {
                    cardLayout.setOrientation(LinearLayout.HORIZONTAL);
                    cardImage.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                    cardImage.getLayoutParams().width = cardImage.getContext().getResources()
                        .getDimensionPixelSize(R.dimen.card_image_horizontal_width);
                    cardImageShape.setTopLeftCorner(CornerFamily.ROUNDED, radius);
                    cardImageShape.setBottomLeftCorner(CornerFamily.ROUNDED, radius);
                }

                cardImage.setShapeAppearanceModel(cardImageShape.build());

                cardImage.setScaleType(ImageView.ScaleType.CENTER_CROP);

                RequestBuilder<Drawable> builder = Glide.with(cardImage.getContext())
                    .load(card.getImage());
                if (statusDisplayOptions.useBlurhash() && !TextUtils.isEmpty(card.getBlurhash())) {
                    builder = builder.placeholder(decodeBlurHash(card.getBlurhash()));
                }
                builder.centerInside()
                    .into(cardImage);
            } else if (statusDisplayOptions.useBlurhash() && !TextUtils.isEmpty(card.getBlurhash())) {
                int radius = cardImage.getContext().getResources()
                    .getDimensionPixelSize(R.dimen.inner_card_radius);

                cardLayout.setOrientation(LinearLayout.HORIZONTAL);
                cardImage.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                cardImage.getLayoutParams().width = cardImage.getContext().getResources()
                    .getDimensionPixelSize(R.dimen.card_image_horizontal_width);

                ShapeAppearanceModel cardImageShape = ShapeAppearanceModel.builder()
                    .setTopLeftCorner(CornerFamily.ROUNDED, radius)
                    .setBottomLeftCorner(CornerFamily.ROUNDED, radius)
                    .build();
                cardImage.setShapeAppearanceModel(cardImageShape);

                cardImage.setScaleType(ImageView.ScaleType.CENTER_CROP);

                Glide.with(cardImage.getContext())
                    .load(decodeBlurHash(card.getBlurhash()))
                    .into(cardImage);
            } else {
                cardLayout.setOrientation(LinearLayout.HORIZONTAL);
                cardImage.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                cardImage.getLayoutParams().width = cardImage.getContext().getResources()
                    .getDimensionPixelSize(R.dimen.card_image_horizontal_width);

                cardImage.setShapeAppearanceModel(new ShapeAppearanceModel());

                cardImage.setScaleType(ImageView.ScaleType.CENTER);

                Glide.with(cardImage.getContext())
                    .load(R.drawable.card_image_placeholder)
                    .into(cardImage);
            }

            View.OnClickListener visitLink = v -> listener.onViewUrl(card.getUrl());

            cardView.setOnClickListener(visitLink);
            // View embedded photos in our image viewer instead of opening the browser
            cardImage.setOnClickListener(card.getType().equals(PreviewCard.TYPE_PHOTO) && !TextUtils.isEmpty(card.getEmbedUrl()) ?
                v -> cardView.getContext().startActivity(ViewMediaActivity.newSingleImageIntent(cardView.getContext(), card.getEmbedUrl())) :
                visitLink);
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
        pollResultsButton.setVisibility(visibility);
        pollDescription.setVisibility(visibility);
        replyButton.setVisibility(visibility);
        reblogButton.setVisibility(visibility);
        favouriteButton.setVisibility(visibility);
        bookmarkButton.setVisibility(visibility);
        moreButton.setVisibility(visibility);
    }
}
