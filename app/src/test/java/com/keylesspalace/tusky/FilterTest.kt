package com.keylesspalace.tusky

import android.os.Bundle
import android.text.SpannedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.PollOption
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.fragment.SFragment
import com.keylesspalace.tusky.network.MastodonApi
import com.nhaarman.mockitokotlin2.mock
import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*

@Config(application = FakeTuskyApplication::class)
@RunWith(AndroidJUnit4::class)
class FilterTest {

    private val fragment = FakeFragment()

    @Before
    fun setup() {

        val controller = Robolectric.buildActivity(FakeActivity::class.java)
        val activity = controller.get()

        activity.accountManager = mock()
        activity.themeUtils = mock()
        val apiMock = Mockito.mock(MastodonApi::class.java)
        Mockito.`when`(apiMock.getFilters()).thenReturn(object: Call<List<Filter>> {
            override fun isExecuted(): Boolean {
                return false
            }
            override fun clone(): Call<List<Filter>> {
                throw Error("not implemented")
            }
            override fun isCanceled(): Boolean {
                throw Error("not implemented")
            }
            override fun cancel() {
                throw Error("not implemented")
            }
            override fun execute(): Response<List<Filter>> {
                throw Error("not implemented")
            }
            override fun request(): Request {
                throw Error("not implemented")
            }

            override fun enqueue(callback: Callback<List<Filter>>) {
                callback.onResponse(
                        this,
                        Response.success(
                                listOf(
                                        Filter(
                                                id = "123",
                                                phrase = "badWord",
                                                context = listOf(Filter.HOME),
                                                expiresAt = null,
                                                irreversible = false,
                                                wholeWord = false
                                        ),
                                        Filter(
                                                id = "123",
                                                phrase = "badWholeWord",
                                                context = listOf(Filter.HOME, Filter.PUBLIC),
                                                expiresAt = null,
                                                irreversible = false,
                                                wholeWord = true
                                        ),
                                        Filter(
                                                id = "123",
                                                phrase = "wrongContext",
                                                context = listOf(Filter.PUBLIC),
                                                expiresAt = null,
                                                irreversible = false,
                                                wholeWord = true
                                        )
                                )
                        )
                )
            }
        })

        activity.mastodonApi = apiMock


        controller.create().start()

        fragment.mastodonApi = apiMock


        activity.supportFragmentManager.beginTransaction()
                .replace(R.id.activity_main, fragment, "fragment")
                .commit()

        fragment.reloadFilters(false)

    }

    @Test
    fun shouldNotFilter() {
        assertFalse(fragment.shouldFilterStatus(
                mockStatus(content = "should not be filtered")
        ))
    }

    @Test
    fun shouldNotFilter_whenContextDoesNotMatch() {
        assertFalse(fragment.shouldFilterStatus(
                mockStatus(content = "one two wrongContext three")
        ))
    }

    @Test
    fun shouldFilter_whenContentMatchesBadWord() {
        assertTrue(fragment.shouldFilterStatus(
                mockStatus(content = "one two badWord three")
        ))
    }

    @Test
    fun shouldFilter_whenContentMatchesBadWordPart() {
        assertTrue(fragment.shouldFilterStatus(
                mockStatus(content = "one two badWordPart three")
        ))
    }

    @Test
    fun shouldFilter_whenContentMatchesBadWholeWord() {
        assertTrue(fragment.shouldFilterStatus(
                mockStatus(content = "one two badWholeWord three")
        ))
    }

    @Test
    fun shouldNotFilter_whenContentDoesNotMAtchWholeWord() {
        assertFalse(fragment.shouldFilterStatus(
                mockStatus(content = "one two badWholeWordTest three")
        ))
    }

    @Test
    fun shouldFilter_whenSpoilerTextDoesMatch() {
        assertTrue(fragment.shouldFilterStatus(
                mockStatus(
                        content = "should not be filtered",
                        spoilerText = "badWord should be filtered"
                )
        ))
    }

    @Test
    fun shouldFilter_whenPollTextDoesMatch() {
        assertTrue(fragment.shouldFilterStatus(
                mockStatus(
                        content = "should not be filtered",
                        spoilerText = "should not be filtered",
                        pollOptions = listOf("should not be filtered", "badWord")
                )
        ))
    }

    private fun mockStatus(
            content: String = "",
            spoilerText: String = "",
            pollOptions: List<String>? = null
    ): Status {
        return Status(
                id = "123",
                url = "https://mastodon.social/@Tusky/100571663297225812",
                account = mock(),
                inReplyToId = null,
                inReplyToAccountId = null,
                reblog = null,
                content = SpannedString(content),
                createdAt = Date(),
                emojis = emptyList(),
                reblogsCount = 0,
                favouritesCount = 0,
                reblogged = false,
                favourited = false,
                sensitive = false,
                spoilerText = spoilerText,
                visibility = Status.Visibility.PUBLIC,
                attachments = arrayListOf(),
                mentions = emptyArray(),
                application = null,
                pinned = false,
                poll = if (pollOptions != null) {
                    Poll(
                            id = "1234",
                            expiresAt = null,
                            expired = false,
                            multiple = false,
                            votesCount = 0,
                            options = pollOptions.map {
                                PollOption(it, 0)
                            },
                            voted = false
                    )
                } else null,
                card = null
        )
    }

}

class FakeActivity: BottomSheetActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}

class FakeFragment: SFragment() {
    override fun removeItem(position: Int) {
    }

    override fun onReblog(reblog: Boolean, position: Int) {
    }

    override fun filterIsRelevant(filter: Filter): Boolean {
        return filter.context.contains(Filter.HOME)
    }
}