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
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

/**
 * This class is the main interface to all account related operations.
 */

private const val TAG = "AccountManager"

@Singleton
class AccountManager @Inject constructor(
    private val db: AppDatabase,
    private val preferences: SharedPreferences,
    @ApplicationScope private val applicationScope: CoroutineScope
) {

    private val accountDao: AccountDao = db.accountDao()

    /** A StateFlow that will update everytime an account in the database changes, is added or removed.
     *  The first account is the currently active one.
     */
    val accountsFlow: StateFlow<List<AccountEntity>> = runBlocking {
        accountDao.allAccounts()
            .stateIn(CoroutineScope(applicationScope.coroutineContext + Dispatchers.IO))
    }

    /** A list of all accounts in the database with the active account first */
    val accounts: List<AccountEntity>
        get() = accountsFlow.value

    /** The currently active account, if there is one */
    val activeAccount: AccountEntity?
        get() = accounts.firstOrNull()

    /** Returns a StateFlow for updates to the currently active account.
     *  Note that always the same account will be emitted,
     *  even if it is no longer active and that it will emit null when the account got removed.
     *  @param scope the [CoroutineScope] this flow will be active in.
     */
    fun activeAccount(scope: CoroutineScope): StateFlow<AccountEntity?> {
        val activeAccount = activeAccount
        return accountsFlow.map { accounts ->
            accounts.find { account -> activeAccount?.id == account.id }
        }.stateIn(scope, SharingStarted.Lazily, activeAccount)
    }

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
            val maxAccountId = accounts.maxOfOrNull { it.id } ?: 0
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
     * @param account The account to save
     * @param changer make the changes to save here - this is to make sure no stale data gets re-saved to the database
     */
    suspend fun updateAccount(account: AccountEntity, changer: AccountEntity.() -> AccountEntity) {
        accounts.find { it.id == account.id }?.let { acc ->
            Log.d(TAG, "updateAccount: saving account with id " + acc.id)
            accountDao.insertOrReplace(changer(acc))
        }
    }

    /**
     * Updates an account with new information from the Mastodon api
     * and saves it in the database.
     * @param accountEntity the [AccountEntity] to update
     * @param account the [Account] object which the newest data from the api
     */
    suspend fun updateAccount(accountEntity: AccountEntity, account: Account) {
        updateAccount (accountEntity) {
            copy(
                accountId = account.id,
                username = account.username,
                displayName = account.name,
                profilePictureUrl = account.avatar,
                profileHeaderUrl = account.header,
                defaultPostPrivacy = account.source?.privacy ?: Status.Visibility.PUBLIC,
                defaultPostLanguage = account.source?.language.orEmpty(),
                defaultMediaSensitivity = account.source?.sensitive == true,
                emojis = account.emojis,
                locked = account.locked
            )
        }
    }

    /**
     * Removes an account from the database.
     * @return the new active account, or null if no other account was found
     */
    suspend fun remove(account: AccountEntity): AccountEntity? = db.withTransaction {
        Log.d(TAG, "remove: deleting account with id " + account.id)
        accountDao.delete(account)

        accounts.find { it.id != account.id }?.let{ otherAccount ->
            val otherAccountActive = otherAccount.copy(
                isActive = true
            )
            Log.d(TAG, "remove: saving account with id " + otherAccountActive.id)
            accountDao.insertOrReplace(otherAccountActive)
            otherAccountActive
        }
    }

    /**
     * Changes the active account
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
