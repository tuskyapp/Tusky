package com.keylesspalace.tusky.appstore

import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Status

data class FavoriteEvent(val statusId: String, val favourite: Boolean) : Dispatchable
data class ReblogEvent(val statusId: String, val reblog: Boolean) : Dispatchable
data class UnfollowEvent(val accountId: String) : Dispatchable
data class BlockEvent(val accountId: String) : Dispatchable
data class MuteEvent(val accountId: String) : Dispatchable
data class StatusDeletedEvent(val statusId: String) : Dispatchable
data class StatusComposedEvent(val status: Status) : Dispatchable
data class ProfileEditedEvent(val newProfileData: Account) : Dispatchable