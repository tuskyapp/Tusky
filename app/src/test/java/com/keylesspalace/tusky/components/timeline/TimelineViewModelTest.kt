package com.keylesspalace.tusky.components.timeline

import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.network.TimelineCases
import com.keylesspalace.tusky.util.Either
import com.keylesspalace.tusky.util.ViewDataUtils
import com.keylesspalace.tusky.viewdata.StatusViewData
import com.nhaarman.mockitokotlin2.*
import io.reactivex.rxjava3.annotations.NonNull
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.observers.TestObserver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [29])
class TimelineViewModelTest {
    lateinit var timelineRepository: TimelineRepository
    lateinit var timelineCases: TimelineCases
    lateinit var mastodonApi: MastodonApi
    lateinit var eventHub: EventHub
    lateinit var viewModel: TimelineViewModel

    @Before
    fun setup() {
        timelineRepository = mock()
        timelineCases = mock()
        mastodonApi = mock()
        eventHub = mock()
        viewModel = TimelineViewModel(timelineRepository, timelineCases, mastodonApi, eventHub)
    }

    @Test
    fun `loadInitial, home, without cache, empty response`() {
        val initialResponse = listOf<Status>()
        setCachedResponse(initialResponse)

        // loadAbove -> loadBelow
        whenever(
            timelineRepository.getStatuses(
                maxId = null,
                sinceId = null,
                sincedIdMinusOne = null,
                requestMode = TimelineRequestMode.ANY,
                limit = TimelineViewModel.LOAD_AT_ONCE
            )
        ).thenReturn(Single.just(listOf()))

        runBlocking {
            viewModel.loadInitial()
        }

        verify(timelineRepository).getStatuses(
            null,
            null,
            null,
            TimelineViewModel.LOAD_AT_ONCE,
            TimelineRequestMode.ANY
        )
    }

    @Test
    fun `loadInitial, home, without cache, single item in response`() {
        setCachedResponse(listOf())

        val status = makeStatus("1")
        whenever(
            timelineRepository.getStatuses(
                isNull(),
                isNull(),
                isNull(),
                eq(TimelineViewModel.LOAD_AT_ONCE),
                eq(TimelineRequestMode.ANY)
            )
        ).thenReturn(
            Single.just(
                listOf(
                    Either.Right(status)
                )
            )
        )

        val updates = viewModel.viewUpdates.test()

        runBlocking {
            viewModel.loadInitial()
        }

        verify(timelineRepository).getStatuses(
            isNull(),
            isNull(),
            isNull(),
            eq(TimelineViewModel.LOAD_AT_ONCE),
            eq(TimelineRequestMode.ANY)
        )

        assertViewUpdated(updates)

        assertHasList(listOf(ViewDataUtils.statusToViewData(status, false, false)!!))
    }

    @Test
    fun updateCurernt() {
        // TODO
    }

    @Test
    fun `loads above cached`() {
        val cachedStatuses = (5 downTo 1).map { makeStatus(it.toString()) }
        setCachedResponse(cachedStatuses)
        setInitialRefresh("5", cachedStatuses.drop(1))

        val additionalStatuses = (10 downTo 6)
            .map { makeStatus(it.toString()) }

        whenever(
            timelineRepository.getStatuses(
                null,
                "5",
                "4",
                TimelineViewModel.LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK
            )
        ).thenReturn(Single.just(additionalStatuses.map { Either.Right(it) }))

        runBlocking {
            viewModel.loadInitial()
        }

        // We could also check refresh progress here but it's a bit cumbersome

        assertHasList(
            additionalStatuses.plus(cachedStatuses)
                .map { ViewDataUtils.statusToViewData(it, false, false)!! })
    }

    @Test
    fun loadAbove() {
        val cachedStatuses = (5 downTo 1).map { makeStatus(it.toString()) }
        setCachedResponse(cachedStatuses)
        setInitialRefresh("5", cachedStatuses.drop(1))

        val additionalStatuses = listOf(makeStatus("6"))

        whenever(
            timelineRepository.getStatuses(
                null,
                "5",
                "4",
                TimelineViewModel.LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK
            )
        ).thenReturn(Single.just(additionalStatuses.map { Either.Right(it) }))

        runBlocking {
            viewModel.loadInitial()
        }

        clearInvocations(timelineRepository)

        val newStatuses = (8 downTo 7).map { makeStatus(it.toString()) }

        // Loading above the cached manually
        whenever(
            timelineRepository.getStatuses(
                null,
                "6",
                "5",
                TimelineViewModel.LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK
            )
        ).thenReturn(Single.just(newStatuses.map { Either.Right(it) }))

        runBlocking {
            viewModel.loadAbove()
        }

        val allStatuses = newStatuses + additionalStatuses + cachedStatuses
        assertHasList(allStatuses.map { ViewDataUtils.statusToViewData(it, false, false)!! })
    }

    @Test
    fun loadBelow() {
        val cachedStatuses = (10 downTo 5).map { makeStatus(it.toString()) }
        setCachedResponse(cachedStatuses)
        setInitialRefresh("10", cachedStatuses.drop(1))

        // Nothing above
        whenever(
            timelineRepository.getStatuses(
                null,
                "10",
                "9",
                TimelineViewModel.LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK
            )
        ).thenReturn(Single.just(listOf()))

        runBlocking {
            viewModel.loadInitial().join()
        }

        clearInvocations(timelineRepository)

        val oldStatuses = (4 downTo 1).map { makeStatus(it.toString()) }

        // Loading below the cached
        whenever(
            timelineRepository.getStatuses(
                "5",
                null,
                null,
                TimelineViewModel.LOAD_AT_ONCE,
                TimelineRequestMode.ANY
            )
        ).thenReturn(Single.just(oldStatuses.map { Either.Right(it) }))

        runBlocking {
            viewModel.loadMore().join()
        }

        val allStatuses = cachedStatuses + oldStatuses
        assertHasList(allStatuses.map { ViewDataUtils.statusToViewData(it, false, false)!! })
    }

    @Test
    fun loadGap() {
        val status5 = makeStatus("5")
        val status4 = makeStatus("4")
        val status3 = makeStatus("3")
        val status1 = makeStatus("1")

        val cachedStatuses: List<TimelineStatus> = listOf(
            Either.Right(status5),
            Either.Left(Placeholder("4")),
            Either.Right(status1)
        )
        val laterFetchedStatuses = listOf<TimelineStatus>(
            Either.Right(status4),
            Either.Right(status3),
        )

        setCachedResponseWithGaps(cachedStatuses)
        setInitialRefreshWithGaps("5", cachedStatuses.drop(1))

        // Nothing above
        whenever(
            timelineRepository.getStatuses(
                null,
                "5",
                null,
                TimelineViewModel.LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK
            )
        ).thenReturn(Single.just(listOf()))

        whenever(
            timelineRepository.getStatuses(
                "5",
                "1",
                null,
                TimelineViewModel.LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK
            )
        ).thenReturn(Single.just(laterFetchedStatuses))

        runBlocking {
            viewModel.loadInitial().join()

            viewModel.loadGap(1).join()
        }

        assertHasList(
            listOf(
                status5,
                status4,
                status3,
                status1
            ).map { ViewDataUtils.statusToViewData(it, false, false)!! }
        )
    }

    private fun assertHasList(aList: List<StatusViewData>) {
        assertEquals(
            aList,
            viewModel.statuses.pairedCopy.toList()
        )
    }

    private fun assertViewUpdated(updates: @NonNull TestObserver<Unit>) {
        assertTrue("There were view updates", updates.values().isNotEmpty())
    }

    private fun setInitialRefresh(maxId: String?, statuses: List<Status>) {
        setInitialRefreshWithGaps(maxId, statuses.map { Either.Right(it) })
    }

    private fun setCachedResponse(initialResponse: List<Status>) {
        setCachedResponseWithGaps(initialResponse.map { Either.Right(it) })
    }

    private fun setCachedResponseWithGaps(initialResponse: List<TimelineStatus>) {
        whenever(
            timelineRepository.getStatuses(
                isNull(),
                isNull(),
                isNull(),
                eq(TimelineViewModel.LOAD_AT_ONCE),
                eq(TimelineRequestMode.DISK)
            )
        )
            .thenReturn(Single.just(initialResponse))
    }

    private fun setInitialRefreshWithGaps(maxId: String?, statuses: List<TimelineStatus>) {
        whenever(
            timelineRepository.getStatuses(
                maxId,
                null,
                null,
                TimelineViewModel.LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK
            )
        ).thenReturn(Single.just(statuses))
    }
}