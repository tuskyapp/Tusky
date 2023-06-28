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

package com.keylesspalace.tusky.components.timeline.viewmodel

import android.util.Log
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.Links
import retrofit2.HttpException
import retrofit2.Response
import java.util.TreeMap
import kotlin.Result.Companion.failure
import kotlin.Result.Companion.success

/** A page of data from the Mastodon API */
data class Page constructor(
    /** Loaded data */
    val data: MutableList<Status>,
    /**
     * Key for previous page (newer results, PREPEND operation) if more data can be loaded in
     * that direction, `null` otherwise.
     */
    val prevKey: String? = null,
    /**
     * Key for next page (older results, APPEND operation) if more data can be loaded in that
     * direction, `null` otherwise.
     */
    val nextKey: String? = null
) {
    override fun toString() = "k: ${data.last().id}, prev: $prevKey, next: $nextKey, size: ${data.size}, range: ${data.first().id}..${data.last().id}"

    companion object {
        private const val TAG = "Page"

        fun tryFrom(response: Response<List<Status>>): Result<Page> {
            val statuses = response.body()
            if (!response.isSuccessful || statuses == null) {
                return failure(HttpException(response))
            }

            val links = Links.from(response.headers()["link"])
            Log.d(TAG, "  link: " + response.headers()["link"])
            Log.d(TAG, "  ${statuses.size} - # statuses loaded")

            return success(
                Page(
                    data = statuses.toMutableList(),
                    nextKey = links.next,
                    prevKey = links.prev
                )
            )
        }
    }
}

/**
 * Cache of pages from Mastodon API calls.
 *
 * Cache pages are identified by the ID of the **last** (smallest, oldest) key in the page.
 *
 * It's the last item, and not the first because a page may be incomplete. E.g,.
 * a prepend operation completes, and instead of loading pageSize items it loads
 * (pageSize - 10) items, because only (pageSize - 10) items were available at the
 * time of the API call.
 *
 * If the page was subsequently refreshed, *and* the ID of the first (newest) item
 * was used as the key then you might have two pages that contain overlapping
 * items.
 */
class PageCache : TreeMap<String, Page>(compareBy({ it.length }, { it })) {
    /**
     * Adds a new page to the cache or updates the existing page with the given key
     */
    fun upsert(page: Page) {
        val key = page.data.last().id

        Log.d(TAG, "Inserting new page:")
        Log.d(TAG, "  $page")

        this[key] = page

        // There should never be duplicate items across all the pages. Enforce this in debug mode.
        if (BuildConfig.DEBUG) {
            val ids = buildList {
                this.addAll(this@PageCache.map { entry -> entry.value.data.map { it.id } }.flatten())
            }
            val groups = ids.groupingBy { it }.eachCount().filter { it.value > 1 }
            if (groups.isNotEmpty()) {
                throw IllegalStateException("Duplicate item IDs in results!: $groups")
            }
        }
    }

    /**
     * Logs the current state of the cache
     */
    fun debug() {
        Log.d(TAG, "Page cache state:")
        if (this.isEmpty()) {
            Log.d(TAG, "  ** empty **")
        } else {
            this.onEachIndexed { index, entry ->
                Log.d(TAG, "  $index: ${entry.value}")
            }
        }
    }

    companion object {
        private const val TAG = "PageCache"
    }
}
