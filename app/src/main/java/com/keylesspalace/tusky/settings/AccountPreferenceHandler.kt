package com.keylesspalace.tusky.settings

import androidx.preference.PreferenceDataStore
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager

class AccountPreferenceHandler(
    private val account: AccountEntity,
    private val accountManager: AccountManager,
    private val eventHub: EventHub,
) : PreferenceDataStore() {

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return when (key) {
            PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA -> account.alwaysShowSensitiveMedia
            PrefKeys.ALWAYS_OPEN_SPOILER -> account.alwaysOpenSpoiler
            PrefKeys.MEDIA_PREVIEW_ENABLED -> account.mediaPreviewEnabled
            else -> defValue
        }
    }

    override fun putBoolean(key: String, value: Boolean) {
        when (key) {
            PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA -> account.alwaysShowSensitiveMedia = value
            PrefKeys.ALWAYS_OPEN_SPOILER -> account.alwaysOpenSpoiler = value
            PrefKeys.MEDIA_PREVIEW_ENABLED -> account.mediaPreviewEnabled = value
        }

        accountManager.saveAccount(account)

        eventHub.dispatch(PreferenceChangedEvent(key))
    }
}
