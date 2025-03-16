package com.keylesspalace.tusky.components.accountlist

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.TimelineAccount

class AccountListPagingSource(
    private val accounts: List<TimelineAccount>,
    private val nextKey: String?
) : PagingSource<String, TimelineAccount>() {
    override fun getRefreshKey(state: PagingState<String, TimelineAccount>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, TimelineAccount> {
        return if (params is LoadParams.Refresh) {
            LoadResult.Page(accounts, null, nextKey)
        } else {
            LoadResult.Page(emptyList(), null, null)
        }
    }
}
