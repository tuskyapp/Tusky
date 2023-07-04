package com.keylesspalace.tusky.components.domainblocks

import androidx.paging.PagingSource
import androidx.paging.PagingState

class DomainBlocksPagingSource(private val viewModel: DomainBlocksViewModel) : PagingSource<String, String>() {
    override fun getRefreshKey(state: PagingState<String, String>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, String> {
        return if (params is LoadParams.Refresh) {
            LoadResult.Page(viewModel.domains.toList(), null, viewModel.nextKey)
        } else {
            LoadResult.Page(emptyList(), null, null)
        }
    }
}
