package com.keylesspalace.tusky.components.timeline

import androidx.paging.PagingSource
import androidx.room.withTransaction
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.entity.HomeTimelineData
import com.keylesspalace.tusky.db.entity.HomeTimelineEntity
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.viewdata.StatusViewData
import java.util.Date
import org.junit.Assert.assertEquals

private val fixedDate = Date(1638889052000)

fun fakeAccount(
    id: String = "100",
    domain: String = "mastodon.example"
) = TimelineAccount(
    id = id,
    localUsername = "connyduck",
    username = "connyduck@$domain",
    displayName = "Conny Duck",
    note = "This is their bio",
    url = "https://$domain/@ConnyDuck",
    avatar = "https://$domain/system/accounts/avatars/000/150/486/original/ab27d7ddd18a10ea.jpg"
)

fun fakeStatus(
    id: String = "100",
    authorServerId: String = "100",
    inReplyToId: String? = null,
    inReplyToAccountId: String? = null,
    spoilerText: String = "",
    reblogged: Boolean = false,
    favourited: Boolean = true,
    bookmarked: Boolean = true,
    domain: String = "mastodon.example",
    reblog: Status? = null
) = Status(
    id = id,
    url = "https://$domain/@ConnyDuck/$id",
    account = fakeAccount(
        id = authorServerId,
        domain = domain
    ),
    inReplyToId = inReplyToId,
    inReplyToAccountId = inReplyToAccountId,
    reblog = reblog,
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
    filtered = emptyList()
)

fun fakeStatusViewData(
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
    status = fakeStatus(
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

fun fakeHomeTimelineData(
    id: String = "100",
    statusId: String = id,
    tuskyAccountId: Long = 1,
    authorServerId: String = "100",
    expanded: Boolean = false,
    domain: String = "mastodon.example",
    reblogAuthorServerId: String? = null
): HomeTimelineData {
    val mockedStatus = fakeStatus(
        id = statusId,
        authorServerId = authorServerId,
        domain = domain
    )

    return HomeTimelineData(
        id = id,
        status = mockedStatus.toEntity(
            tuskyAccountId = tuskyAccountId,
            expanded = expanded,
            contentShowing = false,
            contentCollapsed = true
        ),
        account = mockedStatus.account.toEntity(
            tuskyAccountId = tuskyAccountId,
        ),
        reblogAccount = reblogAuthorServerId?.let { reblogAuthorId ->
            fakeAccount(
                id = reblogAuthorId
            ).toEntity(
                tuskyAccountId = tuskyAccountId,
            )
        },
        repliedToAccount = null,
        loading = false
    )
}

fun fakePlaceholderHomeTimelineData(
    id: String
) = HomeTimelineData(
    id = id,
    account = null,
    status = null,
    reblogAccount = null,
    repliedToAccount = null,
    loading = false
)

suspend fun AppDatabase.insert(timelineItems: List<HomeTimelineData>, tuskyAccountId: Long = 1) = withTransaction {
    timelineItems.forEach { timelineItem ->
        timelineItem.account?.let { account ->
            timelineAccountDao().insert(account)
        }
        timelineItem.reblogAccount?.let { account ->
            timelineAccountDao().insert(account)
        }
        timelineItem.status?.let { status ->
            timelineStatusDao().insert(status)
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
    val pagingSource = timelineDao().getHomeTimeline(tuskyAccountId)

    val loadResult = pagingSource.load(PagingSource.LoadParams.Refresh(null, 100, false))

    val loadedStatuses = (loadResult as PagingSource.LoadResult.Page).data

    assertEquals(expected.size, loadedStatuses.size)

    for ((exp, prov) in expected.zip(loadedStatuses)) {
        assertEquals(exp.status, prov.status)
        assertEquals(exp.account, prov.account)
        assertEquals(exp.reblogAccount, prov.reblogAccount)
    }
}
