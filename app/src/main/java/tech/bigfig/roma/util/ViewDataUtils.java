/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma.util;

import androidx.annotation.Nullable;

import tech.bigfig.roma.entity.Notification;
import tech.bigfig.roma.entity.Status;
import tech.bigfig.roma.viewdata.NotificationViewData;
import tech.bigfig.roma.viewdata.StatusViewData;

/**
 * Created by charlag on 12/07/2017.
 */

public final class ViewDataUtils {
    @Nullable
    public static StatusViewData.Concrete statusToViewData(@Nullable Status status,
                                                           boolean alwaysShowSensitiveMedia) {
        if (status == null) return null;
        Status visibleStatus = status.getReblog() == null ? status : status.getReblog();
        return new StatusViewData.Builder().setId(status.getId())
                .setAttachments(visibleStatus.getAttachments())
                .setAvatar(visibleStatus.getAccount().getAvatar())
                .setContent(visibleStatus.getContent())
                .setCreatedAt(visibleStatus.getCreatedAt())
                .setReblogsCount(visibleStatus.getReblogsCount())
                .setFavouritesCount(visibleStatus.getFavouritesCount())
                .setInReplyToId(visibleStatus.getInReplyToId())
                .setFavourited(visibleStatus.getFavourited())
                .setReblogged(visibleStatus.getReblogged())
                .setIsExpanded(false)
                .setIsShowingSensitiveContent(false)
                .setMentions(visibleStatus.getMentions())
                .setNickname(visibleStatus.getAccount().getUsername())
                .setRebloggedAvatar(status.getReblog() == null ? null : status.getAccount().getAvatar())
                .setSensitive(visibleStatus.getSensitive())
                .setIsShowingSensitiveContent(alwaysShowSensitiveMedia || !visibleStatus.getSensitive())
                .setSpoilerText(visibleStatus.getSpoilerText())
                .setRebloggedByUsername(status.getReblog() == null ? null : status.getAccount().getUsername())
                .setUserFullName(visibleStatus.getAccount().getName())
                .setVisibility(visibleStatus.getVisibility())
                .setSenderId(visibleStatus.getAccount().getId())
                .setRebloggingEnabled(visibleStatus.rebloggingAllowed())
                .setApplication(visibleStatus.getApplication())
                .setStatusEmojis(visibleStatus.getEmojis())
                .setAccountEmojis(visibleStatus.getAccount().getEmojis())
                .setCollapsible(!SmartLengthInputFilter.hasBadRatio(
                        visibleStatus.getContent(),
                        SmartLengthInputFilter.LENGTH_DEFAULT
                ))
                .setCollapsed(true)
                .createStatusViewData();
    }

    public static NotificationViewData.Concrete notificationToViewData(Notification notification,
                                                                       boolean alwaysShowSensitiveData) {
        return new NotificationViewData.Concrete(
                notification.getType(),
                notification.getId(),
                notification.getAccount(),
                statusToViewData(
                        notification.getStatus(),
                        alwaysShowSensitiveData
                ),
                false
        );
    }
}
