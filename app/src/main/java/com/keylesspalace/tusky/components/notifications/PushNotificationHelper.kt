/* Copyright 2022 Tusky contributors
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

@file:JvmName("PushNotificationHelper")
package com.keylesspalace.tusky.components.notifications

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import at.connyduck.calladapter.networkresult.onFailure
import at.connyduck.calladapter.networkresult.onSuccess
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.login.LoginActivity
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.CryptoUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.unifiedpush.android.connector.UnifiedPush

private const val TAG = "PushNotificationHelper"

private const val KEY_MIGRATION_NOTICE_DISMISSED = "migration_notice_dismissed"

private fun anyAccountNeedsMigration(accountManager: AccountManager): Boolean =
    accountManager.accounts.any(::accountNeedsMigration)

private fun accountNeedsMigration(account: AccountEntity): Boolean =
    !account.oauthScopes.contains("push")

fun currentAccountNeedsMigration(accountManager: AccountManager): Boolean =
    accountManager.activeAccount?.let(::accountNeedsMigration) ?: false

fun showMigrationNoticeIfNecessary(
    context: Context,
    parent: View,
    anchorView: View?,
    accountManager: AccountManager
) {
    // No point showing anything if we cannot enable it
    if (!isUnifiedPushAvailable(context)) return
    if (!anyAccountNeedsMigration(accountManager)) return

    val pm = PreferenceManager.getDefaultSharedPreferences(context)
    if (pm.getBoolean(KEY_MIGRATION_NOTICE_DISMISSED, false)) return

    Snackbar.make(parent, R.string.tips_push_notification_migration, Snackbar.LENGTH_INDEFINITE)
        .setAnchorView(anchorView)
        .setAction(R.string.action_details) { showMigrationExplanationDialog(context, accountManager) }
        .show()
}

private fun showMigrationExplanationDialog(context: Context, accountManager: AccountManager) {
    AlertDialog.Builder(context).apply {
        if (currentAccountNeedsMigration(accountManager)) {
            setMessage(R.string.dialog_push_notification_migration)
            setPositiveButton(R.string.title_migration_relogin) { _, _ ->
                context.startActivity(LoginActivity.getIntent(context, LoginActivity.MODE_MIGRATION))
            }
        } else {
            setMessage(R.string.dialog_push_notification_migration_other_accounts)
        }
        setNegativeButton(R.string.action_dismiss) { dialog, _ ->
            val pm = PreferenceManager.getDefaultSharedPreferences(context)
            pm.edit().putBoolean(KEY_MIGRATION_NOTICE_DISMISSED, true).apply()
            dialog.dismiss()
        }
        show()
    }
}

private suspend fun enableUnifiedPushNotificationsForAccount(context: Context, api: MastodonApi, accountManager: AccountManager, account: AccountEntity) {
    if (isUnifiedPushNotificationEnabledForAccount(account)) {
        // Already registered, update the subscription to match notification settings
        updateUnifiedPushSubscription(context, api, accountManager, account)
    } else {
        UnifiedPush.registerAppWithDialog(context, account.id.toString(), features = arrayListOf(UnifiedPush.FEATURE_BYTES_MESSAGE))
    }
}

fun disableUnifiedPushNotificationsForAccount(context: Context, account: AccountEntity) {
    if (!isUnifiedPushNotificationEnabledForAccount(account)) {
        // Not registered
        return
    }

    UnifiedPush.unregisterApp(context, account.id.toString())
}

fun isUnifiedPushNotificationEnabledForAccount(account: AccountEntity): Boolean =
    account.unifiedPushUrl.isNotEmpty()

private fun isUnifiedPushAvailable(context: Context): Boolean =
    UnifiedPush.getDistributors(context).isNotEmpty()

fun canEnablePushNotifications(context: Context, accountManager: AccountManager): Boolean =
    isUnifiedPushAvailable(context) && !anyAccountNeedsMigration(accountManager)

suspend fun enablePushNotificationsWithFallback(context: Context, api: MastodonApi, accountManager: AccountManager) {
    if (!canEnablePushNotifications(context, accountManager)) {
        // No UP distributors
        NotificationHelper.enablePullNotifications(context)
        return
    }

    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    accountManager.accounts.forEach {
        val notificationGroupEnabled = Build.VERSION.SDK_INT < 28 ||
            nm.getNotificationChannelGroup(it.identifier)?.isBlocked == false
        val shouldEnable = it.notificationsEnabled && notificationGroupEnabled

        if (shouldEnable) {
            enableUnifiedPushNotificationsForAccount(context, api, accountManager, it)
        } else {
            disableUnifiedPushNotificationsForAccount(context, it)
        }
    }
}

private fun disablePushNotifications(context: Context, accountManager: AccountManager) {
    accountManager.accounts.forEach {
        disableUnifiedPushNotificationsForAccount(context, it)
    }
}

fun disableAllNotifications(context: Context, accountManager: AccountManager) {
    disablePushNotifications(context, accountManager)
    NotificationHelper.disablePullNotifications(context)
}

private fun buildSubscriptionData(context: Context, account: AccountEntity): Map<String, Boolean> =
    buildMap {
        Notification.Type.asList.forEach {
            put("data[alerts][${it.presentation}]", NotificationHelper.filterNotification(account, it, context))
        }
    }

// Called by UnifiedPush callback
suspend fun registerUnifiedPushEndpoint(
    context: Context,
    api: MastodonApi,
    accountManager: AccountManager,
    account: AccountEntity,
    endpoint: String
) = withContext(Dispatchers.IO) {

    // Generate a prime256v1 key pair for WebPush
    // Decryption is unimplemented for now, since Mastodon uses an old WebPush
    // standard which does not send needed information for decryption in the payload
    // This makes it not directly compatible with UnifiedPush
    // As of now, we use it purely as a way to trigger a pull
    val keyPair = CryptoUtil.generateECKeyPair(CryptoUtil.CURVE_PRIME256_V1)
    val auth = CryptoUtil.secureRandomBytesEncoded(16)

    api.subscribePushNotifications(
        "Bearer ${account.accessToken}", account.domain,
        endpoint, keyPair.pubkey, auth,
        buildSubscriptionData(context, account)
    ).onFailure { throwable ->
        Log.w(TAG, "Error setting push endpoint for account ${account.id}", throwable)
        disableUnifiedPushNotificationsForAccount(context, account)
    }.onSuccess {
        Log.d(TAG, "UnifiedPush registration succeeded for account ${account.id}")

        account.pushPubKey = keyPair.pubkey
        account.pushPrivKey = keyPair.privKey
        account.pushAuth = auth
        account.pushServerKey = it.serverKey
        account.unifiedPushUrl = endpoint
        accountManager.saveAccount(account)
    }
}

// Synchronize the enabled / disabled state of notifications with server-side subscription
suspend fun updateUnifiedPushSubscription(context: Context, api: MastodonApi, accountManager: AccountManager, account: AccountEntity) {
    withContext(Dispatchers.IO) {
        api.updatePushNotificationSubscription(
            "Bearer ${account.accessToken}", account.domain,
            buildSubscriptionData(context, account)
        ).onSuccess {
            Log.d(TAG, "UnifiedPush subscription updated for account ${account.id}")

            account.pushServerKey = it.serverKey
            accountManager.saveAccount(account)
        }
    }
}

suspend fun unregisterUnifiedPushEndpoint(api: MastodonApi, accountManager: AccountManager, account: AccountEntity) {
    withContext(Dispatchers.IO) {
        api.unsubscribePushNotifications("Bearer ${account.accessToken}", account.domain)
            .onFailure { throwable ->
                Log.w(TAG, "Error unregistering push endpoint for account " + account.id, throwable)
            }
            .onSuccess {
                Log.d(TAG, "UnifiedPush unregistration succeeded for account " + account.id)
                // Clear the URL in database
                account.unifiedPushUrl = ""
                account.pushServerKey = ""
                account.pushAuth = ""
                account.pushPrivKey = ""
                account.pushPubKey = ""
                accountManager.saveAccount(account)
            }
    }
}
