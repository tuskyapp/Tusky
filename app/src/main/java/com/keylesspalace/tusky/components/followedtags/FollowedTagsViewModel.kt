package com.keylesspalace.tusky.components.followedtags

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.components.compose.ComposeAutoCompleteAdapter
import com.keylesspalace.tusky.components.search.SearchType
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.network.MastodonApi
import javax.inject.Inject

class FollowedTagsViewModel @Inject constructor(
    private val api: MastodonApi
) : ViewModel(), Injectable {
    val tags: MutableList<HashTag> = mutableListOf()
    var nextKey: String? = null
    var currentSource: FollowedTagsPagingSource? = null

    @OptIn(ExperimentalPagingApi::class)
    val pager = Pager(
        config = PagingConfig(pageSize = 100),
        remoteMediator = FollowedTagsRemoteMediator(api, this),
        pagingSourceFactory = {
            FollowedTagsPagingSource(
                viewModel = this
            ).also { source ->
                currentSource = source
            }
        }
    ).flow.cachedIn(viewModelScope)

    fun searchAutocompleteSuggestions(token: String): List<ComposeAutoCompleteAdapter.AutocompleteResult> {
        return api.searchSync(query = token, type = SearchType.Hashtag.apiParameter, limit = 10)
            .fold({ searchResult ->
                searchResult.hashtags.map { ComposeAutoCompleteAdapter.AutocompleteResult.HashtagResult(it.name) }
            }, { e ->
                Log.e(TAG, "Autocomplete search for $token failed.", e)
                emptyList()
            })
    }

    companion object {
        private const val TAG = "FollowedTagsViewModel"
    }
}
