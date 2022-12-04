package com.keylesspalace.tusky.util

import androidx.paging.PagingSource
import androidx.paging.PagingState

class EmptyPagingSource<T : Any> : PagingSource<Int, T>() {
    override fun getRefreshKey(state: PagingState<Int, T>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> = LoadResult.Page(emptyList(), null, null)
}
