package com.keylesspalace.tusky.components.timeline

import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.keylesspalace.tusky.components.timeline.NetworkTimelineRepository.Companion.makeEmptyPageCache
import com.keylesspalace.tusky.components.timeline.viewmodel.NetworkTimelinePagingSource
import com.keylesspalace.tusky.entity.Status
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class NetworkTimelinePagingSourceTest {
    @Test
    fun `load() with empty pages returns empty list`() = runTest {
        // Given
        val pages = makeEmptyPageCache()
        val pagingSource = NetworkTimelinePagingSource(pages)

        // When
        val loadResult = pagingSource.load(PagingSource.LoadParams.Refresh("0", 2, false))

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
        val pages = makeEmptyPageCache()
        pages["2"] = Page(data = mutableListOf(mockStatus(id = "2")), nextKey = "1")
        pages["1"] = Page(data = mutableListOf(mockStatus(id = "1")), nextKey = "0", prevKey = "2")
        pages["0"] = Page(data = mutableListOf(mockStatus(id = "0")), prevKey = "1")
        val pagingSource = NetworkTimelinePagingSource(pages)

        // When
        val loadResult = pagingSource.load(PagingSource.LoadParams.Refresh("1", 2, false))

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
    fun `Append returns the page after`() = runTest {
        // Given
        val pages = makeEmptyPageCache()
        pages["2"] = Page(data = mutableListOf(mockStatus(id = "2")), nextKey = "1")
        pages["1"] = Page(data = mutableListOf(mockStatus(id = "1")), nextKey = "0", prevKey = "2")
        pages["0"] = Page(data = mutableListOf(mockStatus(id = "0")), prevKey = "1")
        val pagingSource = NetworkTimelinePagingSource(pages)

        // When
        val loadResult = pagingSource.load(PagingSource.LoadParams.Append("1", 2, false))

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
    fun `Prepend returns the page before`() = runTest {
        // Given
        val pages = makeEmptyPageCache()
        pages["2"] = Page(data = mutableListOf(mockStatus(id = "2")), nextKey = "1")
        pages["1"] = Page(data = mutableListOf(mockStatus(id = "1")), nextKey = "0", prevKey = "2")
        pages["0"] = Page(data = mutableListOf(mockStatus(id = "0")), prevKey = "1")
        val pagingSource = NetworkTimelinePagingSource(pages)

        // When
        val loadResult = pagingSource.load(PagingSource.LoadParams.Prepend("1", 2, false))

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
    fun `Refresh with null key returns the latest page`() = runTest {
        // Given
        val pages = makeEmptyPageCache()
        pages["2"] = Page(data = mutableListOf(mockStatus(id = "2")), nextKey = "1")
        pages["1"] = Page(data = mutableListOf(mockStatus(id = "1")), nextKey = "0", prevKey = "2")
        pages["0"] = Page(data = mutableListOf(mockStatus(id = "0")), prevKey = "1")
        val pagingSource = NetworkTimelinePagingSource(pages)

        // When
        val loadResult = pagingSource.load(PagingSource.LoadParams.Refresh<String>(null, 2, false))

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

    @Test
    fun `Append with a too-old key returns empty list`() = runTest {
        // Given
        val pages = makeEmptyPageCache()
        pages["20"] = Page(data = mutableListOf(mockStatus(id = "20")), nextKey = "10")
        pages["10"] = Page(data = mutableListOf(mockStatus(id = "10")), prevKey = "20")
        val pagingSource = NetworkTimelinePagingSource(pages)

        // When
        val loadResult = pagingSource.load(PagingSource.LoadParams.Append("9", 2, false))

        // Then
        assertThat(loadResult).isInstanceOf(LoadResult.Page::class.java)
        assertThat((loadResult as? LoadResult.Page))
            .isEqualTo(
                LoadResult.Page(
                    // No page contains key="9" (oldest is key="10"), so empty list
                    data = emptyList(),
                    prevKey = null,
                    nextKey = null
                )
            )
    }

    @Test
    fun `Prepend with a too-new key returns empty list`() = runTest {
        // Given
        val pages = makeEmptyPageCache()
        pages["20"] = Page(data = mutableListOf(mockStatus(id = "20")), nextKey = "10")
        pages["10"] = Page(data = mutableListOf(mockStatus(id = "10")), prevKey = "20")
        val pagingSource = NetworkTimelinePagingSource(pages)

        // When
        val loadResult = pagingSource.load(PagingSource.LoadParams.Prepend("10", 2, false))

        // Then
        assertThat(loadResult).isInstanceOf(LoadResult.Page::class.java)
        assertThat((loadResult as? LoadResult.Page))
            .isEqualTo(
                LoadResult.Page(
                    // No page contains key="9" (oldest is key="10"), so empty list
                    data = emptyList(),
                    prevKey = null,
                    nextKey = null
                )
            )
    }
}
