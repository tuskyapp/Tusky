package com.keylesspalace.tusky.components.notifications

import android.app.NotificationManager
import com.keylesspalace.tusky.db.AccountManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
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

// TODO architecture-wise: the NotificationHelper should probably be a NotificationManager which either uses
//   pull or push notifications (two detail implementations?).
//   You can see current problems for example in the old NotificationPreferencesFragment.onCreatePreferences()
//   which only would use pull notifications if the notifications option is enabled.
class PushNotificationManager @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val accountManager: AccountManager,
    private val sharedPreferences: SharedPreferences,
    private val context: Context
): Preference.SummaryProvider<Preference> {

    companion object {
        const val TAG = "PushNotificationManager"
        private const val KEY_PUSH_MIGRATION_NOTICE_DISMISSED = "migration_notice_dismissed"
    }

    // TODO? must be changed/extended when distributors are installed or uninstalled on-the-fly?
    //   Or there must be an "restart app fully" possibility.
    private val distributors: List<String> = UnifiedPush.getDistributors(context)

    private fun isUnifiedPushAvailable(): Boolean {
        return distributors.isNotEmpty()
    }

    // TODO! there should be an actual decision (possibility) to say "I don't want to use push notifications for Tusky".

    fun canEnablePushNotifications(): Boolean =
        isUnifiedPushAvailable() && !anyAccountNeedsMigration()

    fun hasPushNotificationsEnabled(account: AccountEntity): Boolean =
        isUnifiedPushAvailable() && !account.unifiedDistributorName.isNullOrEmpty()

    suspend fun enablePushNotifications() {
        if (!canEnablePushNotifications()) {
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
        // TODO/NOTE these api request(s) here take quite some time (100-1000ms each for GET for my 3 instances)

        val currentSubscription = getActiveSubscription(account)

        if (currentSubscription != null && hasActiveDistributor(account)) {
            val alertData = buildAlertsMap(account)

            if (alertData != currentSubscription.alerts) {
                // Update the subscription to match notification settings
                updateUnifiedPushSubscription(account)
            }
        } else {
            // When changing the local UP distributor this is necessary first to enable the following callbacks (i. e. onNewEndpoint);
            //   make sure this is done in any inconsistent case (is not too often and doesn't hurt).
            unregisterUnifiedPushEndpoint(account)

            UnifiedPush.registerAppWithDialog(context, account.id.toString(), features = arrayListOf(UnifiedPush.FEATURE_BYTES_MESSAGE))
            // TODO? if this does not result in a call to registerUnifiedPushEndpoint, something has failed
        }
    }

    private suspend fun getActiveSubscription(account: AccountEntity): NotificationSubscribeResult? {
        mastodonApi.pushNotificationSubscription(
            "Bearer ${account.accessToken}",
            account.domain
        ).fold({
            if (account.unifiedPushUrl.isNotEmpty() && it.endpoint != account.unifiedPushUrl) {
                Log.w(TAG, "Server push endpoint does not match previously registered one: "+it.endpoint+" vs. "+account.unifiedPushUrl)
                // TODO there should be a user information or at least an occurrence log entry

                return null

                // TODO / NOTE this case could also happen regularly if you use the same account on two different devices
                //   the server will only support (?) on subscription but you will need two for two devices (?)
            }

            return it
        }, {
            if (!(it is HttpException && it.code() == 404)) {
                Log.e(TAG, "Cannot get push subscription for account " + account.id + ": " + it.message, it)

                return null
            }

            // else this is alright; there is no subscription on server
            return null
        })
    }

    suspend fun disableUnifiedPushNotificationsForAccount(account: AccountEntity) {
        if (account.unifiedDistributorName == null) {
            return
        }

        unregisterUnifiedPushEndpoint(account)

        // this probably does nothing (distributor to handle this is missing)
        UnifiedPush.unregisterApp(context, account.id.toString())
    }

    private fun hasActiveDistributor(account: AccountEntity): Boolean {
        if (account.unifiedDistributorName.isNullOrEmpty()) {
            return false
        }

        val distributors = UnifiedPush.getDistributors(context)

        return distributors.find { it == account.unifiedDistributorName } != null
    }

    private fun getDistributorUsedByApp(): String? {
        return UnifiedPush.getDistributor(context).ifEmpty { null }
    }

    suspend fun disableAllNotifications() {
        accountManager.accounts.forEach {
            disableUnifiedPushNotificationsForAccount(it)
        }
    }

    private fun buildAlertsMap(account: AccountEntity): Map<String, Boolean> =
        buildMap {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            Notification.Type.visibleTypes.forEach {
                put(it.presentation, NotificationHelper.filterNotification(notificationManager, account, it))
            }
        }

    private fun buildAlertSubscriptionData(account: AccountEntity): Map<String, Boolean> =
        buildAlertsMap(account).mapKeys { "data[alerts][${it.key}]" }

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

            // TODO? none of these are used ever again (except distributor name and endpoint)
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
            val alertsData = buildAlertSubscriptionData(account)

            mastodonApi.updatePushNotificationSubscription(
                "Bearer ${account.accessToken}",
                account.domain,
                alertsData
            ).onSuccess {
                Log.d(TAG, "UnifiedPush subscription updated for account ${account.id}")

                account.pushServerKey = it.serverKey
                accountManager.saveAccount(account)
            }
        }
    }

    suspend fun unregisterUnifiedPushEndpoint(account: AccountEntity) {
        withContext(Dispatchers.IO) {
            // NOTE this is also possible (successful) when there is no subscription present on the server.

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

    // TODO reduce this "migration feature" here; the code should probably also always check for "push" in the
    //   authorization as a normal feature - it could be missing by intent?

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
            // TODO what if another account needs migration? Only finally dismissing is possible?

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

    override fun provideSummary(preference: Preference): CharSequence? {
        return when(val distributor = getDistributorUsedByApp()) {
            "io.heckel.ntfy" -> "NTFY"
            "org.unifiedpush.distributor.fcm" -> "UP-FCM"
            "org.unifiedpush.distributor.nextpush " -> "NextPush"
            else -> distributor
        }
    }
}
