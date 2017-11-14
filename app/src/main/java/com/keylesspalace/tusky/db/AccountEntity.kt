package com.keylesspalace.tusky.db

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey

/**
 * Created by charlag on 14/11/17.
 */

@Entity(tableName = "account")
data class AccountEntity(
       @PrimaryKey(autoGenerate = true) val id: Long,
       val instanceUrl: String,
       val username: String,
       val token: String
)