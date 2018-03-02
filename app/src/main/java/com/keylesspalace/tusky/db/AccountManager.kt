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

import android.util.Log
import com.keylesspalace.tusky.TuskyApplication
import com.keylesspalace.tusky.entity.Account

/**
 * This class caches the account database and handles all account related operations
 * @author ConnyDuck
 */

private const val TAG = "AccountManager"

class AccountManager {

    @Volatile var activeAccount: AccountEntity? = null

    private var accounts: MutableList<AccountEntity> = mutableListOf()
    private val accountDao: AccountDao = TuskyApplication.getDB().accountDao()

    init {
        accounts = accountDao.loadAll().toMutableList()

        activeAccount = accounts.find { acc ->
            acc.isActive
        }
    }

    /**
     * Adds a new empty account and makes it the active account.
     * More account information has to be added later with [updateActiveAccount]
     * or the account wont be saved to the database.
     * @param accessToken the access token for the new account
     * @param domain the domain of the accounts Mastodon instance
     */
    fun addAccount(accessToken: String, domain: String) {

        activeAccount?.let{
            it.isActive = false
            Log.d(TAG, "addAccount: saving account with id "+it.id)

            accountDao.insertOrReplace(it)
        }

        activeAccount = AccountEntity(id = 0, domain = domain, accessToken = accessToken, isActive = true)

    }

    /**
     * Saves an already known account to the database.
     * New accounts must be created with [addAccount]
     * @param account the account to save
     */
    fun saveAccount(account: AccountEntity) {
        if(account.id != 0L) {
            Log.d(TAG, "saveAccount: saving account with id "+account.id)
            accountDao.insertOrReplace(account)
        }

    }

    /**
     * Logs the current account out by deleting all data of the account.
     * @return the new active account, or null if no other account was found
     */
    fun logActiveAccountOut() : AccountEntity? {

        if(activeAccount == null) {
            return null
        } else {
            accounts.remove(activeAccount!!)
            accountDao.delete(activeAccount!!)

            if(accounts.size > 0) {
                accounts[0].isActive = true
                activeAccount = accounts[0]
                Log.d(TAG, "logActiveAccountOut: saving account with id "+accounts[0].id)
                accountDao.insertOrReplace(accounts[0])
            } else {
                activeAccount = null
            }
            return activeAccount

        }

    }

    /**
     * updates the current account with new information from the mastodon api
     * and saves it in the database
     * @param account the [Account] object returned from the api
     */
    fun updateActiveAccount(account: Account) {
        activeAccount?.let{
            it.accountId = account.id
            it.username = account.username
            it.displayName = account.getDisplayName()
            it.profilePictureUrl = account.avatar

            Log.d(TAG,  "updateActiveAccount: saving account with id "+it.id)
            it.id = accountDao.insertOrReplace(it)

            val accountIndex = accounts.indexOf(it)

            if(accountIndex != -1) {
                //in case the user was already logged in with this account, remove the old information
                accounts.removeAt(accountIndex)
                accounts.add(accountIndex, it)
            } else {
                accounts.add(it)
            }

        }
    }

    /**
     * changes the active account
     * @param accountId the database id of the new active account
     */
    fun setActiveAccount(accountId: Long) {

        activeAccount?.let{
            Log.d(TAG,  "setActiveAccount: saving account with id "+it.id)
            it.isActive = false
            saveAccount(it)
        }

        activeAccount = accounts.find { acc ->
            acc.id == accountId
        }

        activeAccount?.let{
            it.isActive = true
            accountDao.insertOrReplace(it)
        }
    }

    /**
     * @return an immutable list of all accounts in the database with the active account first
     */
    fun getAllAccountsOrderedByActive(): List<AccountEntity> {
        accounts.sortWith (Comparator { l, r ->
            when {
                l.isActive && !r.isActive -> -1
                r.isActive && !l.isActive -> 1
                else -> 0
            }
        })

        return accounts.toList()
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
        return accounts.find { acc ->
            acc.id == accountId
        }
    }

}