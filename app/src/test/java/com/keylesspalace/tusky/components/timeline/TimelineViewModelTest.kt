package com.keylesspalace.tusky.components.timeline

import android.content.SharedPreferences
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.components.timeline.TimelineViewModel.Companion.LOAD_AT_ONCE
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.FilterModel
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.IOException


@Config(sdk = [29])
class TimelineViewModelTest {
    lateinit var timelineRepository: TimelineRepository
    lateinit var timelineCases: TimelineCases
    lateinit var mastodonApi: MastodonApi
    lateinit var eventHub: EventHub
    lateinit var viewModel: TimelineViewModel
    lateinit var accountManager: AccountManager
    lateinit var sharedPreference: SharedPreferences

    @Before
    fun setup() {
        ShadowLog.stream = System.out
        timelineRepository = mock()
        timelineCases = mock()
        mastodonApi = mock()
        eventHub = mock()
        accountManager = mock()
        sharedPreference = mock()
        viewModel = TimelineViewModel(
            timelineRepository,
            timelineCases,
            mastodonApi,
            eventHub,
            accountManager,
            sharedPreference,
            FilterModel()
        )
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
                limit = LOAD_AT_ONCE
            )
        ).thenReturn(Single.just(listOf()))

        runBlocking {
            viewModel.loadInitial()
        }

        verify(timelineRepository).getStatuses(
            null,
            null,
            null,
            LOAD_AT_ONCE,
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
                eq(LOAD_AT_ONCE),
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
            eq(LOAD_AT_ONCE),
            eq(TimelineRequestMode.ANY)
        )

        assertViewUpdated(updates)

        assertHasList(listOf(status).toViewData())
    }

    @Test
    fun `loadInitial, home, without cache, error on load`() {
        setCachedResponse(listOf())

        whenever(
            timelineRepository.getStatuses(
                maxId = null,
                sinceId = null,
                sincedIdMinusOne = null,
                limit = LOAD_AT_ONCE,
                TimelineRequestMode.ANY,
            )
        ).thenReturn(Single.error(IOException("test")))

        val updates = viewModel.viewUpdates.test()

        runBlocking {
            viewModel.loadInitial()
        }

        verify(timelineRepository).getStatuses(
            isNull(),
            isNull(),
            isNull(),
            eq(LOAD_AT_ONCE),
            eq(TimelineRequestMode.ANY)
        )

        assertViewUpdated(updates)

        assertHasList(listOf())
        assertEquals(TimelineViewModel.FailureReason.NETWORK, viewModel.failure)
    }

    @Test
    fun `loadInitial, home, with cache, error on refresh`() {
        val statuses = (5 downTo 2).map { makeStatus(it.toString()) }
        setCachedResponse(statuses)

        // Error on refreshing cached
        whenever(
            timelineRepository.getStatuses(
                maxId = "6",
                sinceId = null,
                sincedIdMinusOne = null,
                limit = LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK,
            )
        ).thenReturn(Single.error(IOException("test")))

        // Empty on loading above
        whenever(
            timelineRepository.getStatuses(
                maxId = null,
                sinceId = "5",
                sincedIdMinusOne = "4",
                limit = LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK,
            )
        ).thenReturn(Single.just(listOf()))

        val updates = viewModel.viewUpdates.test()

        runBlocking {
            viewModel.loadInitial()
        }

        assertViewUpdated(updates)

        assertHasList(statuses.toViewData())
        assertNull(viewModel.failure)
    }

    @Test
    fun `loads above cached`() {
        val cachedStatuses = (5 downTo 1).map { makeStatus(it.toString()) }
        setCachedResponse(cachedStatuses)
        setInitialRefresh("6", cachedStatuses.drop(1))

        val additionalStatuses = (10 downTo 6)
            .map { makeStatus(it.toString()) }

        whenever(
            timelineRepository.getStatuses(
                null,
                "5",
                "4",
                LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK
            )
        ).thenReturn(Single.just(additionalStatuses.toEitherList()))

        runBlocking {
            viewModel.loadInitial()
        }

        // We could also check refresh progress here but it's a bit cumbersome

        assertHasList(additionalStatuses.plus(cachedStatuses).toViewData())
    }

    @Test
    fun refresh() {
        val cachedStatuses = (5 downTo 1).map { makeStatus(it.toString()) }
        setCachedResponse(cachedStatuses)
        setInitialRefresh("6", cachedStatuses.drop(1))

        val additionalStatuses = listOf(makeStatus("6"))

        whenever(
            timelineRepository.getStatuses(
                null,
                "5",
                "4",
                LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK
            )
        ).thenReturn(Single.just(additionalStatuses.toEitherList()))

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
                LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK
            )
        ).thenReturn(Single.just(newStatuses.toEitherList()))

        runBlocking {
            viewModel.refresh()
        }

        val allStatuses = newStatuses + additionalStatuses + cachedStatuses
        assertHasList(allStatuses.toViewData())
    }

    @Test
    fun `refresh failed`() {
        val cachedStatuses = (5 downTo 1).map { makeStatus(it.toString()) }
        setCachedResponse(cachedStatuses)
        setInitialRefresh("6", cachedStatuses.drop(1))

        val additionalStatuses = listOf(makeStatus("6"))

        whenever(
            timelineRepository.getStatuses(
                null,
                "5",
                "4",
                LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK
            )
        ).thenReturn(Single.just(additionalStatuses.toEitherList()))

        runBlocking {
            viewModel.loadInitial()
        }

        clearInvocations(timelineRepository)

        // Loading above the cached manually
        whenever(
            timelineRepository.getStatuses(
                null,
                "6",
                "5",
                LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK
            )
        ).thenReturn(Single.error(IOException("test")))

        runBlocking {
            viewModel.refresh().join()
        }

        val allStatuses = additionalStatuses + cachedStatuses
        assertHasList(allStatuses.map { ViewDataUtils.statusToViewData(it, false, false) })
        assertFalse("refreshing", viewModel.isRefreshing)
        assertNull("failure is not set", viewModel.failure)
    }

    @Test
    fun loadMore() {
        val cachedStatuses = (10 downTo 5).map { makeStatus(it.toString()) }
        setCachedResponse(cachedStatuses)
        setInitialRefresh("11", cachedStatuses.drop(1))

        // Nothing above
        whenever(
            timelineRepository.getStatuses(
                null,
                "10",
                "9",
                LOAD_AT_ONCE,
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
                LOAD_AT_ONCE,
                TimelineRequestMode.ANY
            )
        ).thenReturn(Single.just(oldStatuses.toEitherList()))

        runBlocking {
            viewModel.loadMore().join()
        }

        val allStatuses = cachedStatuses + oldStatuses
        assertHasList(allStatuses.toViewData())
    }

    @Test
    fun `loadMore failed`() {
        val cachedStatuses = (10 downTo 5).map { makeStatus(it.toString()) }
        setCachedResponse(cachedStatuses)
        setInitialRefresh("11", cachedStatuses.drop(1))

        // Nothing above
        whenever(
            timelineRepository.getStatuses(
                null,
                "10",
                "9",
                LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK
            )
        ).thenReturn(Single.just(listOf()))

        runBlocking {
            viewModel.loadInitial().join()
        }

        clearInvocations(timelineRepository)

        // Loading below the cached
        whenever(
            timelineRepository.getStatuses(
                "5",
                null,
                null,
                LOAD_AT_ONCE,
                TimelineRequestMode.ANY
            )
        ).thenReturn(Single.error(IOException("test")))

        runBlocking {
            viewModel.loadMore().join()
        }

        assertHasList(cachedStatuses.toViewData())

        // Check that we can still load after that

        val oldStatuses = listOf(makeStatus("4"))
        whenever(
            timelineRepository.getStatuses(
                "5",
                null,
                null,
                LOAD_AT_ONCE,
                TimelineRequestMode.ANY
            )
        ).thenReturn(Single.just(oldStatuses.toEitherList()))

        runBlocking {
            viewModel.loadMore().join()
        }
        assertHasList((cachedStatuses + oldStatuses).toViewData())
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
        setInitialRefreshWithGaps("6", cachedStatuses.drop(1))

        // Nothing above
        whenever(
            timelineRepository.getStatuses(
                null,
                "5",
                null,
                LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK
            )
        ).thenReturn(Single.just(listOf()))

        whenever(
            timelineRepository.getStatuses(
                "5",
                "1",
                null,
                LOAD_AT_ONCE,
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
            ).toViewData()
        )
    }

    // TODO: test failure for each
    // TODO: test concurrent loading below

    private fun assertHasList(aList: List<StatusViewData>) {
        assertEquals(
            aList,
            viewModel.statuses.toList()
        )
    }

    private fun assertViewUpdated(updates: @NonNull TestObserver<Unit>) {
        assertTrue("There were view updates", updates.values().isNotEmpty())
    }

    private fun setInitialRefresh(maxId: String?, statuses: List<Status>) {
        setInitialRefreshWithGaps(maxId, statuses.toEitherList())
    }

    private fun setCachedResponse(initialResponse: List<Status>) {
        setCachedResponseWithGaps(initialResponse.toEitherList())
    }

    private fun setCachedResponseWithGaps(initialResponse: List<TimelineStatus>) {
        whenever(
            timelineRepository.getStatuses(
                isNull(),
                isNull(),
                isNull(),
                eq(LOAD_AT_ONCE),
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
                LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK
            )
        ).thenReturn(Single.just(statuses))
    }

    private fun List<Status>.toViewData() = map {
        ViewDataUtils.statusToViewData(
            it,
            alwaysShowSensitiveMedia = false,
            alwaysOpenSpoiler = false
        )
    }

    private fun List<Status>.toEitherList() = map { Either.Right<Placeholder, Status>(it) }
}