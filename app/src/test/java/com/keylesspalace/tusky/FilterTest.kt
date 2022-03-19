package com.keylesspalace.tusky

import android.text.SpannedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.PollOption
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.FilterModel
import com.nhaarman.mockitokotlin2.mock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.ArrayList
import java.util.Date

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class FilterTest {

    lateinit var filterModel: FilterModel

    @Before
    fun setup() {
        filterModel = FilterModel()
        val filters = listOf(
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
                phrase = "@twitter.com",
                context = listOf(Filter.HOME),
                expiresAt = null,
                irreversible = false,
                wholeWord = true
            )
        )

        filterModel.initWithFilters(filters)
    }

    @Test
    fun shouldNotFilter() {
        assertFalse(
            filterModel.shouldFilterStatus(
                mockStatus(content = "should not be filtered")
            )
        )
    }

    @Test
    fun shouldFilter_whenContentMatchesBadWord() {
        assertTrue(
            filterModel.shouldFilterStatus(
                mockStatus(content = "one two badWord three")
            )
        )
    }

    @Test
    fun shouldFilter_whenContentMatchesBadWordPart() {
        assertTrue(
            filterModel.shouldFilterStatus(
                mockStatus(content = "one two badWordPart three")
            )
        )
    }

    @Test
    fun shouldFilter_whenContentMatchesBadWholeWord() {
        assertTrue(
            filterModel.shouldFilterStatus(
                mockStatus(content = "one two badWholeWord three")
            )
        )
    }

    @Test
    fun shouldNotFilter_whenContentDoesNotMatchWholeWord() {
        assertFalse(
            filterModel.shouldFilterStatus(
                mockStatus(content = "one two badWholeWordTest three")
            )
        )
    }

    @Test
    fun shouldFilter_whenSpoilerTextDoesMatch() {
        assertTrue(
            filterModel.shouldFilterStatus(
                mockStatus(
                    content = "should not be filtered",
                    spoilerText = "badWord should be filtered"
                )
            )
        )
    }

    @Test
    fun shouldFilter_whenPollTextDoesMatch() {
        assertTrue(
            filterModel.shouldFilterStatus(
                mockStatus(
                    content = "should not be filtered",
                    spoilerText = "should not be filtered",
                    pollOptions = listOf("should not be filtered", "badWord")
                )
            )
        )
    }

    @Test
    fun shouldFilter_whenMediaDescriptionDoesMatch() {
        assertTrue(
            filterModel.shouldFilterStatus(
                mockStatus(
                    content = "should not be filtered",
                    spoilerText = "should not be filtered",
                    attachmentsDescriptions = listOf("should not be filtered", "badWord"),
                )
            )
        )
    }

    @Test
    fun shouldFilterPartialWord_whenWholeWordFilterContainsNonAlphanumericCharacters() {
        assertTrue(
            filterModel.shouldFilterStatus(
                mockStatus(content = "one two someone@twitter.com three")
            )
        )
    }

    private fun mockStatus(
        content: String = "",
        spoilerText: String = "",
        pollOptions: List<String>? = null,
        attachmentsDescriptions: List<String>? = null
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
            bookmarked = false,
            sensitive = false,
            spoilerText = spoilerText,
            visibility = Status.Visibility.PUBLIC,
            attachments = if (attachmentsDescriptions != null) {
                ArrayList(
                    attachmentsDescriptions.map {
                        Attachment(
                            id = "1234",
                            url = "",
                            previewUrl = null,
                            meta = null,
                            type = Attachment.Type.IMAGE,
                            description = it,
                            blurhash = null
                        )
                    }
                )
            } else arrayListOf(),
            mentions = listOf(),
            tags = listOf(),
            application = null,
            pinned = false,
            muted = false,
            poll = if (pollOptions != null) {
                Poll(
                    id = "1234",
                    expiresAt = null,
                    expired = false,
                    multiple = false,
                    votesCount = 0,
                    votersCount = 0,
                    options = pollOptions.map {
                        PollOption(it, 0)
                    },
                    voted = false,
                    ownVotes = null
                )
            } else null,
            card = null
        )
    }
}
