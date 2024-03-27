package com.keylesspalace.tusky.components.filters

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.FilterKeyword
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.isHttpNotFound
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class EditFilterViewModel @Inject constructor(val api: MastodonApi, val eventHub: EventHub) : ViewModel() {
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
        _contexts.value = filter.kinds
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
        val contexts = _contexts.value.map { it.kind }
        val title = _title.value
        val durationIndex = _duration.value
        val action = _action.value.action

        return withContext(viewModelScope.coroutineContext) {
            originalFilter?.let { filter ->
                updateFilter(filter, title, contexts, action, durationIndex, context)
            } ?: createFilter(title, contexts, action, durationIndex, context)
        }
    }

    private suspend fun createFilter(
        title: String,
        contexts: List<String>,
        action: String,
        durationIndex: Int,
        context: Context
    ): Boolean {
        val expiresInSeconds = EditFilterActivity.getSecondsForDurationIndex(durationIndex, context)
        api.createFilter(
            title = title,
            context = contexts,
            filterAction = action,
            expiresInSeconds = expiresInSeconds
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
                        createFilterV1(contexts, expiresInSeconds)
                    )
            }
        )
    }

    private suspend fun updateFilter(
        originalFilter: Filter,
        title: String,
        contexts: List<String>,
        action: String,
        durationIndex: Int,
        context: Context
    ): Boolean {
        val expiresInSeconds = EditFilterActivity.getSecondsForDurationIndex(durationIndex, context)
        api.updateFilter(
            id = originalFilter.id,
            title = title,
            context = contexts,
            filterAction = action,
            expiresInSeconds = expiresInSeconds
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
                    if (updateFilterV1(contexts, expiresInSeconds)) {
                        return true
                    }
                }
                return false
            }
        )
    }

    private suspend fun createFilterV1(context: List<String>, expiresInSeconds: Int?): Boolean {
        return _keywords.value.map { keyword ->
            api.createFilterV1(keyword.keyword, context, false, keyword.wholeWord, expiresInSeconds)
        }.none { it.isFailure }
    }

    private suspend fun updateFilterV1(context: List<String>, expiresInSeconds: Int?): Boolean {
        val results = _keywords.value.map { keyword ->
            if (originalFilter == null) {
                api.createFilterV1(
                    phrase = keyword.keyword,
                    context = context,
                    irreversible = false,
                    wholeWord = keyword.wholeWord,
                    expiresInSeconds = expiresInSeconds
                )
            } else {
                api.updateFilterV1(
                    id = originalFilter!!.id,
                    phrase = keyword.keyword,
                    context = context,
                    irreversible = false,
                    wholeWord = keyword.wholeWord,
                    expiresInSeconds = expiresInSeconds
                )
            }
        }
        // Don't handle deleted keywords here because there's only one keyword per v1 filter anyway

        return results.none { it.isFailure }
    }
}
