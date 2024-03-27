package com.keylesspalace.tusky.components.timeline

import androidx.paging.PagingSource
import com.google.gson.Gson
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.HomeTimelineData
import com.keylesspalace.tusky.db.HomeTimelineEntity
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.viewdata.StatusViewData
import java.util.Date
import org.junit.Assert

private val fixedDate = Date(1638889052000)

fun mockAccount(
    authorServerId: String = "100",
    domain: String = "mastodon.example"
) = TimelineAccount(
    id = authorServerId,
    localUsername = "connyduck",
    username = "connyduck@$domain",
    displayName = "Conny Duck",
    note = "This is their bio",
    url = "https://$domain/@ConnyDuck",
    avatar = "https://$domain/system/accounts/avatars/000/150/486/original/ab27d7ddd18a10ea.jpg"
)

fun mockStatus(
    id: String = "100",
    authorServerId: String = "100",
    inReplyToId: String? = null,
    inReplyToAccountId: String? = null,
    spoilerText: String = "",
    reblogged: Boolean = false,
    favourited: Boolean = true,
    bookmarked: Boolean = true,
    domain: String = "mastodon.example"
) = Status(
    id = id,
    url = "https://$domain/@ConnyDuck/$id",
    account = mockAccount(
        authorServerId = authorServerId,
        domain = domain
    ),
    inReplyToId = inReplyToId,
    inReplyToAccountId = inReplyToAccountId,
    reblog = null,
    content = "Test",
    createdAt = fixedDate,
    editedAt = null,
    emojis = emptyList(),
    reblogsCount = 1,
    favouritesCount = 2,
    repliesCount = 3,
    reblogged = reblogged,
    favourited = favourited,
    bookmarked = bookmarked,
    sensitive = true,
    spoilerText = spoilerText,
    visibility = Status.Visibility.PUBLIC,
    attachments = ArrayList(),
    mentions = emptyList(),
    tags = emptyList(),
    application = Status.Application("Tusky", "https://tusky.app"),
    pinned = false,
    muted = false,
    poll = null,
    card = null,
    language = null,
    filtered = null
)

fun mockStatusViewData(
    id: String = "100",
    inReplyToId: String? = null,
    inReplyToAccountId: String? = null,
    isDetailed: Boolean = false,
    spoilerText: String = "",
    isExpanded: Boolean = false,
    isShowingContent: Boolean = false,
    isCollapsed: Boolean = !isDetailed,
    reblogged: Boolean = false,
    favourited: Boolean = true,
    bookmarked: Boolean = true
) = StatusViewData.Concrete(
    status = mockStatus(
        id = id,
        inReplyToId = inReplyToId,
        inReplyToAccountId = inReplyToAccountId,
        spoilerText = spoilerText,
        reblogged = reblogged,
        favourited = favourited,
        bookmarked = bookmarked
    ),
    isExpanded = isExpanded,
    isShowingContent = isShowingContent,
    isCollapsed = isCollapsed,
    isDetailed = isDetailed
)

fun mockHomeTimelineData(
    id: String = "100",
    tuskyAccountId: Long = 1,
    authorServerId: String = "100",
    expanded: Boolean = false,
    domain: String = "mastodon.example",
    reblog: Boolean = false
): HomeTimelineData {
    val mockedStatus = mockStatus(
        id = id,
        authorServerId = authorServerId,
        domain = domain
    )
    val gson = Gson()

    return HomeTimelineData(
        id = id,
        status = mockedStatus.toEntity(
            tuskyAccountId = tuskyAccountId,
            gson = gson,
            expanded = expanded,
            contentShowing = false,
            contentCollapsed = true
        ),
        account = mockedStatus.account.toEntity(
            tuskyAccountId = tuskyAccountId,
            gson = gson
        ),
        reblogAccount = if (reblog) {
            mockAccount(
                authorServerId = "R$authorServerId"
            ).toEntity(tuskyAccountId, gson)
        } else {
            null
        },
        loading = false
    )
}

fun mockPlaceholderHomeTimelineData(
    id: String
) = HomeTimelineData(
    id = id,
    account = null,
    status = null,
    reblogAccount = null,
    loading = false
)

suspend fun AppDatabase.insert(timelineItems: List<HomeTimelineData>, tuskyAccountId: Long = 1) {
    timelineItems.forEach { timelineItem ->
        timelineItem.account?.let { account ->
            timelineDao().insertAccount(account)
        }
        timelineItem.reblogAccount?.let { account ->
            timelineDao().insertAccount(account)
        }
        timelineItem.status?.let { status ->
            timelineDao().insertStatus(status)
        }
        timelineDao().insertHomeTimelineItem(
            HomeTimelineEntity(
                tuskyAccountId = tuskyAccountId,
                id = timelineItem.id,
                statusId = timelineItem.status?.serverId,
                reblogAccountId = timelineItem.reblogAccount?.serverId,
                loading = timelineItem.loading
            )
        )
    }
}

suspend fun AppDatabase.assertTimeline(
    expected: List<HomeTimelineData>,
    tuskyAccountId: Long = 1
) {
    val pagingSource = timelineDao().getStatuses(tuskyAccountId)

    val loadResult = pagingSource.load(PagingSource.LoadParams.Refresh(null, 100, false))

    val loadedStatuses = (loadResult as PagingSource.LoadResult.Page).data

    Assert.assertEquals(expected.size, loadedStatuses.size)

    for ((exp, prov) in expected.zip(loadedStatuses)) {
        Assert.assertEquals(exp.status, prov.status)
        Assert.assertEquals(exp.account, prov.account)
        Assert.assertEquals(exp.reblogAccount, prov.reblogAccount)
    }
}
