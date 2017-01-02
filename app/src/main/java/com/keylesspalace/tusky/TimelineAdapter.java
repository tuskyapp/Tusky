package com.keylesspalace.tusky;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v7.widget.RecyclerView;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TimelineAdapter extends RecyclerView.Adapter {
    private List<Status> statuses = new ArrayList<>();

    /*
    TootActionListener listener;

    public TimelineAdapter(TootActionListener listener) {
        super();
        this.listener = listener;
    }
    */

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View v = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.item_status, viewGroup, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        ViewHolder holder = (ViewHolder) viewHolder;
        Status status = statuses.get(position);
        holder.setDisplayName(status.getDisplayName());
        holder.setUsername(status.getUsername());
        holder.setCreatedAt(status.getCreatedAt());
        holder.setContent(status.getContent());
        holder.setAvatar(status.getAvatar());
        holder.setContent(status.getContent());
        String rebloggedByUsername = status.getRebloggedByUsername();
        if (rebloggedByUsername == null) {
            holder.hideReblogged();
        } else {
            holder.setRebloggedByUsername(rebloggedByUsername);
        }
        // holder.initButtons(mListener, position);
    }

    @Override
    public int getItemCount() {
        return statuses.size();
    }

    public int update(List<Status> new_statuses) {
        int scrollToPosition;
        if (statuses == null || statuses.isEmpty()) {
            statuses = new_statuses;
            scrollToPosition = 0;
        } else {
            int index = new_statuses.indexOf(statuses.get(0));
            if (index == -1) {
                statuses.addAll(0, new_statuses);
                scrollToPosition = 0;
            } else {
                statuses.addAll(0, new_statuses.subList(0, index));
                scrollToPosition = index;
            }
        }
        notifyDataSetChanged();
        return scrollToPosition;
    }

    public void addItems(List<Status> new_statuses) {
        int end = statuses.size();
        statuses.addAll(new_statuses);
        notifyItemRangeInserted(end, new_statuses.size());
    }

    public Status getItem(int position) {
        return statuses.get(position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private TextView displayName;
        private TextView username;
        private TextView sinceCreated;
        private TextView content;
        private NetworkImageView avatar;
        private ImageView boostedIcon;
        private TextView boostedByUsername;

        public ViewHolder(View itemView) {
            super(itemView);
            displayName = (TextView) itemView.findViewById(R.id.status_display_name);
            username = (TextView) itemView.findViewById(R.id.status_username);
            sinceCreated = (TextView) itemView.findViewById(R.id.status_since_created);
            content = (TextView) itemView.findViewById(R.id.status_content);
            avatar = (NetworkImageView) itemView.findViewById(R.id.status_avatar);
            boostedIcon = (ImageView) itemView.findViewById(R.id.status_boosted_icon);
            boostedByUsername = (TextView) itemView.findViewById(R.id.status_boosted);
            /*
            mReplyButton = (ImageButton) itemView.findViewById(R.id.reply);
            mRetweetButton = (ImageButton) itemView.findViewById(R.id.retweet);
            mFavoriteButton = (ImageButton) itemView.findViewById(R.id.favorite);
            */
        }

        public void setDisplayName(String name) {
            displayName.setText(name);
        }

        public void setUsername(String name) {
            Context context = username.getContext();
            String format = context.getString(R.string.status_username_format);
            String usernameText = String.format(format, name);
            username.setText(usernameText);
        }

        public void setContent(Spanned content) {
            this.content.setText(content);
        }

        public void setAvatar(String url) {
            Context context = avatar.getContext();
            ImageLoader imageLoader = VolleySingleton.getInstance(context).getImageLoader();
            avatar.setImageUrl(url, imageLoader);
            avatar.setDefaultImageResId(R.drawable.avatar_default);
            avatar.setErrorImageResId(R.drawable.avatar_error);
        }

        /* This is a rough duplicate of android.text.format.DateUtils.getRelativeTimeSpanString,
         * but even with the FORMAT_ABBREV_RELATIVE flag it wasn't abbreviating enough. */
        private String getRelativeTimeSpanString(long then, long now) {
            final long MINUTE = 60;
            final long HOUR = 60 * MINUTE;
            final long DAY = 24 * HOUR;
            final long YEAR = 365 * DAY;
            long span = (now - then) / 1000;
            String prefix = "";
            if (span < 0) {
                prefix = "in ";
                span = -span;
            }
            String unit;
            if (span < MINUTE) {
                unit = "s";
            } else if (span < HOUR) {
                span /= MINUTE;
                unit = "m";
            } else if (span < DAY) {
                span /= HOUR;
                unit = "h";
            } else if (span < YEAR) {
                span /= DAY;
                unit = "d";
            } else {
                span /= YEAR;
                unit = "y";
            }
            return prefix + span + unit;
        }

        public void setCreatedAt(Date createdAt) {
            long then = createdAt.getTime();
            long now = new Date().getTime();
            String since = getRelativeTimeSpanString(then, now);
            sinceCreated.setText(since);
        }

        public void setRebloggedByUsername(String name) {
            Context context = boostedByUsername.getContext();
            String format = context.getString(R.string.status_boosted_format);
            String boostedText = String.format(format, name);
            boostedByUsername.setText(boostedText);
            boostedIcon.setVisibility(View.VISIBLE);
            boostedByUsername.setVisibility(View.VISIBLE);
        }

        public void hideReblogged() {
            boostedIcon.setVisibility(View.GONE);
            boostedByUsername.setVisibility(View.GONE);
        }
    }
}
