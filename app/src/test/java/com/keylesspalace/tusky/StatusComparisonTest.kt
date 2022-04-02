package com.keylesspalace.tusky

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.viewdata.StatusViewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class StatusComparisonTest {

    @Test
    fun `two equal statuses - should be equal`() {
        assertEquals(createStatus(), createStatus())
    }

    @Test
    fun `status with different id - should not be equal`() {
        assertNotEquals(createStatus(), createStatus(id = "987654321"))
    }

    @Test
    fun `status with different content - should not be equal`() {
        val content: String = """
            \u003cp\u003e\u003cspan class=\"h-card\"\u003e\u003ca href=\"https://mastodon.social/@ConnyDuck\" class=\"u-url mention\" rel=\"nofollow noopener noreferrer\" target=\"_blank\"\u003e@\u003cspan\u003eConnyDuck@mastodon.social\u003c/span\u003e\u003c/a\u003e\u003c/span\u003e 123\u003c/p\u003e
        """.trimIndent()
        assertNotEquals(createStatus(), createStatus(content = content))
    }

    @Test
    fun `accounts with different notes in json - should be equal because notes are not relevant for timelines`() {
        assertEquals(createStatus(note = "Test"), createStatus(note = "Test 123456"))
    }

    private val gson = Gson()

    @Test
    fun `two equal status view data - should be equal`() {
        val viewdata1 = StatusViewData.Concrete(
            status = createStatus(),
            isExpanded = false,
            isShowingContent = false,
            isCollapsed = false
        )
        val viewdata2 = StatusViewData.Concrete(
            status = createStatus(),
            isExpanded = false,
            isShowingContent = false,
            isCollapsed = false
        )
        assertEquals(viewdata1, viewdata2)
    }

    @Test
    fun `status view data with different isExpanded - should not be equal`() {
        val viewdata1 = StatusViewData.Concrete(
            status = createStatus(),
            isExpanded = true,
            isShowingContent = false,
            isCollapsed = false
        )
        val viewdata2 = StatusViewData.Concrete(
            status = createStatus(),
            isExpanded = false,
            isShowingContent = false,
            isCollapsed = false
        )
        assertNotEquals(viewdata1, viewdata2)
    }

    @Test
    fun `status view data with different statuses- should not be equal`() {
        val viewdata1 = StatusViewData.Concrete(
            status = createStatus(content = "whatever"),
            isExpanded = true,
            isShowingContent = false,
            isCollapsed = false
        )
        val viewdata2 = StatusViewData.Concrete(
            status = createStatus(),
            isExpanded = false,
            isShowingContent = false,
            isCollapsed = false
        )
        assertNotEquals(viewdata1, viewdata2)
    }

    private fun createStatus(
        id: String = "123456",
        content: String = """
            \u003cp\u003e\u003cspan class=\"h-card\"\u003e\u003ca href=\"https://mastodon.social/@ConnyDuck\" class=\"u-url mention\" rel=\"nofollow noopener noreferrer\" target=\"_blank\"\u003e@\u003cspan\u003eConnyDuck@mastodon.social\u003c/span\u003e\u003c/a\u003e\u003c/span\u003e Hi\u003c/p\u003e
        """.trimIndent(),
        note: String = ""
    ): Status {
        val statusJson = """
            {
                "id": "$id",
                "created_at": "2022-02-26T09:54:45.000Z",
                "in_reply_to_id": null,
                "in_reply_to_account_id": null,
                "sensitive": false,
                "spoiler_text": "",
                "visibility": "public",
                "language": null,
                "uri": "https://pixelfed.social/p/connyduck/403124983655733325",
                "url": "https://pixelfed.social/p/connyduck/403124983655733325",
                "replies_count": 3,
                "reblogs_count": 28,
                "favourites_count": 6,
                "edited_at": null,
                "favourited": true,
                "reblogged": false,
                "muted": false,
                "bookmarked": false,
                "content": "$content",
                "reblog": null,
                "account": {
                    "id": "419352",
                    "username": "connyduck",
                    "acct": "connyduck@pixelfed.social",
                    "display_name": "Conny Duck",
                    "locked": false,
                    "bot": false,
                    "discoverable": false,
                    "group": false,
                    "created_at": "2018-08-14T00:00:00.000Z",
                    "note": "$note",
                    "url": "https://pixelfed.social/connyduck",
                    "avatar": "https://files.mastodon.social/cache/accounts/avatars/000/419/352/original/31ce660c53962e0c.jpeg",
                    "avatar_static": "https://files.mastodon.social/cache/accounts/avatars/000/419/352/original/31ce660c53962e0c.jpeg",
                    "header": "https://mastodon.social/headers/original/missing.png",
                    "header_static": "https://mastodon.social/headers/original/missing.png",
                    "followers_count": 2,
                    "following_count": 0,
                    "statuses_count": 70,
                    "last_status_at": "2022-03-07",
                    "emojis": [],
                    "fields": []
                },
                "media_attachments": [
                    {
                        "id": "107863694400783337",
                        "type": "image",
                        "url": "https://files.mastodon.social/cache/media_attachments/files/107/863/694/400/783/337/original/71c5bad1756bbc8f.jpg",
                        "preview_url": "https://files.mastodon.social/cache/media_attachments/files/107/863/694/400/783/337/small/71c5bad1756bbc8f.jpg",
                        "remote_url": "https://pixelfed-prod.nyc3.cdn.digitaloceanspaces.com/public/m/_v2/1138/affc38a2b-1c5f41/JRKoMNoj6dKa/9mXs0Fetvj4KwRbKypt8C1PZNVd7d3dQqod4roLZ.jpg",
                        "preview_remote_url": null,
                        "text_url": null,
                        "meta": {
                            "original": {
                                "width": 1371,
                                "height": 1080,
                                "size": "1371x1080",
                                "aspect": 1.2694444444444444
                            },
                            "small": {
                                "width": 451,
                                "height": 355,
                                "size": "451x355",
                                "aspect": 1.2704225352112677
                            }
                        },
                        "description": "Oilpainting of a kingfisher, photographed on my easel",
                        "blurhash": "UUG91|?wxHV@WTkDs.V?xZa_I:WBNFR*WBRk"
                    },
                    {
                        "id": "107863694727565058",
                        "type": "image",
                        "url": "https://files.mastodon.social/cache/media_attachments/files/107/863/694/727/565/058/original/68daef05be7ac6b6.jpg",
                        "preview_url": "https://files.mastodon.social/cache/media_attachments/files/107/863/694/727/565/058/small/68daef05be7ac6b6.jpg",
                        "remote_url": "https://pixelfed-prod.nyc3.cdn.digitaloceanspaces.com/public/m/_v2/1138/affc38a2b-1c5f41/nBVJUnrEIjfO/M6i8GSP44Iv230KWXnMpvVobOqASXY3EkImyxySS.jpg",
                        "preview_remote_url": null,
                        "text_url": null,
                        "meta": {
                            "original": {
                                "width": 1087,
                                "height": 1080,
                                "size": "1087x1080",
                                "aspect": 1.0064814814814815
                            },
                            "small": {
                                "width": 401,
                                "height": 398,
                                "size": "401x398",
                                "aspect": 1.0075376884422111
                            }
                        },
                        "description": "Oilpainting of a kingfisher",
                        "blurhash": "U89u4pPJ4:SoJ6NNnkoxoBtSx0Von-RiNgt8"
                    }
                ],
                "mentions": [],
                "tags": [],
                "emojis": [],
                "card": null,
                "poll": null
            }
        """.trimIndent()
        return gson.fromJson(statusJson, Status::class.java)
    }
}
