package com.keylesspalace.tusky.entity;

import com.google.gson.annotations.SerializedName;

public class Media {
    public String id;

    public String type;

    public String url;

    @SerializedName("preview_url")
    public String previewUrl;

    @SerializedName("text_url")
    public String textUrl;
}
