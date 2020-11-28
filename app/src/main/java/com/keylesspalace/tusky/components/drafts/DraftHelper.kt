package com.keylesspalace.tusky.components.drafts

import android.content.Context
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.DraftAttachment
import com.keylesspalace.tusky.db.DraftEntity
import com.keylesspalace.tusky.entity.NewPoll
import com.keylesspalace.tusky.entity.Status
import io.reactivex.Single
import javax.inject.Inject

class DraftHelper @Inject constructor(
        val context: Context,
        db: AppDatabase
) {

    private val draftDao = db.draftDao()

    fun saveDraft(
            accountId: Long,
            inReplyToId: String?,
            content: String?,
            contentWarning: String?,
            sensitive: Boolean,
            visibility: Status.Visibility?,
            attachments: List<DraftAttachment>,
            poll: NewPoll?,
            failedToSend: Boolean
    ): Single<Boolean> {
        return Single.fromCallable {
            draftDao.insertOrReplace(
                    DraftEntity(
                            accountId = accountId,
                            inReplyToId = inReplyToId,
                            content = content,
                            contentWarning = contentWarning,
                            sensitive = sensitive,
                            visibility = visibility,
                            attachments = attachments,
                            poll = poll,
                            failedToSend = failedToSend
                    )
            )
            true
        }
    }

    fun deleteDraft(draftId: Int): Single<Boolean> {
        return Single.just(true)
    }
}