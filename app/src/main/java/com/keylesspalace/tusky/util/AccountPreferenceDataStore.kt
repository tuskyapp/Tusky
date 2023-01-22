package com.keylesspalace.tusky.util

import androidx.preference.PreferenceDataStore
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.settings.PrefKeys

class AccountPreferenceDataStore(private val account: AccountEntity): PreferenceDataStore() {
    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return when(key) {
            PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA -> account.alwaysShowSensitiveMedia
            else -> defValue
        }
    }

    override fun putBoolean(key: String?, value: Boolean) {
        // This is not called at the moment as Preference.persistBoolean() checks first if there is a discrepancy
        //   (which there is not because the setOnPreferenceChangeListener already changed it in the account object)

        when (key) {
            PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA -> account.alwaysShowSensitiveMedia = value
        }
    }
}