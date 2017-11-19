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

package com.keylesspalace.tusky.util;

import android.support.annotation.Nullable;

import com.keylesspalace.tusky.entity.Notification;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.viewdata.NotificationViewData;
import com.keylesspalace.tusky.viewdata.StatusViewData;

/**
 * Created by charlag on 12/07/2017.
 */

public final class ViewDataUtils {
    @Nullable
    public static StatusViewData.Concrete statusToViewData(@Nullable Status status,
                                                           boolean alwaysShowSensitiveMedia) {
        if (status == null) return null;
        Status visibleStatus = status.reblog == null ? status : status.reblog;
        return new StatusViewData.Builder().setId(status.id)
                .setAttachments(visibleStatus.attachments)
                .setAvatar(visibleStatus.account.avatar)
                .setContent(visibleStatus.content)
                .setCreatedAt(visibleStatus.createdAt)
                .setReblogsCount(visibleStatus.reblogsCount)
                .setFavouritesCount(visibleStatus.favouritesCount)
                .setInReplyToId(visibleStatus.inReplyToId)
                .setFavourited(visibleStatus.favourited)
                .setReblogged(visibleStatus.reblogged)
                .setIsExpanded(false)
                .setIsShowingSensitiveContent(false)
                .setMentions(visibleStatus.mentions)
                .setNickname(visibleStatus.account.username)
                .setRebloggedAvatar(status.reblog == null ? null : status.account.avatar)
                .setSensitive(visibleStatus.sensitive)
                .setIsShowingSensitiveContent(alwaysShowSensitiveMedia || !visibleStatus.sensitive)
                .setSpoilerText(visibleStatus.spoilerText)
                .setRebloggedByUsername(status.reblog == null ? null : status.account.username)
                .setUserFullName(visibleStatus.account.getDisplayName())
                .setVisibility(visibleStatus.visibility)
                .setSenderId(visibleStatus.account.id)
                .setRebloggingEnabled(visibleStatus.rebloggingAllowed())
                .setApplication(visibleStatus.application)
                .setEmojis(visibleStatus.emojis)
                .createStatusViewData();
    }

    public static NotificationViewData.Concrete notificationToViewData(Notification notification, boolean alwaysShowSensitiveData) {
        return new NotificationViewData.Concrete(notification.type, notification.id, notification.account,
                statusToViewData(notification.status, alwaysShowSensitiveData), false);
    }

}
