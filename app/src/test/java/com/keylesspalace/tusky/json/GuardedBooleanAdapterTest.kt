package com.keylesspalace.tusky.json

import com.google.gson.Gson
import com.keylesspalace.tusky.entity.Relationship
import org.junit.Assert.assertEquals
import org.junit.Test

class GuardedBooleanAdapterTest {

    private val gson = Gson()

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
            gson.fromJson(jsonInput, Relationship::class.java)
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
            gson.fromJson(jsonInput, Relationship::class.java)
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
            gson.fromJson(jsonInput, Relationship::class.java)
        )
    }
}
