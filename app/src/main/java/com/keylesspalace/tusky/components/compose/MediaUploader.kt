/* Copyright 2019 Tusky Contributors
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

package com.keylesspalace.tusky.components.compose

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.compose.ComposeActivity.QueuedMedia
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.network.ProgressRequestBody
import com.keylesspalace.tusky.util.*
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

sealed class UploadEvent {
    data class ProgressEvent(val percentage: Int) : UploadEvent()
    data class FinishedEvent(val attachment: Attachment) : UploadEvent()
}

fun createNewImageFile(context: Context): File {
    // Create an image file name
    val randomId = randomAlphanumericString(12)
    val imageFileName = "Tusky_${randomId}_"
    val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File.createTempFile(
            imageFileName, /* prefix */
            ".jpg", /* suffix */
            storageDir      /* directory */
    )
}

data class PreparedMedia(val type: QueuedMedia.Type, val uri: Uri, val size: Long)

interface MediaUploader {
    fun prepareMedia(inUri: Uri): Single<PreparedMedia>
    fun uploadMedia(media: QueuedMedia): Observable<UploadEvent>
}

class AudioSizeException : Exception()
class VideoSizeException : Exception()
class MediaTypeException : Exception()
class CouldNotOpenFileException : Exception()

class MediaUploaderImpl(
        private val context: Context,
        private val mastodonApi: MastodonApi
) : MediaUploader {
    override fun uploadMedia(media: QueuedMedia): Observable<UploadEvent> {
        return Observable
                .fromCallable {
                    if (shouldResizeMedia(media)) {
                        downsize(media)
                    } else media
                }
                .switchMap { upload(it) }
                .subscribeOn(Schedulers.io())
    }

    override fun prepareMedia(inUri: Uri): Single<PreparedMedia> {
        return Single.fromCallable {
            var mediaSize = getMediaSize(contentResolver, inUri)
            var uri = inUri
            val mimeType = contentResolver.getType(uri)

            val suffix = "." + MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType ?: "tmp")

            try {
                contentResolver.openInputStream(inUri).use { input ->
                    if (input == null) {
                        Log.w(TAG, "Media input is null")
                        uri = inUri
                        return@use
                    }
                    val file = File.createTempFile("randomTemp1", suffix, context.cacheDir)
                    FileOutputStream(file.absoluteFile).use { out ->
                        input.copyTo(out)
                        uri = FileProvider.getUriForFile(context,
                                BuildConfig.APPLICATION_ID + ".fileprovider",
                                file)
                        mediaSize = getMediaSize(contentResolver, uri)
                    }

                }
            } catch (e: IOException) {
                Log.w(TAG, e)
                uri = inUri
            }
            if (mediaSize == MEDIA_SIZE_UNKNOWN) {
                throw CouldNotOpenFileException()
            }

            if (mimeType != null) {
                val topLevelType = mimeType.substring(0, mimeType.indexOf('/'))
                when (topLevelType) {
                    "video" -> {
                        if (mediaSize > STATUS_VIDEO_SIZE_LIMIT) {
                            throw VideoSizeException()
                        }
                        PreparedMedia(QueuedMedia.Type.VIDEO, uri, mediaSize)
                    }
                    "image" -> {
                        PreparedMedia(QueuedMedia.Type.IMAGE, uri, mediaSize)
                    }
                    "audio" -> {
                        if (mediaSize > STATUS_AUDIO_SIZE_LIMIT) {
                            throw AudioSizeException()
                        }
                        PreparedMedia(QueuedMedia.Type.AUDIO, uri, mediaSize)
                    }
                    else -> {
                        throw MediaTypeException()
                    }
                }
            } else {
                throw MediaTypeException()
            }
        }
    }

    private val contentResolver = context.contentResolver

    private fun upload(media: QueuedMedia): Observable<UploadEvent> {
        return Observable.create { emitter ->
            var mimeType = contentResolver.getType(media.uri)
            val map = MimeTypeMap.getSingleton()
            val fileExtension = map.getExtensionFromMimeType(mimeType)
            val filename = String.format("%s_%s_%s.%s",
                    context.getString(R.string.app_name),
                    Date().time.toString(),
                    randomAlphanumericString(10),
                    fileExtension)

            val stream = contentResolver.openInputStream(media.uri)

            if (mimeType == null) mimeType = "multipart/form-data"


            var lastProgress = -1
            val fileBody = ProgressRequestBody(stream, media.mediaSize,
                    mimeType.toMediaTypeOrNull()) { percentage ->
                if (percentage != lastProgress) {
                    emitter.onNext(UploadEvent.ProgressEvent(percentage))
                }
                lastProgress = percentage
            }

            val body = MultipartBody.Part.createFormData("file", filename, fileBody)

            val description = if (media.description != null) {
                MultipartBody.Part.createFormData("description", media.description)
            } else {
                null
            }

            val uploadDisposable = mastodonApi.uploadMedia(body, description)
                    .subscribe({ attachment ->
                        emitter.onNext(UploadEvent.FinishedEvent(attachment))
                        emitter.onComplete()
                    }, { e ->
                        emitter.onError(e)
                    })

            // Cancel the request when our observable is cancelled
            emitter.setDisposable(uploadDisposable)
        }
    }

    private fun downsize(media: QueuedMedia): QueuedMedia {
        val file = createNewImageFile(context)
        DownsizeImageTask.resize(arrayOf(media.uri),
                STATUS_IMAGE_SIZE_LIMIT, context.contentResolver, file)
        return media.copy(uri = file.toUri(), mediaSize = file.length())
    }

    private fun shouldResizeMedia(media: QueuedMedia): Boolean {
        return media.type == QueuedMedia.Type.IMAGE
                && (media.mediaSize > STATUS_IMAGE_SIZE_LIMIT
                || getImageSquarePixels(context.contentResolver, media.uri) > STATUS_IMAGE_PIXEL_SIZE_LIMIT)
    }

    private companion object {
        private const val TAG = "MediaUploaderImpl"
        private const val STATUS_VIDEO_SIZE_LIMIT = 41943040 // 40MiB
        private const val STATUS_AUDIO_SIZE_LIMIT = 41943040 // 40MiB
        private const val STATUS_IMAGE_SIZE_LIMIT = 8388608 // 8MiB
        private const val STATUS_IMAGE_PIXEL_SIZE_LIMIT = 16777216 // 4096^2 Pixels

    }
}
