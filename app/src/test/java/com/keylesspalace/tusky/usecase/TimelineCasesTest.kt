package com.keylesspalace.tusky.usecase

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import at.connyduck.calladapter.networkresult.NetworkResult
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.StatusChangedEvent
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import java.util.Date
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

@Config(sdk = [34])
@RunWith(AndroidJUnit4::class)
class TimelineCasesTest {

    private lateinit var api: MastodonApi
    private lateinit var eventHub: EventHub
    private lateinit var timelineCases: TimelineCases

    private val statusId = "1234"

    @Before
    fun setup() {
        api = mock()
        eventHub = EventHub()
        timelineCases = TimelineCases(api, eventHub)
    }

    @Test
    fun `pin success emits StatusChangedEvent`() = runTest {
        val pinnedStatus = mockStatus(pinned = true)

        api.stub {
            onBlocking { pinStatus(statusId) } doReturn NetworkResult.success(pinnedStatus)
        }

        eventHub.events.test {
            timelineCases.pin(statusId, true)
            assertEquals(StatusChangedEvent(pinnedStatus), awaitItem())
        }
    }

    @Test
    fun `pin failure with server error throws TimelineError with server message`() = runTest {
        api.stub {
            onBlocking { pinStatus(statusId) } doReturn NetworkResult.failure(
                HttpException(
                    Response.error<Status>(
                        422,
                        "{\"error\":\"Validation Failed: You have already pinned the maximum number of toots\"}".toResponseBody()
                    )
                )
            )
        }
        assertEquals(
            "Validation Failed: You have already pinned the maximum number of toots",
            timelineCases.pin(statusId, true).exceptionOrNull()?.message
        )
    }

    private fun mockStatus(pinned: Boolean = false): Status {
        return Status(
            id = "123",
            url = "https://mastodon.social/@Tusky/100571663297225812",
            account = mock(),
            inReplyToId = null,
            inReplyToAccountId = null,
            reblog = null,
            content = "",
            createdAt = Date(),
            editedAt = null,
            emojis = emptyList(),
            reblogsCount = 0,
            favouritesCount = 0,
            repliesCount = 0,
            reblogged = false,
            favourited = false,
            bookmarked = false,
            sensitive = false,
            spoilerText = "",
            visibility = Status.Visibility.PUBLIC,
            attachments = arrayListOf(),
            mentions = listOf(),
            tags = listOf(),
            application = null,
            pinned = pinned,
            muted = false,
            poll = null,
            card = null,
            language = null,
            filtered = emptyList()
        )
    }
}
