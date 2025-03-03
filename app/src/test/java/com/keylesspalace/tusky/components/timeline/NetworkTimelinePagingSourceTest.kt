package com.keylesspalace.tusky.components.timeline

import androidx.paging.PagingSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keylesspalace.tusky.components.timeline.viewmodel.NetworkTimelinePagingSource
import com.keylesspalace.tusky.components.timeline.viewmodel.NetworkTimelineViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config

@Config(sdk = [34])
@RunWith(AndroidJUnit4::class)
class NetworkTimelinePagingSourceTest {

    private val status = fakeStatusViewData()

    private val timelineViewModel: NetworkTimelineViewModel = mock {
        on { statusData } doReturn mutableListOf(status)
    }

    @Test
    fun `should return empty list when params are Append`() = runTest {
        val pagingSource = NetworkTimelinePagingSource(timelineViewModel)

        val params = PagingSource.LoadParams.Append("132", 20, false)

        val expectedResult = PagingSource.LoadResult.Page(emptyList(), null, null)

        assertEquals(expectedResult, pagingSource.load(params))
    }

    @Test
    fun `should return empty list when params are Prepend`() = runTest {
        val pagingSource = NetworkTimelinePagingSource(timelineViewModel)

        val params = PagingSource.LoadParams.Prepend("132", 20, false)

        val expectedResult = PagingSource.LoadResult.Page(emptyList(), null, null)

        assertEquals(expectedResult, pagingSource.load(params))
    }

    @Test
    fun `should return full list when params are Refresh`() = runTest {
        val pagingSource = NetworkTimelinePagingSource(timelineViewModel)

        val params = PagingSource.LoadParams.Refresh<String>(null, 20, false)

        val expectedResult = PagingSource.LoadResult.Page(listOf(status), null, null)

        val result = pagingSource.load(params)
        assertEquals(expectedResult, result)
    }
}
