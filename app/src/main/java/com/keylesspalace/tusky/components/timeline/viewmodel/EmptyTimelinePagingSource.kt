package com.keylesspalace.tusky.components.timeline.viewmodel

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.keylesspalace.tusky.db.TimelineStatusWithAccount

class EmptyTimelinePagingSource : PagingSource<Int, TimelineStatusWithAccount>() {
    override fun getRefreshKey(state: PagingState<Int, TimelineStatusWithAccount>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TimelineStatusWithAccount> = LoadResult.Page(emptyList(), null, null)
}
