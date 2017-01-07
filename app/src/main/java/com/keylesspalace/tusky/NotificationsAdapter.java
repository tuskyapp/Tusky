package com.keylesspalace.tusky;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class NotificationsAdapter extends RecyclerView.Adapter {
    private List<Notification> notifications = new ArrayList<>();

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        ViewHolder holder = (ViewHolder) viewHolder;
        Notification notification = notifications.get(position);
        holder.setMessage(notification.getType(), notification.getDisplayName());
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    public Notification getItem(int position) {
        return notifications.get(position);
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

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private TextView message;

        public ViewHolder(View itemView) {
            super(itemView);
            message = (TextView) itemView.findViewById(R.id.notification_text);
        }

        public void setMessage(Notification.Type type, String displayName) {
            Context context = message.getContext();
            String wholeMessage = "";
            switch (type) {
                case MENTION: {
                    wholeMessage = displayName + " mentioned you";
                    break;
                }
                case REBLOG: {
                    String format = context.getString(R.string.notification_reblog_format);
                    wholeMessage = String.format(format, displayName);
                    break;
                }
                case FAVOURITE: {
                    String format = context.getString(R.string.notification_favourite_format);
                    wholeMessage = String.format(format, displayName);
                    break;
                }
                case FOLLOW: {
                    String format = context.getString(R.string.notification_follow_format);
                    wholeMessage = String.format(format, displayName);
                    break;
                }
            }
            message.setText(wholeMessage);
        }
    }
}
