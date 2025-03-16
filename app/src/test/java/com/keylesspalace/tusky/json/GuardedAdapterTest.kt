package com.keylesspalace.tusky.json

import com.keylesspalace.tusky.entity.PreviewCard
import com.keylesspalace.tusky.entity.Relationship
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalStdlibApi::class)
class GuardedAdapterTest {

    private val moshi = Moshi.Builder()
        .add(GuardedAdapter.ANNOTATION_FACTORY)
        .add(Date::class.java, Rfc3339DateJsonAdapter())
        .build()

    @Test
    fun `should deserialize Relationship when attribute 'subscribing' is a boolean`() {
        val jsonInput = """
            {
              "id": "1",
              "following": true,
              "showing_reblogs": true,
              "notifying": false,
              "followed_by": true,
              "blocking": false,
              "blocked_by": false,
              "muting": false,
              "muting_notifications": false,
              "requested": false,
              "domain_blocking": false,
              "endorsed": false,
              "note": "Hi",
              "subscribing": true
            }
        """.trimIndent()

        assertEquals(
            Relationship(
                id = "1",
                following = true,
                followedBy = true,
                blocking = false,
                muting = false,
                mutingNotifications = false,
                requested = false,
                showingReblogs = true,
                subscribing = true,
                blockingDomain = false,
                note = "Hi",
                notifying = false
            ),
            moshi.adapter<Relationship>().fromJson(jsonInput)
        )
    }

    @Test
    fun `should deserialize Relationship when attribute 'subscribing' is an object`() {
        val jsonInput = """
            {
              "id": "2",
              "following": true,
              "showing_reblogs": true,
              "notifying": false,
              "followed_by": true,
              "blocking": false,
              "blocked_by": false,
              "muting": false,
              "muting_notifications": false,
              "requested": false,
              "domain_blocking": false,
              "endorsed": false,
              "note": "Hi",
              "subscribing": { }
            }
        """.trimIndent()

        assertEquals(
            Relationship(
                id = "2",
                following = true,
                followedBy = true,
                blocking = false,
                muting = false,
                mutingNotifications = false,
                requested = false,
                showingReblogs = true,
                subscribing = null,
                blockingDomain = false,
                note = "Hi",
                notifying = false
            ),
            moshi.adapter<Relationship>().fromJson(jsonInput)
        )
    }

    @Test
    fun `should deserialize Relationship when attribute 'subscribing' does not exist`() {
        val jsonInput = """
            {
              "id": "3",
              "following": true,
              "showing_reblogs": true,
              "notifying": false,
              "followed_by": true,
              "blocking": false,
              "blocked_by": false,
              "muting": false,
              "muting_notifications": false,
              "requested": false,
              "domain_blocking": false,
              "endorsed": false,
              "note": "Hi"
            }
        """.trimIndent()

        assertEquals(
            Relationship(
                id = "3",
                following = true,
                followedBy = true,
                blocking = false,
                muting = false,
                mutingNotifications = false,
                requested = false,
                showingReblogs = true,
                subscribing = null,
                blockingDomain = false,
                note = "Hi",
                notifying = false
            ),
            moshi.adapter<Relationship>().fromJson(jsonInput)
        )
    }

    @Test
    fun `should deserialize PreviewCard when attribute 'published_at' is invalid`() {
        // https://github.com/tuskyapp/Tusky/issues/4992
        val jsonInput = """{

            "url": "https://www.cbc.ca/amp/1.7484477",
            "title": "Canada reconsidering F-35 purchase amid tensions with Washington, says minister",
            "description": "Canada is looking at cancelling a major portion of its purchase of U.S.-built F-35 stealth fighters and plans on opening talks with rival aircraft makers, Defence Minister Bill Blair said.",
            "language": "en",
            "type": "link",
            "author_name": "Murray Brewster",
            "author_url": "",
            "provider_name": "CBC",
            "provider_url": "",
            "html": "",
            "width": 0,
            "height": 0,
            "image": "https://files.mastodon.social/cache/preview_cards/images/137/231/445/original/0f63297db3ac7362.jpg",
            "image_description": "",
            "embed_url": "",
            "blurhash": "U74#eeXoK9nLrVWZS+nfXVaenKkXTOjErobx",
            "published_at": "57171-08-04T06:31:30.000Z",
            "authors": [
            {
                "name": "Murray Brewster",
                "url": "",
                "account": null
            }
            ]

        }"""
        assertEquals(
            PreviewCard(
                url = "https://www.cbc.ca/amp/1.7484477",
                title = "Canada reconsidering F-35 purchase amid tensions with Washington, says minister",
                description = "Canada is looking at cancelling a major portion of its purchase of U.S.-built F-35 stealth fighters and plans on opening talks with rival aircraft makers, Defence Minister Bill Blair said.",
                type = "link",
                authorName = "Murray Brewster",
                providerName = "CBC",
                image = "https://files.mastodon.social/cache/preview_cards/images/137/231/445/original/0f63297db3ac7362.jpg",
                width = 0,
                height = 0,
                blurhash = "U74#eeXoK9nLrVWZS+nfXVaenKkXTOjErobx",
                publishedAt = null,
            ),
            moshi.adapter<PreviewCard>().fromJson(jsonInput)
        )
    }
}
