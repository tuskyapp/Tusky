/* Copyright 2018 Conny Duck
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

package com.keylesspalace.tusky.viewmodel

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.keylesspalace.tusky.EditProfileActivity.Companion.AVATAR_SIZE
import com.keylesspalace.tusky.EditProfileActivity.Companion.HEADER_HEIGHT
import com.keylesspalace.tusky.EditProfileActivity.Companion.HEADER_WIDTH
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.ProfileEditedEvent
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.StringField
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.*
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.OutputStream
import javax.inject.Inject

private const val HEADER_FILE_NAME = "header.png"
private const val AVATAR_FILE_NAME = "avatar.png"

private const val TAG = "EditProfileViewModel"

class EditProfileViewModel  @Inject constructor(
        private val mastodonApi: MastodonApi,
        private val eventHub: EventHub
): ViewModel() {

    val profileData = MutableLiveData<Resource<Account>>()
    val avatarData = MutableLiveData<Resource<Bitmap>>()
    val headerData = MutableLiveData<Resource<Bitmap>>()
    val saveData = MutableLiveData<Resource<Nothing>>()

    private var oldProfileData: Account? = null

    private val callList: MutableList<Call<*>> = mutableListOf()

    fun obtainProfile() {
        if(profileData.value == null || profileData.value is Error) {

            profileData.postValue(Loading())

            val call = mastodonApi.accountVerifyCredentials()
            call.enqueue(object : Callback<Account> {
                override fun onResponse(call: Call<Account>,
                                        response: Response<Account>) {
                    if (response.isSuccessful) {
                        val profile = response.body()
                        oldProfileData = profile
                        profileData.postValue(Success(profile))
                    } else {
                        profileData.postValue(Error())
                    }
                }

                override fun onFailure(call: Call<Account>, t: Throwable) {
                    profileData.postValue(Error())
                }
            })

            callList.add(call)
        }
    }

    fun newAvatar(uri: Uri, context: Context) {
        val cacheFile = getCacheFileForName(context, AVATAR_FILE_NAME)

        resizeImage(uri, context, AVATAR_SIZE, AVATAR_SIZE, cacheFile, avatarData)
    }

    fun newHeader(uri: Uri, context: Context) {
        val cacheFile = getCacheFileForName(context, HEADER_FILE_NAME)

        resizeImage(uri, context, HEADER_WIDTH, HEADER_HEIGHT, cacheFile, headerData)
    }

    private fun resizeImage(uri: Uri,
                            context: Context,
                            resizeWidth: Int,
                            resizeHeight: Int,
                            cacheFile: File,
                            imageLiveData: MutableLiveData<Resource<Bitmap>>) {

        Single.fromCallable {
            val contentResolver = context.contentResolver
            val sourceBitmap = getSampledBitmap(contentResolver, uri, resizeWidth, resizeHeight)

            if (sourceBitmap == null) {
                throw Exception()
            }

            //dont upscale image if its smaller than the desired size
            val bitmap =
                    if (sourceBitmap.width <= resizeWidth && sourceBitmap.height <= resizeHeight) {
                        sourceBitmap
                    } else {
                        Bitmap.createScaledBitmap(sourceBitmap, resizeWidth, resizeHeight, true)
                    }

            if (!saveBitmapToFile(bitmap, cacheFile)) {
                throw Exception()
            }

            bitmap
        }.subscribeOn(Schedulers.io())
                .subscribe({
                    imageLiveData.postValue(Success(it))
                }, {
                    imageLiveData.postValue(Error())
                })
    }

    fun save(newDisplayName: String, newNote: String, newLocked: Boolean, newFields: List<StringField>, context: Context) {

        if(saveData.value is Loading || profileData.value !is Success) {
            return
        }

        val displayName = if (oldProfileData?.displayName == newDisplayName) {
            null
        } else {
            RequestBody.create(MultipartBody.FORM, newDisplayName)
        }

        val note = if (oldProfileData?.source?.note == newNote) {
            null
        } else {
            RequestBody.create(MultipartBody.FORM, newNote)
        }

        val locked = if (oldProfileData?.locked == newLocked) {
            null
        } else {
            RequestBody.create(MultipartBody.FORM, newLocked.toString())
        }

        val avatar = if (avatarData.value is Success && avatarData.value?.data != null) {
            val avatarBody = RequestBody.create(MediaType.parse("image/png"), getCacheFileForName(context, AVATAR_FILE_NAME))
            MultipartBody.Part.createFormData("avatar", StringUtils.randomAlphanumericString(12), avatarBody)
        } else {
            null
        }

        val header = if (headerData.value is Success && headerData.value?.data != null) {
            val headerBody = RequestBody.create(MediaType.parse("image/png"), getCacheFileForName(context, HEADER_FILE_NAME))
            MultipartBody.Part.createFormData("header", StringUtils.randomAlphanumericString(12), headerBody)
        } else {
            null
        }

        // when one field changed, all have to be sent or they unchanged ones would get overridden
        val fieldsUnchanged = oldProfileData?.source?.fields == newFields
        val field1 = calculateFieldToUpdate(newFields.getOrNull(0), fieldsUnchanged)
        val field2 = calculateFieldToUpdate(newFields.getOrNull(1), fieldsUnchanged)
        val field3 = calculateFieldToUpdate(newFields.getOrNull(2), fieldsUnchanged)
        val field4 = calculateFieldToUpdate(newFields.getOrNull(3), fieldsUnchanged)

        if (displayName == null && note == null && locked == null && avatar == null && header == null
                && field1 == null && field2 == null && field3 == null && field4 == null) {
            /** if nothing has changed, there is no need to make a network request */
            saveData.postValue(Success())
            return
        }

        mastodonApi.accountUpdateCredentials(displayName, note, locked, avatar, header,
                field1?.first, field1?.second, field2?.first, field2?.second, field3?.first, field3?.second, field4?.first, field4?.second
        ).enqueue(object : Callback<Account> {
            override fun onResponse(call: Call<Account>, response: Response<Account>) {
                val newProfileData = response.body()
                if (!response.isSuccessful || newProfileData == null) {
                    val errorResponse = response.errorBody()?.string()
                    val errorMsg = if(!errorResponse.isNullOrBlank()) {
                        try {
                            JSONObject(errorResponse).optString("error", null)
                        } catch (e: JSONException) {
                            null
                        }
                    } else {
                        null
                    }
                    saveData.postValue(Error(errorMessage = errorMsg))
                    return
                }
                saveData.postValue(Success())
                eventHub.dispatch(ProfileEditedEvent(newProfileData))
            }

            override fun onFailure(call: Call<Account>, t: Throwable) {
                saveData.postValue(Error())
            }
        })

    }

    // cache activity state for rotation change
    fun updateProfile(newDisplayName: String, newNote: String, newLocked: Boolean, newFields: List<StringField>) {
        if(profileData.value is Success) {
            val newProfileSource = profileData.value?.data?.source?.copy(note = newNote, fields = newFields)
            val newProfile = profileData.value?.data?.copy(displayName = newDisplayName,
                    locked = newLocked, source = newProfileSource)

            profileData.postValue(Success(newProfile))
        }

    }


    private fun calculateFieldToUpdate(newField: StringField?, fieldsUnchanged: Boolean): Pair<RequestBody, RequestBody>? {
        if(fieldsUnchanged || newField == null) {
            return null
        }
        return Pair(
                RequestBody.create(MultipartBody.FORM, newField.name),
                RequestBody.create(MultipartBody.FORM, newField.value)
        )
    }

    private fun getCacheFileForName(context: Context, filename: String): File {
        return File(context.cacheDir, filename)
    }

    private fun saveBitmapToFile(bitmap: Bitmap, file: File): Boolean {

        val outputStream: OutputStream

        try {
            outputStream = FileOutputStream(file)
        } catch (e: FileNotFoundException) {
            Log.w(TAG, Log.getStackTraceString(e))
            return false
        }

        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        IOUtils.closeQuietly(outputStream)

        return true
    }

    override fun onCleared() {
        callList.forEach {
            it.cancel()
        }
    }


}