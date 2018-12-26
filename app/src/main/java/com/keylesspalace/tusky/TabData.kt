package com.keylesspalace.tusky

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes


const val HOME = "Home"
const val NOTIFICATIONS = "Notifications"
const val LOCAL = "Local"
const val FEDERATED = "Federated"

class TabData(val id: String,
                     @StringRes val text: Int,
                     @DrawableRes val icon: Int)


fun createTabDataFromId(id: String): TabData {
    return when(id) {
        HOME -> TabData(HOME, R.string.title_home, R.drawable.ic_home_24dp)
        NOTIFICATIONS -> TabData(NOTIFICATIONS, R.string.title_notifications, R.drawable.ic_notifications_24dp)
        LOCAL -> TabData(LOCAL, R.string.title_public_local, R.drawable.ic_local_24dp)
        FEDERATED -> TabData(FEDERATED, R.string.title_public_federated, R.drawable.ic_public_24dp)
        else -> throw IllegalArgumentException("unknown tab type")
    }
}

fun defaultTabs(): List<TabData> {
    return listOf(
            createTabDataFromId(HOME),
            createTabDataFromId(NOTIFICATIONS),
            createTabDataFromId(LOCAL),
            createTabDataFromId(FEDERATED)
    )
}