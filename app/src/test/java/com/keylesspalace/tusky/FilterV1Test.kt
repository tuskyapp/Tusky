/*
 * Copyright 2023 Tusky Contributors
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky

import androidx.test.ext.junit.runners.AndroidJUnit4
import at.connyduck.calladapter.networkresult.NetworkResult
import com.keylesspalace.tusky.components.instanceinfo.InstanceInfoRepository
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.FilterV1
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.PollOption
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.network.MastodonApi
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

@Config(sdk = [34])
@RunWith(AndroidJUnit4::class)
class FilterV1Test {

    private lateinit var filterModel: FilterModel

    @Before
    fun setup() {
        val filters = listOf(
            FilterV1(
                id = "123",
                phrase = "badWord",
                context = listOf(Filter.Kind.HOME.kind),
                expiresAt = null,
                irreversible = false,
                wholeWord = false
            ),
            FilterV1(
                id = "123",
                phrase = "badWholeWord",
                context = listOf(Filter.Kind.HOME.kind, Filter.Kind.PUBLIC.kind),
                expiresAt = null,
                irreversible = false,
                wholeWord = true
            ),
            FilterV1(
                id = "123",
                phrase = "@twitter.com",
                context = listOf(Filter.Kind.HOME.kind),
                expiresAt = null,
                irreversible = false,
                wholeWord = true
            ),
            FilterV1(
                id = "123",
                phrase = "#hashtag",
                context = listOf(Filter.Kind.HOME.kind),
                expiresAt = null,
                irreversible = false,
                wholeWord = true
            ),
            FilterV1(
                id = "123",
                phrase = "expired",
                context = listOf(Filter.Kind.HOME.kind),
                expiresAt = Date.from(Instant.now().minusSeconds(10)),
                irreversible = false,
                wholeWord = true
            ),
            FilterV1(
                id = "123",
                phrase = "unexpired",
                context = listOf(Filter.Kind.HOME.kind),
                expiresAt = Date.from(Instant.now().plusSeconds(3600)),
                irreversible = false,
                wholeWord = true
            ),
            FilterV1(
                id = "123",
                phrase = "href",
                context = listOf(Filter.Kind.HOME.kind),
                expiresAt = null,
                irreversible = false,
                wholeWord = false
            )
        )

        val api: MastodonApi = mock {
            onBlocking { getFiltersV1() } doReturn NetworkResult.success(filters)
            onBlocking { getFilters() } doReturn NetworkResult.failure(
                HttpException(Response.error<Any>(404, "".toResponseBody()))
            )
        }
        val instanceInfoRepo: InstanceInfoRepository = mock {
            onBlocking { isFilterV2Supported() } doReturn false
        }

        filterModel = FilterModel(instanceInfoRepo, api)
        runBlocking {
            filterModel.init(Filter.Kind.HOME)
        }
    }

    @Test
    fun shouldNotFilter() {
        assertNull(
            filterModel.shouldFilterStatus(
                mockStatus(content = "should not be filtered")
            )
        )
    }

    @Test
    fun shouldFilter_whenContentMatchesBadWord() {
        assertEquals(
            Filter.Action.HIDE,
            filterModel.shouldFilterStatus(
                mockStatus(content = "one two badWord three")
            )?.action
        )
    }

    @Test
    fun shouldFilter_whenContentMatchesBadWordPart() {
        assertEquals(
            Filter.Action.HIDE,
            filterModel.shouldFilterStatus(
                mockStatus(content = "one two badWordPart three")
            )?.action
        )
    }

    @Test
    fun shouldFilter_whenContentMatchesBadWholeWord() {
        assertEquals(
            Filter.Action.HIDE,
            filterModel.shouldFilterStatus(
                mockStatus(content = "one two badWholeWord three")
            )?.action
        )
    }

    @Test
    fun shouldNotFilter_whenContentDoesNotMatchWholeWord() {
        assertNull(
            filterModel.shouldFilterStatus(
                mockStatus(content = "one two badWholeWordTest three")
            )
        )
    }

    @Test
    fun shouldFilter_whenSpoilerTextDoesMatch() {
        assertEquals(
            Filter.Action.HIDE,
            filterModel.shouldFilterStatus(
                mockStatus(
                    content = "should not be filtered",
                    spoilerText = "badWord should be filtered"
                )
            )?.action
        )
    }

    @Test
    fun shouldFilter_whenPollTextDoesMatch() {
        assertEquals(
            Filter.Action.HIDE,
            filterModel.shouldFilterStatus(
                mockStatus(
                    content = "should not be filtered",
                    spoilerText = "should not be filtered",
                    pollOptions = listOf("should not be filtered", "badWord")
                )
            )?.action
        )
    }

    @Test
    fun shouldFilter_whenMediaDescriptionDoesMatch() {
        assertEquals(
            Filter.Action.HIDE,
            filterModel.shouldFilterStatus(
                mockStatus(
                    content = "should not be filtered",
                    spoilerText = "should not be filtered",
                    attachmentsDescriptions = listOf("should not be filtered", "badWord")
                )
            )?.action
        )
    }

    @Test
    fun shouldFilterPartialWord_whenWholeWordFilterContainsNonAlphanumericCharacters() {
        assertEquals(
            Filter.Action.HIDE,
            filterModel.shouldFilterStatus(
                mockStatus(content = "one two someone@twitter.com three")
            )?.action
        )
    }

    @Test
    fun shouldFilterHashtags() {
        assertEquals(
            Filter.Action.HIDE,
            filterModel.shouldFilterStatus(
                mockStatus(content = "#hashtag one two three")
            )?.action
        )
    }

    @Test
    fun shouldFilterHashtags_whenContentIsMarkedUp() {
        assertEquals(
            Filter.Action.HIDE,
            filterModel.shouldFilterStatus(
                mockStatus(content = "<p><a href=\"https://foo.bar/tags/hashtag\" class=\"mention hashtag\" rel=\"nofollow noopener noreferrer\" target=\"_blank\">#<span>hashtag</span></a>one two three</p>")
            )?.action
        )
    }

    @Test
    fun shouldNotFilterHtmlAttributes() {
        assertNull(
            filterModel.shouldFilterStatus(
                mockStatus(content = "<p><a href=\"https://foo.bar/\">https://foo.bar/</a> one two three</p>")
            )
        )
    }

    @Test
    fun shouldNotFilter_whenFilterIsExpired() {
        assertNull(
            filterModel.shouldFilterStatus(
                mockStatus(content = "content matching expired filter should not be filtered")
            )
        )
    }

    @Test
    fun shouldFilter_whenFilterIsUnexpired() {
        assertEquals(
            Filter.Action.HIDE,
            filterModel.shouldFilterStatus(
                mockStatus(content = "content matching unexpired filter should be filtered")
            )?.action
        )
    }

    companion object {
        fun mockStatus(
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
                content = content,
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
                } else {
                    arrayListOf()
                },
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
                        ownVotes = emptyList()
                    )
                } else {
                    null
                },
                card = null,
                language = null,
                filtered = emptyList()
            )
        }
    }
}
