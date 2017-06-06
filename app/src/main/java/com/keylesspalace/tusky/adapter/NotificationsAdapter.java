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
import android.graphics.Typeface;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Notification;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.AdapterItemRemover;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NotificationsAdapter extends RecyclerView.Adapter implements AdapterItemRemover {
    private static final int VIEW_TYPE_MENTION = 0;
    private static final int VIEW_TYPE_FOOTER = 1;
    private static final int VIEW_TYPE_STATUS_NOTIFICATION = 2;
    private static final int VIEW_TYPE_FOLLOW = 3;

    public enum FooterState {
        EMPTY,
        END,
        LOADING
    }

    private List<Notification> notifications;
    private StatusActionListener statusListener;
    private NotificationActionListener notificationActionListener;
    private FooterState footerState = FooterState.END;

    public NotificationsAdapter(StatusActionListener statusListener,
            NotificationActionListener notificationActionListener) {
        super();
        notifications = new ArrayList<>();
        this.statusListener = statusListener;
        this.notificationActionListener = notificationActionListener;
    }


    public void setFooterState(FooterState newFooterState) {
        FooterState oldValue = footerState;
        footerState = newFooterState;
        if (footerState != oldValue) {
            notifyItemChanged(notifications.size());
        }
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
                View view;
                switch (footerState) {
                    default:
                    case LOADING:
                        view = LayoutInflater.from(parent.getContext())
                                .inflate(R.layout.item_footer, parent, false);
                        break;
                    case END: {
                        view = LayoutInflater.from(parent.getContext())
                                .inflate(R.layout.item_footer_end, parent, false);
                        break;
                    }
                    case EMPTY: {
                        view = LayoutInflater.from(parent.getContext())
                                .inflate(R.layout.item_footer_empty, parent, false);
                        break;
                    }
                }
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
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        if (position < notifications.size()) {
            Notification notification = notifications.get(position);
            Notification.Type type = notification.type;
            switch (type) {
                case MENTION: {
                    StatusViewHolder holder = (StatusViewHolder) viewHolder;
                    Status status = notification.status;
                    holder.setupWithStatus(status, statusListener);
                    break;
                }
                case FAVOURITE:
                case REBLOG: {
                    StatusNotificationViewHolder holder = (StatusNotificationViewHolder) viewHolder;
                    holder.setMessage(type, notification.account.getDisplayName(),
                            notification.status);
                    holder.setupButtons(notificationActionListener, notification.account.id);
                    break;
                }
                case FOLLOW: {
                    FollowViewHolder holder = (FollowViewHolder) viewHolder;
                    holder.setMessage(notification.account.getDisplayName(), notification.account.username,
                            notification.account.avatar);
                    holder.setupButtons(notificationActionListener, notification.account.id);
                    break;
                }
            }
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
            Notification notification = notifications.get(position);
            switch (notification.type) {
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
        }
    }

    public @Nullable Notification getItem(int position) {
        if (position >= 0 && position < notifications.size()) {
            return notifications.get(position);
        }
        return null;
    }

    public void update(List<Notification> newNotifications) {
        if (newNotifications == null || newNotifications.isEmpty()) {
            return;
        }
        if (notifications.isEmpty()) {
            notifications = newNotifications;
        } else {
            int index = notifications.indexOf(newNotifications.get(newNotifications.size() - 1));
            for (int i = 0; i < index; i++) {
                notifications.remove(0);
            }
            int newIndex = newNotifications.indexOf(notifications.get(0));
            if (newIndex == -1) {
                notifications.addAll(0, newNotifications);
            } else {
                notifications.addAll(0, newNotifications.subList(0, newIndex));
            }
        }
        notifyDataSetChanged();
    }

    public void addItems(List<Notification> new_notifications) {
        int end = notifications.size();
        notifications.addAll(new_notifications);
        notifyItemRangeInserted(end, new_notifications.size());
    }

    @Override
    public void removeItem(int position) {
        notifications.remove(position);
        notifyItemChanged(position);
    }

    @Override
    public void removeAllByAccountId(String id) {
        for (int i = 0; i < notifications.size();) {
            Notification notification = notifications.get(i);
            if (id.equals(notification.account.id)) {
                notifications.remove(i);
                notifyItemRemoved(i);
            } else {
                i += 1;
            }
        }
    }

    public interface NotificationActionListener {
        void onViewAccount(String id);
    }

    private static class FollowViewHolder extends RecyclerView.ViewHolder {
        private TextView message;
        private TextView usernameView;
        private TextView displayNameView;
        private ImageView avatar;

        FollowViewHolder(View itemView) {
            super(itemView);
            message = (TextView) itemView.findViewById(R.id.notification_text);
            usernameView = (TextView) itemView.findViewById(R.id.notification_username);
            displayNameView = (TextView) itemView.findViewById(R.id.notification_display_name);
            avatar = (ImageView) itemView.findViewById(R.id.notification_avatar);
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
                    .placeholder(R.drawable.avatar_default)
                    .error(R.drawable.avatar_error)
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

    private static class StatusNotificationViewHolder extends RecyclerView.ViewHolder {
        private TextView message;
        private ImageView icon;
        private TextView statusContent;
        private ViewGroup container;

        StatusNotificationViewHolder(View itemView) {
            super(itemView);
            message = (TextView) itemView.findViewById(R.id.notification_text);
            icon = (ImageView) itemView.findViewById(R.id.notification_icon);
            statusContent = (TextView) itemView.findViewById(R.id.notification_content);
            container = (ViewGroup) itemView.findViewById(R.id.notification_container);
        }

        void setMessage(Notification.Type type, String displayName, Status status) {
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
            statusContent.setText(status.content);
        }

        void setupButtons(final NotificationActionListener listener, final String accountId) {
            container.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onViewAccount(accountId);
                }
            });
        }
    }
}
