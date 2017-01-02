package com.keylesspalace.tusky;

import android.text.Spanned;

import java.util.Date;

public class Status {
    private String id;
    private String displayName;
    /** the username with the remote domain appended, like @domain.name, if it's a remote account */
    private String username;
    /** the main text of the status, marked up with style for links & mentions, etc */
    private Spanned content;
    /** the fully-qualified url of the avatar image */
    private String avatar;
    private String rebloggedByUsername;
    /** when the status was initially created */
    private Date createdAt;

    public Status(String id, String displayName, String username, Spanned content, String avatar,
                  Date createdAt) {
        this.id = id;
        this.displayName = displayName;
        this.username = username;
        this.content = content;
        this.avatar = avatar;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUsername() {
        return username;
    }

    public Spanned getContent() {
        return content;
    }

    public String getAvatar() {
        return avatar;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public String getRebloggedByUsername() {
        return rebloggedByUsername;
    }

    public void setRebloggedByUsername(String name) {
        rebloggedByUsername = name;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this.id == null) {
            return this == other;
        } else if (!(other instanceof Status)) {
            return false;
        }
        Status status = (Status) other;
        return status.id.equals(this.id);
    }
}
