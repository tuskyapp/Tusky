package com.keylesspalace.tusky.components.filters

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.FilterKeyword
import com.keylesspalace.tusky.network.MastodonApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject

class EditFilterViewModel @Inject constructor(val api: MastodonApi, val eventHub: EventHub) : ViewModel() {
    private var originalFilter: Filter? = null
    val title = MutableStateFlow("")
    val keywords = MutableStateFlow(listOf<FilterKeyword>())
    val action = MutableStateFlow(Filter.Action.WARN)
    val duration = MutableStateFlow(0)
    val contexts = MutableStateFlow(listOf<Filter.Kind>())

    fun load(filter: Filter) {
        originalFilter = filter
        title.value = filter.title
        keywords.value = filter.keywords
        action.value = filter.action
        duration.value = if (filter.expiresAt == null) {
            0
        } else {
            -1
        }
        contexts.value = filter.kinds
    }

    fun addKeyword(keyword: FilterKeyword) {
        keywords.value += keyword
    }

    fun deleteKeyword(keyword: FilterKeyword) {
        keywords.value = keywords.value.filterNot { it == keyword }
    }

    fun modifyKeyword(original: FilterKeyword, updated: FilterKeyword) {
        val index = keywords.value.indexOf(original)
        if (index >= 0) {
            keywords.value = keywords.value.toMutableList().apply {
                set(index, updated)
            }
        }
    }

    fun setTitle(title: String) {
        this.title.value = title
    }

    fun setDuration(index: Int) {
        duration.value = index
    }

    fun setAction(action: Filter.Action) {
        this.action.value = action
    }

    fun addContext(context: Filter.Kind) {
        if (!contexts.value.contains(context)) {
            contexts.value += context
        }
    }

    fun removeContext(context: Filter.Kind) {
        contexts.value = contexts.value.filter { it != context }
    }

    fun validate(): Boolean {
        return title.value.isNotBlank() &&
            keywords.value.isNotEmpty() &&
            contexts.value.isNotEmpty()
    }

    suspend fun saveChanges(context: Context): Boolean {
        val contexts = contexts.value.map { it.kind }
        val title = title.value
        val durationIndex = duration.value
        val action = action.value.action

        return withContext(viewModelScope.coroutineContext) {
            originalFilter?.let { filter ->
                updateFilter(filter, title, contexts, action, durationIndex, context)
            } ?: createFilter(title, contexts, action, durationIndex, context)
        }
    }

    private suspend fun createFilter(title: String, contexts: List<String>, action: String, durationIndex: Int, context: Context): Boolean {
        val expiresInSeconds = EditFilterActivity.getSecondsForDurationIndex(durationIndex, context)
        api.createFilter(
            title = title,
            context = contexts,
            filterAction = action,
            expiresInSeconds = expiresInSeconds
        ).fold(
            { newFilter ->
                // This is _terrible_, but the all-in-one update filter api Just Doesn't Work
                return keywords.value.map { keyword ->
                    api.addFilterKeyword(filterId = newFilter.id, keyword = keyword.keyword, wholeWord = keyword.wholeWord)
                }.none { it.isFailure }
            },
            { throwable ->
                return (
                    throwable is HttpException && throwable.code() == 404 &&
                        // Endpoint not found, fall back to v1 api
                        createFilterV1(contexts, expiresInSeconds)
                    )
            }
        )
    }

    private suspend fun updateFilter(originalFilter: Filter, title: String, contexts: List<String>, action: String, durationIndex: Int, context: Context): Boolean {
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
                val results = keywords.value.map { keyword ->
                    if (keyword.id.isEmpty()) {
                        api.addFilterKeyword(filterId = originalFilter.id, keyword = keyword.keyword, wholeWord = keyword.wholeWord)
                    } else {
                        api.updateFilterKeyword(keywordId = keyword.id, keyword = keyword.keyword, wholeWord = keyword.wholeWord)
                    }
                } + originalFilter.keywords.filter { keyword ->
                    // Deleted keywords
                    keywords.value.none { it.id == keyword.id }
                }.map { api.deleteFilterKeyword(it.id) }

                return results.none { it.isFailure }
            },
            { throwable ->
                if (throwable is HttpException && throwable.code() == 404) {
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
        return keywords.value.map { keyword ->
            api.createFilterV1(keyword.keyword, context, false, keyword.wholeWord, expiresInSeconds)
        }.none { it.isFailure }
    }

    private suspend fun updateFilterV1(context: List<String>, expiresInSeconds: Int?): Boolean {
        val results = keywords.value.map { keyword ->
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
