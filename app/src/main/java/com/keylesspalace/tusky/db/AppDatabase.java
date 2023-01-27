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

package com.keylesspalace.tusky.db;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.keylesspalace.tusky.TabDataKt;
import com.keylesspalace.tusky.components.conversation.ConversationEntity;

import java.io.File;

/**
 * DB version & declare DAO
 */
@Database(entities = { DraftEntity.class, AccountEntity.class, InstanceEntity.class, TimelineStatusEntity.class,
                TimelineAccountEntity.class,  ConversationEntity.class
        }, version = 47)
public abstract class AppDatabase extends RoomDatabase {

    public abstract AccountDao accountDao();
    public abstract InstanceDao instanceDao();
    public abstract ConversationsDao conversationDao();
    public abstract TimelineDao timelineDao();
    public abstract DraftDao draftDao();

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE TootEntity2 (uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, text TEXT, urls TEXT, contentWarning TEXT);");
            database.execSQL("INSERT INTO TootEntity2 SELECT * FROM TootEntity;");
            database.execSQL("DROP TABLE TootEntity;");
            database.execSQL("ALTER TABLE TootEntity2 RENAME TO TootEntity;");
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE TootEntity ADD COLUMN inReplyToId TEXT");
            database.execSQL("ALTER TABLE TootEntity ADD COLUMN inReplyToText TEXT");
            database.execSQL("ALTER TABLE TootEntity ADD COLUMN inReplyToUsername TEXT");
            database.execSQL("ALTER TABLE TootEntity ADD COLUMN visibility INTEGER");
        }
    };

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE `AccountEntity` (" +
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
                    "`activeNotifications` TEXT NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX `index_AccountEntity_domain_accountId` ON `AccountEntity` (`domain`, `accountId`)");
        }
    };

    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `EmojiListEntity` (`instance` TEXT NOT NULL, `emojiList` TEXT NOT NULL, PRIMARY KEY(`instance`))");
        }
    };

    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `InstanceEntity` (`instance` TEXT NOT NULL, `emojiList` TEXT, `maximumTootCharacters` INTEGER, PRIMARY KEY(`instance`))");
            database.execSQL("INSERT OR REPLACE INTO `InstanceEntity` SELECT `instance`,`emojiList`, NULL FROM `EmojiListEntity`;");
            database.execSQL("DROP TABLE `EmojiListEntity`;");
        }
    };

    public static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `emojis` TEXT NOT NULL DEFAULT '[]'");
        }
    };

    public static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `TootEntity` ADD COLUMN `descriptions` TEXT DEFAULT '[]'");
        }
    };

    public static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `defaultPostPrivacy` INTEGER NOT NULL DEFAULT 1");
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `defaultMediaSensitivity` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `alwaysShowSensitiveMedia` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `mediaPreviewEnabled` INTEGER NOT NULL DEFAULT '1'");
        }
    };

    public static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `TimelineAccountEntity` (" +
                    "`serverId` TEXT NOT NULL, " +
                    "`timelineUserId` INTEGER NOT NULL, " +
                    "`instance` TEXT NOT NULL, " +
                    "`localUsername` TEXT NOT NULL, " +
                    "`username` TEXT NOT NULL, " +
                    "`displayName` TEXT NOT NULL, " +
                    "`url` TEXT NOT NULL, " +
                    "`avatar` TEXT NOT NULL, " +
                    "`emojis` TEXT NOT NULL," +
                    "PRIMARY KEY(`serverId`, `timelineUserId`))");

            database.execSQL("CREATE TABLE IF NOT EXISTS `TimelineStatusEntity` (" +
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
                    "ON UPDATE NO ACTION ON DELETE NO ACTION )");
            database.execSQL("CREATE  INDEX IF NOT EXISTS" +
                    "`index_TimelineStatusEntity_authorServerId_timelineUserId` " +
                    "ON `TimelineStatusEntity` (`authorServerId`, `timelineUserId`)");
        }
    };

    public static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            String defaultTabs = TabDataKt.HOME + ";" +
                    TabDataKt.NOTIFICATIONS + ";" +
                    TabDataKt.LOCAL + ";" +
                    TabDataKt.FEDERATED;
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `tabPreferences` TEXT NOT NULL DEFAULT '" + defaultTabs + "'");

            database.execSQL("CREATE TABLE IF NOT EXISTS `ConversationEntity` (" +
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
                    "PRIMARY KEY(`id`, `accountId`))");

        }
    };

    public static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {

            database.execSQL("DROP TABLE IF EXISTS `TimelineAccountEntity`");
            database.execSQL("DROP TABLE IF EXISTS `TimelineStatusEntity`");

            database.execSQL("CREATE TABLE IF NOT EXISTS `TimelineAccountEntity` (" +
                    "`serverId` TEXT NOT NULL, " +
                    "`timelineUserId` INTEGER NOT NULL, " +
                    "`localUsername` TEXT NOT NULL, " +
                    "`username` TEXT NOT NULL, " +
                    "`displayName` TEXT NOT NULL, " +
                    "`url` TEXT NOT NULL, " +
                    "`avatar` TEXT NOT NULL, " +
                    "`emojis` TEXT NOT NULL," +
                    "PRIMARY KEY(`serverId`, `timelineUserId`))");

            database.execSQL("CREATE TABLE IF NOT EXISTS `TimelineStatusEntity` (" +
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
                    "ON UPDATE NO ACTION ON DELETE NO ACTION )");
            database.execSQL("CREATE  INDEX IF NOT EXISTS" +
                    "`index_TimelineStatusEntity_authorServerId_timelineUserId` " +
                    "ON `TimelineStatusEntity` (`authorServerId`, `timelineUserId`)");
        }
    };

    public static final Migration MIGRATION_10_13 = new Migration(10, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            MIGRATION_11_12.migrate(database);
            MIGRATION_12_13.migrate(database);
        }
    };

    public static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `notificationsFilter` TEXT NOT NULL DEFAULT '[]'");
        }
    };

    public static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `TimelineStatusEntity` ADD COLUMN `poll` TEXT");
            database.execSQL("ALTER TABLE `ConversationEntity` ADD COLUMN `s_poll` TEXT");
        }
    };

    public static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `notificationsPolls` INTEGER NOT NULL DEFAULT 1");
        }
    };

    public static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `TimelineAccountEntity` ADD COLUMN `bot` INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `alwaysOpenSpoiler` INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static final Migration MIGRATION_18_19 = new Migration(18, 19) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `maxPollOptions` INTEGER");
            database.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `maxPollOptionLength` INTEGER");

            database.execSQL("ALTER TABLE `TootEntity` ADD COLUMN `poll` TEXT");
        }
    };

    public static final Migration MIGRATION_19_20 = new Migration(19, 20) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `TimelineStatusEntity` ADD COLUMN `bookmarked` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `ConversationEntity` ADD COLUMN `s_bookmarked` INTEGER NOT NULL DEFAULT 0");
        }

    };

    public static final Migration MIGRATION_20_21 = new Migration(20, 21) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `version` TEXT");
        }
    };

    public static final Migration MIGRATION_21_22 = new Migration(21, 22) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `notificationsFollowRequested` INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static final Migration MIGRATION_22_23 = new Migration(22, 23) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `TimelineStatusEntity` ADD COLUMN `muted` INTEGER");
        }
    };
    
    public static final Migration MIGRATION_23_24 = new Migration(23, 24) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `notificationsSubscriptions` INTEGER NOT NULL DEFAULT 1");
        }
    };

    public static final Migration MIGRATION_24_25 = new Migration(24, 25) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
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
            );
        }
    };

    public static class Migration25_26 extends Migration {

        private final File oldDraftDirectory;

        public Migration25_26(File oldDraftDirectory) {
            super(25, 26);
            this.oldDraftDirectory = oldDraftDirectory;
        }

        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE  `TootEntity`");

            if (oldDraftDirectory != null && oldDraftDirectory.isDirectory()) {
                File[] oldDraftFiles = oldDraftDirectory.listFiles();
                if (oldDraftFiles != null) {
                    for (File file : oldDraftFiles) {
                        if (!file.isDirectory()) {
                            file.delete();
                        }
                    }
                }

            }
        }
    }

    public static final Migration MIGRATION_26_27 = new Migration(26, 27) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `ConversationEntity` ADD COLUMN `s_muted`  INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static final Migration MIGRATION_27_28 = new Migration(27, 28) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {

            database.execSQL("DROP TABLE IF EXISTS `TimelineAccountEntity`");
            database.execSQL("DROP TABLE IF EXISTS `TimelineStatusEntity`");

            database.execSQL("CREATE TABLE IF NOT EXISTS `TimelineAccountEntity` (" +
                    "`serverId` TEXT NOT NULL," +
                    "`timelineUserId` INTEGER NOT NULL," +
                    "`localUsername` TEXT NOT NULL," +
                    "`username` TEXT NOT NULL," +
                    "`displayName` TEXT NOT NULL," +
                    "`url` TEXT NOT NULL," +
                    "`avatar` TEXT NOT NULL," +
                    "`emojis` TEXT NOT NULL," +
                    "`bot` INTEGER NOT NULL," +
                    "PRIMARY KEY(`serverId`, `timelineUserId`) )");

            database.execSQL("CREATE TABLE IF NOT EXISTS `TimelineStatusEntity` (" +
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
                    "ON UPDATE NO ACTION ON DELETE NO ACTION )");

            database.execSQL("CREATE INDEX IF NOT EXISTS `index_TimelineStatusEntity_authorServerId_timelineUserId`" +
                    "ON `TimelineStatusEntity` (`authorServerId`, `timelineUserId`)");
        }
    };

    public static final Migration MIGRATION_28_29 = new Migration(28, 29) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `ConversationEntity` ADD COLUMN `s_tags` TEXT");
            database.execSQL("ALTER TABLE `TimelineStatusEntity` ADD COLUMN `tags` TEXT");
        }
    };

    public static final Migration MIGRATION_29_30 = new Migration(29, 30) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `charactersReservedPerUrl` INTEGER");
            database.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `minPollDuration` INTEGER");
            database.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `maxPollDuration` INTEGER");
        }
    };

    public static final Migration MIGRATION_30_31 = new Migration(30, 31) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {

            // no actual scheme change, but placeholder ids are now used differently so the cache needs to be cleared to avoid bugs
            database.execSQL("DELETE FROM `TimelineAccountEntity`");
            database.execSQL("DELETE FROM `TimelineStatusEntity`");
        }
    };

    public static final Migration MIGRATION_31_32 = new Migration(31, 32) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `notificationsSignUps` INTEGER NOT NULL DEFAULT 1");
        }
    };

    public static final Migration MIGRATION_32_33 = new Migration(32, 33) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {

            // ConversationEntity lost the s_collapsible column
            // since SQLite does not support removing columns and it is just a cache table, we recreate the whole table.
            database.execSQL("DROP TABLE `ConversationEntity`");
            database.execSQL("CREATE TABLE IF NOT EXISTS `ConversationEntity` (" +
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
                    "PRIMARY KEY(`id`, `accountId`))");
        }
    };

    public static final Migration MIGRATION_33_34 = new Migration(33, 34) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `notificationsUpdates` INTEGER NOT NULL DEFAULT 1");
        }
    };

    public static final Migration MIGRATION_34_35 = new Migration(34, 35) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `TimelineStatusEntity` ADD COLUMN `card` TEXT");
        }
    };

    public static final Migration MIGRATION_35_36 = new Migration(35, 36) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `oauthScopes`  TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `unifiedPushUrl`  TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `pushPubKey`  TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `pushPrivKey`  TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `pushAuth`  TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `pushServerKey`  TEXT NOT NULL DEFAULT ''");
        }
    };

    public static final Migration MIGRATION_36_37 = new Migration(36, 37) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `TimelineStatusEntity` ADD COLUMN `repliesCount` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `ConversationEntity` ADD COLUMN `s_repliesCount` INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static final Migration MIGRATION_37_38 = new Migration(37, 38) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // database needs to be cleaned because the ConversationAccountEntity got a new attribute
            database.execSQL("DELETE FROM `ConversationEntity`");
            database.execSQL("ALTER TABLE `ConversationEntity` ADD COLUMN `order` INTEGER NOT NULL DEFAULT 0");

            // timestamps are now serialized differently so all cache tables that contain them need to be cleaned
            database.execSQL("DELETE FROM `TimelineStatusEntity`");
        }
    };

    public static final Migration MIGRATION_38_39 = new Migration(38, 39) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `clientId` TEXT");
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `clientSecret` TEXT");
        }
    };

    public static final Migration MIGRATION_39_40 = new Migration(39, 40) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `videoSizeLimit` INTEGER");
            database.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `imageSizeLimit` INTEGER");
            database.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `imageMatrixLimit` INTEGER");
            database.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `maxMediaAttachments` INTEGER");
            database.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `maxFields` INTEGER");
            database.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `maxFieldNameLength` INTEGER");
            database.execSQL("ALTER TABLE `InstanceEntity` ADD COLUMN `maxFieldValueLength` INTEGER");
        }
    };

    public static final Migration MIGRATION_40_41 = new Migration(40, 41) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `DraftEntity` ADD COLUMN `scheduledAt` TEXT");
        }
    };

    public static final Migration MIGRATION_41_42 = new Migration(41, 42) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `DraftEntity` ADD COLUMN `language` TEXT");
            database.execSQL("ALTER TABLE `TimelineStatusEntity` ADD COLUMN `language` TEXT");
            database.execSQL("ALTER TABLE `ConversationEntity` ADD COLUMN `s_language` TEXT");
        }
    };

    public static final Migration MIGRATION_42_43 = new Migration(42, 43) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `defaultPostLanguage` TEXT NOT NULL DEFAULT ''");
        }
    };

    public static final Migration MIGRATION_43_44 = new Migration(43, 44) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `AccountEntity` ADD COLUMN `notificationsReports` INTEGER NOT NULL DEFAULT 1");
        }
    };

    public static final Migration MIGRATION_44_45 = new Migration(44, 45) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `TimelineStatusEntity` ADD COLUMN `editedAt` INTEGER");
            database.execSQL("ALTER TABLE `ConversationEntity` ADD COLUMN `s_editedAt` INTEGER");
        }
    };

    public static final Migration MIGRATION_45_46 = new Migration(45, 46) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `DraftEntity` ADD COLUMN `statusId` TEXT");
        }
    };

    public static final Migration MIGRATION_46_47 = new Migration(46, 47) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `DraftEntity` ADD COLUMN `failedToSendNew` INTEGER NOT NULL DEFAULT 0");
        }
    };
}
