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

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.migration.Migration;
import android.support.annotation.NonNull;

/**
 * DB version & declare DAO
 */

@Database(entities = {TootEntity.class, AccountEntity.class, InstanceEntity.class}, version = 10, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract TootDao tootDao();
    public abstract AccountDao accountDao();
    public abstract InstanceDao instanceDao();

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

}