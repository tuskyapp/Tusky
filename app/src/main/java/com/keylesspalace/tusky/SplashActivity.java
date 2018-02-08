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

package com.keylesspalace.tusky;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.keylesspalace.tusky.db.AccountEntity;
import com.keylesspalace.tusky.util.NotificationHelper;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* Determine whether the user is currently logged in, and if so go ahead and load the
         * timeline. Otherwise, start the activity_login screen. */

        NotificationHelper.deleteLegacyNotificationChannels(this);

        AccountEntity activeAccount = TuskyApplication.getAccountManager().getActiveAccount();

        Intent intent;
        if (activeAccount != null) {
            intent = new Intent(this, MainActivity.class);
        } else {
            intent = LoginActivity.getIntent(this, false);
        }
        startActivity(intent);
        finish();
    }
}
