/* Copyright 2021 Tusky Contributors
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.components.drafts

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.DraftAttachment
import com.keylesspalace.tusky.db.DraftEntity
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.NewPoll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.util.copyToFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class DraftHelper @Inject constructor(
    val context: Context,
    private val okHttpClient: OkHttpClient,
    db: AppDatabase
) {

    private val draftDao = db.draftDao()

    suspend fun saveDraft(
        draftId: Int,
        accountId: Long,
        inReplyToId: String?,
        content: String?,
        contentWarning: String?,
        sensitive: Boolean,
        visibility: Status.Visibility,
        mediaUris: List<String>,
        mediaDescriptions: List<String?>,
        mediaFocus: List<Attachment.Focus?>,
        poll: NewPoll?,
        failedToSend: Boolean,
        failedToSendAlert: Boolean,
        scheduledAt: String?,
        language: String?,
        statusId: String?,
    ) = withContext(Dispatchers.IO) {
        val externalFilesDir = context.getExternalFilesDir("Tusky")

        if (externalFilesDir == null || !(externalFilesDir.exists())) {
            Log.e("DraftHelper", "Error obtaining directory to save media.")
            throw Exception()
        }

        val draftDirectory = File(externalFilesDir, "Drafts")

        if (!draftDirectory.exists()) {
            draftDirectory.mkdir()
        }

        val uris = mediaUris.map { uriString ->
            uriString.toUri()
        }.mapIndexedNotNull { index, uri ->
            if (uri.isInFolder(draftDirectory)) {
                uri
            } else {
                uri.copyToFolder(draftDirectory, index)
            }
        }

        val types = uris.map { uri ->
            val mimeType = context.contentResolver.getType(uri)
            when (mimeType?.substring(0, mimeType.indexOf('/'))) {
                "video" -> DraftAttachment.Type.VIDEO
                "image" -> DraftAttachment.Type.IMAGE
                "audio" -> DraftAttachment.Type.AUDIO
                else -> throw IllegalStateException("unknown media type")
            }
        }

        val attachments: MutableList<DraftAttachment> = mutableListOf()
        for (i in mediaUris.indices) {
            attachments.add(
                DraftAttachment(
                    uriString = uris[i].toString(),
                    description = mediaDescriptions[i],
                    focus = mediaFocus[i],
                    type = types[i]
                )
            )
        }

        val draft = DraftEntity(
            id = draftId,
            accountId = accountId,
            inReplyToId = inReplyToId,
            content = content,
            contentWarning = contentWarning,
            sensitive = sensitive,
            visibility = visibility,
            attachments = attachments,
            poll = poll,
            failedToSend = failedToSend,
            failedToSendNew = failedToSendAlert,
            scheduledAt = scheduledAt,
            language = language,
            statusId = statusId,
        )

        draftDao.insertOrReplace(draft)
        Log.d("DraftHelper", "saved draft to db")
    }

    suspend fun deleteDraftAndAttachments(draftId: Int) {
        draftDao.find(draftId)?.let { draft ->
            deleteDraftAndAttachments(draft)
        }
    }

    private suspend fun deleteDraftAndAttachments(draft: DraftEntity) {
        deleteAttachments(draft)
        draftDao.delete(draft.id)
    }

    suspend fun deleteAllDraftsAndAttachmentsForAccount(accountId: Long) {
        draftDao.loadDrafts(accountId).forEach { draft ->
            deleteDraftAndAttachments(draft)
        }
    }

    suspend fun deleteAttachments(draft: DraftEntity) = withContext(Dispatchers.IO) {
        draft.attachments.forEach { attachment ->
            if (context.contentResolver.delete(attachment.uri, null, null) == 0) {
                Log.e("DraftHelper", "Did not delete file ${attachment.uriString}")
            }
        }
    }

    private fun Uri.isInFolder(folder: File): Boolean {
        val filePath = path ?: return true
        return File(filePath).parentFile == folder
    }

    private fun Uri.copyToFolder(folder: File, index: Int): Uri? {
        val contentResolver = context.contentResolver
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        val fileExtension = if (scheme == "https") {
            lastPathSegment?.substringAfterLast('.', "tmp")
        } else {
            val mimeType = contentResolver.getType(this)
            val map = MimeTypeMap.getSingleton()
            map.getExtensionFromMimeType(mimeType)
        }

        val filename = String.format("Tusky_Draft_Media_%s_%d.%s", timeStamp, index, fileExtension)
        val file = File(folder, filename)

        if (scheme == "https") {
            // saving redrafted media
            try {
                val request = Request.Builder().url(toString()).build()

                val response = okHttpClient.newCall(request).execute()

                val sink = file.sink().buffer()

                response.body?.source()?.use { input ->
                    sink.use { output ->
                        output.writeAll(input)
                    }
                }
            } catch (ex: IOException) {
                Log.w("DraftHelper", "failed to save media", ex)
                return null
            }
        } else {
            this.copyToFile(contentResolver, file)
        }
        return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", file)
    }
}
