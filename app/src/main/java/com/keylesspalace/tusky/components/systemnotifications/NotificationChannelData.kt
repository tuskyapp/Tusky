package com.keylesspalace.tusky.components.systemnotifications

import androidx.annotation.Keep
import androidx.annotation.StringRes
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.entity.Notification

@Keep
enum class NotificationChannelData(
    val notificationTypes: List<Notification.Type>,
    @StringRes val title: Int,
    @StringRes val description: Int,
) {
    MENTION(
        listOf(Notification.Type.Mention),
        R.string.notification_mention_name,
        R.string.notification_mention_descriptions,
    ),

    REBLOG(
        listOf(Notification.Type.Reblog),
        R.string.notification_boost_name,
        R.string.notification_boost_description
    ),

    FAVOURITE(
        listOf(Notification.Type.Favourite),
        R.string.notification_favourite_name,
        R.string.notification_favourite_description
    ),

    FOLLOW(
        listOf(Notification.Type.Follow),
        R.string.notification_follow_name,
        R.string.notification_follow_description
    ),

    FOLLOW_REQUEST(
        listOf(Notification.Type.FollowRequest),
        R.string.notification_follow_request_name,
        R.string.notification_follow_request_description
    ),

    POLL(
        listOf(Notification.Type.Poll),
        R.string.notification_poll_name,
        R.string.notification_poll_description
    ),

    SUBSCRIPTIONS(
        listOf(Notification.Type.Status),
        R.string.notification_subscription_name,
        R.string.notification_subscription_description
    ),

    UPDATES(
        listOf(Notification.Type.Update),
        R.string.notification_update_name,
        R.string.notification_update_description
    ),

    ADMIN(
        listOf(Notification.Type.SignUp, Notification.Type.Report),
        R.string.notification_channel_admin,
        R.string.notification_channel_admin_description
    ),

    OTHER(
        listOf(Notification.Type.SeveredRelationship, Notification.Type.ModerationWarning),
        R.string.notification_channel_other,
        R.string.notification_channel_other_description
    );

    fun getChannelId(account: AccountEntity): String {
        return getChannelId(account.identifier)
    }

    fun getChannelId(accountIdentifier: String): String {
        return "CHANNEL_${name}$accountIdentifier"
    }
}

fun Set<NotificationChannelData>.toTypes(): Set<Notification.Type> {
    return flatMap { channelData -> channelData.notificationTypes }.toSet()
}
