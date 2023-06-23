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

package com.keylesspalace.tusky.components.timeline.viewmodel

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.components.timeline.Page
import com.keylesspalace.tusky.entity.Status
import java.util.TreeMap
import javax.inject.Inject

/** [PagingSource] for Mastodon Status, identified by the Status ID */
class NetworkTimelinePagingSource @Inject constructor(
    private val pages: TreeMap<String, Page<String, Status>>
) : PagingSource<String, Status>() {

    override suspend fun load(params: LoadParams<String>): LoadResult<String, Status> {
        Log.d(TAG, "load() with ${params.javaClass.simpleName} for key: ${params.key}")

        if (BuildConfig.DEBUG) {
            synchronized(pages) {
                Log.d(TAG, "Pages state:")
                if (pages.isEmpty()) {
                    Log.d(TAG, "  **empty**")
                } else {
                    pages.onEachIndexed { i, entry ->
                        Log.d(
                            TAG,
                            "  $i: k: ${entry.key}, prev: ${entry.value.prevKey}, next: ${entry.value.nextKey}"
                        )
                    }
                }
            }
        }

        val page = synchronized(pages) {
            if (pages.isEmpty()) {
                return@synchronized null
            }

            return@synchronized when (params) {
                is LoadParams.Refresh -> {
                    // If no key then return the latest page. Otherwise return the requested page.
                    if (params.key == null) {
                        pages.lastEntry()?.value
                    } else {
                        pages[params.key]
                    }
                }
                is LoadParams.Append -> {
                    pages.lowerEntry(params.key)?.value
                }
                is LoadParams.Prepend -> {
                    pages.higherEntry(params.key)?.value
                }
            }
        }

        if (page == null) {
            Log.d(TAG, "  Returning empty page")
        } else {
            Log.d(TAG, "  Returning full page:")
            Log.d(TAG, "     k: ${page.prevKey}, prev: ${page.prevKey}, next: ${page.nextKey}")

        }
        val result = LoadResult.Page(page?.data ?: emptyList(), nextKey = page?.nextKey, prevKey = page?.prevKey)
        Log.d(TAG, "  result: $result")
        return LoadResult.Page(page?.data ?: emptyList(), nextKey = page?.nextKey, prevKey = page?.prevKey)
    }

    override fun getRefreshKey(state: PagingState<String, Status>): String? {
        Log.d(TAG, "getRefreshKey(): anchorPosition: ${state.anchorPosition}")
        val refreshKey = state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey
        }
        Log.d(TAG, "  refreshKey = $refreshKey")
        return refreshKey
    }

    companion object {
        private const val TAG = "NetworkTimelinePagingSource"
    }
}
