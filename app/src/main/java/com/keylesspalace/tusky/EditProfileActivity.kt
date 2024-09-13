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

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.options
import com.google.android.material.R as materialR
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.adapter.AccountFieldEditAdapter
import com.keylesspalace.tusky.components.instanceinfo.InstanceInfoRepository
import com.keylesspalace.tusky.databinding.ActivityEditProfileBinding
import com.keylesspalace.tusky.util.Error
import com.keylesspalace.tusky.util.Loading
import com.keylesspalace.tusky.util.Success
import com.keylesspalace.tusky.util.await
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.viewmodel.EditProfileViewModel
import com.keylesspalace.tusky.viewmodel.ProfileDataInUi
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EditProfileActivity : BaseActivity() {

    companion object {
        const val AVATAR_SIZE = 400
        const val HEADER_WIDTH = 1500
        const val HEADER_HEIGHT = 500
    }

    private val viewModel: EditProfileViewModel by viewModels()

    private val binding by viewBinding(ActivityEditProfileBinding::inflate)

    private val accountFieldEditAdapter = AccountFieldEditAdapter()

    private var maxAccountFields = InstanceInfoRepository.DEFAULT_MAX_ACCOUNT_FIELDS

    private enum class PickType {
        AVATAR,
        HEADER
    }

    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result is CropImage.CancelledResult) {
            return@registerForActivityResult
        }

        if (!result.isSuccessful) {
            return@registerForActivityResult onPickFailure(result.error)
        }

        if (result.uriContent == viewModel.getAvatarUri()) {
            viewModel.newAvatarPicked()
        } else {
            viewModel.newHeaderPicked()
        }
    }

    private val currentProfileData
        get() = ProfileDataInUi(
            displayName = binding.displayNameEditText.text.toString(),
            note = binding.noteEditText.text.toString(),
            locked = binding.lockedCheckBox.isChecked,
            fields = accountFieldEditAdapter.getFieldData()
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.run {
            setTitle(R.string.title_edit_profile)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.avatarButton.setOnClickListener { pickMedia(PickType.AVATAR) }
        binding.headerButton.setOnClickListener { pickMedia(PickType.HEADER) }

        binding.fieldList.layoutManager = LinearLayoutManager(this)
        binding.fieldList.adapter = accountFieldEditAdapter

        val plusDrawable = IconicsDrawable(this, GoogleMaterial.Icon.gmd_add).apply {
            sizeDp = 12
            colorInt = MaterialColors.getColor(binding.addFieldButton, materialR.attr.colorOnPrimary)
        }

        binding.addFieldButton.setCompoundDrawablesRelativeWithIntrinsicBounds(
            plusDrawable,
            null,
            null,
            null
        )

        binding.addFieldButton.setOnClickListener {
            accountFieldEditAdapter.addField()
            if (accountFieldEditAdapter.itemCount >= maxAccountFields) {
                it.isVisible = false
            }

            binding.scrollView.post {
                binding.scrollView.smoothScrollTo(0, it.bottom)
            }
        }

        viewModel.obtainProfile()

        lifecycleScope.launch {
            viewModel.profileData.collect { profileRes ->
                if (profileRes == null) return@collect
                when (profileRes) {
                    is Success -> {
                        val me = profileRes.data
                        if (me != null) {
                            binding.displayNameEditText.setText(me.displayName)
                            binding.noteEditText.setText(me.source?.note)
                            binding.lockedCheckBox.isChecked = me.locked

                            accountFieldEditAdapter.setFields(me.source?.fields.orEmpty())
                            binding.addFieldButton.isVisible =
                                (me.source?.fields?.size ?: 0) < maxAccountFields

                            if (viewModel.avatarData.value == null) {
                                Glide.with(this@EditProfileActivity)
                                    .load(me.avatar)
                                    .placeholder(R.drawable.avatar_default)
                                    .transform(
                                        FitCenter(),
                                        RoundedCorners(
                                            resources.getDimensionPixelSize(R.dimen.avatar_radius_80dp)
                                        )
                                    )
                                    .into(binding.avatarPreview)
                            }

                            if (viewModel.headerData.value == null) {
                                Glide.with(this@EditProfileActivity)
                                    .load(me.header)
                                    .into(binding.headerPreview)
                            }
                        }
                    }
                    is Error -> {
                        Snackbar.make(
                            binding.avatarButton,
                            R.string.error_generic,
                            Snackbar.LENGTH_LONG
                        )
                            .setAction(R.string.action_retry) {
                                viewModel.obtainProfile()
                            }
                            .show()
                    }
                    is Loading -> { }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.instanceData.collect { instanceInfo ->
                maxAccountFields = instanceInfo.maxFields
                accountFieldEditAdapter.setFieldLimits(
                    instanceInfo.maxFieldNameLength,
                    instanceInfo.maxFieldValueLength
                )
                binding.addFieldButton.isVisible =
                    accountFieldEditAdapter.itemCount < maxAccountFields
            }
        }

        observeImage(viewModel.avatarData, binding.avatarPreview, true)
        observeImage(viewModel.headerData, binding.headerPreview, false)

        lifecycleScope.launch {
            viewModel.saveData.collect {
                if (it == null) return@collect
                when (it) {
                    is Success -> {
                        finish()
                    }
                    is Loading -> {
                        binding.saveProgressBar.visibility = View.VISIBLE
                    }
                    is Error -> {
                        onSaveFailure(it.errorMessage)
                    }
                }
            }
        }

        binding.displayNameEditText.doAfterTextChanged {
            viewModel.dataChanged(currentProfileData)
        }

        binding.displayNameEditText.doAfterTextChanged {
            viewModel.dataChanged(currentProfileData)
        }

        binding.lockedCheckBox.setOnCheckedChangeListener { _, _ ->
            viewModel.dataChanged(currentProfileData)
        }

        accountFieldEditAdapter.onFieldsChanged = {
            viewModel.dataChanged(currentProfileData)
        }

        val onBackCallback = object : OnBackPressedCallback(enabled = false) {
            override fun handleOnBackPressed() {
                showUnsavedChangesDialog()
            }
        }

        onBackPressedDispatcher.addCallback(this, onBackCallback)
        lifecycleScope.launch {
            viewModel.isChanged.collect { dataWasChanged ->
                onBackCallback.isEnabled = dataWasChanged
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) {
            viewModel.updateProfile(currentProfileData)
        }
    }

    private fun observeImage(
        flow: StateFlow<Uri?>,
        imageView: ImageView,
        roundedCorners: Boolean
    ) {
        lifecycleScope.launch {
            flow.collect { imageUri ->

                // skipping all caches so we can always reuse the same uri
                val glide = Glide.with(imageView)
                    .load(imageUri)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)

                if (roundedCorners) {
                    glide.transform(
                        FitCenter(),
                        RoundedCorners(resources.getDimensionPixelSize(R.dimen.avatar_radius_80dp))
                    ).into(imageView)
                } else {
                    glide.into(imageView)
                }

                imageView.show()
            }
        }
    }

    private fun pickMedia(pickType: PickType) {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "image/*"
        when (pickType) {
            PickType.AVATAR -> {
                cropImage.launch(
                    options {
                        setRequestedSize(AVATAR_SIZE, AVATAR_SIZE)
                        setAspectRatio(AVATAR_SIZE, AVATAR_SIZE)
                        setImageSource(includeGallery = true, includeCamera = false)
                        setOutputUri(viewModel.getAvatarUri())
                        setOutputCompressFormat(Bitmap.CompressFormat.PNG)
                    }
                )
            }
            PickType.HEADER -> {
                cropImage.launch(
                    options {
                        setRequestedSize(HEADER_WIDTH, HEADER_HEIGHT)
                        setAspectRatio(HEADER_WIDTH, HEADER_HEIGHT)
                        setImageSource(includeGallery = true, includeCamera = false)
                        setOutputUri(viewModel.getHeaderUri())
                        setOutputCompressFormat(Bitmap.CompressFormat.PNG)
                    }
                )
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.edit_profile_toolbar, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_save -> {
                save()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun save() = viewModel.save(currentProfileData)

    private fun onSaveFailure(msg: String?) {
        val errorMsg = msg ?: getString(R.string.error_media_upload_sending)
        Snackbar.make(binding.avatarButton, errorMsg, Snackbar.LENGTH_LONG).show()
        binding.saveProgressBar.visibility = View.GONE
    }

    private fun onPickFailure(throwable: Throwable?) {
        Log.w("EditProfileActivity", "failed to pick media", throwable)
        Snackbar.make(
            binding.avatarButton,
            R.string.error_media_upload_sending,
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun showUnsavedChangesDialog() = lifecycleScope.launch {
        when (launchSaveDialog()) {
            AlertDialog.BUTTON_POSITIVE -> save()
            else -> finish()
        }
    }

    private suspend fun launchSaveDialog() = MaterialAlertDialogBuilder(this)
        .setMessage(getString(R.string.dialog_save_profile_changes_message))
        .create()
        .await(R.string.action_save, R.string.action_discard)
}
