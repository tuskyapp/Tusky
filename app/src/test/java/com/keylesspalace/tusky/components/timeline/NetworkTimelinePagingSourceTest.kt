package com.keylesspalace.tusky.components.timeline

import androidx.paging.PagingSource
import com.keylesspalace.tusky.components.timeline.viewmodel.NetworkTimelinePagingSource
import com.keylesspalace.tusky.components.timeline.viewmodel.NetworkTimelineViewModel
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkTimelinePagingSourceTest {

    private val status = mockStatusViewData()

    private val timelineViewModel: NetworkTimelineViewModel = mock {
        on { statusData } doReturn mutableListOf(status)
    }

    @Test
    fun `should return empty list when params are Append`() {
        val pagingSource = NetworkTimelinePagingSource(timelineViewModel)

        val params = PagingSource.LoadParams.Append("132", 20, false)

        val expectedResult = PagingSource.LoadResult.Page(emptyList(), null, null)

        runBlocking {
            assertEquals(expectedResult, pagingSource.load(params))
        }
    }

    @Test
    fun `should return empty list when params are Prepend`() {
        val pagingSource = NetworkTimelinePagingSource(timelineViewModel)

        val params = PagingSource.LoadParams.Prepend("132", 20, false)

        val expectedResult = PagingSource.LoadResult.Page(emptyList(), null, null)

        runBlocking {
            assertEquals(expectedResult, pagingSource.load(params))
        }
    }

    @Test
    fun `should return full list when params are Refresh`() {
        val pagingSource = NetworkTimelinePagingSource(timelineViewModel)

        val params = PagingSource.LoadParams.Refresh<String>(null, 20, false)

        val expectedResult = PagingSource.LoadResult.Page(listOf(status), null, null)

        runBlocking {
            val result = pagingSource.load(params)
            assertEquals(expectedResult, result)
        }
    }
}
