package com.keylesspalace.tusky.components.followedtags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.network.MastodonApi
import javax.inject.Inject

class FollowedTagsViewModel @Inject constructor (
    api: MastodonApi
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
        },
    ).flow.cachedIn(viewModelScope)
}
