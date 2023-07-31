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

package com.keylesspalace.tusky.components.trending

import com.keylesspalace.tusky.network.MastodonApi
import javax.inject.Inject

class TrendingLinksRepository @Inject constructor(
    private val api: MastodonApi
) {
    // TODO: Shouldn't return an emptyList by default. If there's a network error
    // it should be returned so it can be surfaced to the user.
    suspend fun getTrendingLinks() = api.trendingLinks()
}
