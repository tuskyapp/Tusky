/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.entity;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

public class Attachment {
    public String id;

    public String url;

    @SerializedName("preview_url")
    public String previewUrl;

    @SerializedName("text_url")
    public String textUrl;

    public Type type;

    public String description;

    public static class Meta {
        public MediaProperties original;
        public MediaProperties small;
    }

    public static class MediaProperties {
        public int width;
        public int height;
        public float aspect;
    }

    @JsonAdapter(MediaTypeDeserializer.class)
    public enum Type {
        @SerializedName("image")
        IMAGE,
        @SerializedName("gifv")
        GIFV,
        @SerializedName("video")
        VIDEO,
        @SerializedName("unknown")
        UNKNOWN
    }

    static class MediaTypeDeserializer implements JsonDeserializer<Type> {
        @Override
        public Type deserialize(JsonElement json, java.lang.reflect.Type classOfT, JsonDeserializationContext context)
                throws JsonParseException {
            switch(json.toString()) {
                case "\"image\"":
                    return Type.IMAGE;
                case "\"gifv\"":
                    return Type.GIFV;
                case "\"video\"":
                    return Type.VIDEO;
                default:
                    return Type.UNKNOWN;
            }
        }
    }
}
