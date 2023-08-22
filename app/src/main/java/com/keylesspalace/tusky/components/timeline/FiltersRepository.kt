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

package com.keylesspalace.tusky.components.timeline

import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.getOrThrow
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.FilterV1
import com.keylesspalace.tusky.network.MastodonApi
import retrofit2.HttpException
import javax.inject.Inject

sealed class FilterKind {
    /** API v1 filter, filtering happens client side */
    data class V1(val filters: List<FilterV1>) : FilterKind()

    /** API v2 filter, filtering happens server side */
    data class V2(val filters: List<Filter>) : FilterKind()
}

/** Repository for filter information */
class FiltersRepository @Inject constructor(
    private val mastodonApi: MastodonApi
) {
    /**
     * Get the current set of filters.
     *
     * Checks for server-side (v2) filters first. If that fails then fetches filters to
     * apply client-side.
     *
     * @throws HttpException if the requests fail
     */
    suspend fun getFilters(): FilterKind = mastodonApi.getFilters().fold(
        { filters -> FilterKind.V2(filters) },
        { throwable ->
            if (throwable is HttpException && throwable.code() == 404) {
                val filters = mastodonApi.getFiltersV1().getOrThrow()
                FilterKind.V1(filters)
            } else {
                throw throwable
            }
        }
    )

    companion object {
        private const val TAG = "FiltersRepository"
    }
}
