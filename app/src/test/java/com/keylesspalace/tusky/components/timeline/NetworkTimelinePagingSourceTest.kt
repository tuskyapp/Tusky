package com.keylesspalace.tusky.components.timeline

import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.keylesspalace.tusky.components.timeline.viewmodel.NetworkTimelinePagingSource
import com.keylesspalace.tusky.entity.Status
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.TreeMap

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class NetworkTimelinePagingSourceTest {
    @Test
    fun `load() with empty pages returns empty list`() = runTest {
        // Given
        val pages = TreeMap<String, Page<String, Status>>()
        val pagingSource = NetworkTimelinePagingSource(pages)
        val loadingParams = PagingSource.LoadParams.Refresh("0", 2, false)

        // When
        val loadResult = pagingSource.load(loadingParams)

        // Then
        assertThat(loadResult).isInstanceOf(LoadResult.Page::class.java)
        assertThat((loadResult as? LoadResult.Page))
            .isEqualTo(
                LoadResult.Page(
                    data = emptyList<Status>(),
                    prevKey = null,
                    nextKey = null
                )
            )
    }

    @Test
    fun `load() for an item in a page returns the page containing that item and next, prev keys`() = runTest {
        // Given
        val pages = TreeMap<String, Page<String, Status>>()
        pages["2"] = Page(data = mutableListOf(mockStatus(id = "2")), nextKey = "1")
        pages["1"] = Page(data = mutableListOf(mockStatus(id = "1")), nextKey = "0", prevKey = "2")
        pages["0"] = Page(data = mutableListOf(mockStatus(id = "0")), prevKey = "1")
        val pagingSource = NetworkTimelinePagingSource(pages)
        val loadingParams = PagingSource.LoadParams.Refresh("1", 2, false)

        // When
        val loadResult = pagingSource.load(loadingParams)

        // Then
        assertThat(loadResult).isInstanceOf(LoadResult.Page::class.java)
        assertThat((loadResult as? LoadResult.Page))
            .isEqualTo(
                LoadResult.Page(
                    data = listOf(mockStatus(id = "1")),
                    prevKey = "2",
                    nextKey = "0"
                )
            )
    }

    @Test
    fun `LoadParams Append returns the page after`() = runTest {
        // Given
        val pages = TreeMap<String, Page<String, Status>>()
        pages["2"] = Page(data = mutableListOf(mockStatus(id = "2")), nextKey = "1")
        pages["1"] = Page(data = mutableListOf(mockStatus(id = "1")), nextKey = "0", prevKey = "2")
        pages["0"] = Page(data = mutableListOf(mockStatus(id = "0")), prevKey = "1")
        val pagingSource = NetworkTimelinePagingSource(pages)
        val loadingParams = PagingSource.LoadParams.Append("1", 2, false)

        // When
        val loadResult = pagingSource.load(loadingParams)

        // Then
        assertThat(loadResult).isInstanceOf(LoadResult.Page::class.java)
        assertThat((loadResult as? LoadResult.Page))
            .isEqualTo(
                LoadResult.Page(
                    data = listOf(mockStatus(id = "0")),
                    prevKey = "1",
                    nextKey = null
                )
            )
    }

    @Test
    fun `LoadParams Prepend returns the page before`() = runTest {
        // Given
        val pages = TreeMap<String, Page<String, Status>>()
        pages["2"] = Page(data = mutableListOf(mockStatus(id = "2")), nextKey = "1")
        pages["1"] = Page(data = mutableListOf(mockStatus(id = "1")), nextKey = "0", prevKey = "2")
        pages["0"] = Page(data = mutableListOf(mockStatus(id = "0")), prevKey = "1")
        val pagingSource = NetworkTimelinePagingSource(pages)
        val loadingParams = PagingSource.LoadParams.Prepend("1", 2, false)

        // When
        val loadResult = pagingSource.load(loadingParams)

        // Then
        assertThat(loadResult).isInstanceOf(LoadResult.Page::class.java)
        assertThat((loadResult as? LoadResult.Page))
            .isEqualTo(
                LoadResult.Page(
                    data = listOf(mockStatus(id = "2")),
                    prevKey = null,
                    nextKey = "1"
                )
            )
    }
}
