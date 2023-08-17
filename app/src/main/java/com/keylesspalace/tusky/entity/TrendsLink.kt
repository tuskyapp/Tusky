/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.entity

import com.google.gson.annotations.SerializedName

enum class PreviewCardKind {
    @SerializedName("link")
    LINK,

    @SerializedName("photo")
    PHOTO,

    @SerializedName("video")
    VIDEO,

    @SerializedName("rich")
    RICH
}

/**
 * Representation of a Mastodon [PreviewCard](https://docs.joinmastodon.org/entities/PreviewCard/)
 */
interface PreviewCard {
    val url: String
    val title: String
    val description: String
    val kind: PreviewCardKind
    val authorName: String
    val authorUrl: String
    val providerName: String
    val providerUrl: String
    val html: String
    val width: Int
    val height: Int
    val image: String?
    val embedUrl: String
    val blurhash: String?
}

data class LinkHistory(
    val day: String,
    val accounts: Int,
    val uses: Int
)

data class TrendsLink(
    override val url: String,
    override val title: String,
    override val description: String,
    @SerializedName("type") override val kind: PreviewCardKind,
    @SerializedName("author_name") override val authorName: String,
    @SerializedName("author_url") override val authorUrl: String,
    @SerializedName("provider_name") override val providerName: String,
    @SerializedName("provider_url") override val providerUrl: String,
    override val html: String,
    override val width: Int,
    override val height: Int,
    override val image: String? = null,
    @SerializedName("embed_url") override val embedUrl: String,
    override val blurhash: String? = null,
    val history: List<LinkHistory>
) : PreviewCard
