/* Copyright 2017 Andrew Dawson
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
 * see <http://www.gnu.org/licenses>. */
@file:Suppress("ClassName")

package com.keylesspalace.tusky.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.keylesspalace.tusky.FEDERATED
import com.keylesspalace.tusky.HOME
import com.keylesspalace.tusky.LOCAL
import com.keylesspalace.tusky.NOTIFICATIONS
import com.keylesspalace.tusky.components.conversation.ConversationEntity
import com.keylesspalace.tusky.db.AppDatabase.MIGRATION_49_50
import java.io.File

/**
 * DB version & declare DAO
 */
@Database(
    entities = [
        DraftEntity::class,
        AccountEntity::class,
        InstanceEntity::class,
        TimelineStatusEntity::class,
        TimelineAccountEntity::class,
        ConversationEntity::class,
    ],
    version = 58,
    autoMigrations = [
        AutoMigration(from = 48, to = 49),
        AutoMigration(from = 49, to = 50, spec = MIGRATION_49_50::class),
        AutoMigration(from = 50, to = 51),
        AutoMigration(from = 51, to = 52),
        AutoMigration(from = 53, to = 54),
        AutoMigration(from = 56, to = 58),
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun instanceDao(): InstanceDao
    abstract fun conversationDao(): ConversationsDao
    abstract fun timelineDao(): TimelineDao
    abstract fun draftDao(): DraftDao

    class Migration25_26(private val oldDraftDirectory: File?) : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE  `TootEntity`")

            if (oldDraftDirectory != null && oldDraftDirectory.isDirectory) {
                val oldDraftFiles = oldDraftDirectory.listFiles()
                if (oldDraftFiles != null) {
                    for (file in oldDraftFiles) {
                        if (!file.isDirectory) {
                            file.delete()
                        }
                    }
                }
            }
        }
    }

    @DeleteColumn(tableName = "AccountEntity", columnName = "activeNotifications")
    class MIGRATION_49_50 : AutoMigrationSpec

    companion object {
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE TootEntity2 (uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, text TEXT, urls TEXT, contentWarning TEXT);")
                db.execSQL("INSERT INTO TootEntity2 SELECT * FROM TootEntity;")
                db.execSQL("DROP TABLE TootEntity;")
                db.execSQL("ALTER TABLE TootEntity2 RENAME TO TootEntity;")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE TootEntity ADD COLUMN inReplyToId TEXT")
                db.execSQL("ALTER TABLE TootEntity ADD COLUMN inReplyToText TEXT")
                db.execSQL("ALTER TABLE TootEntity ADD COLUMN inReplyToUsername TEXT")
                db.execSQL("ALTER TABLE TootEntity ADD COLUMN visibility INTEGER")
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE `AccountEntity` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`domain` TEXT NOT NULL, `accessToken` TEXT NOT NULL, " +
                        "`isActive` INTEGER NOT NULL, `accountId` TEXT NOT NULL, " +
                        "`username` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                        "`profilePictureUrl` TEXT NOT NULL, " +
                        "`notificationsEnabled` INTEGER NOT NULL, " +
                        "`notificationsMentioned` INTEGER NOT NULL, " +
                        "`notificationsFollowed` INTEGER NOT NULL, " +
                        "`notificationsReblogged` INTEGER NOT NULL, " +
                        "`notificationsFavorited` INTEGER NOT NULL, " +
                        "`notificationSound` INTEGER NOT NULL, " +
                        "`notificationVibration` INTEGER NOT NULL, " +
                        "`notificationLight` INTEGER NOT NULL, " +
                        "`lastNotificationId` TEXT NOT NULL, " +
                        "`activeNotifications` TEXT NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX `index_AccountEntity_domain_accountId` ON `AccountEntity` (`domain`, `accountId`)")
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `EmojiListEntity` (`instance` TEXT NOT NULL, `emojiList` TEXT NOT NULL, PRIMARY KEY(`instance`))")
            }
        }

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `InstanceEntity` (`instance` TEXT NOT NULL, `emojiList` TEXT, `maximumTootCharacters` INTEGER, PRIMARY KEY(`instance`))")
                db.execSQL("INSERT OR REPLACE INTO `InstanceEntity` SELECT `instance`,`emojiList`, NULL FROM `EmojiListEntity`;")
                db.execSQL("DROP TABLE `EmojiListEntity`;")
            }
        }

        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `emojis` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `TootEntity` ADD COLUMN `descriptions` TEXT DEFAULT '[]'")
            }
        }

        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `defaultPostPrivacy` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `defaultMediaSensitivity` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `alwaysShowSensitiveMedia` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `mediaPreviewEnabled` INTEGER NOT NULL DEFAULT '1'")
            }
        }

        val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `TimelineAccountEntity` (" +
                        "`serverId` TEXT NOT NULL, " +
                        "`timelineUserId` INTEGER NOT NULL, " +
                        "`instance` TEXT NOT NULL, " +
                        "`localUsername` TEXT NOT NULL, " +
                        "`username` TEXT NOT NULL, " +
                        "`displayName` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, " +
                        "`avatar` TEXT NOT NULL, " +
                        "`emojis` TEXT NOT NULL," +
                        "PRIMARY KEY(`serverId`, `timelineUserId`))"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `TimelineStatusEntity` (" +
                        "`serverId` TEXT NOT NULL, " +
                        "`url` TEXT, " +
                        "`timelineUserId` INTEGER NOT NULL, " +
                        "`authorServerId` TEXT," +
                        "`instance` TEXT, " +
                        "`inReplyToId` TEXT, " +
                        "`inReplyToAccountId` TEXT, " +
                        "`content` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`emojis` TEXT, " +
                        "`reblogsCount` INTEGER NOT NULL, " +
                        "`favouritesCount` INTEGER NOT NULL, " +
                        "`reblogged` INTEGER NOT NULL, " +
                        "`favourited` INTEGER NOT NULL, " +
                        "`sensitive` INTEGER NOT NULL, " +
                        "`spoilerText` TEXT, " +
                        "`visibility` INTEGER, " +
                        "`attachments` TEXT, " +
                        "`mentions` TEXT, " +
                        "`application` TEXT, " +
                        "`reblogServerId` TEXT, " +
                        "`reblogAccountId` TEXT," +
                        " PRIMARY KEY(`serverId`, `timelineUserId`)," +
                        " FOREIGN KEY(`authorServerId`, `timelineUserId`) REFERENCES `TimelineAccountEntity`(`serverId`, `timelineUserId`) " +
                        "ON UPDATE NO ACTION ON DELETE NO ACTION )"
                )
                db.execSQL(
                    "CREATE  INDEX IF NOT EXISTS" +
                        "`index_TimelineStatusEntity_authorServerId_timelineUserId` " +
                        "ON `TimelineStatusEntity` (`authorServerId`, `timelineUserId`)"
                )
            }
        }

        val MIGRATION_11_12: Migration = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val defaultTabs: String = HOME + ";" +
                    NOTIFICATIONS + ";" +
                    LOCAL + ";" +
                    FEDERATED
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `tabPreferences` TEXT NOT NULL DEFAULT '$defaultTabs'")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ConversationEntity` (" +
                        "`accountId` INTEGER NOT NULL, " +
                        "`id` TEXT NOT NULL, " +
                        "`accounts` TEXT NOT NULL, " +
                        "`unread` INTEGER NOT NULL, " +
                        "`s_id` TEXT NOT NULL, " +
                        "`s_url` TEXT, " +
                        "`s_inReplyToId` TEXT, " +
                        "`s_inReplyToAccountId` TEXT, " +
                        "`s_account` TEXT NOT NULL, " +
                        "`s_content` TEXT NOT NULL, " +
                        "`s_createdAt` INTEGER NOT NULL, " +
                        "`s_emojis` TEXT NOT NULL, " +
                        "`s_favouritesCount` INTEGER NOT NULL, " +
                        "`s_favourited` INTEGER NOT NULL, " +
                        "`s_sensitive` INTEGER NOT NULL, " +
                        "`s_spoilerText` TEXT NOT NULL, " +
                        "`s_attachments` TEXT NOT NULL, " +
                        "`s_mentions` TEXT NOT NULL, " +
                        "`s_showingHiddenContent` INTEGER NOT NULL, " +
                        "`s_expanded` INTEGER NOT NULL, " +
                        "`s_collapsible` INTEGER NOT NULL, " +
                        "`s_collapsed` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`, `accountId`))"
                )
            }
        }

        val MIGRATION_12_13: Migration = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `TimelineAccountEntity`")
                db.execSQL("DROP TABLE IF EXISTS `TimelineStatusEntity`")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `TimelineAccountEntity` (" +
                        "`serverId` TEXT NOT NULL, " +
                        "`timelineUserId` INTEGER NOT NULL, " +
                        "`localUsername` TEXT NOT NULL, " +
                        "`username` TEXT NOT NULL, " +
                        "`displayName` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, " +
                        "`avatar` TEXT NOT NULL, " +
                        "`emojis` TEXT NOT NULL," +
                        "PRIMARY KEY(`serverId`, `timelineUserId`))"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `TimelineStatusEntity` (" +
                        "`serverId` TEXT NOT NULL, " +
                        "`url` TEXT, " +
                        "`timelineUserId` INTEGER NOT NULL, " +
                        "`authorServerId` TEXT," +
                        "`inReplyToId` TEXT, " +
                        "`inReplyToAccountId` TEXT, " +
                        "`content` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`emojis` TEXT, " +
                        "`reblogsCount` INTEGER NOT NULL, " +
                        "`favouritesCount` INTEGER NOT NULL, " +
                        "`reblogged` INTEGER NOT NULL, " +
                        "`favourited` INTEGER NOT NULL, " +
                        "`sensitive` INTEGER NOT NULL, " +
                        "`spoilerText` TEXT, " +
                        "`visibility` INTEGER, " +
                        "`attachments` TEXT, " +
                        "`mentions` TEXT, " +
                        "`application` TEXT, " +
                        "`reblogServerId` TEXT, " +
                        "`reblogAccountId` TEXT," +
                        " PRIMARY KEY(`serverId`, `timelineUserId`)," +
                        " FOREIGN KEY(`authorServerId`, `timelineUserId`) REFERENCES `TimelineAccountEntity`(`serverId`, `timelineUserId`) " +
                        "ON UPDATE NO ACTION ON DELETE NO ACTION )"
                )
                db.execSQL(
                    "CREATE  INDEX IF NOT EXISTS" +
                        "`index_TimelineStatusEntity_authorServerId_timelineUserId` " +
                        "ON `TimelineStatusEntity` (`authorServerId`, `timelineUserId`)"
                )
            }
        }

        val MIGRATION_10_13: Migration = object : Migration(10, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_11_12.migrate(db)
                MIGRATION_12_13.migrate(db)
            }
        }

        val MIGRATION_13_14: Migration = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `notificationsFilter` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_14_15: Migration = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `TimelineStatusEntity` ADD COLUMN `poll` TEXT")
                db.execSQL("ALTER TABLE `ConversationEntity` ADD COLUMN `s_poll` TEXT")
            }
        }

        val MIGRATION_15_16: Migration = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `notificationsPolls` INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_16_17: Migration = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `TimelineAccountEntity` ADD COLUMN `bot` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_17_18: Migration = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `alwaysOpenSpoiler` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_18_19: Migration = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `maxPollOptions` INTEGER")
                db.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `maxPollOptionLength` INTEGER")

                db.execSQL("ALTER TABLE `TootEntity` ADD COLUMN `poll` TEXT")
            }
        }

        val MIGRATION_19_20: Migration = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `TimelineStatusEntity` ADD COLUMN `bookmarked` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `ConversationEntity` ADD COLUMN `s_bookmarked` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_20_21: Migration = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `version` TEXT")
            }
        }

        val MIGRATION_21_22: Migration = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `notificationsFollowRequested` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_22_23: Migration = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `TimelineStatusEntity` ADD COLUMN `muted` INTEGER")
            }
        }

        val MIGRATION_23_24: Migration = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `notificationsSubscriptions` INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_24_25: Migration = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `DraftEntity` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`accountId` INTEGER NOT NULL, " +
                        "`inReplyToId` TEXT," +
                        "`content` TEXT," +
                        "`contentWarning` TEXT," +
                        "`sensitive` INTEGER NOT NULL," +
                        "`visibility` INTEGER NOT NULL," +
                        "`attachments` TEXT NOT NULL," +
                        "`poll` TEXT," +
                        "`failedToSend` INTEGER NOT NULL)"
                )
            }
        }

        val MIGRATION_26_27: Migration = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `ConversationEntity` ADD COLUMN `s_muted`  INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_27_28: Migration = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `TimelineAccountEntity`")
                db.execSQL("DROP TABLE IF EXISTS `TimelineStatusEntity`")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `TimelineAccountEntity` (" +
                        "`serverId` TEXT NOT NULL," +
                        "`timelineUserId` INTEGER NOT NULL," +
                        "`localUsername` TEXT NOT NULL," +
                        "`username` TEXT NOT NULL," +
                        "`displayName` TEXT NOT NULL," +
                        "`url` TEXT NOT NULL," +
                        "`avatar` TEXT NOT NULL," +
                        "`emojis` TEXT NOT NULL," +
                        "`bot` INTEGER NOT NULL," +
                        "PRIMARY KEY(`serverId`, `timelineUserId`) )"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `TimelineStatusEntity` (" +
                        "`serverId` TEXT NOT NULL," +
                        "`url` TEXT," +
                        "`timelineUserId` INTEGER NOT NULL," +
                        "`authorServerId` TEXT," +
                        "`inReplyToId` TEXT," +
                        "`inReplyToAccountId` TEXT," +
                        "`content` TEXT," +
                        "`createdAt` INTEGER NOT NULL," +
                        "`emojis` TEXT," +
                        "`reblogsCount` INTEGER NOT NULL," +
                        "`favouritesCount` INTEGER NOT NULL," +
                        "`reblogged` INTEGER NOT NULL," +
                        "`bookmarked` INTEGER NOT NULL," +
                        "`favourited` INTEGER NOT NULL," +
                        "`sensitive` INTEGER NOT NULL," +
                        "`spoilerText` TEXT NOT NULL," +
                        "`visibility` INTEGER NOT NULL," +
                        "`attachments` TEXT," +
                        "`mentions` TEXT," +
                        "`application` TEXT," +
                        "`reblogServerId` TEXT," +
                        "`reblogAccountId` TEXT," +
                        "`poll` TEXT," +
                        "`muted` INTEGER," +
                        "`expanded` INTEGER NOT NULL," +
                        "`contentCollapsed` INTEGER NOT NULL," +
                        "`contentShowing` INTEGER NOT NULL," +
                        "`pinned` INTEGER NOT NULL," +
                        "PRIMARY KEY(`serverId`, `timelineUserId`)," +
                        "FOREIGN KEY(`authorServerId`, `timelineUserId`) REFERENCES `TimelineAccountEntity`(`serverId`, `timelineUserId`)" +
                        "ON UPDATE NO ACTION ON DELETE NO ACTION )"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_TimelineStatusEntity_authorServerId_timelineUserId`" +
                        "ON `TimelineStatusEntity` (`authorServerId`, `timelineUserId`)"
                )
            }
        }

        val MIGRATION_28_29: Migration = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `ConversationEntity` ADD COLUMN `s_tags` TEXT")
                db.execSQL("ALTER TABLE `TimelineStatusEntity` ADD COLUMN `tags` TEXT")
            }
        }

        val MIGRATION_29_30: Migration = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `charactersReservedPerUrl` INTEGER")
                db.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `minPollDuration` INTEGER")
                db.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `maxPollDuration` INTEGER")
            }
        }

        val MIGRATION_30_31: Migration = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // no actual scheme change, but placeholder ids are now used differently so the cache needs to be cleared to avoid bugs

                db.execSQL("DELETE FROM `TimelineAccountEntity`")
                db.execSQL("DELETE FROM `TimelineStatusEntity`")
            }
        }

        val MIGRATION_31_32: Migration = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `notificationsSignUps` INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_32_33: Migration = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ConversationEntity lost the s_collapsible column
                // since SQLite does not support removing columns and it is just a cache table, we recreate the whole table.

                db.execSQL("DROP TABLE `ConversationEntity`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ConversationEntity` (" +
                        "`accountId` INTEGER NOT NULL," +
                        "`id` TEXT NOT NULL," +
                        "`accounts` TEXT NOT NULL," +
                        "`unread` INTEGER NOT NULL," +
                        "`s_id` TEXT NOT NULL," +
                        "`s_url` TEXT," +
                        "`s_inReplyToId` TEXT," +
                        "`s_inReplyToAccountId` TEXT," +
                        "`s_account` TEXT NOT NULL," +
                        "`s_content` TEXT NOT NULL," +
                        "`s_createdAt` INTEGER NOT NULL," +
                        "`s_emojis` TEXT NOT NULL," +
                        "`s_favouritesCount` INTEGER NOT NULL," +
                        "`s_favourited` INTEGER NOT NULL," +
                        "`s_bookmarked` INTEGER NOT NULL," +
                        "`s_sensitive` INTEGER NOT NULL," +
                        "`s_spoilerText` TEXT NOT NULL," +
                        "`s_attachments` TEXT NOT NULL," +
                        "`s_mentions` TEXT NOT NULL," +
                        "`s_tags` TEXT," +
                        "`s_showingHiddenContent` INTEGER NOT NULL," +
                        "`s_expanded` INTEGER NOT NULL," +
                        "`s_collapsed` INTEGER NOT NULL," +
                        "`s_muted` INTEGER NOT NULL," +
                        "`s_poll` TEXT," +
                        "PRIMARY KEY(`id`, `accountId`))"
                )
            }
        }

        val MIGRATION_33_34: Migration = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `notificationsUpdates` INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_34_35: Migration = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `TimelineStatusEntity` ADD COLUMN `card` TEXT")
            }
        }

        val MIGRATION_35_36: Migration = object : Migration(35, 36) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `oauthScopes`  TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `unifiedPushUrl`  TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `pushPubKey`  TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `pushPrivKey`  TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `pushAuth`  TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `pushServerKey`  TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_36_37: Migration = object : Migration(36, 37) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `TimelineStatusEntity` ADD COLUMN `repliesCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `ConversationEntity` ADD COLUMN `s_repliesCount` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_37_38: Migration = object : Migration(37, 38) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // database needs to be cleaned because the ConversationAccountEntity got a new attribute
                db.execSQL("DELETE FROM `ConversationEntity`")
                db.execSQL("ALTER TABLE `ConversationEntity` ADD COLUMN `order` INTEGER NOT NULL DEFAULT 0")

                // timestamps are now serialized differently so all cache tables that contain them need to be cleaned
                db.execSQL("DELETE FROM `TimelineStatusEntity`")
            }
        }

        val MIGRATION_38_39: Migration = object : Migration(38, 39) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `clientId` TEXT")
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `clientSecret` TEXT")
            }
        }

        val MIGRATION_39_40: Migration = object : Migration(39, 40) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `videoSizeLimit` INTEGER")
                db.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `imageSizeLimit` INTEGER")
                db.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `imageMatrixLimit` INTEGER")
                db.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `maxMediaAttachments` INTEGER")
                db.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `maxFields` INTEGER")
                db.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `maxFieldNameLength` INTEGER")
                db.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `maxFieldValueLength` INTEGER")
            }
        }

        val MIGRATION_40_41: Migration = object : Migration(40, 41) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `DraftEntity` ADD COLUMN `scheduledAt` TEXT")
            }
        }

        val MIGRATION_41_42: Migration = object : Migration(41, 42) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `DraftEntity` ADD COLUMN `language` TEXT")
                db.execSQL("ALTER TABLE `TimelineStatusEntity` ADD COLUMN `language` TEXT")
                db.execSQL("ALTER TABLE `ConversationEntity` ADD COLUMN `s_language` TEXT")
            }
        }

        val MIGRATION_42_43: Migration = object : Migration(42, 43) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `defaultPostLanguage` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_43_44: Migration = object : Migration(43, 44) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `notificationsReports` INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_44_45: Migration = object : Migration(44, 45) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `TimelineStatusEntity` ADD COLUMN `editedAt` INTEGER")
                db.execSQL("ALTER TABLE `ConversationEntity` ADD COLUMN `s_editedAt` INTEGER")
            }
        }

        val MIGRATION_45_46: Migration = object : Migration(45, 46) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `DraftEntity` ADD COLUMN `statusId` TEXT")
            }
        }

        val MIGRATION_46_47: Migration = object : Migration(46, 47) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `DraftEntity` ADD COLUMN `failedToSendNew` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_47_48: Migration = object : Migration(47, 48) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `TimelineStatusEntity` ADD COLUMN `filtered` TEXT")
            }
        }

        /**
         * TabData.TRENDING was renamed to TabData.TRENDING_TAGS, and the text
         * representation was changed from "Trending" to "TrendingTags".
         */
        val MIGRATION_52_53: Migration = object : Migration(52, 53) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE `AccountEntity` SET `tabpreferences` = REPLACE(tabpreferences, 'Trending:', 'TrendingTags:')")
            }
        }

        val MIGRATION_54_56: Migration = object : Migration(54, 56) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `isShowHomeBoosts` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `isShowHomeReplies` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `isShowHomeSelfBoosts` INTEGER NOT NULL DEFAULT 1")
            }
        }
    }
}
