/* Copyright 2018 Conny Duck
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

package com.keylesspalace.tusky.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.components.notifications.NotificationHelper
import dagger.android.AndroidInjection
import javax.inject.Inject

class NotificationClearBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var accountManager: AccountManager

    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)

        val accountId = intent.getLongExtra(NotificationHelper.ACCOUNT_ID, -1)

        val account = accountManager.getAccountById(accountId)
        if (account != null) {
            account.activeNotifications = "[]"
            accountManager.saveAccount(account)
        }
    }

}
