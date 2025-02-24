package com.keylesspalace.tusky.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class NotificationPolicyEntity(
    @PrimaryKey val tuskyAccountId: Long,
    val pendingRequestsCount: Int,
    val pendingNotificationsCount: Int
)
