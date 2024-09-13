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
import com.keylesspalace.tusky.db.dao.AccountDao
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.settings.PrefKeys
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This class caches the account database and handles all account related operations
 * @author ConnyDuck
 */

private const val TAG = "AccountManager"

@Singleton
class AccountManager @Inject constructor(
    db: AppDatabase,
    private val preferences: SharedPreferences
) {

    @Volatile
    var activeAccount: AccountEntity? = null
        private set

    var accounts: MutableList<AccountEntity> = mutableListOf()
        private set

    private val accountDao: AccountDao = db.accountDao()

    init {
        accounts = accountDao.loadAll().toMutableList()

        activeAccount = accounts.find { acc -> acc.isActive }
            ?: accounts.firstOrNull()?.also { acc -> acc.isActive = true }
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
    fun addAccount(
        accessToken: String,
        domain: String,
        clientId: String,
        clientSecret: String,
        oauthScopes: String,
        newAccount: Account
    ) {
        activeAccount?.let {
            it.isActive = false
            Log.d(TAG, "addAccount: saving account with id " + it.id)

            accountDao.insertOrReplace(it)
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
            ).also { accounts[existingAccountIndex] = it }
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
            ).also { accounts.add(it) }
        }

        activeAccount = newAccountEntity
        updateAccount(newAccountEntity, newAccount)
    }

    /**
     * Saves an already known account to the database.
     * New accounts must be created with [addAccount]
     * @param account the account to save
     */
    fun saveAccount(account: AccountEntity) {
        if (account.id != 0L) {
            Log.d(TAG, "saveAccount: saving account with id " + account.id)
            accountDao.insertOrReplace(account)
        }
    }

    /**
     * Logs an account out by deleting all its data.
     * @return the new active account, or null if no other account was found
     */
    fun logout(account: AccountEntity): AccountEntity? {
        account.logout()

        accounts.remove(account)
        accountDao.delete(account)

        if (accounts.size > 0) {
            accounts[0].isActive = true
            activeAccount = accounts[0]
            Log.d(TAG, "logActiveAccountOut: saving account with id " + accounts[0].id)
            accountDao.insertOrReplace(accounts[0])
        } else {
            activeAccount = null
        }
        return activeAccount
    }

    /**
     * Updates an account with new information from the Mastodon api
     * and saves it in the database.
     * @param accountEntity the [AccountEntity] to update
     * @param account the [Account] object which the newest data from the api
     */
    fun updateAccount(accountEntity: AccountEntity, account: Account) {
        accountEntity.accountId = account.id
        accountEntity.username = account.username
        accountEntity.displayName = account.name
        accountEntity.profilePictureUrl = account.avatar
        accountEntity.defaultPostPrivacy = account.source?.privacy ?: Status.Visibility.PUBLIC
        accountEntity.defaultPostLanguage = account.source?.language.orEmpty()
        accountEntity.defaultMediaSensitivity = account.source?.sensitive ?: false
        accountEntity.emojis = account.emojis
        accountEntity.locked = account.locked

        Log.d(TAG, "updateAccount: saving account with id " + accountEntity.id)
        accountDao.insertOrReplace(accountEntity)
    }

    /**
     * changes the active account
     * @param accountId the database id of the new active account
     */
    fun setActiveAccount(accountId: Long) {
        val newActiveAccount = accounts.find { (id) ->
            id == accountId
        } ?: return // invalid accountId passed, do nothing

        activeAccount?.let {
            Log.d(TAG, "setActiveAccount: saving account with id " + it.id)
            it.isActive = false
            saveAccount(it)
        }

        activeAccount = newActiveAccount

        activeAccount?.let {
            it.isActive = true
            accountDao.insertOrReplace(it)
        }
    }

    /**
     * @return an immutable list of all accounts in the database with the active account first
     */
    fun getAllAccountsOrderedByActive(): List<AccountEntity> {
        val accountsCopy = accounts.toMutableList()
        accountsCopy.sortWith { l, r ->
            when {
                l.isActive && !r.isActive -> -1
                r.isActive && !l.isActive -> 1
                else -> 0
            }
        }

        return accountsCopy
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
