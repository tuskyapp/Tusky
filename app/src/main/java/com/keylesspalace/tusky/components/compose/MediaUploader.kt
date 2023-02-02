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

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.compose.ComposeActivity.QueuedMedia
import com.keylesspalace.tusky.components.instanceinfo.InstanceInfo
import com.keylesspalace.tusky.network.MediaUploadApi
import com.keylesspalace.tusky.network.ProgressRequestBody
import com.keylesspalace.tusky.util.MEDIA_SIZE_UNKNOWN
import com.keylesspalace.tusky.util.getImageSquarePixels
import com.keylesspalace.tusky.util.getMediaSize
import com.keylesspalace.tusky.util.getServerErrorMessage
import com.keylesspalace.tusky.util.randomAlphanumericString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import retrofit2.HttpException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

sealed interface FinalUploadEvent

sealed class UploadEvent {
    data class ProgressEvent(val percentage: Int) : UploadEvent()
    data class FinishedEvent(val mediaId: String, val processed: Boolean) : UploadEvent(), FinalUploadEvent
    data class ErrorEvent(val error: Throwable) : UploadEvent(), FinalUploadEvent
}

data class UploadData(
    val flow: Flow<UploadEvent>,
    val scope: CoroutineScope
)

fun createNewImageFile(context: Context, suffix: String = ".jpg"): File {
    // Create an image file name
    val randomId = randomAlphanumericString(12)
    val imageFileName = "Tusky_${randomId}_"
    val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File.createTempFile(
        imageFileName, /* prefix */
        suffix, /* suffix */
        storageDir /* directory */
    )
}

data class PreparedMedia(val type: QueuedMedia.Type, val uri: Uri, val size: Long)

class FileSizeException(val allowedSizeInBytes: Int) : Exception()
class MediaTypeException : Exception()
class CouldNotOpenFileException : Exception()
class UploadServerError(val errorMessage: String) : Exception()

@Singleton
class MediaUploader @Inject constructor(
    private val context: Context,
    private val mediaUploadApi: MediaUploadApi
) {

    private val uploads = mutableMapOf<Int, UploadData>()

    private var mostRecentId: Int = 0

    fun getNewLocalMediaId(): Int {
        return mostRecentId++
    }

    suspend fun getMediaUploadState(localId: Int): FinalUploadEvent {
        return uploads[localId]?.flow
            ?.filterIsInstance<FinalUploadEvent>()
            ?.first()
            ?: UploadEvent.ErrorEvent(IllegalStateException("media upload with id $localId not found"))
    }

    /**
     * Uploads media.
     * @param media the media to upload
     * @param instanceInfo info about the current media to make sure the media gets resized correctly
     * @return A Flow emitting upload events.
     * The Flow is hot, in order to cancel upload or clear resources call [cancelUploadScope].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun uploadMedia(media: QueuedMedia, instanceInfo: InstanceInfo): Flow<UploadEvent> {
        val uploadScope = CoroutineScope(Dispatchers.IO)
        val uploadFlow = flow {
            if (shouldResizeMedia(media, instanceInfo)) {
                emit(downsize(media, instanceInfo))
            } else {
                emit(media)
            }
        }
            .flatMapLatest { upload(it) }
            .catch { exception ->
                emit(UploadEvent.ErrorEvent(exception))
            }
            .shareIn(uploadScope, SharingStarted.Lazily, 1)

        uploads[media.localId] = UploadData(uploadFlow, uploadScope)
        return uploadFlow
    }

    /**
     * Cancels the CoroutineScope of a media upload.
     * Call this when to abort the upload or to clean up resources after upload info is no longer needed
     */
    fun cancelUploadScope(vararg localMediaIds: Int) {
        localMediaIds.forEach { localId ->
            uploads.remove(localId)?.scope?.cancel()
        }
    }

    fun prepareMedia(inUri: Uri, instanceInfo: InstanceInfo): PreparedMedia {
        var mediaSize = MEDIA_SIZE_UNKNOWN
        var uri = inUri
        val mimeType: String?

        try {
            when (inUri.scheme) {
                ContentResolver.SCHEME_CONTENT -> {

                    mimeType = contentResolver.getType(uri)

                    val suffix = "." + MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType ?: "tmp")

                    contentResolver.openInputStream(inUri).use { input ->
                        if (input == null) {
                            Log.w(TAG, "Media input is null")
                            uri = inUri
                            return@use
                        }
                        val file = File.createTempFile("randomTemp1", suffix, context.cacheDir)
                        FileOutputStream(file.absoluteFile).use { out ->
                            input.copyTo(out)
                            uri = FileProvider.getUriForFile(
                                context,
                                BuildConfig.APPLICATION_ID + ".fileprovider",
                                file
                            )
                            mediaSize = getMediaSize(contentResolver, uri)
                        }
                    }
                }
                ContentResolver.SCHEME_FILE -> {
                    val path = uri.path
                    if (path == null) {
                        Log.w(TAG, "empty uri path $uri")
                        throw CouldNotOpenFileException()
                    }
                    val inputFile = File(path)
                    val suffix = inputFile.name.substringAfterLast('.', "tmp")
                    mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(suffix)
                    val file = File.createTempFile("randomTemp1", ".$suffix", context.cacheDir)
                    val input = FileInputStream(inputFile)

                    FileOutputStream(file.absoluteFile).use { out ->
                        input.copyTo(out)
                        uri = FileProvider.getUriForFile(
                            context,
                            BuildConfig.APPLICATION_ID + ".fileprovider",
                            file
                        )
                        mediaSize = getMediaSize(contentResolver, uri)
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown uri scheme $uri")
                    throw CouldNotOpenFileException()
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, e)
            throw CouldNotOpenFileException()
        }
        if (mediaSize == MEDIA_SIZE_UNKNOWN) {
            Log.w(TAG, "Could not determine file size of upload")
            throw MediaTypeException()
        }

        if (mimeType != null) {
            return when (mimeType.substring(0, mimeType.indexOf('/'))) {
                "video" -> {
                    if (mediaSize > instanceInfo.videoSizeLimit) {
                        throw FileSizeException(instanceInfo.videoSizeLimit)
                    }
                    PreparedMedia(QueuedMedia.Type.VIDEO, uri, mediaSize)
                }
                "image" -> {
                    PreparedMedia(QueuedMedia.Type.IMAGE, uri, mediaSize)
                }
                "audio" -> {
                    if (mediaSize > instanceInfo.videoSizeLimit) {
                        throw FileSizeException(instanceInfo.videoSizeLimit)
                    }
                    PreparedMedia(QueuedMedia.Type.AUDIO, uri, mediaSize)
                }
                else -> {
                    throw MediaTypeException()
                }
            }
        } else {
            Log.w(TAG, "Could not determine mime type of upload")
            throw MediaTypeException()
        }
    }

    private val contentResolver = context.contentResolver

    private suspend fun upload(media: QueuedMedia): Flow<UploadEvent> {
        return callbackFlow {
            var mimeType = contentResolver.getType(media.uri)

            // Android's MIME type suggestions from file extensions is broken for at least
            // .m4a files. See https://github.com/tuskyapp/Tusky/issues/3189 for details.
            // Sniff the content of the file to determine the actual type.
            if (mimeType != null && (
                mimeType.startsWith("audio/", ignoreCase = true) ||
                    mimeType.startsWith("video/", ignoreCase = true)
                )
            ) {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, media.uri)
                mimeType = retriever.extractMetadata(METADATA_KEY_MIMETYPE)
            }
            val map = MimeTypeMap.getSingleton()
            val fileExtension = map.getExtensionFromMimeType(mimeType)
            val filename = "%s_%s_%s.%s".format(
                context.getString(R.string.app_name),
                Date().time.toString(),
                randomAlphanumericString(10),
                fileExtension
            )

            val stream = contentResolver.openInputStream(media.uri)

            if (mimeType == null) mimeType = "multipart/form-data"

            var lastProgress = -1
            val fileBody = ProgressRequestBody(
                stream!!, media.mediaSize,
                mimeType.toMediaTypeOrNull()!!
            ) { percentage ->
                if (percentage != lastProgress) {
                    trySend(UploadEvent.ProgressEvent(percentage))
                }
                lastProgress = percentage
            }

            val body = MultipartBody.Part.createFormData("file", filename, fileBody)

            val description = if (media.description != null) {
                MultipartBody.Part.createFormData("description", media.description)
            } else {
                null
            }

            val focus = if (media.focus != null) {
                MultipartBody.Part.createFormData("focus", "${media.focus.x},${media.focus.y}")
            } else {
                null
            }

            val uploadResponse = mediaUploadApi.uploadMedia(body, description, focus)
            val responseBody = uploadResponse.body()
            if (uploadResponse.isSuccessful && responseBody != null) {
                send(UploadEvent.FinishedEvent(responseBody.id, uploadResponse.code() == 200))
            } else {
                val error = HttpException(uploadResponse)
                val errorMessage = error.getServerErrorMessage()
                if (errorMessage == null) {
                    throw error
                } else {
                    throw UploadServerError(errorMessage)
                }
            }

            awaitClose()
        }
    }

    private fun downsize(media: QueuedMedia, instanceInfo: InstanceInfo): QueuedMedia {
        val file = createNewImageFile(context)
        downsizeImage(media.uri, instanceInfo.imageSizeLimit, contentResolver, file)
        return media.copy(uri = file.toUri(), mediaSize = file.length())
    }

    private fun shouldResizeMedia(media: QueuedMedia, instanceInfo: InstanceInfo): Boolean {
        return media.type == QueuedMedia.Type.IMAGE &&
            (media.mediaSize > instanceInfo.imageSizeLimit || getImageSquarePixels(context.contentResolver, media.uri) > instanceInfo.imageMatrixLimit)
    }

    private companion object {
        private const val TAG = "MediaUploader"
    }
}
