package com.keylesspalace.tusky.components.timeline

import android.text.SpannedString
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.viewdata.StatusViewData
import java.util.ArrayList
import java.util.Date

fun mockStatus(id: String = "100") = Status(
    id = id,
    url = "https://mastodon.example/@ConnyDuck/$id",
    account = Account(
        id = "1",
        localUsername = "connyduck",
        username = "connyduck@mastodon.example",
        displayName = "Conny Duck",
        note = SpannedString(""),
        url = "https://mastodon.example/@ConnyDuck",
        avatar = "https://mastodon.example/system/accounts/avatars/000/150/486/original/ab27d7ddd18a10ea.jpg",
        header = "https://mastodon.example/system/accounts/header/000/106/476/original/e590545d7eb4da39.jpg"
    ),
    inReplyToId = null,
    inReplyToAccountId = null,
    reblog = null,
    content = SpannedString("Test"),
    createdAt = Date(),
    emojis = emptyList(),
    reblogsCount = 1,
    favouritesCount = 2,
    reblogged = false,
    favourited = true,
    bookmarked = true,
    sensitive = true,
    spoilerText = "",
    visibility = Status.Visibility.PUBLIC,
    attachments = ArrayList(),
    mentions = emptyList(),
    application = Status.Application("Tusky", "https://tusky.app"),
    pinned = false,
    muted = false,
    poll = null,
    card = null
)

fun mockStatusViewData(id: String = "100") = StatusViewData.Concrete(
    status = mockStatus(id),
    isExpanded = false,
    isShowingContent = true,
    isCollapsible = false,
    isCollapsed= false,
)