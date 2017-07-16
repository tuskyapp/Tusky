package com.keylesspalace.tusky.util;

import android.arch.core.util.Function;
import android.support.annotation.Nullable;

import com.keylesspalace.tusky.entity.Notification;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.viewdata.NotificationViewData;
import com.keylesspalace.tusky.viewdata.StatusViewData;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by charlag on 12/07/2017.
 */

public final class ViewDataUtils {
    @Nullable
    public static StatusViewData statusToViewData(@Nullable Status status) {
        if (status == null) return null;
        Status visibleStatus = status.reblog == null ? status : status.reblog;
        return new StatusViewData.Builder()
                .setId(status.id)
                .setAttachments(visibleStatus.attachments)
                .setAvatar(visibleStatus.account.avatar)
                .setContent(visibleStatus.content)
                .setCreatedAt(visibleStatus.createdAt)
                .setFavourited(visibleStatus.favourited)
                .setReblogged(visibleStatus.reblogged)
                .setIsExpanded(false)
                .setIsShowingSensitiveContent(false)
                .setMentions(visibleStatus.mentions)
                .setNickname(visibleStatus.account.username)
                .setRebloggedAvatar(status.reblog == null ? null : status.account.avatar)
                .setSensitive(visibleStatus.sensitive)
                .setSpoilerText(visibleStatus.spoilerText)
                .setRebloggedByUsername(status.reblog == null ? null : status.account.username)
                .setUserFullName(visibleStatus.account.getDisplayName())
                .setVisibility(visibleStatus.visibility)
                .setSenderId(visibleStatus.account.id)
                .setRebloggingEnabled(visibleStatus.rebloggingAllowed())
                .createStatusViewData();
    }

    public static List<StatusViewData> statusListToViewDataList(List<Status> statuses) {
        List<StatusViewData> viewDatas = new ArrayList<>(statuses.size());
        for (Status s : statuses) {
            viewDatas.add(statusToViewData(s));
        }
        return viewDatas;
    }

    public static Function<Status, StatusViewData> statusMapper() {
        return statusMapper;
    }

    public static NotificationViewData notificationToViewData(Notification notification) {
        return new NotificationViewData(notification.type, notification.id, notification.account,
                statusToViewData(notification.status));
    }

    public static List<NotificationViewData>
    notificationListToViewDataList(List<Notification> notifications) {
        List<NotificationViewData> viewDatas = new ArrayList<>(notifications.size());
        for (Notification n : notifications) {
            viewDatas.add(notificationToViewData(n));
        }
        return viewDatas;
    }

    private static final Function<Status, StatusViewData> statusMapper =
            new Function<Status, StatusViewData>() {
                @Override
                public StatusViewData apply(Status input) {
                    return ViewDataUtils.statusToViewData(input);
                }
            };
}
