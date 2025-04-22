package com.keylesspalace.tusky.components.domainblocks

import androidx.paging.PagingSource
import androidx.paging.PagingState

class DomainBlocksPagingSource(
    private val domains: List<String>,
    private val nextKey: String?
) : PagingSource<String, String>() {
    override fun getRefreshKey(state: PagingState<String, String>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, String> = if (params is LoadParams.Refresh) {
        LoadResult.Page(domains, null, nextKey)
    } else {
        LoadResult.Page(emptyList(), null, null)
    }
}
