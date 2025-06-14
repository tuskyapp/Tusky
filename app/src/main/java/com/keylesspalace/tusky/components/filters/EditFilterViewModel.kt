/* Copyright 2024 Tusky contributors
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

package com.keylesspalace.tusky.components.filters

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.FilterKeyword
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.isHttpNotFound
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@HiltViewModel
class EditFilterViewModel @Inject constructor(val api: MastodonApi) : ViewModel() {
    private var originalFilter: Filter? = null

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _keywords = MutableStateFlow(listOf<FilterKeyword>())
    val keywords: StateFlow<List<FilterKeyword>> = _keywords.asStateFlow()

    private val _action = MutableStateFlow(Filter.Action.WARN)
    val action: StateFlow<Filter.Action> = _action.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    private val _contexts = MutableStateFlow(listOf<Filter.Kind>())
    val contexts: StateFlow<List<Filter.Kind>> = _contexts.asStateFlow()

    fun load(filter: Filter) {
        originalFilter = filter
        _title.value = filter.title
        _keywords.value = filter.keywords
        _action.value = filter.action
        _duration.value = if (filter.expiresAt == null) {
            0
        } else {
            -1
        }
        _contexts.value = filter.context
    }

    fun addKeyword(keyword: FilterKeyword) {
        _keywords.value += keyword
    }

    fun deleteKeyword(keyword: FilterKeyword) {
        _keywords.value = _keywords.value.filterNot { it == keyword }
    }

    fun modifyKeyword(original: FilterKeyword, updated: FilterKeyword) {
        val index = _keywords.value.indexOf(original)
        if (index >= 0) {
            _keywords.value = _keywords.value.toMutableList().apply {
                set(index, updated)
            }
        }
    }

    fun setTitle(title: String) {
        this._title.value = title
    }

    fun setDuration(index: Int) {
        _duration.value = index
    }

    fun setAction(action: Filter.Action) {
        this._action.value = action
    }

    fun addContext(context: Filter.Kind) {
        if (!_contexts.value.contains(context)) {
            _contexts.value += context
        }
    }

    fun removeContext(context: Filter.Kind) {
        _contexts.value = _contexts.value.filter { it != context }
    }

    fun validate(): Boolean {
        return _title.value.isNotBlank() &&
            _keywords.value.isNotEmpty() &&
            _contexts.value.isNotEmpty()
    }

    suspend fun saveChanges(context: Context): Boolean {
        val contexts = _contexts.value
        val title = _title.value
        val durationIndex = _duration.value
        val action = _action.value

        return withContext(viewModelScope.coroutineContext) {
            originalFilter?.let { filter ->
                updateFilter(filter, title, contexts, action, durationIndex, context)
            } ?: createFilter(title, contexts, action, durationIndex, context)
        }
    }

    private suspend fun createFilter(
        title: String,
        contexts: List<Filter.Kind>,
        action: Filter.Action,
        durationIndex: Int,
        context: Context
    ): Boolean {
        val expiration = getExpirationForDurationIndex(durationIndex, context)
        api.createFilter(
            title = title,
            context = contexts,
            filterAction = action,
            expiresIn = expiration
        ).fold(
            { newFilter ->
                // This is _terrible_, but the all-in-one update filter api Just Doesn't Work
                return _keywords.value.map { keyword ->
                    api.addFilterKeyword(
                        filterId = newFilter.id,
                        keyword = keyword.keyword,
                        wholeWord = keyword.wholeWord
                    )
                }.none { it.isFailure }
            },
            { throwable ->
                return (
                    throwable.isHttpNotFound() &&
                        // Endpoint not found, fall back to v1 api
                        createFilterV1(contexts.map(Filter.Kind::kind), expiration)
                    )
            }
        )
    }

    private suspend fun updateFilter(
        originalFilter: Filter,
        title: String,
        contexts: List<Filter.Kind>,
        action: Filter.Action,
        durationIndex: Int,
        context: Context
    ): Boolean {
        val expiration = getExpirationForDurationIndex(durationIndex, context)
        api.updateFilter(
            id = originalFilter.id,
            title = title,
            context = contexts,
            filterAction = action,
            expires = expiration
        ).fold(
            {
                // This is _terrible_, but the all-in-one update filter api Just Doesn't Work
                val results = _keywords.value.map { keyword ->
                    if (keyword.id.isEmpty()) {
                        api.addFilterKeyword(filterId = originalFilter.id, keyword = keyword.keyword, wholeWord = keyword.wholeWord)
                    } else {
                        api.updateFilterKeyword(keywordId = keyword.id, keyword = keyword.keyword, wholeWord = keyword.wholeWord)
                    }
                } + originalFilter.keywords.filter { keyword ->
                    // Deleted keywords
                    _keywords.value.none { it.id == keyword.id }
                }.map { api.deleteFilterKeyword(it.id) }

                return results.none { it.isFailure }
            },
            { throwable ->
                if (throwable.isHttpNotFound()) {
                    // Endpoint not found, fall back to v1 api
                    if (updateFilterV1(contexts.map(Filter.Kind::kind), expiration)) {
                        return true
                    }
                }
                return false
            }
        )
    }

    private suspend fun createFilterV1(context: List<String>, expiration: FilterExpiration?): Boolean {
        return _keywords.value.map { keyword ->
            api.createFilterV1(keyword.keyword, context, false, keyword.wholeWord, expiration)
        }.none { it.isFailure }
    }

    private suspend fun updateFilterV1(context: List<String>, expiration: FilterExpiration?): Boolean {
        val results = _keywords.value.map { keyword ->
            if (originalFilter == null) {
                api.createFilterV1(
                    phrase = keyword.keyword,
                    context = context,
                    irreversible = false,
                    wholeWord = keyword.wholeWord,
                    expiresIn = expiration
                )
            } else {
                api.updateFilterV1(
                    id = originalFilter!!.id,
                    phrase = keyword.keyword,
                    context = context,
                    irreversible = false,
                    wholeWord = keyword.wholeWord,
                    expiresIn = expiration
                )
            }
        }
        // Don't handle deleted keywords here because there's only one keyword per v1 filter anyway

        return results.none { it.isFailure }
    }

    companion object {
        // Mastodon *stores* the absolute date in the filter,
        // but create/edit take a number of seconds (relative to the time the operation is posted)
        private fun getExpirationForDurationIndex(index: Int, context: Context): FilterExpiration? {
            return when (index) {
                -1 -> FilterExpiration.unchanged
                0 -> FilterExpiration.never
                else -> FilterExpiration.seconds(
                    context.resources.getIntArray(R.array.filter_duration_values)[index]
                )
            }
        }
    }
}
