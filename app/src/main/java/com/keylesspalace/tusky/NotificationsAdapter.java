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
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NotificationsAdapter extends RecyclerView.Adapter implements AdapterItemRemover {
    private static final int VIEW_TYPE_MENTION = 0;
    private static final int VIEW_TYPE_FOOTER = 1;
    private static final int VIEW_TYPE_STATUS_NOTIFICATION = 2;
    private static final int VIEW_TYPE_FOLLOW = 3;

    private List<Notification> notifications;
    private StatusActionListener statusListener;
    private FooterActionListener footerListener;

    public NotificationsAdapter(StatusActionListener statusListener,
        FooterActionListener footerListener) {
        super();
        notifications = new ArrayList<>();
        this.statusListener = statusListener;
        this.footerListener = footerListener;
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
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        if (position < notifications.size()) {
            Notification notification = notifications.get(position);
            Notification.Type type = notification.getType();
            switch (type) {
                case MENTION: {
                    StatusViewHolder holder = (StatusViewHolder) viewHolder;
                    Status status = notification.getStatus();
                    assert(status != null);
                    holder.setupWithStatus(status, statusListener, position);
                    break;
                }
                case FAVOURITE:
                case REBLOG: {
                    StatusNotificationViewHolder holder = (StatusNotificationViewHolder) viewHolder;
                    holder.setMessage(type, notification.getDisplayName(),
                            notification.getStatus());
                    break;
                }
                case FOLLOW: {
                    FollowViewHolder holder = (FollowViewHolder) viewHolder;
                    holder.setMessage(notification.getDisplayName());
                    break;
                }
            }
        } else {
            FooterViewHolder holder = (FooterViewHolder) viewHolder;
            holder.setupButton(footerListener);
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
            switch (notification.getType()) {
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

    public int update(List<Notification> new_notifications) {
        int scrollToPosition;
        if (notifications == null || notifications.isEmpty()) {
            notifications = new_notifications;
            scrollToPosition = 0;
        } else {
            int index = new_notifications.indexOf(notifications.get(0));
            if (index == -1) {
                notifications.addAll(0, new_notifications);
                scrollToPosition = 0;
            } else {
                notifications.addAll(0, new_notifications.subList(0, index));
                scrollToPosition = index;
            }
        }
        notifyDataSetChanged();
        return scrollToPosition;
    }

    public void addItems(List<Notification> new_notifications) {
        int end = notifications.size();
        notifications.addAll(new_notifications);
        notifyItemRangeInserted(end, new_notifications.size());
    }

    public void removeItem(int position) {
        notifications.remove(position);
        notifyItemChanged(position);
    }

    public static class FollowViewHolder extends RecyclerView.ViewHolder {
        private TextView message;

        public FollowViewHolder(View itemView) {
            super(itemView);
            message = (TextView) itemView.findViewById(R.id.notification_text);
        }

        public void setMessage(String displayName) {
            Context context = message.getContext();
            String format = context.getString(R.string.notification_follow_format);
            String wholeMessage = String.format(format, displayName);
            message.setText(wholeMessage);
        }
    }

    public static class StatusNotificationViewHolder extends RecyclerView.ViewHolder {
        private TextView message;
        private ImageView icon;
        private TextView statusContent;

        public StatusNotificationViewHolder(View itemView) {
            super(itemView);
            message = (TextView) itemView.findViewById(R.id.notification_text);
            icon = (ImageView) itemView.findViewById(R.id.notification_icon);
            statusContent = (TextView) itemView.findViewById(R.id.notification_content);
        }

        public void setMessage(Notification.Type type, String displayName, Status status) {
            Context context = message.getContext();
            String format;
            switch (type) {
                default:
                case FAVOURITE: {
                    icon.setImageResource(R.drawable.ic_favourited);
                    format = context.getString(R.string.notification_favourite_format);
                    break;
                }
                case REBLOG: {
                    icon.setImageResource(R.drawable.ic_reblogged);
                    format = context.getString(R.string.notification_reblog_format);
                    break;
                }
            }
            String wholeMessage = String.format(format, displayName);
            message.setText(wholeMessage);
            String timestamp = DateUtils.getRelativeTimeSpanString(
                    status.getCreatedAt().getTime(),
                    new Date().getTime());
            statusContent.setText(String.format("%s: ", timestamp));
            statusContent.append(status.getContent());
        }
    }
}
