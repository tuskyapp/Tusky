/* Copyright 2023 Tusky Contributors
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

import java.util.Date

/**
 * Mastodon API Documentation: https://docs.joinmastodon.org/methods/trends/#tags
 *
 * @param name The name of the hashtag (after the #). The "caturday" in "#caturday".
 * @param url The URL to your mastodon instance list for this hashtag.
 * @param history A list of [TrendingTagHistory]. Each element contains metrics per day for this hashtag.
 * @param following This is not listed in the APIs at the time of writing, but an instance is delivering it.
 */
data class TrendingTag(
    val name: String,
    val url: String,
    val history: List<TrendingTagHistory>,
    val following: Boolean,
)

/**
 * Mastodon API Documentation: https://docs.joinmastodon.org/methods/trends/#tags
 *
 * @param day The day that this was posted in Unix Epoch Seconds.
 * @param accounts The number of accounts that have posted with this hashtag.
 * @param uses The number of posts with this hashtag.
 */
data class TrendingTagHistory(
    val day: String,
    val accounts: String,
    val uses: String,
)

fun TrendingTag.start() = Date(history.last().day.toLong() * 1000L)
fun TrendingTag.end() = Date(history.first().day.toLong() * 1000L)
