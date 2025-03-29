package com.keylesspalace.tusky.components.accountlist

import com.keylesspalace.tusky.entity.TimelineAccount

data class AccountViewData(
    val account: TimelineAccount,
    val mutingNotifications: Boolean
) {
    val id: String
        get() = account.id
}

fun TimelineAccount.toViewData(
    mutingNotifications: Boolean
) = AccountViewData(
    account = this,
    mutingNotifications = mutingNotifications
)
