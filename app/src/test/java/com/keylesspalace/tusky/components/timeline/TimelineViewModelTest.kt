package com.keylesspalace.tusky.components.timeline

import android.os.Looper
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.paging.AsyncPagingDataDiffer
import androidx.paging.ExperimentalPagingApi
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.keylesspalace.tusky.appstore.EventHubImpl
import com.keylesspalace.tusky.components.timeline.TimelinePagingAdapter.Companion.TimelineDifferCallback
import com.keylesspalace.tusky.components.timeline.viewmodel.CachedTimelineViewModel
import com.keylesspalace.tusky.components.timeline.viewmodel.NetworkTimelineViewModel
import com.keylesspalace.tusky.components.timeline.viewmodel.TimelineViewModel
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.Converters
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.network.TimelineCasesImpl
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.Headers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Response
import java.util.concurrent.Executors

@ExperimentalCoroutinesApi
@Config(sdk = [29])
@RunWith(AndroidJUnit4::class)
class TimelineViewModelTest {

    @get:Rule
    val instantRule = InstantTaskExecutorRule()

    private val testDispatcher = TestCoroutineDispatcher()
    private val testScope = TestCoroutineScope(testDispatcher)

    private val accountManager: AccountManager = mock {
        on { activeAccount } doReturn AccountEntity(
            id = 1,
            domain = "mastodon.example",
            accessToken = "token",
            isActive = true
        )
    }

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        shadowOf(Looper.getMainLooper()).idle()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addTypeConverter(Converters(Gson()))
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
        db.close()
    }

    @Test
    @ExperimentalPagingApi
    fun shouldLoadNetworkTimeline() = runBlocking {

        val api: MastodonApi = mock {
            on { publicTimeline(local = true, maxId = null, sinceId = null, limit = 30) } doReturn Single.just(
                Response.success(
                    listOf(
                        mockStatus("6"),
                        mockStatus("5"),
                        mockStatus("4")
                    ),
                    Headers.headersOf(
                        "Link", "<https://mastodon.examples/api/v1/favourites?limit=30&max_id=1>; rel=\"next\", <https://mastodon.example/api/v1/favourites?limit=30&min_id=5>; rel=\"prev\""
                    )
                )
            )

            on { publicTimeline(local = true, maxId = "1", sinceId = null, limit = 30) } doReturn Single.just(
                Response.success(emptyList())
            )

            on { getFilters() } doReturn Single.just(emptyList())
        }

        val viewModel = NetworkTimelineViewModel(
            TimelineCasesImpl(api, EventHubImpl),
            api,
            EventHubImpl,
            accountManager,
            mock(),
            FilterModel()
        )

        viewModel.init(TimelineViewModel.Kind.PUBLIC_LOCAL, null, emptyList())

        val differ = AsyncPagingDataDiffer(
            diffCallback = TimelineDifferCallback,
            updateCallback = NoopListCallback(),
            workerDispatcher = testDispatcher
        )

        viewModel.statuses.take(2).collectLatest {
            testScope.launch {
                differ.submitData(it)
            }
        }

        assertEquals(
            listOf(
                mockStatusViewData("6"),
                mockStatusViewData("5"),
                mockStatusViewData("4")
            ),
            differ.snapshot().items
        )
    }

    // ToDo: Find out why Room & coroutines are not playing nice here
    // @Test
    @ExperimentalPagingApi
    fun shouldLoadCachedTimeline() = runBlocking {

        val api: MastodonApi = mock {
            on { homeTimeline(limit = 30) } doReturn Single.just(
                Response.success(
                    listOf(
                        mockStatus("6"),
                        mockStatus("5"),
                        mockStatus("4")
                    )
                )
            )

            on { homeTimeline(maxId = "1", sinceId = null, limit = 30) } doReturn Single.just(
                Response.success(emptyList())
            )

            on { getFilters() } doReturn Single.just(emptyList())
        }

        val viewModel = CachedTimelineViewModel(
            TimelineCasesImpl(api, EventHubImpl),
            api,
            EventHubImpl,
            accountManager,
            mock(),
            FilterModel(),
            db,
            Gson()
        )

        viewModel.init(TimelineViewModel.Kind.HOME, null, emptyList())

        val differ = AsyncPagingDataDiffer(
            diffCallback = TimelineDifferCallback,
            updateCallback = NoopListCallback(),
            workerDispatcher = testDispatcher
        )

        var x = 1
        viewModel.statuses.take(1000).collectLatest {
            testScope.launch {
                differ.submitData(it)
            }
        }

        assertEquals(
            listOf(
                mockStatusViewData("6"),
                mockStatusViewData("5"),
                mockStatusViewData("4")
            ),
            differ.snapshot().items
        )
    }
}

class NoopListCallback : ListUpdateCallback {
    override fun onChanged(position: Int, count: Int, payload: Any?) {}
    override fun onMoved(fromPosition: Int, toPosition: Int) {}
    override fun onInserted(position: Int, count: Int) {}
    override fun onRemoved(position: Int, count: Int) {}
}
