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

package com.keylesspalace.tusky.db

import android.content.SharedPreferences
import android.util.Log
import androidx.room.withTransaction
import com.keylesspalace.tusky.db.dao.AccountDao
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.di.ApplicationScope
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.settings.PrefKeys
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * This class caches the account database and handles all account related operations
 * @author ConnyDuck
 */

private const val TAG = "AccountManager"

@Singleton
class AccountManager @Inject constructor(
    private val db: AppDatabase,
    private val preferences: SharedPreferences,
    @ApplicationScope private val applicationScope: CoroutineScope
) {

    private val accountDao: AccountDao = db.accountDao()

    val accountsFlow: StateFlow<List<AccountEntity>> = runBlocking {
        accountDao.allAccounts()
            .onEach {
                Log.d(TAG, "accounts updated: $it")
            }
            .onCompletion {
                Log.d(TAG, "accounts flow completed: $it")

            }
            .stateIn(CoroutineScope(applicationScope.coroutineContext + Dispatchers.IO))
    }

    val accounts: List<AccountEntity>
        get() = accountsFlow.value

    val activeAccount: AccountEntity?
        get() {
            val a = accounts.firstOrNull()
            Log.d(TAG, "returning active account with id ${a?.id}")
            return a
        }

    fun activeAccount() = ActiveAccountDelegate(this)

    /**
     * Adds a new account and makes it the active account.
     * @param accessToken the access token for the new account
     * @param domain the domain of the account's Mastodon instance
     * @param clientId the oauth client id used to sign in the account
     * @param clientSecret the oauth client secret used to sign in the account
     * @param oauthScopes the oauth scopes granted to the account
     * @param newAccount the [Account] as returned by the Mastodon Api
     */
    suspend fun addAccount(
        accessToken: String,
        domain: String,
        clientId: String,
        clientSecret: String,
        oauthScopes: String,
        newAccount: Account
    ) = db.withTransaction {
        activeAccount?.let {
            //it.isActive = false
            Log.d(TAG, "addAccount: saving account with id " + it.id)

            accountDao.insertOrReplace(it.copy(isActive = false))
        }
        // check if this is a relogin with an existing account, if yes update it, otherwise create a new one
        val existingAccountIndex = accounts.indexOfFirst { account ->
            domain == account.domain && newAccount.id == account.accountId
        }
        val newAccountEntity = if (existingAccountIndex != -1) {
            accounts[existingAccountIndex].copy(
                accessToken = accessToken,
                clientId = clientId,
                clientSecret = clientSecret,
                oauthScopes = oauthScopes,
                isActive = true
            )
        } else {
            val maxAccountId = accounts.maxByOrNull { it.id }?.id ?: 0
            val newAccountId = maxAccountId + 1
            AccountEntity(
                id = newAccountId,
                domain = domain.lowercase(Locale.ROOT),
                accessToken = accessToken,
                clientId = clientId,
                clientSecret = clientSecret,
                oauthScopes = oauthScopes,
                isActive = true,
                accountId = newAccount.id
            )
        }
        updateAccount(newAccountEntity, newAccount)
    }

    /**
     * Saves an already known account to the database.
     * New accounts must be created with [addAccount]
     * @param account the account to save
     */
    suspend fun updateAccount(account: AccountEntity, changer: AccountEntity.() -> AccountEntity) {
        if (account.id != 0L) {
            // get the newest version of the account to make sure no stale data gets re-saved to db
            val acc = accounts.find { it.id == account.id } ?: return
            accountDao.insertOrReplace(changer(acc))
        }
    }

    /**
     * Logs an account out by deleting all its data.
     * @return the new active account, or null if no other account was found
     */
    suspend fun logout(account: AccountEntity): AccountEntity? = db.withTransaction {
        accountDao.delete(account)

        val otherAccount = accounts.find { it.id != account.id }
        if (otherAccount != null) {
            val otherAccountActive = otherAccount.copy(
                isActive = true
            )
            Log.d(TAG, "logActiveAccountOut: saving account with id " + otherAccountActive.id)
            accountDao.insertOrReplace(otherAccountActive)
            otherAccountActive
        } else {
            null
        }
    }

    /**
     * Updates an account with new information from the Mastodon api
     * and saves it in the database.
     * @param accountEntity the [AccountEntity] to update
     * @param account the [Account] object which the newest data from the api
     * @return the updated [AccountEntity]
     */
    suspend fun updateAccount(accountEntity: AccountEntity, account: Account): AccountEntity {
        val newAccount = accountEntity.copy(
        accountId = account.id,
        username = account.username,
        displayName = account.name,
        profilePictureUrl = account.avatar,
        profileHeaderUrl = account.header,
        defaultPostPrivacy = account.source?.privacy ?: Status.Visibility.PUBLIC,
        defaultPostLanguage = account.source?.language.orEmpty(),
        defaultMediaSensitivity = account.source?.sensitive ?: false,
        emojis = account.emojis,
        locked = account.locked
        )

        Log.d(TAG, "updateAccount: saving account with id " + accountEntity.id)
        accountDao.insertOrReplace(newAccount)
        return newAccount
    }

    /**
     * changes the active account
     * @param accountId the database id of the new active account
     */
    suspend fun setActiveAccount(accountId: Long) = db.withTransaction {
        Log.d(TAG, "setActiveAccount $accountId")

        val newActiveAccount = accounts.find { (id) ->
            id == accountId
        } ?: return@withTransaction // invalid accountId passed, do nothing

        activeAccount?.let {
            accountDao.insertOrReplace(it.copy(isActive = false))
        }

        accountDao.insertOrReplace(newActiveAccount.copy(isActive = true))
    }

    /**
     * @return an immutable list of all accounts in the database with the active account first
     */
    fun getAllAccountsOrderedByActive(): List<AccountEntity> {
        return accounts
    }

    /**
     * @return true if at least one account has notifications enabled
     */
    fun areNotificationsEnabled(): Boolean {
        return accounts.any { it.notificationsEnabled }
    }

    /**
     * Finds an account by its database id
     * @param accountId the id of the account
     * @return the requested account or null if it was not found
     */
    fun getAccountById(accountId: Long): AccountEntity? {
        return accounts.find { (id) ->
            id == accountId
        }
    }

    /**
     * Finds an account by its string identifier
     * @param identifier the string identifier of the account
     * @return the requested account or null if it was not found
     */
    fun getAccountByIdentifier(identifier: String): AccountEntity? {
        return accounts.find {
            identifier == it.identifier
        }
    }

    /**
     * @return true if the name of the currently-selected account should be displayed in UIs
     */
    fun shouldDisplaySelfUsername(): Boolean {
        val showUsernamePreference = preferences.getString(
            PrefKeys.SHOW_SELF_USERNAME,
            "disambiguate"
        )
        if (showUsernamePreference == "always") {
            return true
        }
        if (showUsernamePreference == "never") {
            return false
        }

        return accounts.size > 1 // "disambiguate"
    }
}


class ActiveAccountDelegate(
    private val accountManager: AccountManager
) {

    val accountId = accountManager.activeAccount?.id

    operator fun getValue(thisRef: Any?, property: KProperty<*>): AccountEntity? {
        return accountManager.accounts.find { it.id == accountId }
    }
}
