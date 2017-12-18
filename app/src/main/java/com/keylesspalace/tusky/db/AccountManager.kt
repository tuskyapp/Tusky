package com.keylesspalace.tusky.db

import com.keylesspalace.tusky.TuskyApplication
import com.keylesspalace.tusky.entity.Account

class AccountManager {

    var activeAccount: AccountEntity? = null

    private var accounts: MutableList<AccountEntity> = mutableListOf()
    private val accountDao: AccountDao = TuskyApplication.getDB().accountDao()

    init {
        accounts = accountDao.loadAll().toMutableList()

        activeAccount = accounts.find { acc ->
            acc.isActive
        }
    }

    fun addAccount(accessToken: String, domain: String) {

        activeAccount?.let{
            it.isActive = false
            accountDao.insertOrReplace(it)
        }

        activeAccount = AccountEntity(0, domain, "", "", accessToken, "", "", true)

    }

    fun logActiveAccountOut() : AccountEntity? {

        if(activeAccount == null) {
            return null
        } else {
            accounts.remove(activeAccount!!)
            accountDao.delete(activeAccount!!)

            if(accounts.size > 0) {
                accounts[0].isActive = true
                activeAccount = accounts[0]
                accountDao.insertOrReplace(accounts[0])
            } else {
                activeAccount = null
            }
            return activeAccount

        }

    }

    fun updateActiveAccount(account: Account) {
        activeAccount?.let{
            it.accountId = account.id
            it.username = account.username
            it.displayName = account.getDisplayName()
            it.profilePictureUrl = account.avatar

            it.id = accountDao.insertOrReplace(it)

            val accountIndex = accounts.indexOf(it)

            if(accountIndex != -1) {
                accounts.removeAt(accountIndex)
                accounts.add(accountIndex, it)
            } else {
                accounts.add(it)
            }

        }
    }

    fun setActiveAccount(accountId: Long) {

        activeAccount?.let{
            it.isActive = false
            accountDao.insertOrReplace(it)
        }

        activeAccount = accounts.find { acc ->
            acc.id == accountId
        }

        activeAccount?.let{
            it.isActive = true
            accountDao.insertOrReplace(it)
        }
    }

    fun getAllAccountsOrderedByActive(): List<AccountEntity> {
        accounts.sortWith (Comparator { l, r ->
            when {
                l.isActive && !r.isActive -> -1
                r.isActive && !l.isActive -> 1
                else -> 0
            }
        })

        return accounts
    }

}