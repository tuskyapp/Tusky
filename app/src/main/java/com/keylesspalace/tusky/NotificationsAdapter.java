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
import android.graphics.Typeface;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.keylesspalace.tusky.entity.Notification;
import com.keylesspalace.tusky.entity.Status;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

class NotificationsAdapter extends RecyclerView.Adapter implements AdapterItemRemover {
    private static final int VIEW_TYPE_MENTION = 0;
    private static final int VIEW_TYPE_FOOTER = 1;
    private static final int VIEW_TYPE_STATUS_NOTIFICATION = 2;
    private static final int VIEW_TYPE_FOLLOW = 3;

    private List<Notification> notifications;
    private StatusActionListener statusListener;
    private FollowListener followListener;
    private FooterViewHolder.State footerState;

    NotificationsAdapter(StatusActionListener statusListener, FollowListener followListener) {
        super();
        notifications = new ArrayList<>();
        this.statusListener = statusListener;
        this.followListener = followListener;
        footerState = FooterViewHolder.State.LOADING;
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
                    holder.setMessage(type, notification.account.displayName,
                            notification.status);
                    break;
                }
                case FOLLOW: {
                    FollowViewHolder holder = (FollowViewHolder) viewHolder;
                    holder.setMessage(notification.account.displayName, notification.account.username,
                            notification.account.avatar);
                    holder.setupButtons(followListener, notification.account.id);
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

    int update(List<Notification> new_notifications) {
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

    void addItems(List<Notification> new_notifications) {
        int end = notifications.size();
        notifications.addAll(new_notifications);
        notifyItemRangeInserted(end, new_notifications.size());
    }

    public void removeItem(int position) {
        notifications.remove(position);
        notifyItemChanged(position);
    }

    void setFooterState(FooterViewHolder.State state) {
        footerState = state;
    }

    interface FollowListener {
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

        void setupButtons(final FollowListener listener, final String accountId) {
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

        StatusNotificationViewHolder(View itemView) {
            super(itemView);
            message = (TextView) itemView.findViewById(R.id.notification_text);
            icon = (ImageView) itemView.findViewById(R.id.notification_icon);
            statusContent = (TextView) itemView.findViewById(R.id.notification_content);
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
            str.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, displayName.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            message.setText(str);
            statusContent.setText(status.content);
        }
    }
}
