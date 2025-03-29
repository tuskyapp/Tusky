package com.keylesspalace.tusky.components.accountlist

import androidx.paging.PagingSource
import androidx.paging.PagingState

class AccountListPagingSource(
    private val accounts: List<AccountViewData>,
    private val nextKey: String?
) : PagingSource<String, AccountViewData>() {
    override fun getRefreshKey(state: PagingState<String, AccountViewData>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, AccountViewData> {
        return if (params is LoadParams.Refresh) {
            LoadResult.Page(accounts, null, nextKey)
        } else {
            LoadResult.Page(emptyList(), null, null)
        }
    }
}
