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

package com.keylesspalace.tusky.viewdata;

import android.support.annotation.Nullable;
import android.text.Spanned;

import com.keylesspalace.tusky.entity.Attachment;
import com.keylesspalace.tusky.entity.Card;
import com.keylesspalace.tusky.entity.Status;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Created by charlag on 11/07/2017.
 *
 * Class to represent data required to display either a notification or a placeholder.
 * It is either a {@link StatusViewData.Concrete} or a {@link StatusViewData.Placeholder}.
 */

public abstract class StatusViewData {

    private StatusViewData() {
    }

    public static final class Concrete extends StatusViewData {
        private final String id;
        private final Spanned content;
        private final boolean reblogged;
        private final boolean favourited;
        @Nullable
        private final String spoilerText;
        private final Status.Visibility visibility;
        private final Attachment[] attachments;
        @Nullable
        private final String rebloggedByUsername;
        @Nullable
        private final String rebloggedAvatar;
        private final boolean isSensitive;
        private final boolean isExpanded;
        private final boolean isShowingContent;
        private final String userFullName;
        private final String nickname;
        private final String avatar;
        private final Date createdAt;
        private final String reblogsCount;
        private final String favouritesCount;
        @Nullable
        private final String inReplyToId;
        // I would rather have something else but it would be too much of a rewrite
        @Nullable
        private final Status.Mention[] mentions;
        private final String senderId;
        private final boolean rebloggingEnabled;
        private final Status.Application application;
        private final List<Status.Emoji> emojis;
        @Nullable
        private final Card card;

        public Concrete(String id, Spanned content, boolean reblogged, boolean favourited,
                        @Nullable String spoilerText, Status.Visibility visibility, Attachment[] attachments,
                        @Nullable String rebloggedByUsername, @Nullable String rebloggedAvatar, boolean sensitive, boolean isExpanded,
                        boolean isShowingContent, String userFullName, String nickname, String avatar,
                        Date createdAt, String reblogsCount, String favouritesCount, @Nullable String inReplyToId,
                        @Nullable Status.Mention[] mentions, String senderId, boolean rebloggingEnabled,
                        Status.Application application, List<Status.Emoji> emojis, @Nullable Card card) {
            this.id = id;
            this.content = content;
            this.reblogged = reblogged;
            this.favourited = favourited;
            this.spoilerText = spoilerText;
            this.visibility = visibility;
            this.attachments = attachments;
            this.rebloggedByUsername = rebloggedByUsername;
            this.rebloggedAvatar = rebloggedAvatar;
            this.isSensitive = sensitive;
            this.isExpanded = isExpanded;
            this.isShowingContent = isShowingContent;
            this.userFullName = userFullName;
            this.nickname = nickname;
            this.avatar = avatar;
            this.createdAt = createdAt;
            this.reblogsCount = reblogsCount;
            this.favouritesCount = favouritesCount;
            this.inReplyToId = inReplyToId;
            this.mentions = mentions;
            this.senderId = senderId;
            this.rebloggingEnabled = rebloggingEnabled;
            this.application = application;
            this.emojis = emojis;
            this.card = card;
        }

        public String getId() {
            return id;
        }

        public Spanned getContent() {
            return content;
        }

        public boolean isReblogged() {
            return reblogged;
        }

        public boolean isFavourited() {
            return favourited;
        }

        @Nullable
        public String getSpoilerText() {
            return spoilerText;
        }

        public Status.Visibility getVisibility() {
            return visibility;
        }

        public Attachment[] getAttachments() {
            return attachments;
        }

        @Nullable
        public String getRebloggedByUsername() {
            return rebloggedByUsername;
        }

        public boolean isSensitive() {
            return isSensitive;
        }

        public boolean isExpanded() {
            return isExpanded;
        }

        public boolean isShowingContent() {
            return isShowingContent;
        }

        @Nullable
        public String getRebloggedAvatar() {
            return rebloggedAvatar;
        }

        public String getUserFullName() {
            return userFullName;
        }

        public String getNickname() {
            return nickname;
        }

        public String getAvatar() {
            return avatar;
        }

        public Date getCreatedAt() {
            return createdAt;
        }

        public String getReblogsCount() {
            return reblogsCount;
        }

        public String getFavouritesCount() {
            return favouritesCount;
        }

        @Nullable
        public String getInReplyToId() {
            return inReplyToId;
        }

        public String getSenderId() {
            return senderId;
        }

        public Boolean getRebloggingEnabled() {
            return rebloggingEnabled;
        }

        @Nullable
        public Status.Mention[] getMentions() {
            return mentions;
        }

        public Status.Application getApplication() {
            return application;
        }

        public List<Status.Emoji> getEmojis() {
            return emojis;
        }

        @Nullable
        public Card getCard() {
            return card;
        }

    }

    public static final class Placeholder extends StatusViewData {
        private final boolean isLoading;

        public Placeholder(boolean isLoading) {
            this.isLoading = isLoading;
        }

        public boolean isLoading() {
            return isLoading;
        }
    }

    public static class Builder {
        private String id;
        private Spanned content;
        private boolean reblogged;
        private boolean favourited;
        private String spoilerText;
        private Status.Visibility visibility;
        private Attachment[] attachments;
        private String rebloggedByUsername;
        private String rebloggedAvatar;
        private boolean isSensitive;
        private boolean isExpanded;
        private boolean isShowingContent;
        private String userFullName;
        private String nickname;
        private String avatar;
        private Date createdAt;
        private String reblogsCount;
        private String favouritesCount;
        private String inReplyToId;
        private Status.Mention[] mentions;
        private String senderId;
        private boolean rebloggingEnabled;
        private Status.Application application;
        private List<Status.Emoji> emojis;
        private Card card;

        public Builder() {
        }

        public Builder(final StatusViewData.Concrete viewData) {
            id = viewData.id;
            content = viewData.content;
            reblogged = viewData.reblogged;
            favourited = viewData.favourited;
            spoilerText = viewData.spoilerText;
            visibility = viewData.visibility;
            attachments = viewData.attachments == null ? null : viewData.attachments.clone();
            rebloggedByUsername = viewData.rebloggedByUsername;
            rebloggedAvatar = viewData.rebloggedAvatar;
            isSensitive = viewData.isSensitive;
            isExpanded = viewData.isExpanded;
            isShowingContent = viewData.isShowingContent;
            userFullName = viewData.userFullName;
            nickname = viewData.nickname;
            avatar = viewData.avatar;
            createdAt = new Date(viewData.createdAt.getTime());
            reblogsCount = viewData.reblogsCount;
            favouritesCount = viewData.favouritesCount;
            inReplyToId = viewData.inReplyToId;
            mentions = viewData.mentions == null ? null : viewData.mentions.clone();
            senderId = viewData.senderId;
            rebloggingEnabled = viewData.rebloggingEnabled;
            application = viewData.application;
            emojis = viewData.getEmojis();
            card = viewData.getCard();
        }

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setContent(Spanned content) {
            this.content = content;
            return this;
        }

        public Builder setReblogged(boolean reblogged) {
            this.reblogged = reblogged;
            return this;
        }

        public Builder setFavourited(boolean favourited) {
            this.favourited = favourited;
            return this;
        }

        public Builder setSpoilerText(String spoilerText) {
            this.spoilerText = spoilerText;
            return this;
        }

        public Builder setVisibility(Status.Visibility visibility) {
            this.visibility = visibility;
            return this;
        }

        public Builder setAttachments(Attachment[] attachments) {
            this.attachments = attachments;
            return this;
        }

        public Builder setRebloggedByUsername(String rebloggedByUsername) {
            this.rebloggedByUsername = rebloggedByUsername;
            return this;
        }

        public Builder setRebloggedAvatar(String rebloggedAvatar) {
            this.rebloggedAvatar = rebloggedAvatar;
            return this;
        }

        public Builder setSensitive(boolean sensitive) {
            this.isSensitive = sensitive;
            return this;
        }

        public Builder setIsExpanded(boolean isExpanded) {
            this.isExpanded = isExpanded;
            return this;
        }

        public Builder setIsShowingSensitiveContent(boolean isShowingSensitiveContent) {
            this.isShowingContent = isShowingSensitiveContent;
            return this;
        }

        public Builder setUserFullName(String userFullName) {
            this.userFullName = userFullName;
            return this;
        }

        public Builder setNickname(String nickname) {
            this.nickname = nickname;
            return this;
        }

        public Builder setAvatar(String avatar) {
            this.avatar = avatar;
            return this;
        }

        public Builder setCreatedAt(Date createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder setReblogsCount(String reblogsCount) {
            this.reblogsCount = reblogsCount;
            return this;
        }

        public Builder setFavouritesCount(String favouritesCount) {
            this.favouritesCount = favouritesCount;
            return this;
        }

        public Builder setInReplyToId(String inReplyToId) {
            this.inReplyToId = inReplyToId;
            return this;
        }

        public Builder setMentions(Status.Mention[] mentions) {
            this.mentions = mentions;
            return this;
        }

        public Builder setSenderId(String senderId) {
            this.senderId = senderId;
            return this;
        }

        public Builder setRebloggingEnabled(boolean rebloggingEnabled) {
            this.rebloggingEnabled = rebloggingEnabled;
            return this;
        }

        public Builder setApplication(Status.Application application) {
            this.application = application;
            return this;
        }

        public Builder setEmojis(List<Status.Emoji> emojis) {
            this.emojis = emojis;
            return this;
        }

        public Builder setCard(Card card) {
            this.card = card;
            return this;
        }

        public StatusViewData.Concrete createStatusViewData() {
            if (this.emojis == null) emojis = Collections.emptyList();
            if (this.createdAt == null) createdAt = new Date();

            return new StatusViewData.Concrete(id, content, reblogged, favourited, spoilerText, visibility,
                    attachments, rebloggedByUsername, rebloggedAvatar, isSensitive, isExpanded,
                    isShowingContent, userFullName, nickname, avatar, createdAt, reblogsCount,
                    favouritesCount, inReplyToId, mentions, senderId, rebloggingEnabled, application,
                    emojis, card);
        }
    }
}
