package com.keylesspalace.tusky.components.timeline

import com.google.gson.Gson
import com.keylesspalace.tusky.db.TimelineStatusWithAccount
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.viewdata.StatusViewData
import java.util.ArrayList
import java.util.Date

private val fixedDate = Date(1638889052000)

fun mockStatus(id: String = "100") = Status(
    id = id,
    url = "https://mastodon.example/@ConnyDuck/$id",
    account = TimelineAccount(
        id = "1",
        localUsername = "connyduck",
        username = "connyduck@mastodon.example",
        displayName = "Conny Duck",
        url = "https://mastodon.example/@ConnyDuck",
        avatar = "https://mastodon.example/system/accounts/avatars/000/150/486/original/ab27d7ddd18a10ea.jpg"
    ),
    inReplyToId = null,
    inReplyToAccountId = null,
    reblog = null,
    content = "Test",
    createdAt = fixedDate,
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
    tags = emptyList(),
    application = Status.Application("Tusky", "https://tusky.app"),
    pinned = false,
    muted = false,
    poll = null,
    card = null
)

fun mockStatusViewData(id: String = "100") = StatusViewData.Concrete(
    status = mockStatus(id),
    isExpanded = false,
    isShowingContent = false,
    isCollapsed = true,
)

fun mockStatusEntityWithAccount(
    id: String = "100",
    userId: Long = 1,
    expanded: Boolean = false
): TimelineStatusWithAccount {
    val mockedStatus = mockStatus(id)
    val gson = Gson()

    return TimelineStatusWithAccount().apply {
        status = mockedStatus.toEntity(
            timelineUserId = userId,
            gson = gson,
            expanded = expanded,
            contentShowing = false,
            contentCollapsed = true
        )
        account = mockedStatus.account.toEntity(
            accountId = userId,
            gson = gson
        )
    }
}

fun mockPlaceholderEntityWithAccount(
    id: String,
    userId: Long = 1,
): TimelineStatusWithAccount {
    return TimelineStatusWithAccount().apply {
        status = Placeholder(id, false).toEntity(userId)
    }
}
