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
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Emoji;
import com.keylesspalace.tusky.entity.Filter;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.entity.TimelineAccount;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.util.CustomEmojiHelper;
import com.keylesspalace.tusky.util.NumberUtils;
import com.keylesspalace.tusky.util.SmartLengthInputFilter;
import com.keylesspalace.tusky.util.StatusDisplayOptions;
import com.keylesspalace.tusky.util.StringUtils;
import com.keylesspalace.tusky.viewdata.StatusViewData;

import java.util.Collections;
import java.util.List;

import at.connyduck.sparkbutton.helpers.Utils;

public class StatusViewHolder extends StatusBaseViewHolder {
    private static final InputFilter[] COLLAPSE_INPUT_FILTER = new InputFilter[]{SmartLengthInputFilter.INSTANCE};
    private static final InputFilter[] NO_INPUT_FILTER = new InputFilter[0];

    private final TextView statusInfo;
    private final Button contentCollapseButton;
    private final TextView favouritedCountLabel;
    private final TextView reblogsCountLabel;

    public StatusViewHolder(@NonNull View itemView) {
        super(itemView);
        statusInfo = itemView.findViewById(R.id.status_info);
        contentCollapseButton = itemView.findViewById(R.id.button_toggle_content);
        favouritedCountLabel = itemView.findViewById(R.id.status_favourites_count);
        reblogsCountLabel = itemView.findViewById(R.id.status_insets);
    }

    @Override
    public void setupWithStatus(@NonNull StatusViewData.Concrete status,
                                @NonNull final StatusActionListener listener,
                                @NonNull StatusDisplayOptions statusDisplayOptions,
                                @Nullable Object payloads,
                                boolean showStatusInfo) {
        if (payloads == null) {
            boolean sensitive = !TextUtils.isEmpty(status.getActionable().getSpoilerText());
            boolean expanded = status.isExpanded();

            setupCollapsedState(sensitive, expanded, status, listener);

            Status reblogging = status.getRebloggingStatus();
            boolean isReply = status.getStatus().getInReplyToId() != null;
            boolean isReplyOnly = isReply && reblogging == null;

            boolean hasStatusContext = reblogging != null || isReply;

            if (!hasStatusContext || !showStatusInfo || status.getFilterAction() == Filter.Action.WARN) {
                hideStatusInfo();
            } else {
                String accountName = "";
                List<Emoji> emojis = Collections.emptyList();
                if (reblogging != null) {
                    accountName = reblogging.getAccount().getName();
                    emojis = reblogging.getAccount().getEmojis();
                } else if (isReply) {
                    TimelineAccount repliedTo = status.getInReplyToAccount();
                    if (repliedTo != null) {
                        accountName = repliedTo.getName();
                        emojis = repliedTo.getEmojis();
                    }
                }

                setStatusInfoText(isReplyOnly, accountName, emojis, statusDisplayOptions);
                statusInfo.setOnClickListener(v -> listener.onOpenReblog(getBindingAdapterPosition()));
            }

        }

        reblogsCountLabel.setVisibility(statusDisplayOptions.showStatsInline() ? View.VISIBLE : View.INVISIBLE);
        favouritedCountLabel.setVisibility(statusDisplayOptions.showStatsInline() ? View.VISIBLE : View.INVISIBLE);
        setFavouritedCount(status.getActionable().getFavouritesCount());
        setReblogsCount(status.getActionable().getReblogsCount());

        super.setupWithStatus(status, listener, statusDisplayOptions, payloads, showStatusInfo);
    }

    private void setStatusInfoText(final boolean isReply,
                                   final CharSequence name,
                                   final List<Emoji> accountEmoji,
                                   final StatusDisplayOptions statusDisplayOptions) {

        Context context = statusInfo.getContext();
        if (name.length() > 0) {
            CharSequence wrappedName = StringUtils.unicodeWrap(name);
            CharSequence statusContextText = context.getString(isReply ? R.string.post_replied_format : R.string.post_boosted_format, wrappedName);
            CharSequence emojifiedText = CustomEmojiHelper.emojify(
                statusContextText, accountEmoji, statusInfo, statusDisplayOptions.animateEmojis()
            );
            statusInfo.setText(emojifiedText);
        } else {
            statusInfo.setText(context.getString(R.string.post_replied));
        }
        statusInfo.setCompoundDrawablesWithIntrinsicBounds(isReply ? R.drawable.ic_reply_all_18dp : R.drawable.ic_reblog_18dp, 0, 0, 0);

        statusInfo.setVisibility(View.VISIBLE);
    }

    // don't use this on the same ViewHolder as setRebloggedByDisplayName, will cause recycling issues as paddings are changed
    protected void setPollInfo(final boolean ownPoll) {
        statusInfo.setText(ownPoll ? R.string.poll_ended_created : R.string.poll_ended_voted);
        statusInfo.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_poll_24dp, 0, 0, 0);
        statusInfo.setCompoundDrawablePadding(Utils.dpToPx(statusInfo.getContext(), 10));
        statusInfo.setPaddingRelative(Utils.dpToPx(statusInfo.getContext(), 28), 0, 0, 0);
        statusInfo.setVisibility(View.VISIBLE);
    }

    protected void setReblogsCount(int reblogsCount) {
        reblogsCountLabel.setText(NumberUtils.formatNumber(reblogsCount, 1000));
    }

    protected void setFavouritedCount(int favouritedCount) {
        favouritedCountLabel.setText(NumberUtils.formatNumber(favouritedCount, 1000));
    }

    protected void hideStatusInfo() {
        statusInfo.setVisibility(View.GONE);
    }

    private void setupCollapsedState(boolean sensitive,
                                     boolean expanded,
                                     final StatusViewData.Concrete status,
                                     final StatusActionListener listener) {
        /* input filter for TextViews have to be set before text */
        if (status.isCollapsible() && (!sensitive || expanded)) {
            contentCollapseButton.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION)
                    listener.onContentCollapsedChange(!status.isCollapsed(), position);
            });

            contentCollapseButton.setVisibility(View.VISIBLE);
            if (status.isCollapsed()) {
                contentCollapseButton.setText(R.string.post_content_warning_show_more);
                content.setFilters(COLLAPSE_INPUT_FILTER);
            } else {
                contentCollapseButton.setText(R.string.post_content_warning_show_less);
                content.setFilters(NO_INPUT_FILTER);
            }
        } else {
            contentCollapseButton.setVisibility(View.GONE);
            content.setFilters(NO_INPUT_FILTER);
        }
    }

    public void showStatusContent(boolean show) {
        super.showStatusContent(show);
        contentCollapseButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void toggleExpandedState(boolean sensitive,
                                       boolean expanded,
                                       @NonNull StatusViewData.Concrete status,
                                       @NonNull StatusDisplayOptions statusDisplayOptions,
                                       @NonNull final StatusActionListener listener) {

        setupCollapsedState(sensitive, expanded, status, listener);

        super.toggleExpandedState(sensitive, expanded, status, statusDisplayOptions, listener);
    }
}
