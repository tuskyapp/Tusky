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

import android.os.Build;
import android.support.annotation.Nullable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import com.keylesspalace.tusky.entity.Attachment;
import com.keylesspalace.tusky.entity.Card;
import com.keylesspalace.tusky.entity.Emoji;
import com.keylesspalace.tusky.entity.Status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Created by charlag on 11/07/2017.
 * <p>
 * Class to represent data required to display either a notification or a placeholder.
 * It is either a {@link StatusViewData.Concrete} or a {@link StatusViewData.Placeholder}.
 */

public abstract class StatusViewData {

    private StatusViewData() {
    }

    public abstract long getViewDataId();

    public abstract boolean deepEquals(StatusViewData other);

    public static final class Concrete extends StatusViewData {
        private static final char SOFT_HYPHEN = '\u00ad';
        private static final char ASCII_HYPHEN = '-';

        private final String id;
        private final Spanned content;
        private final boolean reblogged;
        private final boolean favourited;
        @Nullable
        private final String spoilerText;
        private final Status.Visibility visibility;
        private final List<Attachment> attachments;
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
        private final long reblogsCount;
        private final long favouritesCount;
        @Nullable
        private final String inReplyToId;
        // I would rather have something else but it would be too much of a rewrite
        @Nullable
        private final Status.Mention[] mentions;
        private final String senderId;
        private final boolean rebloggingEnabled;
        private final Status.Application application;
        private final List<Emoji> statusEmojis;
        private final List<Emoji> accountEmojis;
        @Nullable
        private final Card card;
        private final boolean isCollapsible; /** Whether the status meets the requirement to be collapse */
        private final boolean isCollapsed; /** Whether the status is shown partially or fully */

        public Concrete(String id, Spanned content, boolean reblogged, boolean favourited,
                        @Nullable String spoilerText, Status.Visibility visibility, List<Attachment> attachments,
                        @Nullable String rebloggedByUsername, @Nullable String rebloggedAvatar, boolean sensitive, boolean isExpanded,
                        boolean isShowingContent, String userFullName, String nickname, String avatar,
                        Date createdAt, long reblogsCount, long favouritesCount, @Nullable String inReplyToId,
                        @Nullable Status.Mention[] mentions, String senderId, boolean rebloggingEnabled,
                        Status.Application application, List<Emoji> statusEmojis, List<Emoji> accountEmojis, @Nullable Card card,
                        boolean isCollapsible, boolean isCollapsed) {
            this.id = id;
            if (Build.VERSION.SDK_INT == 23) {
                // https://github.com/tuskyapp/Tusky/issues/563
                this.content = replaceCrashingCharacters(content);
                this.spoilerText = spoilerText == null ? null : replaceCrashingCharacters(spoilerText).toString();
                this.nickname = replaceCrashingCharacters(nickname).toString();
            } else {
                this.content = content;
                this.spoilerText = spoilerText;
                this.nickname = nickname;
            }
            this.reblogged = reblogged;
            this.favourited = favourited;
            this.visibility = visibility;
            this.attachments = attachments;
            this.rebloggedByUsername = rebloggedByUsername;
            this.rebloggedAvatar = rebloggedAvatar;
            this.isSensitive = sensitive;
            this.isExpanded = isExpanded;
            this.isShowingContent = isShowingContent;
            this.userFullName = userFullName;
            this.avatar = avatar;
            this.createdAt = createdAt;
            this.reblogsCount = reblogsCount;
            this.favouritesCount = favouritesCount;
            this.inReplyToId = inReplyToId;
            this.mentions = mentions;
            this.senderId = senderId;
            this.rebloggingEnabled = rebloggingEnabled;
            this.application = application;
            this.statusEmojis = statusEmojis;
            this.accountEmojis = accountEmojis;
            this.card = card;
            this.isCollapsible = isCollapsible;
            this.isCollapsed = isCollapsed;
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

        public List<Attachment> getAttachments() {
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

        public long getReblogsCount() {
            return reblogsCount;
        }

        public long getFavouritesCount() {
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

        public List<Emoji> getStatusEmojis() {
            return statusEmojis;
        }

        public List<Emoji> getAccountEmojis() {
            return accountEmojis;
        }

        @Nullable
        public Card getCard() {
            return card;
        }

        /**
         * Specifies whether the content of this post is allowed to be collapsed or if it should show
         * all content regardless.
         *
         * @return Whether the post is collapsible or never collapsed.
         */
        public boolean isCollapsible() {
            return isCollapsible;
        }

        /**
         * Specifies whether the content of this post is currently limited in visibility to the first
         * 500 characters or not.
         *
         * @return Whether the post is collapsed or fully expanded.
         */
        public boolean isCollapsed() {
            return isCollapsed;
        }

        @Override public long getViewDataId() {
            // Chance of collision is super low and impact of mistake is low as well
            return getId().hashCode();
        }

        public boolean deepEquals(StatusViewData o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Concrete concrete = (Concrete) o;
            return reblogged == concrete.reblogged &&
                    favourited == concrete.favourited &&
                    isSensitive == concrete.isSensitive &&
                    isExpanded == concrete.isExpanded &&
                    isShowingContent == concrete.isShowingContent &&
                    reblogsCount == concrete.reblogsCount &&
                    favouritesCount == concrete.favouritesCount &&
                    rebloggingEnabled == concrete.rebloggingEnabled &&
                    Objects.equals(id, concrete.id) &&
                    Objects.equals(content, concrete.content) &&
                    Objects.equals(spoilerText, concrete.spoilerText) &&
                    visibility == concrete.visibility &&
                    Objects.equals(attachments, concrete.attachments) &&
                    Objects.equals(rebloggedByUsername, concrete.rebloggedByUsername) &&
                    Objects.equals(rebloggedAvatar, concrete.rebloggedAvatar) &&
                    Objects.equals(userFullName, concrete.userFullName) &&
                    Objects.equals(nickname, concrete.nickname) &&
                    Objects.equals(avatar, concrete.avatar) &&
                    Objects.equals(createdAt, concrete.createdAt) &&
                    Objects.equals(inReplyToId, concrete.inReplyToId) &&
                    Arrays.equals(mentions, concrete.mentions) &&
                    Objects.equals(senderId, concrete.senderId) &&
                    Objects.equals(application, concrete.application) &&
                    Objects.equals(statusEmojis, concrete.statusEmojis) &&
                    Objects.equals(accountEmojis, concrete.accountEmojis) &&
                    Objects.equals(card, concrete.card)
                    && isCollapsed == concrete.isCollapsed;
        }

        static Spanned replaceCrashingCharacters(Spanned content) {
            return (Spanned) replaceCrashingCharacters((CharSequence) content);
        }

        static CharSequence replaceCrashingCharacters(CharSequence content) {
            Boolean replacing = false;
            SpannableStringBuilder builder = null;
            int length = content.length();

            for (int index = 0; index < length; ++index) {
                char character = content.charAt(index);

                // If there are more than one or two, switch to a map
                if (character == SOFT_HYPHEN) {
                    if (!replacing) {
                        replacing = true;
                        builder = new SpannableStringBuilder(content, 0, index);
                    }
                    builder.append(ASCII_HYPHEN);
                } else if (replacing) {
                    builder.append(character);
                }
            }

            return replacing ? builder : content;
        }
    }

    public static final class Placeholder extends StatusViewData {
        private final boolean isLoading;
        private final String id;

        public Placeholder(String id, boolean isLoading) {
            this.id = id;
            this.isLoading = isLoading;
        }

        public boolean isLoading() {
            return isLoading;
        }

        public String getId() {
            return id;
        }

        @Override public long getViewDataId() {
            return id.hashCode();
        }

        @Override public boolean deepEquals(StatusViewData other) {
            if (!(other instanceof Placeholder)) return false;
            Placeholder that = (Placeholder) other;
            return isLoading == that.isLoading && id.equals(that.id);
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Placeholder that = (Placeholder) o;

            return deepEquals(that);
        }

        @Override
        public int hashCode() {
            int result = (isLoading ? 1 : 0);
            result = 31 * result + id.hashCode();
            return result;
        }
    }

    public static class Builder {
        private String id;
        private Spanned content;
        private boolean reblogged;
        private boolean favourited;
        private String spoilerText;
        private Status.Visibility visibility;
        private List<Attachment> attachments;
        private String rebloggedByUsername;
        private String rebloggedAvatar;
        private boolean isSensitive;
        private boolean isExpanded;
        private boolean isShowingContent;
        private String userFullName;
        private String nickname;
        private String avatar;
        private Date createdAt;
        private long reblogsCount;
        private long favouritesCount;
        private String inReplyToId;
        private Status.Mention[] mentions;
        private String senderId;
        private boolean rebloggingEnabled;
        private Status.Application application;
        private List<Emoji> statusEmojis;
        private List<Emoji> accountEmojis;
        private Card card;
        private boolean isCollapsible; /** Whether the status meets the requirement to be collapsed */
        private boolean isCollapsed; /** Whether the status is shown partially or fully */

        public Builder() {
        }

        public Builder(final StatusViewData.Concrete viewData) {
            id = viewData.id;
            content = viewData.content;
            reblogged = viewData.reblogged;
            favourited = viewData.favourited;
            spoilerText = viewData.spoilerText;
            visibility = viewData.visibility;
            attachments = viewData.attachments == null ? null : new ArrayList<>(viewData.attachments);
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
            statusEmojis = viewData.getStatusEmojis();
            accountEmojis = viewData.getAccountEmojis();
            card = viewData.getCard();
            isCollapsible = viewData.isCollapsible();
            isCollapsed = viewData.isCollapsed();
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

        public Builder setAttachments(List<Attachment> attachments) {
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

        public Builder setReblogsCount(long reblogsCount) {
            this.reblogsCount = reblogsCount;
            return this;
        }

        public Builder setFavouritesCount(long favouritesCount) {
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

        public Builder setStatusEmojis(List<Emoji> emojis) {
            this.statusEmojis = emojis;
            return this;
        }

        public Builder setAccountEmojis(List<Emoji> emojis) {
            this.accountEmojis = emojis;
            return this;
        }

        public Builder setCard(Card card) {
            this.card = card;
            return this;
        }

        /**
         * Configure the {@link com.keylesspalace.tusky.viewdata.StatusViewData} to support collapsing
         * its content limiting the visible length when collapsed at 500 characters,
         *
         * @param collapsible Whether the status should support being collapsed or not.
         * @return This {@link com.keylesspalace.tusky.viewdata.StatusViewData.Builder} instance.
         */
        public Builder setCollapsible(boolean collapsible) {
            isCollapsible = collapsible;
            return this;
        }

        /**
         * Configure the {@link com.keylesspalace.tusky.viewdata.StatusViewData} to start in a collapsed
         * state, hiding partially the content of the post if it exceeds a certain amount of characters.
         *
         * @param collapsed Whether to show the full content of the status or not.
         * @return This {@link com.keylesspalace.tusky.viewdata.StatusViewData.Builder} instance.
         */
        public Builder setCollapsed(boolean collapsed) {
            isCollapsed = collapsed;
            return this;
        }

        public StatusViewData.Concrete createStatusViewData() {
            if (this.statusEmojis == null) statusEmojis = Collections.emptyList();
            if (this.accountEmojis == null) accountEmojis = Collections.emptyList();
            if (this.createdAt == null) createdAt = new Date();

            return new StatusViewData.Concrete(id, content, reblogged, favourited, spoilerText, visibility,
                    attachments, rebloggedByUsername, rebloggedAvatar, isSensitive, isExpanded,
                    isShowingContent, userFullName, nickname, avatar, createdAt, reblogsCount,
                    favouritesCount, inReplyToId, mentions, senderId, rebloggingEnabled, application,
                    statusEmojis, accountEmojis, card, isCollapsible, isCollapsed);
        }
    }
}
