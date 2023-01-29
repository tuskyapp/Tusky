package com.keylesspalace.tusky.settings

import androidx.preference.PreferenceDataStore
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager

// TODO this must be possible with DI / @Inject somehow?

class AccountPreferenceHandler(
    private val account: AccountEntity,
    private val accountManager: AccountManager,
    private val eventHub: EventHub,
): PreferenceDataStore() {

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return when(key) {
            PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA -> account.alwaysShowSensitiveMedia
            PrefKeys.ALWAYS_OPEN_SPOILER -> account.alwaysOpenSpoiler
            PrefKeys.MEDIA_PREVIEW_ENABLED -> account.mediaPreviewEnabled
            else -> defValue
        }
    }

    override fun putBoolean(key: String?, value: Boolean) {
        if (key == null) {
            return;
        }

        when (key) {
            PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA -> account.alwaysShowSensitiveMedia = value
            PrefKeys.ALWAYS_OPEN_SPOILER -> account.alwaysOpenSpoiler = value
            PrefKeys.MEDIA_PREVIEW_ENABLED -> account.mediaPreviewEnabled = value
        }

        accountManager.saveAccount(account)

        eventHub.dispatch(PreferenceChangedEvent(key))
    }

//    companion object {
//        fun newInstance(accountE: AccountEntity): AccountPreferenceDataStore {
//            return AccountPreferenceDataStore().apply { account = accountE }
//        }
//    }
}