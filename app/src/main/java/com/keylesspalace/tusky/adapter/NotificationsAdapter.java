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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.text.BidiFormatter;
import android.support.v7.widget.RecyclerView;
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
import com.keylesspalace.tusky.util.DateUtils;
import com.keylesspalace.tusky.util.LinkHelper;
import com.keylesspalace.tusky.viewdata.NotificationViewData;
import com.keylesspalace.tusky.viewdata.StatusViewData;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NotificationsAdapter extends RecyclerView.Adapter {
    private static final int VIEW_TYPE_MENTION = 0;
    private static final int VIEW_TYPE_FOOTER = 1;
    private static final int VIEW_TYPE_STATUS_NOTIFICATION = 2;
    private static final int VIEW_TYPE_FOLLOW = 3;
    private static final int VIEW_TYPE_PLACEHOLDER = 4;

    private List<NotificationViewData> notifications;
    private StatusActionListener statusListener;
    private NotificationActionListener notificationActionListener;
    private boolean mediaPreviewEnabled;
    private BidiFormatter bidiFormatter;

    public NotificationsAdapter(StatusActionListener statusListener,
                                NotificationActionListener notificationActionListener) {
        super();
        notifications = new ArrayList<>();
        this.statusListener = statusListener;
        this.notificationActionListener = notificationActionListener;
        mediaPreviewEnabled = true;
        bidiFormatter = BidiFormatter.getInstance();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            default:
            case VIEW_TYPE_MENTION: {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_status, parent, false);
                return new StatusViewHolder(view);
            }
            case VIEW_TYPE_FOOTER: {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_footer, parent, false);
                return new FooterViewHolder(view);
            }
            case VIEW_TYPE_STATUS_NOTIFICATION: {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_status_notification, parent, false);
                return new StatusNotificationViewHolder(view);
            }
            case VIEW_TYPE_FOLLOW: {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_follow, parent, false);
                return new FollowViewHolder(view);
            }
            case VIEW_TYPE_PLACEHOLDER: {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_status_placeholder, parent, false);
                return new PlaceholderViewHolder(view);
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        if (position < notifications.size()) {
            NotificationViewData notification = notifications.get(position);
            if (notification instanceof NotificationViewData.Placeholder) {
                NotificationViewData.Placeholder placeholder = ((NotificationViewData.Placeholder) notification);
                PlaceholderViewHolder holder = (PlaceholderViewHolder) viewHolder;
                holder.setup(statusListener, placeholder.isLoading());
                return;
            }
            NotificationViewData.Concrete concreteNotificaton =
                    (NotificationViewData.Concrete) notification;
            Notification.Type type = concreteNotificaton.getType();
            switch (type) {
                case MENTION: {
                    StatusViewHolder holder = (StatusViewHolder) viewHolder;
                    StatusViewData.Concrete status = concreteNotificaton.getStatusViewData();
                    holder.setupWithStatus(status,
                            statusListener, mediaPreviewEnabled);
                    break;
                }
                case FAVOURITE:
                case REBLOG: {
                    StatusNotificationViewHolder holder = (StatusNotificationViewHolder) viewHolder;
                    StatusViewData.Concrete statusViewData = concreteNotificaton.getStatusViewData();

                    if(statusViewData == null) {
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
                    break;
                }
                case FOLLOW: {
                    FollowViewHolder holder = (FollowViewHolder) viewHolder;
                    holder.setMessage(concreteNotificaton.getAccount(), bidiFormatter);
                    holder.setupButtons(notificationActionListener, concreteNotificaton.getAccount().getId());
                    break;
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (position == notifications.size()) {
            return VIEW_TYPE_FOOTER;
        } else {
            NotificationViewData notification = notifications.get(position);
            if (notification instanceof NotificationViewData.Concrete) {
                NotificationViewData.Concrete concrete = ((NotificationViewData.Concrete) notification);
                switch (concrete.getType()) {
                    default:
                    case MENTION: {
                        return VIEW_TYPE_MENTION;
                    }
                    case FAVOURITE:
                    case REBLOG: {
                        return VIEW_TYPE_STATUS_NOTIFICATION;
                    }
                    case FOLLOW: {
                        return VIEW_TYPE_FOLLOW;
                    }
                }
            } else if (notification instanceof NotificationViewData.Placeholder) {
                return VIEW_TYPE_PLACEHOLDER;
            } else {
                throw new AssertionError("Unknown notification type");
            }
        }
    }

    public void update(@Nullable List<NotificationViewData> newNotifications) {
        if (newNotifications == null || newNotifications.isEmpty()) {
            return;
        }
        notifications.clear();
        notifications.addAll(newNotifications);
        notifyDataSetChanged();
    }

    public void updateItemWithNotify(int position, NotificationViewData notification,
                                     boolean notifyAdapter) {
        notifications.set(position, notification);
        if (notifyAdapter) notifyItemChanged(position);
    }

    public void addItems(List<NotificationViewData> newNotifications) {
        notifications.addAll(newNotifications);
        notifyItemRangeInserted(notifications.size(), newNotifications.size());
    }

    public void removeItemAndNotify(int position) {
        notifications.remove(position);
        notifyItemRemoved(position);
    }

    public void clear() {
        notifications.clear();
        notifyDataSetChanged();
    }

    public void setMediaPreviewEnabled(boolean enabled) {
        mediaPreviewEnabled = enabled;
    }

    public boolean isMediaPreviewEnabled() {
        return mediaPreviewEnabled;
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

        FollowViewHolder(View itemView) {
            super(itemView);
            message = itemView.findViewById(R.id.notification_text);
            usernameView = itemView.findViewById(R.id.notification_username);
            displayNameView = itemView.findViewById(R.id.notification_display_name);
            avatar = itemView.findViewById(R.id.notification_avatar);
            //workaround because Android < API 21 does not support setting drawableLeft from xml when it is a vector image
            Drawable followIcon = ContextCompat.getDrawable(message.getContext(), R.drawable.ic_person_add_24dp);
            message.setCompoundDrawablesWithIntrinsicBounds(followIcon, null, null, null);
        }

        void setMessage(Account account, BidiFormatter bidiFormatter) {
            Context context = message.getContext();

            String format = context.getString(R.string.notification_follow_format);
            String wrappedDisplayName = bidiFormatter.unicodeWrap(account.getName());
            String wholeMessage = String.format(format, wrappedDisplayName);
            CharSequence emojifiedMessage = CustomEmojiHelper.emojifyString(wholeMessage, account.getEmojis(), message);
            message.setText(emojifiedMessage);

            format = context.getString(R.string.status_username_format);
            String username = String.format(format, account.getUsername());
            usernameView.setText(username);

            CharSequence emojifiedDisplayName = CustomEmojiHelper.emojifyString(wrappedDisplayName, account.getEmojis(), usernameView);

            displayNameView.setText(emojifiedDisplayName);

            if (TextUtils.isEmpty(account.getAvatar())) {
                avatar.setImageResource(R.drawable.avatar_default);
            } else {
                Picasso.with(context)
                        .load(account.getAvatar())
                        .fit()
                        .placeholder(R.drawable.avatar_default)
                        .into(avatar);
            }
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
        private final ViewGroup container;
        private final ImageView statusAvatar;
        private final ImageView notificationAvatar;
        private final TextView contentWarningDescriptionTextView;
        private final ToggleButton contentWarningButton;
        private final ToggleButton contentCollapseButton; // TODO: This code SHOULD be based on StatusBaseViewHolder

        private String accountId;
        private String notificationId;
        private NotificationActionListener notificationActionListener;
        private StatusViewData.Concrete statusViewData;

        StatusNotificationViewHolder(View itemView) {
            super(itemView);
            message = itemView.findViewById(R.id.notification_top_text);
            statusNameBar = itemView.findViewById(R.id.status_name_bar);
            displayName = itemView.findViewById(R.id.status_display_name);
            username = itemView.findViewById(R.id.status_username);
            timestampInfo = itemView.findViewById(R.id.status_timestamp_info);
            statusContent = itemView.findViewById(R.id.notification_content);
            container = itemView.findViewById(R.id.notification_container);
            statusAvatar = itemView.findViewById(R.id.notification_status_avatar);
            notificationAvatar = itemView.findViewById(R.id.notification_notification_avatar);
            contentWarningDescriptionTextView = itemView.findViewById(R.id.notification_content_warning_description);
            contentWarningButton = itemView.findViewById(R.id.notification_content_warning_button);

            int darkerFilter = Color.rgb(123, 123, 123);
            statusAvatar.setColorFilter(darkerFilter, PorterDuff.Mode.MULTIPLY);
            notificationAvatar.setColorFilter(darkerFilter, PorterDuff.Mode.MULTIPLY);

            container.setOnClickListener(this);
            message.setOnClickListener(this);
            statusContent.setOnClickListener(this);
            contentWarningButton.setOnCheckedChangeListener(this);

            contentCollapseButton = itemView.findViewById(R.id.button_toggle_notification_content);
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

        private void setCreatedAt(@Nullable Date createdAt) {
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
                                R.color.status_favourite_button_marked_dark), PorterDuff.Mode.SRC_ATOP);
                    }

                    format = context.getString(R.string.notification_favourite_format);
                    break;
                }
                case REBLOG: {
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_repeat_24dp);
                    if(icon != null) {
                        icon.setColorFilter(ContextCompat.getColor(context,
                                R.color.color_accent_dark), PorterDuff.Mode.SRC_ATOP);
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
            Context context = statusAvatar.getContext();

            if (TextUtils.isEmpty(statusAvatarUrl)) {
                statusAvatar.setImageResource(R.drawable.avatar_default);
            } else {
                Picasso.with(context)
                        .load(statusAvatarUrl)
                        .placeholder(R.drawable.avatar_default)
                        .into(statusAvatar);
            }

            if (TextUtils.isEmpty(notificationAvatarUrl)) {
                notificationAvatar.setImageResource(R.drawable.avatar_default);
            } else {
                Picasso.with(context)
                        .load(notificationAvatarUrl)
                        .placeholder(R.drawable.avatar_default)
                        .fit()
                        .into(notificationAvatar);
            }
        }

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.notification_container:
                case R.id.notification_content:
                    if (notificationActionListener != null) notificationActionListener.onViewStatusForNotificationId(notificationId);
                    break;
                case R.id.notification_top_text:
                    if (notificationActionListener != null) notificationActionListener.onViewAccount(accountId);
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

            if(contentCollapseButton != null && statusViewData.isCollapsible() && (notificationViewData.isExpanded() || !hasSpoiler)) {
                contentCollapseButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    int position = getAdapterPosition();
                    if(position != RecyclerView.NO_POSITION && notificationActionListener != null) {
                        notificationActionListener.onNotificationContentCollapsedChange(isChecked, position);
                    }
                });

                contentCollapseButton.setVisibility(View.VISIBLE);
                if(statusViewData.isCollapsed()) {
                    contentCollapseButton.setChecked(true);
                    statusContent.setFilters(new InputFilter[]{(source, start, end, dest, dstart, dend) -> {

                        // Code imported from InputFilter.LengthFilter
                        // https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/text/InputFilter.java#175

                        // Changes:
                        // - After the text it adds and ellipsis to make it feel like the text continues
                        // - Max value is 500 rather than a variable
                        // - Trim invisible characters off the end of the 500-limited string
                        // - Slimmed code for saving LOCs

                        int keep = 500 - (dest.length() - (dend - dstart));
                        if(keep <= 0) return "";
                        if(keep >= end - start) return null; // keep original

                        keep += start;

                        while(Character.isWhitespace(source.charAt(keep - 1))) {
                            --keep;
                            if(keep == start) return "";
                        }

                        if(Character.isHighSurrogate(source.charAt(keep - 1))) {
                            --keep;
                            if(keep == start) return "";
                        }

                        return source.subSequence(start, keep) + "…";
                    }});
                } else {
                    contentCollapseButton.setChecked(false);
                    statusContent.setFilters(new InputFilter[]{});
                }
            } else if(contentCollapseButton != null) {
                contentCollapseButton.setVisibility(View.GONE);
                statusContent.setFilters(new InputFilter[]{});
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
            if (isChecked) {
                statusContent.setVisibility(View.VISIBLE);
            } else {
                statusContent.setVisibility(View.GONE);
            }
        }
    }
}
