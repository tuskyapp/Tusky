/* Copyright 2021 Tusky Contributors
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

package com.keylesspalace.tusky.components.search.adapter

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.keylesspalace.tusky.components.search.SearchType
import com.keylesspalace.tusky.entity.SearchResult
import com.keylesspalace.tusky.network.MastodonApi
import kotlinx.coroutines.rx3.await

class SearchPagingSource<T : Any>(
    private val mastodonApi: MastodonApi,
    private val searchType: SearchType,
    private val searchRequest: String,
    private val initialItems: List<T>?,
    private val parser: (SearchResult) -> List<T>
) : PagingSource<Int, T>() {

    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        if (searchRequest.isEmpty()) {
            return LoadResult.Page(
                data = emptyList(),
                prevKey = null,
                nextKey = null
            )
        }

        if (params.key == null && !initialItems.isNullOrEmpty()) {
            return LoadResult.Page(
                data = initialItems.toList(),
                prevKey = null,
                nextKey = initialItems.size
            )
        }

        val currentKey = params.key ?: 0

        try {

            val data = mastodonApi.searchObservable(
                query = searchRequest,
                type = searchType.apiParameter,
                resolve = true,
                limit = params.loadSize,
                offset = currentKey,
                following = false
            ).await()

            val res = parser(data)

            val nextKey = if (res.isEmpty()) {
                null
            } else {
                currentKey + res.size
            }

            return LoadResult.Page(
                data = res,
                prevKey = null,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            return LoadResult.Error(e)
        }
    }
}
