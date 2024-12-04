package com.keylesspalace.tusky.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.keylesspalace.tusky.di.StorageModule
import com.keylesspalace.tusky.entity.Emoji
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [34])
@RunWith(AndroidJUnit4::class)
class MigrationsTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun testMigrations() = runTest {
        /** the db name must match the one in [StorageModule.providesDatabase] */
        val db = migrationHelper.createDatabase("tuskyDB", 10)
        val moshi = Moshi.Builder().build()

        val id = 1L
        val domain = "domain.site"
        val token = "token"
        val active = true
        val accountId = "accountId"
        val username = "username"
        val emoji = moshi.adapter<List<Emoji>>(Types.newParameterizedType(List::class.java, Emoji::class.java), emptySet()).toJson(
            listOf(
                Emoji(
                    shortcode = "testemoji",
                    url = "https://some.url",
                    staticUrl = "https://some.url",
                    visibleInPicker = true,
                    category = null
                )
            )
        )
        val values = arrayOf(
            id, domain, token, active, accountId, username, "Display Name",
            "https://picture.url", true, true, true, true, true, true, true,
            true, "1000", "[]", emoji, 0, false,
            false, true
        )

        db.execSQL(
            "INSERT OR REPLACE INTO `AccountEntity`(`id`,`domain`,`accessToken`,`isActive`," +
                "`accountId`,`username`,`displayName`,`profilePictureUrl`,`notificationsEnabled`," +
                "`notificationsMentioned`,`notificationsFollowed`,`notificationsReblogged`," +
                "`notificationsFavorited`,`notificationSound`,`notificationVibration`," +
                "`notificationLight`,`lastNotificationId`,`activeNotifications`,`emojis`," +
                "`defaultPostPrivacy`,`defaultMediaSensitivity`,`alwaysShowSensitiveMedia`," +
                "`mediaPreviewEnabled`) " +
                "VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            values
        )

        db.close()

        // Room will run all migrations and validate the scheme afterwards
        val roomDb = StorageModule.providesDatabase(
            InstrumentationRegistry.getInstrumentation().context,
            Converters(moshi)
        )

        val account = roomDb.accountDao().allAccounts().first().first()

        roomDb.close()

        assertEquals(id, account.id)
        assertEquals(domain, account.domain)
        assertEquals(token, account.accessToken)
        assertEquals(active, account.isActive)
        assertEquals(accountId, account.accountId)
        assertEquals(username, account.username)
    }
}
