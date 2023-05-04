package com.keylesspalace.tusky.components.notifications

import android.app.NotificationManager
import com.keylesspalace.tusky.db.AccountManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.onFailure
import at.connyduck.calladapter.networkresult.onSuccess
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.login.LoginActivity
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.NotificationSubscribeResult
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.CryptoUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.unifiedpush.android.connector.UnifiedPush
import retrofit2.HttpException
import javax.inject.Inject

class PushNotificationManager @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val accountManager: AccountManager,
    private val sharedPreferences: SharedPreferences,
    private val context: Context
) {
    private val distributors: List<String> = UnifiedPush.getDistributors(context)

    private fun isUnifiedPushAvailable(): Boolean {
        return distributors.isNotEmpty()
    }

    fun canEnablePushNotifications(): Boolean =
        isUnifiedPushAvailable() && !anyAccountNeedsMigration()

    // TODO the fallback has no place here
    suspend fun enablePushNotificationsWithFallback() {
        if (!canEnablePushNotifications()) {
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
                enableUnifiedPushNotificationsForAccount(it)
            } else {
                disableUnifiedPushNotificationsForAccount(it)
            }
        }
    }

    private suspend fun enableUnifiedPushNotificationsForAccount(account: AccountEntity) {
        var currentSubscription: NotificationSubscribeResult? = null
        mastodonApi.pushNotificationSubscription(
            "Bearer ${account.accessToken}",
            account.domain
        ).fold({
            currentSubscription = it
        }, {
            if (!(it is HttpException && it.code() == 404)) {
                Log.e(TAG, "Cannot get push subscription for account " + account.id + ": " + it.message, it)

                return
            }
            // else this is alright; there is no subscription on server
        })

        // TODO compare server key?
        // TODO only update below if notifications changed

        if (getActiveDistributor(account) != null) {
            // Already registered, update the subscription to match notification settings
            updateUnifiedPushSubscription(account)
        } else {
            if (!account.unifiedDistributorName.isNullOrEmpty()) {
                // This does nothing as the distributor is not present anymore to receive this message
//            UnifiedPush.unregisterApp(context, account.id.toString())

                // When changing the local UP distributor this is necessary first to enable the following callbacks (i. e. onNewEndpoint)
                unregisterUnifiedPushEndpoint(account)
            }

            UnifiedPush.registerAppWithDialog(context, account.id.toString(), features = arrayListOf(UnifiedPush.FEATURE_BYTES_MESSAGE))
            // TODO? if this does not result in a call to registerUnifiedPushEndpoint, something has failed
        }
    }

    fun disableUnifiedPushNotificationsForAccount(account: AccountEntity) {
        if (account.unifiedDistributorName == null) {
            // TODO Hm, this is the third synced location for UP information (others being the server and preference store of UP locally)
            //   all could be out-of-sync

            return
        }

        // TODO this probably does nothing (distributor to handle this is missing); so especially the db fields are not cleared
        UnifiedPush.unregisterApp(context, account.id.toString())

        // TODO only do this instead?
//    if (!account.unifiedDistributorName.isNullOrEmpty()) {
//        unregisterUnifiedPushEndpoint(api, accountManager, account)
//    }
    }

    fun getActiveDistributor(account: AccountEntity): String? {
        if (account.unifiedDistributorName.isNullOrEmpty()) {
            return null
        }

        // TODO there is also a GET api for push notifications (current push settings on server)
        //   which could be checked as well or alternatively.

        val distributors = UnifiedPush.getDistributors(context)

        return distributors.find { it == account.unifiedDistributorName }
    }

    private fun disablePushNotifications() {
        accountManager.accounts.forEach {
            disableUnifiedPushNotificationsForAccount(it)
        }
    }

    fun disableAllNotifications() {
        disablePushNotifications()
        NotificationHelper.disablePullNotifications(context)
    }

    private fun buildAlertSubscriptionData(account: AccountEntity): Map<String, Boolean> =
        buildMap {
            Notification.Type.visibleTypes.forEach {
                put("data[alerts][${it.presentation}]", NotificationHelper.filterNotification(account, it, context))
            }
        }

    // Called by UnifiedPush callback
    suspend fun registerUnifiedPushEndpoint(
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

        mastodonApi.subscribePushNotifications(
            "Bearer ${account.accessToken}",
            account.domain,
            endpoint,
            keyPair.pubkey,
            auth,
            buildAlertSubscriptionData(account)
        ).onFailure { throwable ->
            Log.w(TAG, "Error setting push endpoint for account ${account.id}", throwable)
            disableUnifiedPushNotificationsForAccount(account)
        }.onSuccess {
            Log.d(TAG, "UnifiedPush registration succeeded for account ${account.id}")

            val distributor = UnifiedPush.getDistributor(context)

            // TODO? none of these are used ever again (except distributor name)
            account.unifiedDistributorName = distributor
            account.pushPubKey = keyPair.pubkey
            account.pushPrivKey = keyPair.privKey
            account.pushAuth = auth
            account.pushServerKey = it.serverKey
            account.unifiedPushUrl = endpoint

            accountManager.saveAccount(account)
        }
    }

    // Synchronize the enabled / disabled state of notifications with server-side subscription
    suspend fun updateUnifiedPushSubscription(account: AccountEntity) {
        withContext(Dispatchers.IO) {
            mastodonApi.updatePushNotificationSubscription(
                "Bearer ${account.accessToken}",
                account.domain,
                buildAlertSubscriptionData(account)
            ).onSuccess {
                Log.d(TAG, "UnifiedPush subscription updated for account ${account.id}")

                account.pushServerKey = it.serverKey
                accountManager.saveAccount(account)
            }
        }
    }

    suspend fun unregisterUnifiedPushEndpoint(account: AccountEntity) {
        withContext(Dispatchers.IO) {
            mastodonApi.unsubscribePushNotifications("Bearer ${account.accessToken}", account.domain)
                .onFailure { throwable ->
                    Log.w(TAG, "Error unregistering push endpoint for account " + account.id, throwable)
                }
                .onSuccess {
                    Log.d(TAG, "UnifiedPush unregistration succeeded for account " + account.id)

                    account.unifiedDistributorName = null
                    account.unifiedPushUrl = ""
                    account.pushServerKey = ""
                    account.pushAuth = ""
                    account.pushPrivKey = ""
                    account.pushPubKey = ""

                    accountManager.saveAccount(account)
                }
        }
    }

    // TODO reduce this "migration feature" here

    private fun anyAccountNeedsMigration(): Boolean =
        accountManager.accounts.any(::accountNeedsMigration)

    private fun accountNeedsMigration(account: AccountEntity): Boolean =
        !account.oauthScopes.contains("push")

    fun currentAccountNeedsMigration(): Boolean =
        accountManager.activeAccount?.let(::accountNeedsMigration) ?: false

    fun showMigrationNoticeIfNecessary(
        parent: View,
        anchorView: View?
    ) {
        // No point showing anything if we cannot enable it
        if (!isUnifiedPushAvailable()) return
        if (!anyAccountNeedsMigration()) return

        if (sharedPreferences.getBoolean(KEY_PUSH_MIGRATION_NOTICE_DISMISSED, false)) return

        Snackbar.make(parent, R.string.tips_push_notification_migration, Snackbar.LENGTH_INDEFINITE)
            .setAnchorView(anchorView)
            .setAction(R.string.action_details) { showMigrationExplanationDialog() }
            .show()
    }

    private fun showMigrationExplanationDialog() {
        AlertDialog.Builder(context).apply {
            if (currentAccountNeedsMigration()) {
                setMessage(R.string.dialog_push_notification_migration)
                setPositiveButton(R.string.title_migration_relogin) { _, _ ->
                    context.startActivity(LoginActivity.getIntent(context, LoginActivity.MODE_MIGRATION))
                }
            } else {
                setMessage(R.string.dialog_push_notification_migration_other_accounts)
            }
            setNegativeButton(R.string.action_dismiss) { dialog, _ ->
                // NOTE there is a corresponding preference in AccountPreferencesFragment (only depending on currentAccountNeedsMigration()).
                sharedPreferences.edit().putBoolean(KEY_PUSH_MIGRATION_NOTICE_DISMISSED, true).apply()
                dialog.dismiss()
            }
            show()
        }
    }

    companion object {
        const val TAG = "PushNotificationManager"
        private const val KEY_PUSH_MIGRATION_NOTICE_DISMISSED = "migration_notice_dismissed"
    }
}
