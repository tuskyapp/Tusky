package com.keylesspalace.tusky.entity;

public class Relationship {
    public boolean isFollowing() {
        return following;
    }

    public void setFollowing(boolean following) {
        this.following = following;
    }

    public boolean isFollowed_by() {
        return followed_by;
    }

    public void setFollowed_by(boolean followed_by) {
        this.followed_by = followed_by;
    }

    public boolean isBlocking() {
        return blocking;
    }

    public void setBlocking(boolean blocking) {
        this.blocking = blocking;
    }

    public boolean isMuting() {
        return muting;
    }

    public void setMuting(boolean muting) {
        this.muting = muting;
    }

    public boolean isRequested() {
        return requested;
    }

    public void setRequested(boolean requested) {
        this.requested = requested;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    int id;
    boolean following;
    boolean followed_by;
    boolean blocking;
    boolean muting;
    boolean requested;
}
