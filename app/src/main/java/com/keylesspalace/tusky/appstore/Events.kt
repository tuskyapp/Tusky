package com.keylesspalace.tusky.appstore

import com.keylesspalace.tusky.TabData
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status

data class StatusChangedEvent(val status: Status) : Event
data class UnfollowEvent(val accountId: String) : Event
data class BlockEvent(val accountId: String) : Event
data class MuteEvent(val accountId: String) : Event
data class StatusDeletedEvent(val statusId: String) : Event
data class StatusComposedEvent(val status: Status) : Event
data class StatusScheduledEvent(val scheduledStatusId: String) : Event
data class ProfileEditedEvent(val newProfileData: Account) : Event
data class PreferenceChangedEvent(val preferenceKey: String) : Event
data class PollVoteEvent(val statusId: String, val poll: Poll) : Event
data class DomainMuteEvent(val instance: String) : Event
data class AnnouncementReadEvent(val announcementId: String) : Event
data class FilterUpdatedEvent(val filterContext: List<String>) : Event
data class NewNotificationsEvent(
    val accountId: String,
    val notifications: List<Notification>
) : Event
data class ConversationsLoadingEvent(val accountId: String) : Event
data class NotificationsLoadingEvent(val accountId: String) : Event
