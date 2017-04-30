package com.keylesspalace.tusky.entity;

import com.google.gson.annotations.SerializedName;

public class Profile {
    @SerializedName("display_name")
    public String displayName;

    @SerializedName("note")
    public String note;

    /** Encoded in Base-64 */
    @SerializedName("avatar")
    public String avatar;

    /** Encoded in Base-64 */
    @SerializedName("header")
    public String header;
}
