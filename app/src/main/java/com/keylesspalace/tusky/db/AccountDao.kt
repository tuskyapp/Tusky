package com.keylesspalace.tusky.db

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.Query

/**
 * Created by charlag on 14/11/17.
 */

@Dao
interface AccountDao {
    @Insert
    fun insertOrUpdate(account: AccountEntity): Long

    @Query("SELECT * FROM account")
    fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM account WHERE id == :id LIMIT 1")
    fun getById(id: Long): AccountEntity

    @Query("DELETE FROM account WHERE id = :id")
    fun delete(id: Long)
}