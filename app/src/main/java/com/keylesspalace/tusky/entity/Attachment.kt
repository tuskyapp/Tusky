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

package com.keylesspalace.tusky.entity

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

@JsonClass(generateAdapter = true)
@Parcelize
data class Attachment(
    val id: String,
    val url: String,
    // can be null for e.g. audio attachments
    @Json(name = "preview_url") val previewUrl: String? = null,
    // null when local attachment
    @Json(name = "remote_url") val remoteUrl: String? = null,
    val meta: MetaData? = null,
    val type: Type,
    val description: String? = null,
    val blurhash: String? = null
) : Parcelable {

    /** The url to open for attachments of unknown type */
    val unknownUrl: String
        get() = remoteUrl ?: url

    @JsonClass(generateAdapter = false)
    enum class Type {
        @Json(name = "image")
        IMAGE,

        @Json(name = "gifv")
        GIFV,

        @Json(name = "video")
        VIDEO,

        @Json(name = "audio")
        AUDIO,

        UNKNOWN
    }

    /**
     * The meta data of an [Attachment].
     */
    @JsonClass(generateAdapter = true)
    @Parcelize
    data class MetaData(
        val focus: Focus? = null,
        val duration: Float? = null,
        val original: Size? = null,
        val small: Size? = null
    ) : Parcelable

    /**
     * The Focus entity, used to specify the focal point of an image.
     *
     * See here for more details what the x and y mean:
     *   https://github.com/jonom/jquery-focuspoint#1-calculate-your-images-focus-point
     */
    @JsonClass(generateAdapter = true)
    @Parcelize
    data class Focus(
        val x: Float?,
        val y: Float?
    ) : Parcelable {
        fun toMastodonApiString(): String = "$x,$y"
    }

    /**
     * The size of an image, used to specify the width/height.
     */
    @JsonClass(generateAdapter = true)
    @Parcelize
    data class Size(
        val width: Int = 0,
        val height: Int = 0,
        val aspect: Double = 0.0
    ) : Parcelable
}
