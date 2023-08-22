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

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.ProfileEditedEvent
import com.keylesspalace.tusky.components.instanceinfo.InstanceInfo
import com.keylesspalace.tusky.components.instanceinfo.InstanceInfoRepository
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.StringField
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.Error
import com.keylesspalace.tusky.util.Loading
import com.keylesspalace.tusky.util.Resource
import com.keylesspalace.tusky.util.Success
import com.keylesspalace.tusky.util.getServerErrorMessage
import com.keylesspalace.tusky.util.randomAlphanumericString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject

private const val HEADER_FILE_NAME = "header.png"
private const val AVATAR_FILE_NAME = "avatar.png"

/**
 * Conveniently groups Profile Data users can modify in the UI.
 */
internal data class ProfileData(
    val displayName: String,
    val note: String,
    val locked: Boolean,
    val fields: List<StringField>
)

class EditProfileViewModel @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val eventHub: EventHub,
    private val application: Application,
    private val instanceInfoRepo: InstanceInfoRepository
) : ViewModel() {

    val profileData = MutableLiveData<Resource<Account>>()
    val avatarData = MutableLiveData<Uri>()
    val headerData = MutableLiveData<Uri>()
    val saveData = MutableLiveData<Resource<Nothing>>()

    val instanceData: Flow<InstanceInfo> = instanceInfoRepo::getInstanceInfo.asFlow()
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    private var apiProfileAccount: Account? = null

    fun obtainProfile() = viewModelScope.launch {
        if (profileData.value == null || profileData.value is Error) {
            profileData.postValue(Loading())

            mastodonApi.accountVerifyCredentials().fold(
                { profile ->
                    apiProfileAccount = profile
                    profileData.postValue(Success(profile))
                },
                {
                    profileData.postValue(Error())
                }
            )
        }
    }

    fun getAvatarUri() = getCacheFileForName(AVATAR_FILE_NAME).toUri()

    fun getHeaderUri() = getCacheFileForName(HEADER_FILE_NAME).toUri()

    fun newAvatarPicked() {
        avatarData.value = getAvatarUri()
    }

    fun newHeaderPicked() {
        headerData.value = getHeaderUri()
    }

    internal fun save(newProfileData: ProfileData) {
        if (saveData.value is Loading || profileData.value !is Success) {
            return
        }

        saveData.value = Loading()

        val diff = getProfileDiff(apiProfileAccount, newProfileData)
        if (!diff.hasChanges()) {
            // if nothing has changed, there is no need to make an api call
            saveData.postValue(Success())
            return
        }

        viewModelScope.launch {
            var avatarFileBody: MultipartBody.Part? = null
            diff.avatarFile?.let {
                avatarFileBody = MultipartBody.Part.createFormData("avatar", randomAlphanumericString(12),  it.asRequestBody("image/png".toMediaTypeOrNull()))
            }

            var headerFileBody: MultipartBody.Part? = null
            diff.headerFile?.let {
                headerFileBody = MultipartBody.Part.createFormData("header", randomAlphanumericString(12),  it.asRequestBody("image/png".toMediaTypeOrNull()))
            }

            mastodonApi.accountUpdateCredentials(
                diff.displayName?.toRequestBody(MultipartBody.FORM),
                diff.note?.toRequestBody(MultipartBody.FORM),
                diff.locked?.toString()?.toRequestBody(MultipartBody.FORM),
                avatarFileBody,
                headerFileBody,
                diff.field1?.first?.toRequestBody(MultipartBody.FORM),
                diff.field1?.second?.toRequestBody(MultipartBody.FORM),
                diff.field2?.first?.toRequestBody(MultipartBody.FORM),
                diff.field2?.second?.toRequestBody(MultipartBody.FORM),
                diff.field3?.first?.toRequestBody(MultipartBody.FORM),
                diff.field3?.second?.toRequestBody(MultipartBody.FORM),
                diff.field4?.first?.toRequestBody(MultipartBody.FORM),
                diff.field4?.second?.toRequestBody(MultipartBody.FORM),
            ).fold(
                { newAccountData ->
                    saveData.postValue(Success())
                    eventHub.dispatch(ProfileEditedEvent(newAccountData))
                },
                { throwable ->
                    saveData.postValue(Error(errorMessage = throwable.getServerErrorMessage()))
                }
            )
        }
    }

    // cache activity state for rotation change
    internal fun updateProfile(newProfileData: ProfileData) {
        if (profileData.value is Success) {
            val newProfileSource = profileData.value?.data?.source?.copy(note = newProfileData.note, fields = newProfileData.fields)
            val newProfile = profileData.value?.data?.copy(
                displayName = newProfileData.displayName,
                locked = newProfileData.locked,
                source = newProfileSource
            )

            profileData.postValue(Success(newProfile))
        }
    }

    internal fun hasUnsavedChanges(newProfileData: ProfileData): Boolean {
        val diff = getProfileDiff(apiProfileAccount, newProfileData)

        return diff.hasChanges()
    }

    private fun getProfileDiff(oldProfileAccount: Account?, newProfileData: ProfileData): DiffProfileData {
        val displayName = if (oldProfileAccount?.displayName == newProfileData.displayName) {
            null
        } else {
            newProfileData.displayName
        }

        val note = if (oldProfileAccount?.source?.note == newProfileData.note) {
            null
        } else {
            newProfileData.note
        }

        val locked = if (oldProfileAccount?.locked == newProfileData.locked) {
            null
        } else {
            newProfileData.locked
        }

        val avatarFile = if (avatarData.value != null) {
            getCacheFileForName(AVATAR_FILE_NAME)
        } else {
            null
        }

        val headerFile = if (headerData.value != null) {
            getCacheFileForName(HEADER_FILE_NAME)
        } else {
            null
        }

        // when one field changed, all have to be sent or they unchanged ones would get overridden
        val allFieldsUnchanged = oldProfileAccount?.source?.fields == newProfileData.fields
        val field1 = calculateFieldToUpdate(newProfileData.fields.getOrNull(0), allFieldsUnchanged)
        val field2 = calculateFieldToUpdate(newProfileData.fields.getOrNull(1), allFieldsUnchanged)
        val field3 = calculateFieldToUpdate(newProfileData.fields.getOrNull(2), allFieldsUnchanged)
        val field4 = calculateFieldToUpdate(newProfileData.fields.getOrNull(3), allFieldsUnchanged)

        return DiffProfileData(
            displayName, note, locked, field1, field2, field3, field4, headerFile, avatarFile
        )
    }

    private fun calculateFieldToUpdate(newField: StringField?, fieldsUnchanged: Boolean): Pair<String, String>? {
        if (fieldsUnchanged || newField == null) {
            return null
        }
        return Pair(
            newField.name,
            newField.value,
        )
    }

    private fun getCacheFileForName(filename: String): File {
        return File(application.cacheDir, filename)
    }

    private data class DiffProfileData(
        val displayName: String?,
        val note: String?,
        val locked: Boolean?,
        val field1: Pair<String, String>?,
        val field2: Pair<String, String>?,
        val field3: Pair<String, String>?,
        val field4: Pair<String, String>?,
        val headerFile: File?,
        val avatarFile: File?
    ) {
        fun hasChanges() = displayName != null || note != null || locked != null ||
            avatarFile != null || headerFile != null || field1 != null || field2 != null ||
            field3 != null || field4 != null
    }
}
