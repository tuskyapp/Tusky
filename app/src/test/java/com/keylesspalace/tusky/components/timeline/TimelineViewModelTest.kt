package com.keylesspalace.tusky.components.timeline

import android.content.SharedPreferences
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.components.timeline.TimelineViewModel.Companion.LOAD_AT_ONCE
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.PollOption
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.network.TimelineCases
import com.keylesspalace.tusky.util.Either
import com.keylesspalace.tusky.util.inc
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.rxjava3.annotations.NonNull
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.observers.TestObserver
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import retrofit2.Response
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
        eventHub = mock {
            on { events } doReturn Observable.never()
        }
        val account = AccountEntity(
            0,
            "domain",
            "accessToken",
            isActive = true,
        )

        accountManager = mock {
            on { activeAccount } doReturn account
        }
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

        // loadAbove
        whenever(
            timelineRepository.getStatuses(
                maxId = null,
                sinceId = null,
                sincedIdMinusOne = null,
                requestMode = TimelineRequestMode.NETWORK,
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
            TimelineRequestMode.NETWORK
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
                eq(TimelineRequestMode.NETWORK)
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
            eq(TimelineRequestMode.NETWORK)
        )

        assertViewUpdated(updates)

        assertHasList(listOf(status).toViewData())
    }

    @Test
    fun `loadInitial, list`() {
        val listId = "listId"
        viewModel.init(TimelineViewModel.Kind.LIST, listId, listOf())
        val status = makeStatus("1")

        whenever(
            mastodonApi.listTimeline(
                listId,
                null,
                null,
                LOAD_AT_ONCE,
            )
        ).thenReturn(
            Single.just(
                Response.success(
                    listOf(
                        status
                    )
                )
            )
        )

        val updates = viewModel.viewUpdates.test()

        runBlocking {
            viewModel.loadInitial().join()
        }
        assertViewUpdated(updates)

        assertHasList(listOf(status).toViewData())
        assertFalse("loading", viewModel.isLoadingInitially)
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
                TimelineRequestMode.NETWORK,
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
            eq(TimelineRequestMode.NETWORK)
        )

        assertViewUpdated(updates)

        assertHasList(listOf())
        assertEquals(TimelineViewModel.FailureReason.NETWORK, viewModel.failure)
    }

    @Test
    fun `loadInitial, home, with cache, error on load above`() {
        val statuses = (5 downTo 1).map { makeStatus(it.toString()) }
        setCachedResponse(statuses)
        setInitialRefresh(statuses)

        whenever(
            timelineRepository.getStatuses(
                maxId = null,
                sinceId = "5",
                sincedIdMinusOne = "4",
                limit = LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK,
            )
        ).thenReturn(Single.error(IOException("test")))

        val updates = viewModel.viewUpdates.test()

        runBlocking {
            viewModel.loadInitial()
        }

        assertViewUpdated(updates)

        assertHasList(statuses.toViewData())
        // No failure set since we had statuses
        assertNull(viewModel.failure)
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
        setInitialRefresh(listOf())

        val updates = viewModel.viewUpdates.test()

        runBlocking {
            viewModel.loadInitial()
        }

        assertViewUpdated(updates)

        assertHasList(statuses.toViewData())
        assertNull(viewModel.failure)
    }

    @Test
    fun `loadInitial but there's a gap above now`() {
        val cachedStatuses = (5 downTo 1).map { makeStatus(it.toString()) }
        val newStatuses = (100 downTo 100 - LOAD_AT_ONCE).map { makeStatus(it.toString()) }
        setCachedResponse(cachedStatuses)
        setInitialRefresh(newStatuses)

        runBlocking {
            viewModel.loadInitial()
        }

        clearInvocations(timelineRepository)

        runBlocking {
            viewModel.refresh().join()
        }

        val expected = newStatuses.toViewData() +
                listOf(StatusViewData.Placeholder(newStatuses.last().id.inc(), false)) +
                cachedStatuses.toViewData()

        assertHasList(expected)
        assertFalse("refreshing", viewModel.isRefreshing)
        assertNull("failure is not set", viewModel.failure)
    }

    @Test
    fun `loadInitial but there's overlap`() {
        val cachedStatuses = (5 downTo 1).map { makeStatus(it.toString()) }
        val newStatuses = (3 + LOAD_AT_ONCE downTo 3).map { makeStatus(it.toString()) }
        setCachedResponse(cachedStatuses)
        setInitialRefresh(newStatuses)

        runBlocking {
            viewModel.loadInitial()
        }

        clearInvocations(timelineRepository)

        runBlocking {
            viewModel.refresh().join()
        }

        val expected = (3 + LOAD_AT_ONCE downTo 1).map { makeStatus(it.toString()) }.toViewData()

        assertHasList(expected)
        assertFalse("refreshing", viewModel.isRefreshing)
        assertNull("failure is not set", viewModel.failure)
    }

    @Test
    fun `loads above cached`() {
        val cachedStatuses = (5 downTo 1).map { makeStatus(it.toString()) }
        setCachedResponse(cachedStatuses)

        val additionalStatuses = (10 downTo 6)
            .map { makeStatus(it.toString()) }

        setInitialRefresh(additionalStatuses + cachedStatuses)

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

        val additionalStatuses = listOf(makeStatus("6"))

        setInitialRefresh(additionalStatuses + cachedStatuses)

        runBlocking {
            viewModel.loadInitial()
        }

        clearInvocations(timelineRepository)

        val newStatuses = (8 downTo 7).map { makeStatus(it.toString()) }

        // Loading above the cached manually
        whenever(
            timelineRepository.getStatuses(
                null,
                null,
                null,
                LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK
            )
        ).thenReturn(Single.just((newStatuses + additionalStatuses + cachedStatuses).toEitherList()))

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
        setInitialRefresh(cachedStatuses)

        runBlocking {
            viewModel.loadInitial()
        }

        clearInvocations(timelineRepository)

        // Loading above the cached manually
        whenever(
            timelineRepository.getStatuses(
                null,
                null,
                null,
                LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK
            )
        ).thenReturn(Single.error(IOException("test")))

        runBlocking {
            viewModel.refresh().join()
        }

        assertHasList(cachedStatuses.map { it.toViewData(false, false) })
        assertFalse("refreshing", viewModel.isRefreshing)
        assertNull("failure is not set", viewModel.failure)
    }

    @Test
    fun loadMore() {
        val cachedStatuses = (10 downTo 5).map { makeStatus(it.toString()) }
        setCachedResponse(cachedStatuses)
        setInitialRefresh(cachedStatuses)

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
    fun `loadMore parallel`() {
        val cachedStatuses = (10 downTo 5).map { makeStatus(it.toString()) }
        setCachedResponse(cachedStatuses)
        setInitialRefresh(cachedStatuses)

        runBlocking {
            viewModel.loadInitial().join()
        }

        clearInvocations(timelineRepository)

        val oldStatuses = (4 downTo 1).map { makeStatus(it.toString()) }

        val responseSubject = PublishSubject.create<List<TimelineStatus>>()
        // Loading below the cached
        whenever(
            timelineRepository.getStatuses(
                "5",
                null,
                null,
                LOAD_AT_ONCE,
                TimelineRequestMode.ANY
            )
        ).thenReturn(responseSubject.firstOrError())

        clearInvocations(timelineRepository)

        runBlocking {
            // Trigger them in parallel
            val job1 = viewModel.loadMore()
            val job2 = viewModel.loadMore()
            // Send the response
            responseSubject.onNext(oldStatuses.toEitherList())
            // Wait for both
            job1.join()
            job2.join()
        }

        val allStatuses = cachedStatuses + oldStatuses
        assertHasList(allStatuses.toViewData())

        verify(timelineRepository, times(1)).getStatuses(
            "5",
            null,
            null,
            LOAD_AT_ONCE,
            TimelineRequestMode.ANY
        )
    }

    @Test
    fun `loadMore failed`() {
        val cachedStatuses = (10 downTo 5).map { makeStatus(it.toString()) }
        setCachedResponse(cachedStatuses)
        setInitialRefresh(cachedStatuses)

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
        setInitialRefreshWithGaps(cachedStatuses)

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

    @Test
    fun `loadGap failed`() {
        val status5 = makeStatus("5")
        val status1 = makeStatus("1")

        val cachedStatuses: List<TimelineStatus> = listOf(
            Either.Right(status5),
            Either.Left(Placeholder("4")),
            Either.Right(status1)
        )
        setCachedResponseWithGaps(cachedStatuses)
        setInitialRefreshWithGaps(cachedStatuses)

        whenever(
            timelineRepository.getStatuses(
                "5",
                "1",
                null,
                LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK
            )
        ).thenReturn(Single.error(IOException("test")))

        runBlocking {
            viewModel.loadInitial().join()

            viewModel.loadGap(1).join()
        }

        assertHasList(
            listOf(
                status5.toViewData(false, false),
                StatusViewData.Placeholder("4", false),
                status1.toViewData(false, false),
            )
        )
    }

    @Test
    fun favorite() {
        val status5 = makeStatus("5")
        val status4 = makeStatus("4")
        val status3 = makeStatus("3")
        val statuses = listOf(status5, status4, status3)
        setCachedResponse(statuses)
        setInitialRefresh(listOf())

        runBlocking { viewModel.loadInitial() }

        whenever(timelineCases.favourite("4", true))
            .thenReturn(Single.just(status4.copy(favourited = true)))

        runBlocking {
            viewModel.favorite(true, 1).join()
        }

        verify(timelineCases).favourite("4", true)

        assertHasList(listOf(status5, status4.copy(favourited = true), status3).toViewData())
    }

    @Test
    fun reblog() {
        val status5 = makeStatus("5")
        val status4 = makeStatus("4")
        val status3 = makeStatus("3")
        val statuses = listOf(status5, status4, status3)
        setCachedResponse(statuses)
        setInitialRefresh(statuses)

        runBlocking { viewModel.loadInitial() }

        whenever(timelineCases.reblog("4", true))
            .thenReturn(Single.just(status4.copy(reblogged = true)))

        runBlocking {
            viewModel.reblog(true, 1).join()
        }

        verify(timelineCases).reblog("4", true)

        assertHasList(listOf(status5, status4.copy(reblogged = true), status3).toViewData())
    }

    @Test
    fun bookmark() {
        val status5 = makeStatus("5")
        val status4 = makeStatus("4")
        val status3 = makeStatus("3")
        val statuses = listOf(status5, status4, status3)
        setCachedResponse(statuses)
        setInitialRefresh(statuses)

        runBlocking { viewModel.loadInitial() }

        whenever(timelineCases.bookmark("4", true))
            .thenReturn(Single.just(status4.copy(bookmarked = true)))

        runBlocking {
            viewModel.bookmark(true, 1).join()
        }

        verify(timelineCases).bookmark("4", true)

        assertHasList(listOf(status5, status4.copy(bookmarked = true), status3).toViewData())
    }

    @Test
    fun voteInPoll() {
        val status5 = makeStatus("5")
        val poll = Poll(
            "1",
            expiresAt = null,
            expired = false,
            multiple = false,
            votersCount = 1,
            votesCount = 1,
            voted = false,
            options = listOf(PollOption("1", 1), PollOption("2", 2)),
        )
        val status4 = makeStatus("4").copy(poll = poll)
        val status3 = makeStatus("3")
        val statuses = listOf(status5, status4, status3)
        setCachedResponse(statuses)
        setInitialRefresh(statuses)

        runBlocking { viewModel.loadInitial() }

        val votedPoll = poll.votedCopy(listOf(0))
        whenever(timelineCases.voteInPoll("4", poll.id, listOf(0)))
            .thenReturn(Single.just(votedPoll))

        runBlocking {
            viewModel.voteInPoll(1, listOf(0)).join()
        }

        verify(timelineCases).voteInPoll("4", poll.id, listOf(0))

        assertHasList(listOf(status5, status4.copy(poll = votedPoll), status3).toViewData())
    }

    private fun assertHasList(aList: List<StatusViewData>) {
        assertEquals(
            aList,
            viewModel.statuses.toList()
        )
    }

    private fun assertViewUpdated(updates: @NonNull TestObserver<Unit>) {
        assertTrue("There were view updates", updates.values().isNotEmpty())
    }

    private fun setInitialRefresh(statuses: List<Status>) {
        setInitialRefreshWithGaps(statuses.toEitherList())
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

    private fun setInitialRefreshWithGaps(statuses: List<TimelineStatus>) {
        whenever(
            timelineRepository.getStatuses(
                null,
                null,
                null,
                LOAD_AT_ONCE,
                TimelineRequestMode.NETWORK
            )
        ).thenReturn(Single.just(statuses))
    }

    private fun List<Status>.toViewData(): List<StatusViewData> = map {
        it.toViewData(
            alwaysShowSensitiveMedia = false,
            alwaysOpenSpoiler = false
        )
    }

    private fun List<Status>.toEitherList() = map { Either.Right<Placeholder, Status>(it) }
}
