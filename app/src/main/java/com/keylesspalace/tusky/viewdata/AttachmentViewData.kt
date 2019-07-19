package com.keylesspalace.tusky.viewdata

import android.os.Parcelable
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Status
import kotlinx.android.parcel.Parcelize

@Parcelize
data class AttachmentViewData(
        val attachment: Attachment,
        val statusId: String,
        val statusUrl: String
) : Parcelable {
    companion object {
        @JvmStatic
        fun list(status: Status): List<AttachmentViewData> {
            val actionable = status.actionableStatus
            return actionable.attachments.map {
                AttachmentViewData(it, actionable.id, actionable.url!!)
            }
        }

        fun list(attachments: List<Attachment>): List<AttachmentViewData> {
            return attachments.map {
                AttachmentViewData(it, it.id, it.url)
            }
        }

    }
}