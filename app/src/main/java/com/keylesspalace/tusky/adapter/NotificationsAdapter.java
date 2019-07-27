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
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.entity.Emoji;
import com.keylesspalace.tusky.entity.Notification;
import com.keylesspalace.tusky.interfaces.LinkListener;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.util.CustomEmojiHelper;
import com.keylesspalace.tusky.util.TimestampUtils;
import com.keylesspalace.tusky.util.ImageLoadingHelper;
import com.keylesspalace.tusky.util.LinkHelper;
import com.keylesspalace.tusky.util.SmartLengthInputFilter;
import com.keylesspalace.tusky.viewdata.NotificationViewData;
import com.keylesspalace.tusky.viewdata.StatusViewData;
import com.mikepenz.iconics.utils.Utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.text.BidiFormatter;
import androidx.recyclerview.widget.RecyclerView;

public class NotificationsAdapter extends RecyclerView.Adapter {

    public interface AdapterDataSource<T> {
        int getItemCount();

        T getItemAt(int pos);
    }


    private static final int VIEW_TYPE_STATUS = 0;
    private static final int VIEW_TYPE_STATUS_NOTIFICATION = 1;
    private static final int VIEW_TYPE_FOLLOW = 2;
    private static final int VIEW_TYPE_PLACEHOLDER = 3;
    private static final int VIEW_TYPE_UNKNOWN = 4;

    private static final InputFilter[] COLLAPSE_INPUT_FILTER = new InputFilter[]{SmartLengthInputFilter.INSTANCE};
    private static final InputFilter[] NO_INPUT_FILTER = new InputFilter[0];

    private String accountId;
    private StatusActionListener statusListener;
    private NotificationActionListener notificationActionListener;
    private boolean mediaPreviewEnabled;
    private boolean useAbsoluteTime;
    private boolean showBotOverlay;
    private boolean animateAvatar;
    private BidiFormatter bidiFormatter;
    private AdapterDataSource<NotificationViewData> dataSource;

    public NotificationsAdapter(String accountId,
                                AdapterDataSource<NotificationViewData> dataSource,
                                StatusActionListener statusListener,
                                NotificationActionListener notificationActionListener) {

        this.accountId = accountId;
        this.dataSource = dataSource;
        this.statusListener = statusListener;
        this.notificationActionListener = notificationActionListener;
        mediaPreviewEnabled = true;
        useAbsoluteTime = false;
        showBotOverlay = true;
        animateAvatar = false;
        bidiFormatter = BidiFormatter.getInstance();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VIEW_TYPE_STATUS:    {
                View view = inflater
                        .inflate(R.layout.item_status, parent, false);
                return new StatusViewHolder(view, useAbsoluteTime);
            }
            case VIEW_TYPE_STATUS_NOTIFICATION: {
                View view = inflater
                        .inflate(R.layout.item_status_notification, parent, false);
                return new StatusNotificationViewHolder(view, useAbsoluteTime, animateAvatar);
            }
            case VIEW_TYPE_FOLLOW: {
                View view = inflater
                        .inflate(R.layout.item_follow, parent, false);
                return new FollowViewHolder(view, animateAvatar);
            }
            case VIEW_TYPE_PLACEHOLDER: {
                View view = inflater
                        .inflate(R.layout.item_status_placeholder, parent, false);
                return new PlaceholderViewHolder(view);
            }
            default:
            case VIEW_TYPE_UNKNOWN: {
                View view = new View(parent.getContext());
                view.setLayoutParams(
                        new ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                Utils.convertDpToPx(parent.getContext(), 24)
                        )
                );
                return new RecyclerView.ViewHolder(view) {};
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        bindViewHolder(viewHolder, position, null);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position, @NonNull List payloads) {
        bindViewHolder(viewHolder, position, payloads);
    }

    private void bindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position, @Nullable List payloads) {
        Object payloadForHolder = payloads != null && !payloads.isEmpty() ? payloads.get(0) : null;
        if (position < this.dataSource.getItemCount()) {
            NotificationViewData notification = dataSource.getItemAt(position);
            if (notification instanceof NotificationViewData.Placeholder) {
                if (payloadForHolder == null) {
                    NotificationViewData.Placeholder placeholder = ((NotificationViewData.Placeholder) notification);
                    PlaceholderViewHolder holder = (PlaceholderViewHolder) viewHolder;
                    holder.setup(statusListener, placeholder.isLoading());
                }
                return;
            }
            NotificationViewData.Concrete concreteNotificaton =
                    (NotificationViewData.Concrete) notification;
            switch (viewHolder.getItemViewType()) {
                case VIEW_TYPE_STATUS: {
                    StatusViewHolder holder = (StatusViewHolder) viewHolder;
                    StatusViewData.Concrete status = concreteNotificaton.getStatusViewData();
                    holder.setupWithStatus(status,
                            statusListener, mediaPreviewEnabled, showBotOverlay, animateAvatar, payloadForHolder);
                    if(concreteNotificaton.getType() == Notification.Type.POLL) {
                        holder.setPollInfo(accountId.equals(concreteNotificaton.getAccount().getId()));
                    } else {
                        holder.hideStatusInfo();
                    }
                    break;
                }
                case VIEW_TYPE_STATUS_NOTIFICATION: {
                    StatusNotificationViewHolder holder = (StatusNotificationViewHolder) viewHolder;
                    StatusViewData.Concrete statusViewData = concreteNotificaton.getStatusViewData();
                    if (payloadForHolder == null) {
                        if (statusViewData == null) {
                            holder.showNotificationContent(false);
                        } else {
                            holder.showNotificationContent(true);

                            holder.setDisplayName(statusViewData.getUserFullName(), statusViewData.getAccountEmojis());
                            holder.setUsername(statusViewData.getNickname());
                            holder.setCreatedAt(statusViewData.getCreatedAt());

                            holder.setAvatars(concreteNotificaton.getStatusViewData().getAvatar(),
                                    concreteNotificaton.getAccount().getAvatar());
                        }

                        holder.setMessage(concreteNotificaton, statusListener, bidiFormatter);
                        holder.setupButtons(notificationActionListener,
                                concreteNotificaton.getAccount().getId(),
                                concreteNotificaton.getId());
                    } else {
                        if (payloadForHolder instanceof List)
                            for (Object item : payloads) {
                                if (StatusBaseViewHolder.Key.KEY_CREATED.equals(item)) {
                                    holder.setCreatedAt(statusViewData.getCreatedAt());
                                }
                            }
                    }
                    break;
                }
                case VIEW_TYPE_FOLLOW: {
                    if (payloadForHolder == null) {
                        FollowViewHolder holder = (FollowViewHolder) viewHolder;
                        holder.setMessage(concreteNotificaton.getAccount(), bidiFormatter);
                        holder.setupButtons(notificationActionListener, concreteNotificaton.getAccount().getId());
                    }
                    break;
                }
                default:
            }
        }

    }

    @Override
    public int getItemCount() {
        return dataSource.getItemCount();
    }

    @Override
    public int getItemViewType(int position) {
        NotificationViewData notification = dataSource.getItemAt(position);
        if (notification instanceof NotificationViewData.Concrete) {
            NotificationViewData.Concrete concrete = ((NotificationViewData.Concrete) notification);
            switch (concrete.getType()) {
                case MENTION:
                case POLL: {
                    return VIEW_TYPE_STATUS;
                }
                case FAVOURITE:
                case REBLOG: {
                    return VIEW_TYPE_STATUS_NOTIFICATION;
                }
                case FOLLOW: {
                    return VIEW_TYPE_FOLLOW;
                }
                default: {
                    return VIEW_TYPE_UNKNOWN;
                }
            }
        } else if (notification instanceof NotificationViewData.Placeholder) {
            return VIEW_TYPE_PLACEHOLDER;
        } else {
            throw new AssertionError("Unknown notification type");
        }

    }

    public void setMediaPreviewEnabled(boolean enabled) {
        mediaPreviewEnabled = enabled;
    }

    public boolean isMediaPreviewEnabled() {
        return mediaPreviewEnabled;
    }

    public void setUseAbsoluteTime(boolean useAbsoluteTime) {
        this.useAbsoluteTime = useAbsoluteTime;
    }

    public void setShowBotOverlay(boolean showBotOverlay) {
        this.showBotOverlay = showBotOverlay;
    }

    public void setAnimateAvatar(boolean animateAvatar) {
        this.animateAvatar = animateAvatar;
    }

    public interface NotificationActionListener {
        void onViewAccount(String id);

        void onViewStatusForNotificationId(String notificationId);

        void onExpandedChange(boolean expanded, int position);

        /**
         * Called when the status {@link android.widget.ToggleButton} responsible for collapsing long
         * status content is interacted with.
         *
         * @param isCollapsed Whether the status content is shown in a collapsed state or fully.
         * @param position    The position of the status in the list.
         */
        void onNotificationContentCollapsedChange(boolean isCollapsed, int position);
    }

    private static class FollowViewHolder extends RecyclerView.ViewHolder {
        private TextView message;
        private TextView usernameView;
        private TextView displayNameView;
        private ImageView avatar;
        private boolean animateAvatar;

        FollowViewHolder(View itemView, boolean animateAvatar) {
            super(itemView);
            message = itemView.findViewById(R.id.notification_text);
            usernameView = itemView.findViewById(R.id.notification_username);
            displayNameView = itemView.findViewById(R.id.notification_display_name);
            avatar = itemView.findViewById(R.id.notification_avatar);
            this.animateAvatar = animateAvatar;
        }

        void setMessage(Account account, BidiFormatter bidiFormatter) {
            Context context = message.getContext();

            String format = context.getString(R.string.notification_follow_format);
            String wrappedDisplayName = bidiFormatter.unicodeWrap(account.getName());
            String wholeMessage = String.format(format, wrappedDisplayName);
            CharSequence emojifiedMessage = CustomEmojiHelper.emojifyString(wholeMessage, account.getEmojis(), message);
            message.setText(emojifiedMessage);

            String username = context.getString(R.string.status_username_format, account.getUsername());
            usernameView.setText(username);

            CharSequence emojifiedDisplayName = CustomEmojiHelper.emojifyString(wrappedDisplayName, account.getEmojis(), usernameView);

            displayNameView.setText(emojifiedDisplayName);

            int avatarRadius = avatar.getContext().getResources()
                    .getDimensionPixelSize(R.dimen.avatar_radius_24dp);

            ImageLoadingHelper.loadAvatar(account.getAvatar(), avatar, avatarRadius, animateAvatar);

        }

        void setupButtons(final NotificationActionListener listener, final String accountId) {
            avatar.setOnClickListener(v -> listener.onViewAccount(accountId));
        }
    }

    private static class StatusNotificationViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener, ToggleButton.OnCheckedChangeListener {
        private final TextView message;
        private final View statusNameBar;
        private final TextView displayName;
        private final TextView username;
        private final TextView timestampInfo;
        private final TextView statusContent;
        private final ImageView statusAvatar;
        private final ImageView notificationAvatar;
        private final TextView contentWarningDescriptionTextView;
        private final ToggleButton contentWarningButton;
        private final ToggleButton contentCollapseButton; // TODO: This code SHOULD be based on StatusBaseViewHolder

        private String accountId;
        private String notificationId;
        private NotificationActionListener notificationActionListener;
        private StatusViewData.Concrete statusViewData;

        private boolean useAbsoluteTime;
        private boolean animateAvatar;
        private SimpleDateFormat shortSdf;
        private SimpleDateFormat longSdf;

        StatusNotificationViewHolder(View itemView, boolean useAbsoluteTime, boolean animateAvatar) {
            super(itemView);
            message = itemView.findViewById(R.id.notification_top_text);
            statusNameBar = itemView.findViewById(R.id.status_name_bar);
            displayName = itemView.findViewById(R.id.status_display_name);
            username = itemView.findViewById(R.id.status_username);
            timestampInfo = itemView.findViewById(R.id.status_timestamp_info);
            statusContent = itemView.findViewById(R.id.notification_content);
            statusAvatar = itemView.findViewById(R.id.notification_status_avatar);
            notificationAvatar = itemView.findViewById(R.id.notification_notification_avatar);
            contentWarningDescriptionTextView = itemView.findViewById(R.id.notification_content_warning_description);
            contentWarningButton = itemView.findViewById(R.id.notification_content_warning_button);
            contentCollapseButton = itemView.findViewById(R.id.button_toggle_notification_content);

            int darkerFilter = Color.rgb(123, 123, 123);
            statusAvatar.setColorFilter(darkerFilter, PorterDuff.Mode.MULTIPLY);
            notificationAvatar.setColorFilter(darkerFilter, PorterDuff.Mode.MULTIPLY);

            itemView.setOnClickListener(this);
            message.setOnClickListener(this);
            statusContent.setOnClickListener(this);
            contentWarningButton.setOnCheckedChangeListener(this);

            this.useAbsoluteTime = useAbsoluteTime;
            this.animateAvatar = animateAvatar;
            shortSdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            longSdf = new SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault());
        }

        private void showNotificationContent(boolean show) {
            statusNameBar.setVisibility(show ? View.VISIBLE : View.GONE);
            contentWarningDescriptionTextView.setVisibility(show ? View.VISIBLE : View.GONE);
            contentWarningButton.setVisibility(show ? View.VISIBLE : View.GONE);
            statusContent.setVisibility(show ? View.VISIBLE : View.GONE);
            statusAvatar.setVisibility(show ? View.VISIBLE : View.GONE);
            notificationAvatar.setVisibility(show ? View.VISIBLE : View.GONE);
        }

        private void setDisplayName(String name, List<Emoji> emojis) {
            CharSequence emojifiedName = CustomEmojiHelper.emojifyString(name, emojis, displayName);
            displayName.setText(emojifiedName);
        }

        private void setUsername(String name) {
            Context context = username.getContext();
            String format = context.getString(R.string.status_username_format);
            String usernameText = String.format(format, name);
            username.setText(usernameText);
        }

        protected void setCreatedAt(@Nullable Date createdAt) {
            if (useAbsoluteTime) {
                String time;
                if (createdAt != null) {
                    if (System.currentTimeMillis() - createdAt.getTime() > 86400000L) {
                        time = longSdf.format(createdAt);
                    } else {
                        time = shortSdf.format(createdAt);
                    }
                } else {
                    time = "??:??:??";
                }
                timestampInfo.setText(time);
            } else {
                // This is the visible timestampInfo.
                String readout;
                /* This one is for screen-readers. Frequently, they would mispronounce timestamps like "17m"
                 * as 17 meters instead of minutes. */
                CharSequence readoutAloud;
                if (createdAt != null) {
                    long then = createdAt.getTime();
                    long now = new Date().getTime();
                    readout = TimestampUtils.getRelativeTimeSpanString(timestampInfo.getContext(), then, now);
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
        }

        void setMessage(NotificationViewData.Concrete notificationViewData, LinkListener listener, BidiFormatter bidiFormatter) {
            this.statusViewData = notificationViewData.getStatusViewData();

            String displayName = bidiFormatter.unicodeWrap(notificationViewData.getAccount().getName());
            Notification.Type type = notificationViewData.getType();

            Context context = message.getContext();
            String format;
            Drawable icon;
            switch (type) {
                default:
                case FAVOURITE: {
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_star_24dp);
                    if (icon != null) {
                        icon.setColorFilter(ContextCompat.getColor(context,
                                R.color.tusky_orange), PorterDuff.Mode.SRC_ATOP);
                    }

                    format = context.getString(R.string.notification_favourite_format);
                    break;
                }
                case REBLOG: {
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_repeat_24dp);
                    if (icon != null) {
                        icon.setColorFilter(ContextCompat.getColor(context,
                                R.color.tusky_blue), PorterDuff.Mode.SRC_ATOP);
                    }

                    format = context.getString(R.string.notification_reblog_format);
                    break;
                }
            }
            message.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
            String wholeMessage = String.format(format, displayName);
            final SpannableStringBuilder str = new SpannableStringBuilder(wholeMessage);
            str.setSpan(new StyleSpan(Typeface.BOLD), 0, displayName.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            CharSequence emojifiedText = CustomEmojiHelper.emojifyText(str, notificationViewData.getAccount().getEmojis(), message);
            message.setText(emojifiedText);

            if (statusViewData != null) {
                boolean hasSpoiler = !TextUtils.isEmpty(statusViewData.getSpoilerText());
                contentWarningDescriptionTextView.setVisibility(hasSpoiler ? View.VISIBLE : View.GONE);
                contentWarningButton.setVisibility(hasSpoiler ? View.VISIBLE : View.GONE);
                setupContentAndSpoiler(notificationViewData, listener);
            }

        }

        void setupButtons(final NotificationActionListener listener, final String accountId,
                          final String notificationId) {
            this.notificationActionListener = listener;
            this.accountId = accountId;
            this.notificationId = notificationId;
        }

        void setAvatars(@Nullable String statusAvatarUrl, @Nullable String notificationAvatarUrl) {

            int statusAvatarRadius = statusAvatar.getContext().getResources()
                    .getDimensionPixelSize(R.dimen.avatar_radius_48dp);

            ImageLoadingHelper.loadAvatar(statusAvatarUrl,
                    statusAvatar, statusAvatarRadius, animateAvatar);

            int notificationAvatarRadius = statusAvatar.getContext().getResources()
                    .getDimensionPixelSize(R.dimen.avatar_radius_24dp);

            ImageLoadingHelper.loadAvatar(notificationAvatarUrl,
                    notificationAvatar, notificationAvatarRadius, animateAvatar);
        }

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.notification_container:
                case R.id.notification_content:
                    if (notificationActionListener != null)
                        notificationActionListener.onViewStatusForNotificationId(notificationId);
                    break;
                case R.id.notification_top_text:
                    if (notificationActionListener != null)
                        notificationActionListener.onViewAccount(accountId);
                    break;
            }
        }

        private void setupContentAndSpoiler(NotificationViewData.Concrete notificationViewData, final LinkListener listener) {

            boolean shouldShowContentIfSpoiler = notificationViewData.isExpanded();
            boolean hasSpoiler = !TextUtils.isEmpty(statusViewData.getSpoilerText());
            if (!shouldShowContentIfSpoiler && hasSpoiler) {
                statusContent.setVisibility(View.GONE);
            } else {
                statusContent.setVisibility(View.VISIBLE);
            }

            Spanned content = statusViewData.getContent();
            List<Emoji> emojis = statusViewData.getStatusEmojis();

            if (statusViewData.isCollapsible() && (notificationViewData.isExpanded() || !hasSpoiler)) {
                contentCollapseButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && notificationActionListener != null) {
                        notificationActionListener.onNotificationContentCollapsedChange(isChecked, position);
                    }
                });

                contentCollapseButton.setVisibility(View.VISIBLE);
                if (statusViewData.isCollapsed()) {
                    contentCollapseButton.setChecked(true);
                    statusContent.setFilters(COLLAPSE_INPUT_FILTER);
                } else {
                    contentCollapseButton.setChecked(false);
                    statusContent.setFilters(NO_INPUT_FILTER);
                }
            } else {
                contentCollapseButton.setVisibility(View.GONE);
                statusContent.setFilters(NO_INPUT_FILTER);
            }

            Spanned emojifiedText = CustomEmojiHelper.emojifyText(content, emojis, statusContent);
            LinkHelper.setClickableText(statusContent, emojifiedText, statusViewData.getMentions(), listener);

            Spanned emojifiedContentWarning =
                    CustomEmojiHelper.emojifyString(statusViewData.getSpoilerText(), statusViewData.getStatusEmojis(), contentWarningDescriptionTextView);
            contentWarningDescriptionTextView.setText(emojifiedContentWarning);
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                notificationActionListener.onExpandedChange(isChecked, getAdapterPosition());
            }
            statusContent.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        }
    }
}
