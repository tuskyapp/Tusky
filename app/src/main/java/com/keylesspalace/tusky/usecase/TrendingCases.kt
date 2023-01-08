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

package com.keylesspalace.tusky.usecase

import android.util.Log
import com.keylesspalace.tusky.entity.TrendingTag
import com.keylesspalace.tusky.network.MastodonApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Created by @knossos@fosstodon.org on 2023-01-07.
 */
class TrendingCases @Inject constructor(
    private val mastodonApi: MastodonApi,
) {
    suspend fun trendingTags(): List<TrendingTag> {
        val call = withContext(Dispatchers.IO) {
            mastodonApi.trendingTags()
        }

        call.exceptionOrNull()?.also { throw it }

        val tags = call.getOrNull() ?: listOf()

        Log.v(TAG, "Trending tags: ${tags.map { it.name }}")

        return tags
    }

    companion object {
        private const val TAG = "TrendingCases"
    }
}
