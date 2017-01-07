package com.keylesspalace.tusky;

import android.support.annotation.Nullable;

public class Notification {
    public enum Type {
        MENTION,
        REBLOG,
        FAVOURITE,
        FOLLOW,
    }
    private Type type;
    private String id;
    private String displayName;
    /** Which of the user's statuses has been mentioned, reblogged, or favourited. */
    private Status status;

    public Notification(Type type, String id, String displayName) {
        this.type = type;
        this.id = id;
        this.displayName = displayName;
    }

    public Type getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public @Nullable Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
