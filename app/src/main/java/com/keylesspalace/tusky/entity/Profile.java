package com.keylesspalace.tusky.entity;

import com.google.gson.annotations.SerializedName;

public class Profile {
    @SerializedName("display_name")
    public String displayName;

    @SerializedName("note")
    public String note;

    @SerializedName("avatar")
    public String avatar;

    @SerializedName("header")
    public String header;
}
