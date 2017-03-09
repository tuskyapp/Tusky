package com.keylesspalace.tusky.entity;

import com.google.gson.annotations.SerializedName;

public class Relationship {
    public String id;

    public boolean following;

    @SerializedName("followed_by")
    public boolean followedBy;

    public boolean blocking;

    public boolean muting;

    public boolean requested;
}
