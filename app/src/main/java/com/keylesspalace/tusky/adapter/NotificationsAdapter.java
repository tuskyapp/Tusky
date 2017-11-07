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
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Notification;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.view.RoundedTransformation;
import com.keylesspalace.tusky.viewdata.NotificationViewData;
import com.keylesspalace.tusky.viewdata.StatusViewData;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
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
    private FooterViewHolder.State footerState;
    private boolean mediaPreviewEnabled;

    public NotificationsAdapter(StatusActionListener statusListener,
                                NotificationActionListener notificationActionListener) {
        super();
        notifications = new ArrayList<>();
        this.statusListener = statusListener;
        this.notificationActionListener = notificationActionListener;
        footerState = FooterViewHolder.State.END;
        mediaPreviewEnabled = true;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
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
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        if (position < notifications.size()) {
            NotificationViewData notification = notifications.get(position);
            if (notification instanceof NotificationViewData.Placeholder) {
                NotificationViewData.Placeholder placeholder = ((NotificationViewData.Placeholder) notification);
                PlaceholderViewHolder holder = (PlaceholderViewHolder) viewHolder;
                holder.setup(!placeholder.isLoading(), statusListener);
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
                    holder.setMessage(type, concreteNotificaton.getAccount().getDisplayName(),
                            concreteNotificaton.getStatusViewData());
                    holder.setupButtons(notificationActionListener,
                            concreteNotificaton.getAccount().id,
                            concreteNotificaton.getId());
                    holder.setAvatars(concreteNotificaton.getStatusViewData().getAvatar(),
                            concreteNotificaton.getId());
                    break;
                }
                case FOLLOW: {
                    FollowViewHolder holder = (FollowViewHolder) viewHolder;
                    holder.setMessage(concreteNotificaton.getAccount().getDisplayName(),
                            concreteNotificaton.getAccount().username, concreteNotificaton.getAccount().avatar);
                    holder.setupButtons(notificationActionListener, concreteNotificaton.getAccount().id);
                    break;
                }
            }
        } else {
            FooterViewHolder holder = (FooterViewHolder) viewHolder;
            holder.setState(footerState);
        }
    }

    @Override
    public int getItemCount() {
        return notifications.size() + 1;
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
        if (notifyAdapter) notifyDataSetChanged();
    }

    public void addItems(List<NotificationViewData> newNotifications) {
        notifications.addAll(newNotifications);
        notifyItemRangeInserted(notifications.size(), newNotifications.size());
    }

    public void clear() {
        notifications.clear();
        notifyDataSetChanged();
    }

    public void setFooterState(FooterViewHolder.State newFooterState) {
        footerState = newFooterState;
    }

    public void setMediaPreviewEnabled(boolean enabled) {
        mediaPreviewEnabled = enabled;
    }

    public interface NotificationActionListener {
        void onViewAccount(String id);

        void onViewStatusForNotificationId(String notificationId);
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
        }

        void setMessage(String displayName, String username, String avatarUrl) {
            Context context = message.getContext();

            String format = context.getString(R.string.notification_follow_format);
            String wholeMessage = String.format(format, displayName);
            message.setText(wholeMessage);

            format = context.getString(R.string.status_username_format);
            String wholeUsername = String.format(format, username);
            usernameView.setText(wholeUsername);

            displayNameView.setText(displayName);

            Picasso.with(context)
                    .load(avatarUrl)
                    .fit()
                    .transform(new RoundedTransformation(7, 0))
                    .placeholder(R.drawable.avatar_default)
                    .into(avatar);
        }

        void setupButtons(final NotificationActionListener listener, final String accountId) {
            avatar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onViewAccount(accountId);
                }
            });
        }
    }

    private static class StatusNotificationViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener, ToggleButton.OnCheckedChangeListener {
        private final TextView message;
        private final ImageView icon;
        private final TextView statusContent;
        private final ViewGroup container;
        private final ImageView statusAvatar;
        private final ImageView notificationAvatar;
        private final ViewGroup topBar;
        private final View contentWarningBar;
        private final TextView contentWarningDescriptionTextView;
        private final ToggleButton contentWarningButton;

        private String accountId;
        private String notificationId;
        private NotificationActionListener listener;
        private StatusViewData.Concrete statusViewData;

        StatusNotificationViewHolder(View itemView) {
            super(itemView);
            message = itemView.findViewById(R.id.notification_text);
            icon = itemView.findViewById(R.id.notification_icon);
            statusContent = itemView.findViewById(R.id.notification_content);
            container = itemView.findViewById(R.id.notification_container);
            statusAvatar = itemView.findViewById(R.id.notification_status_avatar);
            notificationAvatar = itemView.findViewById(R.id.notification_notification_avatar);
            topBar = itemView.findViewById(R.id.notification_top_bar);
            contentWarningBar = itemView.findViewById(R.id.notification_content_warning_bar);
            contentWarningDescriptionTextView = itemView.findViewById(R.id.notification_content_warning_description);
            contentWarningButton = itemView.findViewById(R.id.notification_content_warning_button);

            int darkerFilter = Color.rgb(123, 123, 123);
            statusAvatar.setColorFilter(darkerFilter, PorterDuff.Mode.MULTIPLY);
            notificationAvatar.setColorFilter(darkerFilter, PorterDuff.Mode.MULTIPLY);

            container.setOnClickListener(this);
            topBar.setOnClickListener(this);
            contentWarningButton.setOnCheckedChangeListener(this);
        }

        void setMessage(Notification.Type type, String displayName,
                        StatusViewData.Concrete status) {
            this.statusViewData = status;

            Context context = message.getContext();
            String format;
            switch (type) {
                default:
                case FAVOURITE: {
                    icon.setImageResource(R.drawable.ic_star_24dp);
                    icon.setColorFilter(ContextCompat.getColor(context,
                            R.color.status_favourite_button_marked_dark));
                    format = context.getString(R.string.notification_favourite_format);
                    break;
                }
                case REBLOG: {
                    icon.setImageResource(R.drawable.ic_repeat_24dp);
                    icon.setColorFilter(ContextCompat.getColor(context,
                            R.color.color_accent_dark));
                    format = context.getString(R.string.notification_reblog_format);
                    break;
                }
            }
            String wholeMessage = String.format(format, displayName);
            final SpannableStringBuilder str = new SpannableStringBuilder(wholeMessage);
            str.setSpan(new StyleSpan(Typeface.BOLD), 0, displayName.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            message.setText(str);

            boolean hasSpoiler = !TextUtils.isEmpty(statusViewData.getSpoilerText());
            contentWarningBar.setVisibility(hasSpoiler ? View.VISIBLE : View.GONE);
            setupContentAndSpoiler(false);
        }

        void setupButtons(final NotificationActionListener listener, final String accountId,
                          final String notificationId) {
            this.listener = listener;
            this.accountId = accountId;
            this.notificationId = notificationId;
        }

        void setAvatars(@Nullable String statusAvatarUrl, @Nullable String notificationAvatarUrl) {
            Context context = statusAvatar.getContext();

            if (statusAvatarUrl == null || statusAvatarUrl.isEmpty()) {
                statusAvatar.setImageResource(R.drawable.avatar_default);
            } else {
                Picasso.with(context)
                        .load(statusAvatarUrl)
                        .placeholder(R.drawable.avatar_default)
                        .transform(new RoundedTransformation(7, 0))
                        .into(statusAvatar);
            }

            if (notificationAvatarUrl == null || notificationAvatarUrl.isEmpty()) {
                notificationAvatar.setVisibility(View.GONE);
            } else {
                notificationAvatar.setVisibility(View.VISIBLE);
                Picasso.with(context)
                        .load(notificationAvatarUrl)
                        .fit()
                        .transform(new RoundedTransformation(7, 0))
                        .into(notificationAvatar);
            }
        }

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.notification_container:
                    if (listener != null) listener.onViewStatusForNotificationId(notificationId);
                    break;
                case R.id.notification_top_bar:
                    if (listener != null) listener.onViewAccount(accountId);
                    break;
            }
        }

        private void setupContentAndSpoiler(boolean shouldShowContentIfSpoiler) {
            boolean hasSpoiler = !TextUtils.isEmpty(statusViewData.getSpoilerText());
            CharSequence content;
            if (!shouldShowContentIfSpoiler && hasSpoiler) {
                if (statusViewData.getMentions() != null &&
                        statusViewData.getMentions().length > 0) {
                    // If there is a content warning and mentions we're alternating between
                    // showing mentions and showing full content. As mentions are plain text we
                    // have to construct URLSpans ourselves.
                    SpannableStringBuilder contentBuilder = new SpannableStringBuilder();
                    for (Status.Mention mention : statusViewData.getMentions()) {
                        int start = contentBuilder.length() > 0 ? contentBuilder.length() - 1 : 0;
                        contentBuilder.append('@');
                        contentBuilder.append(mention.username);
                        contentBuilder.append(' ');
                        contentBuilder.setSpan(new URLSpan(mention.url), start,
                                mention.username.length() + 1, 0);
                    }
                    content = contentBuilder;
                } else {
                    content = null;
                }
            } else {
                content = statusViewData.getContent();
            }
            statusContent.setText(content);
            contentWarningDescriptionTextView.setText(statusViewData.getSpoilerText());
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            setupContentAndSpoiler(isChecked);
        }
    }
}
