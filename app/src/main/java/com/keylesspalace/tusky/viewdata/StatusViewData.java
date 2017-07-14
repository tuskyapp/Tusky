package com.keylesspalace.tusky.viewdata;

import android.support.annotation.Nullable;
import android.text.Spanned;

import com.keylesspalace.tusky.entity.Status;

import java.util.Date;

/**
 * Created by charlag on 11/07/2017.
 */

public final class StatusViewData {
    private final String id;
    private final Spanned content;
    private final boolean reblogged;
    private final boolean favourited;
    @Nullable
    private final String spoilerText;
    private final Status.Visibility visibility;
    private final Status.MediaAttachment[] attachments;
    @Nullable
    private final String rebloggedByUsername;
    @Nullable
    private final String rebloggedAvatar;
    private final boolean isSensitive;
    private final boolean isExpanded;
    private final boolean isShowingSensitiveContent;
    private final String userFullName;
    private final String nickname;
    private final String avatar;
    private final Date createdAt;
    // I would rather have something else but it would be too much of a rewrite
    @Nullable
    private final Status.Mention[] mentions;
    private final String senderId;
    private final boolean rebloggingEnabled;

    public StatusViewData(String id, Spanned contnet, boolean reblogged, boolean favourited,
                          String spoilerText, Status.Visibility visibility,
                          Status.MediaAttachment[] attachments, String rebloggedByUsername,
                          String rebloggedAvatar, boolean sensitive, boolean isExpanded,
                          boolean isShowingSensitiveWarning, String userFullName, String nickname,
                          String avatar, Date createdAt, Status.Mention[] mentions,
                          String senderId, boolean rebloggingEnabled) {
        this.id = id;
        this.content = contnet;
        this.reblogged = reblogged;
        this.favourited = favourited;
        this.spoilerText = spoilerText;
        this.visibility = visibility;
        this.attachments = attachments;
        this.rebloggedByUsername = rebloggedByUsername;
        this.rebloggedAvatar = rebloggedAvatar;
        this.isSensitive = sensitive;
        this.isExpanded = isExpanded;
        this.isShowingSensitiveContent = isShowingSensitiveWarning;
        this.userFullName = userFullName;
        this.nickname = nickname;
        this.avatar = avatar;
        this.createdAt = createdAt;
        this.mentions = mentions;
        this.senderId = senderId;
        this.rebloggingEnabled = rebloggingEnabled;
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

    public Status.MediaAttachment[] getAttachments() {
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

    public boolean isShowingSensitiveContent() {
        return isShowingSensitiveContent;
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

    public static class Builder {
        private String id;
        private Spanned contnet;
        private boolean reblogged;
        private boolean favourited;
        private String spoilerText;
        private Status.Visibility visibility;
        private Status.MediaAttachment[] attachments;
        private String rebloggedByUsername;
        private String rebloggedAvatar;
        private boolean isSensitive;
        private boolean isExpanded;
        private boolean isShowingSensitiveContent;
        private String userFullName;
        private String nickname;
        private String avatar;
        private Date createdAt;
        private Status.Mention[] mentions;
        private String senderId;
        private boolean rebloggingEnabled;

        public Builder() {
        }

        public Builder(final StatusViewData viewData) {
            id = viewData.id;
            contnet = viewData.content;
            reblogged = viewData.reblogged;
            favourited = viewData.favourited;
            spoilerText = viewData.spoilerText;
            visibility = viewData.visibility;
            attachments = viewData.attachments == null ? null : viewData.attachments.clone();
            rebloggedByUsername = viewData.rebloggedByUsername;
            rebloggedAvatar = viewData.rebloggedAvatar;
            isSensitive = viewData.isSensitive;
            isExpanded = viewData.isExpanded;
            isShowingSensitiveContent = viewData.isShowingSensitiveContent;
            userFullName = viewData.userFullName;
            nickname = viewData.nickname;
            avatar = viewData.avatar;
            createdAt = new Date(viewData.createdAt.getTime());
            mentions = viewData.mentions == null ? null : viewData.mentions.clone();
            senderId = viewData.senderId;
            rebloggingEnabled = viewData.rebloggingEnabled;
        }

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setContent(Spanned content) {
            this.contnet = content;
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

        public Builder setAttachments(Status.MediaAttachment[] attachments) {
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
            this.isShowingSensitiveContent = isShowingSensitiveContent;
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

        public StatusViewData createStatusViewData() {
            return new StatusViewData(id, contnet, reblogged, favourited, spoilerText, visibility,
                    attachments, rebloggedByUsername, rebloggedAvatar, isSensitive, isExpanded,
                    isShowingSensitiveContent, userFullName, nickname, avatar, createdAt, mentions,
                    senderId, rebloggingEnabled);
        }
    }
}
