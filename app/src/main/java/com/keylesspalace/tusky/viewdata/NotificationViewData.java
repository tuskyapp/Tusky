package com.keylesspalace.tusky.viewdata;

import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.entity.Notification;

/**
 * Created by charlag on 12/07/2017.
 */

public final class NotificationViewData {
    private final Notification.Type type;
    private final String id;
    private final Account account;
    private final StatusViewData statusViewData;

    public NotificationViewData(Notification.Type type, String id, Account account,
                                StatusViewData statusViewData) {
        this.type = type;
        this.id = id;
        this.account = account;
        this.statusViewData = statusViewData;
    }

    public Notification.Type getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public StatusViewData getStatusViewData() {
        return statusViewData;
    }
}
