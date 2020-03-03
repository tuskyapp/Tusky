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

package com.keylesspalace.tusky.components.conversation;

import android.content.Context;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder;
import com.keylesspalace.tusky.entity.Attachment;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.util.ImageLoadingHelper;
import com.keylesspalace.tusky.util.SmartLengthInputFilter;
import com.keylesspalace.tusky.util.StatusDisplayOptions;
import com.keylesspalace.tusky.viewdata.PollViewDataKt;

import java.util.List;

public class ConversationViewHolder extends StatusBaseViewHolder {
    private static final InputFilter[] COLLAPSE_INPUT_FILTER = new InputFilter[]{SmartLengthInputFilter.INSTANCE};
    private static final InputFilter[] NO_INPUT_FILTER = new InputFilter[0];

    private TextView conversationNameTextView;
    private Button contentCollapseButton;
    private ImageView[] avatars;

    private StatusDisplayOptions statusDisplayOptions;
    private StatusActionListener listener;

    ConversationViewHolder(View itemView,
                           StatusDisplayOptions statusDisplayOptions,
                           StatusActionListener listener) {
        super(itemView);
        conversationNameTextView = itemView.findViewById(R.id.conversation_name);
        contentCollapseButton = itemView.findViewById(R.id.button_toggle_content);
        avatars = new ImageView[]{
                avatar,
                itemView.findViewById(R.id.status_avatar_1),
                itemView.findViewById(R.id.status_avatar_2)
        };
        this.statusDisplayOptions = statusDisplayOptions;

        this.listener = listener;

    }

    @Override
    protected int getMediaPreviewHeight(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.status_media_preview_height);
    }

    void setupWithConversation(ConversationEntity conversation) {
        ConversationStatusEntity status = conversation.getLastStatus();
        ConversationAccountEntity account = status.getAccount();

        setupCollapsedState(status.getCollapsible(), status.getCollapsed(), status.getExpanded(), status.getSpoilerText(), listener);

        setDisplayName(account.getDisplayName(), account.getEmojis());
        setUsername(account.getUsername());
        setCreatedAt(status.getCreatedAt(), statusDisplayOptions);
        setIsReply(status.getInReplyToId() != null);
        setFavourited(status.getFavourited());
        setBookmarked(status.getBookmarked());
        List<Attachment> attachments = status.getAttachments();
        boolean sensitive = status.getSensitive();
        if (statusDisplayOptions.mediaPreviewEnabled() && !hasAudioAttachment(attachments)) {
            setMediaPreviews(attachments, sensitive, listener, status.getShowingHiddenContent(),
                    statusDisplayOptions.useBlurhash());

            if (attachments.size() == 0) {
                hideSensitiveMediaWarning();
            }
            // Hide the unused label.
            for (TextView mediaLabel : mediaLabels) {
                mediaLabel.setVisibility(View.GONE);
            }
        } else {
            setMediaLabel(attachments, sensitive, listener, status.getShowingHiddenContent());
            // Hide all unused views.
            mediaPreviews[0].setVisibility(View.GONE);
            mediaPreviews[1].setVisibility(View.GONE);
            mediaPreviews[2].setVisibility(View.GONE);
            mediaPreviews[3].setVisibility(View.GONE);
            hideSensitiveMediaWarning();
        }

        setupButtons(listener, account.getId(), status.getContent().toString(),
                statusDisplayOptions);

        setSpoilerAndContent(status.getExpanded(), status.getContent(), status.getSpoilerText(),
                status.getMentions(), status.getEmojis(),
                PollViewDataKt.toViewData(status.getPoll()), statusDisplayOptions, listener);

        setConversationName(conversation.getAccounts());

        setAvatars(conversation.getAccounts());
    }

    private void setConversationName(List<ConversationAccountEntity> accounts) {
        Context context = conversationNameTextView.getContext();
        String conversationName = "";
        if (accounts.size() == 1) {
            conversationName = context.getString(R.string.conversation_1_recipients, accounts.get(0).getUsername());
        } else if (accounts.size() == 2) {
            conversationName = context.getString(R.string.conversation_2_recipients, accounts.get(0).getUsername(), accounts.get(1).getUsername());
        } else if (accounts.size() > 2) {
            conversationName = context.getString(R.string.conversation_more_recipients, accounts.get(0).getUsername(), accounts.get(1).getUsername(), accounts.size() - 2);
        }

        conversationNameTextView.setText(conversationName);
    }

    private void setAvatars(List<ConversationAccountEntity> accounts) {
        for (int i = 0; i < avatars.length; i++) {
            ImageView avatarView = avatars[i];
            if (i < accounts.size()) {
                ImageLoadingHelper.loadAvatar(accounts.get(i).getAvatar(), avatarView,
                        avatarRadius48dp, statusDisplayOptions.animateAvatars());
                avatarView.setVisibility(View.VISIBLE);
            } else {
                avatarView.setVisibility(View.GONE);
            }
        }
    }

    private void setupCollapsedState(boolean collapsible, boolean collapsed, boolean expanded, String spoilerText, final StatusActionListener listener) {
        /* input filter for TextViews have to be set before text */
        if (collapsible && (expanded || TextUtils.isEmpty(spoilerText))) {
            contentCollapseButton.setOnClickListener(view -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION)
                    listener.onContentCollapsedChange(!collapsed, position);
            });

            contentCollapseButton.setVisibility(View.VISIBLE);
            if (collapsed) {
                contentCollapseButton.setText(R.string.status_content_warning_show_more);
                content.setFilters(COLLAPSE_INPUT_FILTER);
            } else {
                contentCollapseButton.setText(R.string.status_content_warning_show_less);
                content.setFilters(NO_INPUT_FILTER);
            }
        } else {
            contentCollapseButton.setVisibility(View.GONE);
            content.setFilters(NO_INPUT_FILTER);
        }
    }
}