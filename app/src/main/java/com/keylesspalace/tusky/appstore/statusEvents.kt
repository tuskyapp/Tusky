package com.keylesspalace.tusky.appstore

data class FavoriteEvent(val statusId: String, val favourite: Boolean) : Dispatchable
data class ReblogEvent(val statusId: String, val boost: Boolean) : Dispatchable