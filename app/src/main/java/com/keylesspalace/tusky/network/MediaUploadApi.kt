package com.keylesspalace.tusky.network

import com.keylesspalace.tusky.entity.MediaUploadResult
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/** endpoints defined in this interface will be called with a higher timeout than usual
 * which is necessary for media uploads to succeed on some servers
 */
interface MediaUploadApi {
    @Multipart
    @POST("api/v2/media")
    suspend fun uploadMedia(
        @Part file: MultipartBody.Part,
        @Part description: MultipartBody.Part? = null,
        @Part focus: MultipartBody.Part? = null
    ): Response<MediaUploadResult>
}
