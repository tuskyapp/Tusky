package com.keylesspalace.tusky.components.followedtags

import androidx.paging.PagingSource
import androidx.paging.PagingState

class FollowedTagsPagingSource(private val viewModel: FollowedTagsViewModel) : PagingSource<String, String>() {
    override fun getRefreshKey(state: PagingState<String, String>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, String> {
        return if (params is LoadParams.Refresh) {
            LoadResult.Page(viewModel.tags.map { it.name }, null, viewModel.nextKey)
        } else {
            LoadResult.Page(emptyList(), null, null)
        }
    }
}
