package com.keylesspalace.tusky.entity

import com.keylesspalace.tusky.adapter.FollowingAccountListAdapter.FollowState
import com.keylesspalace.tusky.db.AccountEntity

data class AuthorizeFollow(
        val accountEntity: AccountEntity,
        val subjectAccount: Account,
        var followState: FollowState,
        var anyPendingTransaction: Boolean
)
