package com.keylesspalace.tusky.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.bumptech.glide.Glide;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Attachment;
import com.keylesspalace.tusky.entity.Attachment.Focus;
import com.keylesspalace.tusky.entity.Attachment.MetaData;
import com.keylesspalace.tusky.entity.Emoji;
import com.keylesspalace.tusky.entity.Poll;
import com.keylesspalace.tusky.entity.PollOption;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.util.CustomEmojiHelper;
import com.keylesspalace.tusky.util.DateUtils;
import com.keylesspalace.tusky.util.HtmlUtils;
import com.keylesspalace.tusky.util.LinkHelper;
import com.keylesspalace.tusky.util.ThemeUtils;
import com.keylesspalace.tusky.view.MediaPreviewImageView;
import com.keylesspalace.tusky.viewdata.StatusViewData;
import com.mikepenz.iconics.utils.Utils;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.RecyclerView;
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
    private boolean favourited;
    private boolean reblogged;
    protected MediaPreviewImageView[] mediaPreviews;
    private ImageView[] mediaOverlays;
    private TextView sensitiveMediaWarning;
    private View sensitiveMediaShow;
    protected TextView mediaLabel;
    private ToggleButton contentWarningButton;
    protected ImageView avatarInset;

    public ImageView avatar;
    public TextView timestampInfo;
    public TextView content;
    public TextView contentWarningDescription;

    private TextView[] pollResults;
    private TextView pollDescription;
    private RadioGroup pollRadioGroup;
    private RadioButton[] pollRadioOptions;
    private CheckBox[] pollCheckboxOptions;
    private Button pollButton;

    private boolean useAbsoluteTime;
    private SimpleDateFormat shortSdf;
    private SimpleDateFormat longSdf;
    private boolean showBotOverlay;

    private final NumberFormat numberFormat = NumberFormat.getNumberInstance();

    protected StatusBaseViewHolder(View itemView, boolean useAbsoluteTime) {
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
        reblogged = false;
        favourited = false;
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
        mediaLabel = itemView.findViewById(R.id.status_media_label);
        contentWarningDescription = itemView.findViewById(R.id.status_content_warning_description);
        contentWarningButton = itemView.findViewById(R.id.status_content_warning_button);
        avatarInset = itemView.findViewById(R.id.status_avatar_inset);

        pollResults = new TextView[] {
                itemView.findViewById(R.id.status_poll_option_result_0),
                itemView.findViewById(R.id.status_poll_option_result_1),
                itemView.findViewById(R.id.status_poll_option_result_2),
                itemView.findViewById(R.id.status_poll_option_result_3)
        };

        pollDescription = itemView.findViewById(R.id.status_poll_description);

        pollRadioGroup = itemView.findViewById(R.id.status_poll_radio_group);
        pollRadioOptions = new RadioButton[] {
                pollRadioGroup.findViewById(R.id.status_poll_radio_button_0),
                pollRadioGroup.findViewById(R.id.status_poll_radio_button_1),
                pollRadioGroup.findViewById(R.id.status_poll_radio_button_2),
                pollRadioGroup.findViewById(R.id.status_poll_radio_button_3)
        };
        pollCheckboxOptions = new CheckBox[] {
                itemView.findViewById(R.id.status_poll_checkbox_0),
                itemView.findViewById(R.id.status_poll_checkbox_1),
                itemView.findViewById(R.id.status_poll_checkbox_2),
                itemView.findViewById(R.id.status_poll_checkbox_3)
        };

        pollButton = itemView.findViewById(R.id.status_poll_button);

        this.useAbsoluteTime = useAbsoluteTime;
        shortSdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        longSdf = new SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault());
        showBotOverlay = PreferenceManager.getDefaultSharedPreferences(itemView.getContext()).getBoolean("showBotOverlay", true);
    }

    protected abstract int getMediaPreviewHeight(Context context);

    protected void setDisplayName(String name, List<Emoji> customEmojis) {
        CharSequence emojifiedName = CustomEmojiHelper.emojifyString(name, customEmojis, displayName);
        displayName.setText(emojifiedName);
    }

    protected void setUsername(String name) {
        Context context = username.getContext();
        String format = context.getString(R.string.status_username_format);
        String usernameText = String.format(format, name);
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
                contentWarningDescription.invalidate();
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

    protected void setAvatar(String url, @Nullable String rebloggedUrl, boolean isBot) {
        if (TextUtils.isEmpty(url)) {
            avatar.setImageResource(R.drawable.avatar_default);
        } else {
            Glide.with(avatar)
                    .load(url)
                    .placeholder(R.drawable.avatar_default)
                    .into(avatar);
        }

        if (showBotOverlay && isBot && TextUtils.isEmpty(rebloggedUrl)) {
            avatarInset.setVisibility(View.VISIBLE);
            avatarInset.setImageResource(R.drawable.ic_bot_24dp);
            avatarInset.setBackgroundColor(0x50ffffff);
        } else {
            avatarInset.setBackground(null);
            avatarInset.setVisibility(View.GONE);
        }
    }

    protected void setCreatedAt(@Nullable Date createdAt) {
        if (useAbsoluteTime) {
            timestampInfo.setText(getAbsoluteTime(createdAt));
        } else {
            String readout;
            if (createdAt != null) {
                long then = createdAt.getTime();
                long now = new Date().getTime();
                readout = DateUtils.getRelativeTimeSpanString(timestampInfo.getContext(), then, now);
            } else {
                // unknown minutes~
                readout = "?m";
            }
            timestampInfo.setText(readout);
        }
    }

    private String getAbsoluteTime(@Nullable Date createdAt) {
        String time;
        if (createdAt != null) {
            if (android.text.format.DateUtils.isToday(createdAt.getTime())) {
                time = shortSdf.format(createdAt);
            } else {
                time = longSdf.format(createdAt);
            }
        } else {
            time = "??:??:??";
        }
        return time;
    }

    private CharSequence getCreatedAtDescription(@Nullable Date createdAt) {
        if (useAbsoluteTime) {
            return getAbsoluteTime(createdAt);
        } else {
            /* This one is for screen-readers. Frequently, they would mispronounce timestamps like "17m"
             * as 17 meters instead of minutes. */

            if (createdAt != null) {
                long then = createdAt.getTime();
                long now = new Date().getTime();
                return android.text.format.DateUtils.getRelativeTimeSpanString(then, now,
                        android.text.format.DateUtils.SECOND_IN_MILLIS,
                        android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE);
            } else {
                // unknown minutes~
                return "? minutes";
            }
        }
    }

    protected void showContent(boolean show) {
        if (show) {
            itemView.setVisibility(View.VISIBLE);
            itemView.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        } else {
            itemView.setVisibility(View.INVISIBLE);
            itemView.getLayoutParams().height = Utils.convertDpToPx(itemView.getContext(), 24);
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
        this.reblogged = reblogged;
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
        this.favourited = favourited;
        favouriteButton.setChecked(favourited);
    }

    protected void setMediaPreviews(final List<Attachment> attachments, boolean sensitive,
                                    final StatusActionListener listener, boolean showingContent) {

        Context context = itemView.getContext();

        int mediaPreviewUnloadedId =
                ThemeUtils.getDrawableId(itemView.getContext(), R.attr.media_preview_unloaded_drawable,
                        android.R.color.black);

        final int n = Math.min(attachments.size(), Status.MAX_MEDIA_ATTACHMENTS);

        for (int i = 0; i < n; i++) {
            String previewUrl = attachments.get(i).getPreviewUrl();
            String description = attachments.get(i).getDescription();

            if (TextUtils.isEmpty(description)) {
                mediaPreviews[i].setContentDescription(context.getString(R.string.action_view_media));
            } else {
                mediaPreviews[i].setContentDescription(description);
            }

            mediaPreviews[i].setVisibility(View.VISIBLE);

            if (TextUtils.isEmpty(previewUrl)) {
                Glide.with(mediaPreviews[i])
                        .load(mediaPreviewUnloadedId)
                        .centerInside()
                        .into(mediaPreviews[i]);
            } else {
                MetaData meta = attachments.get(i).getMeta();
                Focus focus = meta != null ? meta.getFocus() : null;

                if (focus != null) { // If there is a focal point for this attachment:
                    mediaPreviews[i].setFocalPoint(focus);

                    Glide.with(mediaPreviews[i])
                            .load(previewUrl)
                            .placeholder(mediaPreviewUnloadedId)
                            .centerInside()
                            .addListener(mediaPreviews[i])
                            .into(mediaPreviews[i]);
                } else {
                    mediaPreviews[i].removeFocalPoint();

                    Glide.with(mediaPreviews[i])
                            .load(previewUrl)
                            .placeholder(mediaPreviewUnloadedId)
                            .centerInside()
                            .into(mediaPreviews[i]);
                }
            }

            final Attachment.Type type = attachments.get(i).getType();
            if (type == Attachment.Type.VIDEO | type == Attachment.Type.GIFV) {
                mediaOverlays[i].setVisibility(View.VISIBLE);
            } else {
                mediaOverlays[i].setVisibility(View.GONE);
            }

            final int urlIndex = i;
            mediaPreviews[i].setOnClickListener(v -> {
                if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onViewMedia(getAdapterPosition(), urlIndex, v);
                }
            });

            if (n <= 2) {
                mediaPreviews[0].getLayoutParams().height = getMediaPreviewHeight(context) * 2;
                mediaPreviews[1].getLayoutParams().height = getMediaPreviewHeight(context) * 2;
            } else {
                mediaPreviews[0].getLayoutParams().height = getMediaPreviewHeight(context);
                mediaPreviews[1].getLayoutParams().height = getMediaPreviewHeight(context);
                mediaPreviews[2].getLayoutParams().height = getMediaPreviewHeight(context);
                mediaPreviews[3].getLayoutParams().height = getMediaPreviewHeight(context);
            }
        }

        String hiddenContentText;
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

    protected void setMediaLabel(List<Attachment> attachments, boolean sensitive,
                                 final StatusActionListener listener) {
        if (attachments.size() == 0) {
            mediaLabel.setVisibility(View.GONE);
            return;
        }
        mediaLabel.setVisibility(View.VISIBLE);

        // Set the label's text.
        Context context = itemView.getContext();
        String labelText = getLabelTypeText(context, attachments.get(0).getType());
        if (sensitive) {
            String sensitiveText = context.getString(R.string.status_sensitive_media_title);
            labelText += String.format(" (%s)", sensitiveText);
        }
        mediaLabel.setText(labelText);

        // Set the icon next to the label.
        int drawableId = getLabelIcon(attachments.get(0).getType());
        Drawable drawable = AppCompatResources.getDrawable(context, drawableId);
        ThemeUtils.setDrawableTint(context, drawable, android.R.attr.textColorTertiary);
        mediaLabel.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);

        mediaLabel.setOnClickListener(v -> listener.onViewMedia(getAdapterPosition(), 0, null));
    }

    protected void hideSensitiveMediaWarning() {
        sensitiveMediaWarning.setVisibility(View.GONE);
        sensitiveMediaShow.setVisibility(View.GONE);
    }

    protected void setupButtons(final StatusActionListener listener, final String accountId) {
        /* Originally position was passed through to all these listeners, but it caused several
         * bugs where other statuses in the list would be removed or added and cause the position
         * here to become outdated. So, getting the adapter position at the time the listener is
         * actually called is the appropriate solution. */
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
        }
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

    protected void setupWithStatus(StatusViewData.Concrete status, final StatusActionListener listener,
                                   boolean mediaPreviewEnabled) {
        this.setupWithStatus(status, listener, mediaPreviewEnabled, null);
    }

    protected void setupWithStatus(StatusViewData.Concrete status, final StatusActionListener listener,
                                   boolean mediaPreviewEnabled, @Nullable Object payloads) {
        if (payloads == null) {
            setDisplayName(status.getUserFullName(), status.getAccountEmojis());
            setUsername(status.getNickname());
            setCreatedAt(status.getCreatedAt());
            setIsReply(status.getInReplyToId() != null);
            setAvatar(status.getAvatar(), status.getRebloggedAvatar(), status.isBot());
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
                mediaLabel.setVisibility(View.GONE);
            } else {
                setMediaLabel(attachments, sensitive, listener);
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

            setContentDescription(status);

            setupPoll(status.getPoll(),status.getStatusEmojis(), listener);

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


    private void setContentDescription(@Nullable StatusViewData.Concrete status) {
        if (status == null) {
            itemView.setContentDescription(
                    itemView.getContext().getString(R.string.load_more_placeholder_text));
        } else {
            setDescriptionForStatus(status);
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
                getReblogsText(context, status.getReblogsCount())
        );
        itemView.setContentDescription(description);
    }

    private CharSequence getReblogDescription(Context context,
                                              @NonNull StatusViewData.Concrete status) {
        CharSequence reblogDescriontion;
        String rebloggedUsername = status.getRebloggedByUsername();
        if (rebloggedUsername != null) {
            reblogDescriontion = context
                    .getString(R.string.status_boosted_format, rebloggedUsername);
        } else {
            reblogDescriontion = "";
        }
        return reblogDescriontion;
    }

    private CharSequence getMediaDescription(Context context,
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

    private CharSequence getContentWarningDescription(Context context,
                                                      @NonNull StatusViewData.Concrete status) {
        if (!TextUtils.isEmpty(status.getSpoilerText())) {
            return context.getString(R.string.description_status_cw, status.getSpoilerText());
        } else {
            return "";
        }
    }

    private CharSequence getVisibilityDescription(Context context, Status.Visibility visibility) {
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

    protected void setupPoll(Poll poll, List<Emoji> emojis, StatusActionListener listener) {
        if(poll == null) {
            for(TextView pollResult: pollResults) {
                pollResult.setVisibility(View.GONE);
            }
            pollDescription.setVisibility(View.GONE);
            pollRadioGroup.setVisibility(View.GONE);

            for(CheckBox checkBox: pollCheckboxOptions) {
                checkBox.setVisibility(View.GONE);
            }

            pollButton.setVisibility(View.GONE);
        } else {
            long timestamp = System.currentTimeMillis();

            boolean expired = poll.getExpired() || (poll.getExpiresAt() != null && timestamp > poll.getExpiresAt().getTime());

            Context context = pollDescription.getContext();

            if(expired || poll.getVoted())   {
                // no voting possible
               setupPollResult(poll, emojis);
            } else {
                // voting possible
                setupPollVoting(poll, emojis, listener);
            }

            pollDescription.setVisibility(View.VISIBLE);

            String votes = numberFormat.format(poll.getVotesCount());
            String votesText = context.getResources().getQuantityString(R.plurals.poll_info_votes, poll.getVotesCount(), votes);


            CharSequence pollDurationInfo;
            if(expired) {
                pollDurationInfo = context.getString(R.string.poll_info_closed);
            } else {
                if(useAbsoluteTime) {
                    pollDurationInfo = context.getString(R.string.poll_info_time_absolute, getAbsoluteTime(poll.getExpiresAt()));
                } else {
                    String pollDuration = DateUtils.formatDuration(pollDescription.getContext(), poll.getExpiresAt().getTime(), timestamp);
                    pollDurationInfo = context.getString(R.string.poll_info_time_relative, pollDuration);
                }
            }

            String pollInfo = pollDescription.getContext().getString(R.string.poll_info_format, votesText, pollDurationInfo);

            pollDescription.setText(pollInfo);


        }
    }

    private void setupPollResult(Poll poll, List<Emoji> emojis) {
        List<PollOption> options = poll.getOptions();

        for(int i = 0; i < Status.MAX_POLL_OPTIONS; i++) {
            if(i < options.size()) {
                int percent = options.get(i).getPercent(poll.getVotesCount());

                String pollOptionText = pollResults[i].getContext().getString(R.string.poll_option_format, percent, options.get(i).getTitle());
                pollResults[i].setText(CustomEmojiHelper.emojifyText(HtmlUtils.fromHtml(pollOptionText), emojis, pollResults[i]));
                pollResults[i].setVisibility(View.VISIBLE);

                int level = percent * 100;

                pollResults[i].getBackground().setLevel(level);

            } else {
                pollResults[i].setVisibility(View.GONE);
            }
        }

        pollRadioGroup.setVisibility(View.GONE);

        for(CheckBox checkBox: pollCheckboxOptions) {
            checkBox.setVisibility(View.GONE);
        }

        pollButton.setVisibility(View.GONE);
    }

    private void setupPollVoting(Poll poll, List<Emoji> emojis, StatusActionListener listener) {
        List<PollOption> options = poll.getOptions();

        pollButton.setVisibility(View.VISIBLE);

        for(TextView pollResult: pollResults) {
            pollResult.setVisibility(View.GONE);
        }

        if(poll.getMultiple()) {

            pollRadioGroup.setVisibility(View.GONE);

            for(int i = 0; i < Status.MAX_POLL_OPTIONS; i++) {
                if(i < options.size()) {
                    pollCheckboxOptions[i].setText(CustomEmojiHelper.emojifyString(options.get(i).getTitle(), emojis, pollCheckboxOptions[i]));
                    pollCheckboxOptions[i].setVisibility(View.VISIBLE);
                    pollCheckboxOptions[i].setChecked(false);
                } else {
                    pollCheckboxOptions[i].setVisibility(View.GONE);
                }
            }

            pollButton.setOnClickListener(v -> {

                int position = getAdapterPosition();

                if (position != RecyclerView.NO_POSITION) {

                    List<Integer> pollResult = new ArrayList<>(options.size());
                    for (int i = 0; i < options.size(); i++) {
                        if (pollCheckboxOptions[i].isChecked()) {
                            pollResult.add(i);
                        }
                    }
                    if (pollResult.size() == 0) {
                        return;
                    }

                    listener.onVoteInPoll(position, pollResult);
                }

            });
        } else {

            for(CheckBox pollCheckbox: pollCheckboxOptions) {
                pollCheckbox.setVisibility(View.GONE);
            }

            pollRadioGroup.setVisibility(View.VISIBLE);
            pollRadioGroup.clearCheck();

            for(int i = 0; i < Status.MAX_POLL_OPTIONS; i++) {
                if(i < options.size()) {
                    pollRadioOptions[i].setText(CustomEmojiHelper.emojifyString(options.get(i).getTitle(), emojis, pollRadioOptions[i]));
                    pollRadioOptions[i].setVisibility(View.VISIBLE);
                } else {
                    pollRadioOptions[i].setVisibility(View.GONE);
                }
            }

            pollButton.setOnClickListener(v -> {

                int position = getAdapterPosition();

                if (position != RecyclerView.NO_POSITION) {

                    int selectedRadioButtonIndex;
                    switch (pollRadioGroup.getCheckedRadioButtonId()) {
                        case R.id.status_poll_radio_button_0:
                            selectedRadioButtonIndex = 0;
                            break;
                        case R.id.status_poll_radio_button_1:
                            selectedRadioButtonIndex = 1;
                            break;
                        case R.id.status_poll_radio_button_2:
                            selectedRadioButtonIndex = 2;
                            break;
                        case R.id.status_poll_radio_button_3:
                            selectedRadioButtonIndex = 3;
                            break;
                        default:
                            return;
                    }

                    listener.onVoteInPoll(position, Collections.singletonList(selectedRadioButtonIndex));
                }
            });

        }
    }

}
