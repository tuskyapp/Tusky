/* Copyright 2018 Conny Duck
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import tech.bigfig.roma.db.AccountManager
import tech.bigfig.roma.util.NotificationHelper
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
