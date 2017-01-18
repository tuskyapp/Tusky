package com.keylesspalace.tusky;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.PagerSnapHelper;
import android.support.v7.widget.RecyclerView;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TimelineAdapter extends RecyclerView.Adapter {
    private static final int VIEW_TYPE_STATUS = 0;
    private static final int VIEW_TYPE_FOOTER = 1;

    private List<Status> statuses;
    private StatusActionListener statusListener;
    private FooterActionListener footerListener;

    public TimelineAdapter(StatusActionListener statusListener,
            FooterActionListener footerListener) {
        super();
        statuses = new ArrayList<>();
        this.statusListener = statusListener;
        this.footerListener = footerListener;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        switch (viewType) {
            default:
            case VIEW_TYPE_STATUS: {
                View view = LayoutInflater.from(viewGroup.getContext())
                        .inflate(R.layout.item_status, viewGroup, false);
                return new StatusViewHolder(view);
            }
            case VIEW_TYPE_FOOTER: {
                View view = LayoutInflater.from(viewGroup.getContext())
                        .inflate(R.layout.item_footer, viewGroup, false);
                return new FooterViewHolder(view);
            }
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        if (position < statuses.size()) {
            StatusViewHolder holder = (StatusViewHolder) viewHolder;
            Status status = statuses.get(position);
            holder.setDisplayName(status.getDisplayName());
            holder.setUsername(status.getUsername());
            holder.setCreatedAt(status.getCreatedAt());
            holder.setContent(status.getContent());
            holder.setAvatar(status.getAvatar());
            holder.setContent(status.getContent());
            holder.setReblogged(status.getReblogged());
            holder.setFavourited(status.getFavourited());
            String rebloggedByUsername = status.getRebloggedByUsername();
            if (rebloggedByUsername == null) {
                holder.hideRebloggedByUsername();
            } else {
                holder.setRebloggedByUsername(rebloggedByUsername);
            }
            Status.MediaAttachment[] attachments = status.getAttachments();
            boolean sensitive = status.getSensitive();
            holder.setMediaPreviews(attachments, sensitive, statusListener);
            /* A status without attachments is sometimes still marked sensitive, so it's necessary
             * to check both whether there are any attachments and if it's marked sensitive. */
            if (!sensitive || attachments.length == 0) {
                holder.hideSensitiveMediaWarning();
            }
            holder.setupButtons(statusListener, position);
            if (status.getVisibility() == Status.Visibility.PRIVATE) {
                holder.disableReblogging();
            }
        } else {
            FooterViewHolder holder = (FooterViewHolder) viewHolder;
            holder.setupButton(footerListener);
        }
    }

    @Override
    public int getItemCount() {
        return statuses.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == statuses.size()) {
            return VIEW_TYPE_FOOTER;
        } else {
            return VIEW_TYPE_STATUS;
        }
    }

    public int update(List<Status> newStatuses) {
        int scrollToPosition;
        if (statuses == null || statuses.isEmpty()) {
            statuses = newStatuses;
            scrollToPosition = 0;
        } else {
            int index = newStatuses.indexOf(statuses.get(0));
            if (index == -1) {
                statuses.addAll(0, newStatuses);
                scrollToPosition = 0;
            } else {
                statuses.addAll(0, newStatuses.subList(0, index));
                scrollToPosition = index;
            }
        }
        notifyDataSetChanged();
        return scrollToPosition;
    }

    public void addItems(List<Status> newStatuses) {
        int end = statuses.size();
        statuses.addAll(newStatuses);
        notifyItemRangeInserted(end, newStatuses.size());
    }

    public void removeItem(int position) {
        statuses.remove(position);
        notifyItemRemoved(position);
    }

    public @Nullable Status getItem(int position) {
        if (position >= 0 && position < statuses.size()) {
            return statuses.get(position);
        }
        return null;
    }

    public static class StatusViewHolder extends RecyclerView.ViewHolder {
        private TextView displayName;
        private TextView username;
        private TextView sinceCreated;
        private TextView content;
        private NetworkImageView avatar;
        private ImageView boostedIcon;
        private TextView boostedByUsername;
        private ImageButton replyButton;
        private ImageButton reblogButton;
        private ImageButton favouriteButton;
        private ImageButton moreButton;
        private boolean favourited;
        private boolean reblogged;
        private NetworkImageView mediaPreview0;
        private NetworkImageView mediaPreview1;
        private NetworkImageView mediaPreview2;
        private NetworkImageView mediaPreview3;
        private View sensitiveMediaWarning;

        public StatusViewHolder(View itemView) {
            super(itemView);
            displayName = (TextView) itemView.findViewById(R.id.status_display_name);
            username = (TextView) itemView.findViewById(R.id.status_username);
            sinceCreated = (TextView) itemView.findViewById(R.id.status_since_created);
            content = (TextView) itemView.findViewById(R.id.status_content);
            avatar = (NetworkImageView) itemView.findViewById(R.id.status_avatar);
            boostedIcon = (ImageView) itemView.findViewById(R.id.status_boosted_icon);
            boostedByUsername = (TextView) itemView.findViewById(R.id.status_boosted);
            replyButton = (ImageButton) itemView.findViewById(R.id.status_reply);
            reblogButton = (ImageButton) itemView.findViewById(R.id.status_reblog);
            favouriteButton = (ImageButton) itemView.findViewById(R.id.status_favourite);
            moreButton = (ImageButton) itemView.findViewById(R.id.status_more);
            reblogged = false;
            favourited = false;
            mediaPreview0 = (NetworkImageView) itemView.findViewById(R.id.status_media_preview_0);
            mediaPreview1 = (NetworkImageView) itemView.findViewById(R.id.status_media_preview_1);
            mediaPreview2 = (NetworkImageView) itemView.findViewById(R.id.status_media_preview_2);
            mediaPreview3 = (NetworkImageView) itemView.findViewById(R.id.status_media_preview_3);
            mediaPreview0.setDefaultImageResId(R.drawable.media_preview_unloaded);
            mediaPreview1.setDefaultImageResId(R.drawable.media_preview_unloaded);
            mediaPreview2.setDefaultImageResId(R.drawable.media_preview_unloaded);
            mediaPreview3.setDefaultImageResId(R.drawable.media_preview_unloaded);
            sensitiveMediaWarning = itemView.findViewById(R.id.status_sensitive_media_warning);
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

        public void setCreatedAt(@Nullable Date createdAt) {
            String readout;
            if (createdAt != null) {
                long then = createdAt.getTime();
                long now = new Date().getTime();
                readout = getRelativeTimeSpanString(then, now);
            } else {
                readout = "?m"; // unknown minutes~
            }
            sinceCreated.setText(readout);
        }

        public void setRebloggedByUsername(String name) {
            Context context = boostedByUsername.getContext();
            String format = context.getString(R.string.status_boosted_format);
            String boostedText = String.format(format, name);
            boostedByUsername.setText(boostedText);
            boostedIcon.setVisibility(View.VISIBLE);
            boostedByUsername.setVisibility(View.VISIBLE);
        }

        public void hideRebloggedByUsername() {
            boostedIcon.setVisibility(View.GONE);
            boostedByUsername.setVisibility(View.GONE);
        }

        public void setReblogged(boolean reblogged) {
            this.reblogged = reblogged;
            if (!reblogged) {
                reblogButton.setImageResource(R.drawable.ic_reblog_off);
            } else {
                reblogButton.setImageResource(R.drawable.ic_reblog_on);
            }
        }

        public void disableReblogging() {
            reblogButton.setEnabled(false);
            reblogButton.setImageResource(R.drawable.ic_reblog_disabled);
        }

        public void setFavourited(boolean favourited) {
            this.favourited = favourited;
            if (!favourited) {
                favouriteButton.setImageResource(R.drawable.ic_favourite_off);
            } else {
                favouriteButton.setImageResource(R.drawable.ic_favourite_on);
            }
        }

        public void setMediaPreviews(final Status.MediaAttachment[] attachments,
                 boolean sensitive, final StatusActionListener listener) {
            final NetworkImageView[] previews = {
                mediaPreview0,
                mediaPreview1,
                mediaPreview2,
                mediaPreview3
            };
            Context context = mediaPreview0.getContext();
            ImageLoader imageLoader = VolleySingleton.getInstance(context).getImageLoader();
            final int n = Math.min(attachments.length, Status.MAX_MEDIA_ATTACHMENTS);
            for (int i = 0; i < n; i++) {
                String previewUrl = attachments[i].getPreviewUrl();
                previews[i].setImageUrl(previewUrl, imageLoader);
                if (!sensitive) {
                    previews[i].setVisibility(View.VISIBLE);
                } else {
                    previews[i].setVisibility(View.GONE);
                }
                final String url = attachments[i].getUrl();
                final Status.MediaAttachment.Type type = attachments[i].getType();
                previews[i].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onViewMedia(url, type);
                    }
                });
            }
            if (sensitive) {
                sensitiveMediaWarning.setVisibility(View.VISIBLE);
                sensitiveMediaWarning.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        v.setVisibility(View.GONE);
                        for (int i = 0; i < n; i++) {
                            previews[i].setVisibility(View.VISIBLE);
                        }
                        v.setOnClickListener(null);
                    }
                });
            }
            // Hide any of the placeholder previews beyond the ones set.
            for (int i = n; i < Status.MAX_MEDIA_ATTACHMENTS; i++) {
                previews[i].setImageUrl(null, imageLoader);
                previews[i].setVisibility(View.GONE);
            }
        }

        public void hideSensitiveMediaWarning() {
            sensitiveMediaWarning.setVisibility(View.GONE);
        }

        public void setupButtons(final StatusActionListener listener, final int position) {
            reblogButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onReblog(!reblogged, position);
                }
            });
            favouriteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onFavourite(!favourited, position);
                }
            });
            moreButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onMore(v, position);
                }
            });
        }
    }

    public static class FooterViewHolder extends RecyclerView.ViewHolder {
        private LinearLayout retryBar;
        private Button retry;
        private ProgressBar progressBar;

        public FooterViewHolder(View itemView) {
            super(itemView);
            retryBar = (LinearLayout) itemView.findViewById(R.id.footer_retry_bar);
            retry = (Button) itemView.findViewById(R.id.footer_retry_button);
            progressBar = (ProgressBar) itemView.findViewById(R.id.footer_progress_bar);
            progressBar.setIndeterminate(true);
        }

        public void setupButton(final FooterActionListener listener) {
            retry.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onLoadMore();
                }
            });
        }

        public void showRetry(boolean show) {
            if (!show) {
                retryBar.setVisibility(View.GONE);
                progressBar.setVisibility(View.VISIBLE);
            } else {
                retryBar.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
            }
        }
    }
}
