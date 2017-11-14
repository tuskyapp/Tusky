package com.keylesspalace.tusky.data

import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AppDatabase

/**
 * Created by charlag on 14/11/17.
 */

interface CurrentUser {
    var activeAccount: AccountEntity?
}

class CurrentUserImpl : CurrentUser {

    @Volatile private var _activeAccount: AccountEntity? = null
    override var activeAccount: AccountEntity?
    get() {
        synchronized(this) {
            return _activeAccount
        }
    }
    set(value) {
        synchronized(this) {
            _activeAccount = value
        }
    }

}