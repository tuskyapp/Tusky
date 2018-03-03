/* Copyright 2017 Andrew Dawson
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

package com.keylesspalace.tusky

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.util.IOUtils
import com.squareup.picasso.Picasso
import com.theartofdev.edmodo.cropper.CropImage
import kotlinx.android.synthetic.main.activity_edit_profile.*
import kotlinx.android.synthetic.main.toolbar_basic.*
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.*
import java.util.*

private const val TAG = "EditProfileActivity"

private const val HEADER_FILE_NAME = "header.png"
private const val AVATAR_FILE_NAME = "avatar.png"

private const val KEY_OLD_DISPLAY_NAME = "OLD_DISPLAY_NAME"
private const val KEY_OLD_NOTE = "OLD_NOTE"
private const val KEY_IS_SAVING = "IS_SAVING"
private const val KEY_CURRENTLY_PICKING = "CURRENTLY_PICKING"
private const val KEY_AVATAR_CHANGED = "AVATAR_CHANGED"
private const val KEY_HEADER_CHANGED = "HEADER_CHANGED"

private const val AVATAR_PICK_RESULT = 1
private const val HEADER_PICK_RESULT = 2
private const val PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1
private const val AVATAR_SIZE = 120
private const val HEADER_WIDTH = 700
private const val HEADER_HEIGHT = 335

class EditProfileActivity : BaseActivity() {

    private var oldDisplayName: String? = null
    private var oldNote: String? = null
    private var isSaving: Boolean = false
    private var currentlyPicking: PickType = PickType.NOTHING
    private var avatarChanged: Boolean = false
    private var headerChanged: Boolean = false

    private enum class PickType {
        NOTHING,
        AVATAR,
        HEADER
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        setSupportActionBar(toolbar)
        supportActionBar?.run {
            setTitle(R.string.title_edit_profile)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)
        }

        savedInstanceState?.let {
            oldDisplayName = it.getString(KEY_OLD_DISPLAY_NAME)
            oldNote = it.getString(KEY_OLD_NOTE)
            isSaving = it.getBoolean(KEY_IS_SAVING)
            currentlyPicking = it.getSerializable(KEY_CURRENTLY_PICKING) as PickType
            avatarChanged = it.getBoolean(KEY_AVATAR_CHANGED)
            headerChanged = it.getBoolean(KEY_HEADER_CHANGED)

            if(avatarChanged) {
                val avatar = BitmapFactory.decodeFile(getCacheFileForName(AVATAR_FILE_NAME).absolutePath)
                avatarPreview.setImageBitmap(avatar)
            }
            if(headerChanged) {
                val header = BitmapFactory.decodeFile(getCacheFileForName(HEADER_FILE_NAME).absolutePath)
                headerPreview.setImageBitmap(header)
            }
        }

        avatarButton.setOnClickListener { onMediaPick(PickType.AVATAR) }
        headerButton.setOnClickListener { onMediaPick(PickType.HEADER) }

        avatarPreview.setOnClickListener {
            avatarPreview.setImageBitmap(null)
            avatarPreview.visibility = View.INVISIBLE
        }
        headerPreview.setOnClickListener {
            headerPreview.setImageBitmap(null)
            headerPreview.visibility = View.INVISIBLE
        }

        mastodonApi.accountVerifyCredentials().enqueue(object : Callback<Account> {
            override fun onResponse(call: Call<Account>, response: Response<Account>) {
                if (!response.isSuccessful) {
                    onAccountVerifyCredentialsFailed()
                    return
                }
                val me = response.body()
                oldDisplayName = me!!.displayName
                oldNote = me.note.toString()


                displayNameEditText.setText(oldDisplayName)
                noteEditText.setText(oldNote)
                if(!avatarChanged) {
                    Picasso.with(avatarPreview.context)
                            .load(me.avatar)
                            .placeholder(R.drawable.avatar_default)
                            .into(avatarPreview)
                }
                if(!headerChanged) {
                    Picasso.with(headerPreview.context)
                            .load(me.header)
                            .placeholder(R.drawable.account_header_default)
                            .into(headerPreview)
                }
            }

            override fun onFailure(call: Call<Account>, t: Throwable) {
                onAccountVerifyCredentialsFailed()
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.run {
            putString(KEY_OLD_DISPLAY_NAME, oldDisplayName)
            putString(KEY_OLD_NOTE, oldNote)
            putBoolean(KEY_IS_SAVING, isSaving)
            putSerializable(KEY_CURRENTLY_PICKING, currentlyPicking)
            putBoolean(KEY_AVATAR_CHANGED, avatarChanged)
            putBoolean(KEY_HEADER_CHANGED, headerChanged)
        }
        super.onSaveInstanceState(outState)
    }

    private fun onAccountVerifyCredentialsFailed() {
        Log.e(TAG, "The account failed to load.")
    }

    private fun onMediaPick(pickType: PickType) {
        if (currentlyPicking != PickType.NOTHING) {
            // Ignore inputs if another pick operation is still occurring.
            return
        }
        currentlyPicking = pickType
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE)
        } else {
            initiateMediaPicking()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        when (requestCode) {
            PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initiateMediaPicking()
                } else {
                    endMediaPicking()
                    Snackbar.make(avatarButton, R.string.error_media_upload_permission, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun initiateMediaPicking() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "image/*"
        when (currentlyPicking) {
            EditProfileActivity.PickType.AVATAR -> {
                startActivityForResult(intent, AVATAR_PICK_RESULT)
            }
            EditProfileActivity.PickType.HEADER -> {
                startActivityForResult(intent, HEADER_PICK_RESULT)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.edit_profile_toolbar, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
            R.id.action_save -> {
                save()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun save() {
        if (isSaving || currentlyPicking != PickType.NOTHING) {
            return
        }

        isSaving = true
        saveProgressBar.visibility = View.VISIBLE

        val newDisplayName = displayNameEditText.text.toString()
        val displayName = if (oldDisplayName == newDisplayName) {
            null
        } else {
            RequestBody.create(MultipartBody.FORM, newDisplayName)
        }

        val newNote = noteEditText.text.toString()
        val note = if (oldNote == newNote) {
            null
        } else {
            RequestBody.create(MultipartBody.FORM, newNote)
        }

        val avatar = if(avatarChanged) {
            val avatarBody = RequestBody.create(MediaType.parse("image/png"), getCacheFileForName(AVATAR_FILE_NAME))
            MultipartBody.Part.createFormData("avatar", getFileName(), avatarBody)
        } else {
            null
        }

        val header = if(headerChanged) {
            val headerBody = RequestBody.create(MediaType.parse("image/png"), getCacheFileForName(HEADER_FILE_NAME))
            MultipartBody.Part.createFormData("header", getFileName(), headerBody)
        } else {
            null
        }

        if(displayName == null && note == null && avatar == null && header == null) {
            /** if nothing has changed, there is no need to make a network request */
            finish()
            return
        }

        mastodonApi.accountUpdateCredentials(displayName, note, avatar, header).enqueue(object : Callback<Account> {
            override fun onResponse(call: Call<Account>, response: Response<Account>) {
                if (!response.isSuccessful) {
                    onSaveFailure()
                    return
                }
                privatePreferences.edit()
                        .putBoolean("refreshProfileHeader", true)
                        .apply()
                finish()
            }

            override fun onFailure(call: Call<Account>, t: Throwable) {
                onSaveFailure()
            }
        })
    }

    private fun onSaveFailure() {
        isSaving = false
        Snackbar.make(avatarButton, R.string.error_media_upload_sending, Snackbar.LENGTH_LONG).show()
        saveProgressBar.visibility = View.GONE
    }

    private fun beginMediaPicking() {
        when (currentlyPicking) {
            EditProfileActivity.PickType.AVATAR -> {
                avatarProgressBar.visibility = View.VISIBLE
                avatarPreview.visibility = View.INVISIBLE
                avatarButton.setImageDrawable(null)

            }
            EditProfileActivity.PickType.HEADER -> {
                headerProgressBar.visibility = View.VISIBLE
                headerPreview.visibility = View.INVISIBLE
                headerButton.setImageDrawable(null)
            }
        }
    }

    private fun endMediaPicking() {
        avatarProgressBar.visibility = View.GONE
        headerProgressBar.visibility = View.GONE

        currentlyPicking = PickType.NOTHING
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            AVATAR_PICK_RESULT -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    CropImage.activity(data.data)
                            .setInitialCropWindowPaddingRatio(0f)
                            .setAspectRatio(AVATAR_SIZE, AVATAR_SIZE)
                            .start(this)
                } else {
                    endMediaPicking()
                }
            }
            HEADER_PICK_RESULT -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    CropImage.activity(data.data)
                            .setInitialCropWindowPaddingRatio(0f)
                            .setAspectRatio(HEADER_WIDTH, HEADER_HEIGHT)
                            .start(this)
                } else {
                    endMediaPicking()
                }
            }
            CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE -> {
                val result = CropImage.getActivityResult(data)
                when (resultCode) {
                    Activity.RESULT_OK -> beginResize(result.uri)
                    CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE -> onResizeFailure()
                    else -> endMediaPicking()
                }
            }
        }
    }

    private fun beginResize(uri: Uri) {
        beginMediaPicking()
        val width: Int
        val height: Int
        val cacheFile: File
        when (currentlyPicking) {
            EditProfileActivity.PickType.AVATAR -> {
                width = AVATAR_SIZE
                height = AVATAR_SIZE
                cacheFile = getCacheFileForName(AVATAR_FILE_NAME)
            }
            EditProfileActivity.PickType.HEADER -> {
                width = HEADER_WIDTH
                height = HEADER_HEIGHT
                cacheFile = getCacheFileForName(HEADER_FILE_NAME)
            }
            else -> {
                throw AssertionError("PickType not set.")
            }
        }
        ResizeImageTask(contentResolver, width, height, cacheFile, object : ResizeImageTask.Listener {
            override fun onSuccess(resizedImage: Bitmap?) {
                val pickType = currentlyPicking
                endMediaPicking()
                when (pickType) {
                    EditProfileActivity.PickType.AVATAR -> {
                        avatarPreview.setImageBitmap(resizedImage)
                        avatarPreview.visibility = View.VISIBLE
                        avatarButton.setImageResource(R.drawable.ic_add_a_photo_32dp)
                        avatarChanged = true
                    }
                    EditProfileActivity.PickType.HEADER -> {
                        headerPreview.setImageBitmap(resizedImage)
                        headerPreview.visibility = View.VISIBLE
                        headerButton.setImageResource(R.drawable.ic_add_a_photo_32dp)
                        headerChanged = true
                    }
                }
            }

            override fun onFailure() {
                onResizeFailure()
            }
        }).execute(uri)
    }

    private fun onResizeFailure() {
        Snackbar.make(avatarButton, R.string.error_media_upload_sending, Snackbar.LENGTH_LONG).show()
        endMediaPicking()
    }

    private fun getCacheFileForName(filename: String): File {
        return File(cacheDir, filename)
    }

    private fun getFileName(): String {
        return java.lang.Long.toHexString(Random().nextLong())
    }

    private class ResizeImageTask (private val contentResolver: ContentResolver,
                                   private val resizeWidth: Int,
                                   private val resizeHeight: Int,
                                   private val cacheFile: File,
                                   private val listener: Listener) : AsyncTask<Uri, Void, Boolean>() {
        private var resultBitmap: Bitmap? = null

        override fun doInBackground(vararg uris: Uri): Boolean? {
            val uri = uris[0]
            val inputStream: InputStream?
            try {
                inputStream = contentResolver.openInputStream(uri)
            } catch (e: FileNotFoundException) {
                Log.d(TAG, Log.getStackTraceString(e))
                return false
            }

            val sourceBitmap: Bitmap?
            try {
                sourceBitmap = BitmapFactory.decodeStream(inputStream, null, null)
            } catch (error: OutOfMemoryError) {
                Log.d(TAG, Log.getStackTraceString(error))
                return false
            } finally {
                IOUtils.closeQuietly(inputStream)
            }
            if (sourceBitmap == null) {
                return false
            }

            //dont upscale image if its smaller than the desired size
            val bitmap =
                    if(sourceBitmap.width <= resizeWidth && sourceBitmap.height <= resizeHeight) {
                        sourceBitmap
                    } else {
                        Bitmap.createScaledBitmap(sourceBitmap, resizeWidth, resizeHeight, true)
                    }

            resultBitmap = bitmap

            if (!saveBitmapToFile(bitmap, cacheFile)) {
                return false
            }

            if (isCancelled) {
                return false
            }

            return true
        }

        override fun onPostExecute(successful: Boolean) {
            if (successful) {
                listener.onSuccess(resultBitmap)
            } else {
                listener.onFailure()
            }
        }

        fun saveBitmapToFile(bitmap: Bitmap, file: File): Boolean {

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

        internal interface Listener {
            fun onSuccess(resizedImage: Bitmap?)
            fun onFailure()
        }
    }

}
