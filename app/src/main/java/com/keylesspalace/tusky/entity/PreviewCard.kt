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

import com.keylesspalace.tusky.json.Guarded
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class PreviewCard(
    val url: String,
    val title: String,
    val description: String = "",
    val authors: List<PreviewCardAuthor> = emptyList(),
    @Json(name = "author_name") val authorName: String? = null,
    @Json(name = "provider_name") val providerName: String? = null,
    // sometimes this date is invalid https://github.com/tuskyapp/Tusky/issues/4992
    @Json(name = "published_at") @Guarded val publishedAt: Date?,
    val image: String? = null,
    val type: String,
    val width: Int = 0,
    val height: Int = 0,
    val blurhash: String? = null,
    @Json(name = "embed_url") val embedUrl: String? = null
) {

    override fun hashCode() = url.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other !is PreviewCard) {
            return false
        }
        return other.url == this.url
    }

    companion object {
        const val TYPE_PHOTO = "photo"
    }
}

@JsonClass(generateAdapter = true)
data class PreviewCardAuthor(
    val name: String,
    val url: String,
    val account: TimelineAccount?
)
