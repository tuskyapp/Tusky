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

import android.Manifest
import android.app.ProgressDialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.icu.text.BreakIterator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.text.Spanned
import android.text.style.URLSpan
import android.util.Log
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.content.res.use
import androidx.core.os.BundleCompat
import androidx.core.view.ContentInfoCompat
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.TransitionManager
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.options
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.EmojiAdapter
import com.keylesspalace.tusky.adapter.LocaleAdapter
import com.keylesspalace.tusky.adapter.OnEmojiSelectedListener
import com.keylesspalace.tusky.components.compose.ComposeViewModel.ConfirmationKind
import com.keylesspalace.tusky.components.compose.dialog.CaptionDialog
import com.keylesspalace.tusky.components.compose.dialog.makeFocusDialog
import com.keylesspalace.tusky.components.compose.dialog.showAddPollDialog
import com.keylesspalace.tusky.components.compose.view.ComposeOptionsListener
import com.keylesspalace.tusky.components.compose.view.ComposeScheduleView
import com.keylesspalace.tusky.components.instanceinfo.InstanceInfoRepository
import com.keylesspalace.tusky.databinding.ActivityComposeBinding
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.DraftAttachment
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.NewPoll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.settings.AppTheme
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.settings.PrefKeys.APP_THEME
import com.keylesspalace.tusky.util.MentionSpan
import com.keylesspalace.tusky.util.PickMediaFiles
import com.keylesspalace.tusky.util.getInitialLanguages
import com.keylesspalace.tusky.util.getLocaleList
import com.keylesspalace.tusky.util.getMediaSize
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.highlightSpans
import com.keylesspalace.tusky.util.loadAvatar
import com.keylesspalace.tusky.util.modernLanguageCode
import com.keylesspalace.tusky.util.setDrawableTint
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.unsafeLazy
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.io.File
import java.io.IOException
import java.text.DecimalFormat
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class ComposeActivity :
    BaseActivity(),
    ComposeOptionsListener,
    ComposeAutoCompleteAdapter.AutocompletionProvider,
    OnEmojiSelectedListener,
    Injectable,
    OnReceiveContentListener,
    ComposeScheduleView.OnTimeSetListener,
    CaptionDialog.Listener {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private lateinit var composeOptionsBehavior: BottomSheetBehavior<*>
    private lateinit var addMediaBehavior: BottomSheetBehavior<*>
    private lateinit var emojiBehavior: BottomSheetBehavior<*>
    private lateinit var scheduleBehavior: BottomSheetBehavior<*>

    /** The account that is being used to compose the status */
    private lateinit var activeAccount: AccountEntity

    private var photoUploadUri: Uri? = null

    private val preferences by unsafeLazy { PreferenceManager.getDefaultSharedPreferences(this) }

    @VisibleForTesting
    var maximumTootCharacters = InstanceInfoRepository.DEFAULT_CHARACTER_LIMIT
    var charactersReservedPerUrl = InstanceInfoRepository.DEFAULT_CHARACTERS_RESERVED_PER_URL

    private val viewModel: ComposeViewModel by viewModels { viewModelFactory }

    private val binding by viewBinding(ActivityComposeBinding::inflate)

    private var maxUploadMediaNumber = InstanceInfoRepository.DEFAULT_MAX_MEDIA_ATTACHMENTS

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pickMedia(photoUploadUri!!)
        }
    }
    private val pickMediaFile = registerForActivityResult(PickMediaFiles()) { uris ->
        if (viewModel.media.value.size + uris.size > maxUploadMediaNumber) {
            Toast.makeText(this, resources.getQuantityString(R.plurals.error_upload_max_media_reached, maxUploadMediaNumber, maxUploadMediaNumber), Toast.LENGTH_SHORT).show()
        } else {
            uris.forEach { uri ->
                pickMedia(uri)
            }
        }
    }

    // Contract kicked off by editImageInQueue; expects viewModel.cropImageItemOld set
    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        val uriNew = result.uriContent
        if (result.isSuccessful && uriNew != null) {
            viewModel.cropImageItemOld?.let { itemOld ->
                val size = getMediaSize(contentResolver, uriNew)

                lifecycleScope.launch {
                    viewModel.addMediaToQueue(
                        itemOld.type,
                        uriNew,
                        size,
                        itemOld.description,
                        null, // Intentionally reset focus when cropping
                        itemOld
                    )
                }
            }
        } else if (result == CropImage.CancelledResult) {
            Log.w("ComposeActivity", "Edit image cancelled by user")
        } else {
            Log.w("ComposeActivity", "Edit image failed: " + result.error)
            displayTransientMessage(R.string.error_image_edit_failed)
        }
        viewModel.cropImageItemOld = null
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activeAccount = accountManager.activeAccount ?: return

        val theme = preferences.getString(APP_THEME, AppTheme.DEFAULT.value)
        if (theme == "black") {
            setTheme(R.style.TuskyDialogActivityBlackTheme)
        }
        setContentView(binding.root)

        setupActionBar()

        setupAvatar(activeAccount)
        val mediaAdapter = MediaPreviewAdapter(
            this,
            onAddCaption = { item ->
                CaptionDialog.newInstance(item.localId, item.description, item.uri).show(supportFragmentManager, "caption_dialog")
            },
            onAddFocus = { item ->
                makeFocusDialog(item.focus, item.uri) { newFocus ->
                    viewModel.updateFocus(item.localId, newFocus)
                }
                // TODO this is inconsistent to CaptionDialog (device rotation)?
            },
            onEditImage = this::editImageInQueue,
            onRemove = this::removeMediaFromQueue
        )
        binding.composeMediaPreviewBar.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.composeMediaPreviewBar.adapter = mediaAdapter
        binding.composeMediaPreviewBar.itemAnimator = null

        /* If the composer is started up as a reply to another post, override the "starting" state
         * based on what the intent from the reply request passes. */
        val composeOptions: ComposeOptions? = IntentCompat.getParcelableExtra(intent, COMPOSE_OPTIONS_EXTRA, ComposeOptions::class.java)
        viewModel.setup(composeOptions)

        setupButtons()
        subscribeToUpdates(mediaAdapter)

        if (accountManager.shouldDisplaySelfUsername(this)) {
            binding.composeUsernameView.text = getString(
                R.string.compose_active_account_description,
                activeAccount.fullName
            )
            binding.composeUsernameView.show()
        } else {
            binding.composeUsernameView.hide()
        }

        setupReplyViews(composeOptions?.replyingStatusAuthor, composeOptions?.replyingStatusContent)
        val statusContent = composeOptions?.content
        if (!statusContent.isNullOrEmpty()) {
            binding.composeEditField.setText(statusContent)
        }

        if (!composeOptions?.scheduledAt.isNullOrEmpty()) {
            binding.composeScheduleView.setDateTime(composeOptions?.scheduledAt)
        }

        setupLanguageSpinner(getInitialLanguages(composeOptions?.language, activeAccount))
        setupComposeField(preferences, viewModel.startingText)
        setupContentWarningField(composeOptions?.contentWarning)
        setupPollView()
        applyShareIntent(intent, savedInstanceState)

        /* Finally, overwrite state with data from saved instance state. */
        savedInstanceState?.let {
            photoUploadUri = BundleCompat.getParcelable(it, PHOTO_UPLOAD_URI_KEY, Uri::class.java)

            (it.getSerializable(VISIBILITY_KEY) as Status.Visibility).apply {
                setStatusVisibility(this)
            }

            it.getBoolean(CONTENT_WARNING_VISIBLE_KEY).apply {
                viewModel.contentWarningChanged(this)
            }

            it.getString(SCHEDULED_TIME_KEY)?.let { time ->
                viewModel.updateScheduledAt(time)
            }
        }

        binding.composeEditField.post {
            binding.composeEditField.requestFocus()
        }
    }

    private fun applyShareIntent(intent: Intent, savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            /* Get incoming images being sent through a share action from another app. Only do this
             * when savedInstanceState is null, otherwise both the images from the intent and the
             * instance state will be re-queued. */
            intent.type?.also { type ->
                if (type.startsWith("image/") || type.startsWith("video/") || type.startsWith("audio/")) {
                    when (intent.action) {
                        Intent.ACTION_SEND -> {
                            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uri ->
                                pickMedia(uri)
                            }
                        }
                        Intent.ACTION_SEND_MULTIPLE -> {
                            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.forEach { uri ->
                                pickMedia(uri)
                            }
                        }
                    }
                }

                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                val shareBody = if (!subject.isNullOrBlank() && subject !in text) {
                    subject + '\n' + text
                } else {
                    text
                }

                if (shareBody.isNotBlank()) {
                    val start = binding.composeEditField.selectionStart.coerceAtLeast(0)
                    val end = binding.composeEditField.selectionEnd.coerceAtLeast(0)
                    val left = min(start, end)
                    val right = max(start, end)
                    binding.composeEditField.text.replace(left, right, shareBody, 0, shareBody.length)
                    // move edittext cursor to first when shareBody parsed
                    binding.composeEditField.text.insert(0, "\n")
                    binding.composeEditField.setSelection(0)
                }
            }
        }
    }

    private fun setupReplyViews(replyingStatusAuthor: String?, replyingStatusContent: String?) {
        if (replyingStatusAuthor != null) {
            binding.composeReplyView.show()
            binding.composeReplyView.text = getString(R.string.replying_to, replyingStatusAuthor)
            val arrowDownIcon = IconicsDrawable(this, GoogleMaterial.Icon.gmd_arrow_drop_down).apply { sizeDp = 12 }

            setDrawableTint(this, arrowDownIcon, android.R.attr.textColorTertiary)
            binding.composeReplyView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, arrowDownIcon, null)

            binding.composeReplyView.setOnClickListener {
                TransitionManager.beginDelayedTransition(binding.composeReplyContentView.parent as ViewGroup)

                if (binding.composeReplyContentView.isVisible) {
                    binding.composeReplyContentView.hide()
                    binding.composeReplyView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, arrowDownIcon, null)
                } else {
                    binding.composeReplyContentView.show()
                    val arrowUpIcon = IconicsDrawable(this, GoogleMaterial.Icon.gmd_arrow_drop_up).apply { sizeDp = 12 }

                    setDrawableTint(this, arrowUpIcon, android.R.attr.textColorTertiary)
                    binding.composeReplyView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, arrowUpIcon, null)
                }
            }
        }
        replyingStatusContent?.let { binding.composeReplyContentView.text = it }
    }

    private fun setupContentWarningField(startingContentWarning: String?) {
        if (startingContentWarning != null) {
            binding.composeContentWarningField.setText(startingContentWarning)
        }
        binding.composeContentWarningField.doOnTextChanged { _, _, _, _ -> updateVisibleCharactersLeft() }
    }

    private fun setupComposeField(preferences: SharedPreferences, startingText: String?) {
        binding.composeEditField.setOnReceiveContentListener(this)

        binding.composeEditField.setOnKeyListener { _, keyCode, event -> this.onKeyDown(keyCode, event) }

        binding.composeEditField.setAdapter(
            ComposeAutoCompleteAdapter(
                this,
                preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false),
                preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false),
                preferences.getBoolean(PrefKeys.SHOW_BOT_OVERLAY, true)
            )
        )
        binding.composeEditField.setTokenizer(ComposeTokenizer())

        binding.composeEditField.setText(startingText)
        binding.composeEditField.setSelection(binding.composeEditField.length())

        val mentionColour = binding.composeEditField.linkTextColors.defaultColor
        highlightSpans(binding.composeEditField.text, mentionColour)
        binding.composeEditField.doAfterTextChanged { editable ->
            highlightSpans(editable!!, mentionColour)
            updateVisibleCharactersLeft()
        }

        // work around Android platform bug -> https://issuetracker.google.com/issues/67102093
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O ||
            Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1
        ) {
            binding.composeEditField.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
    }

    private fun subscribeToUpdates(mediaAdapter: MediaPreviewAdapter) {
        lifecycleScope.launch {
            viewModel.instanceInfo.collect { instanceData ->
                maximumTootCharacters = instanceData.maxChars
                charactersReservedPerUrl = instanceData.charactersReservedPerUrl
                maxUploadMediaNumber = instanceData.maxMediaAttachments
                updateVisibleCharactersLeft()
            }
        }

        lifecycleScope.launch {
            viewModel.emoji.collect(::setEmojiList)
        }

        lifecycleScope.launch {
            viewModel.showContentWarning.combine(viewModel.markMediaAsSensitive) { showContentWarning, markSensitive ->
                updateSensitiveMediaToggle(markSensitive, showContentWarning)
                showContentWarning(showContentWarning)
            }.collect()
        }

        lifecycleScope.launch {
            viewModel.statusVisibility.collect(::setStatusVisibility)
        }

        lifecycleScope.launch {
            viewModel.media.collect { media ->
                mediaAdapter.submitList(media)

                binding.composeMediaPreviewBar.visible(media.isNotEmpty())
                updateSensitiveMediaToggle(viewModel.markMediaAsSensitive.value, viewModel.showContentWarning.value)
            }
        }

        lifecycleScope.launch {
            viewModel.poll.collect { poll ->
                binding.pollPreview.visible(poll != null)
                poll?.let(binding.pollPreview::setPoll)
            }
        }

        lifecycleScope.launch {
            viewModel.scheduledAt.collect { scheduledAt ->
                if (scheduledAt == null) {
                    binding.composeScheduleView.resetSchedule()
                } else {
                    binding.composeScheduleView.setDateTime(scheduledAt)
                }
                updateScheduleButton()
            }
        }

        lifecycleScope.launch {
            viewModel.media.combine(viewModel.poll) { media, poll ->
                val active = poll == null &&
                    media.size < maxUploadMediaNumber &&
                    (media.isEmpty() || media.first().type == QueuedMedia.Type.IMAGE)
                enableButton(binding.composeAddMediaButton, active, active)
                enablePollButton(media.isEmpty())
            }.collect()
        }

        lifecycleScope.launch {
            viewModel.uploadError.collect { throwable ->
                if (throwable is UploadServerError) {
                    displayTransientMessage(throwable.errorMessage)
                } else {
                    displayTransientMessage(
                        getString(
                            R.string.error_media_upload_sending_fmt,
                            throwable.message
                        )
                    )
                }
            }
        }
    }

    private fun setupButtons() {
        binding.composeOptionsBottomSheet.listener = this

        composeOptionsBehavior = BottomSheetBehavior.from(binding.composeOptionsBottomSheet)
        addMediaBehavior = BottomSheetBehavior.from(binding.addMediaBottomSheet)
        scheduleBehavior = BottomSheetBehavior.from(binding.composeScheduleView)
        emojiBehavior = BottomSheetBehavior.from(binding.emojiView)

        enableButton(binding.composeEmojiButton, clickable = false, colorActive = false)

        // Setup the interface buttons.
        binding.composeTootButton.setOnClickListener { onSendClicked() }
        binding.composeAddMediaButton.setOnClickListener { openPickDialog() }
        binding.composeToggleVisibilityButton.setOnClickListener { showComposeOptions() }
        binding.composeContentWarningButton.setOnClickListener { onContentWarningChanged() }
        binding.composeEmojiButton.setOnClickListener { showEmojis() }
        binding.composeHideMediaButton.setOnClickListener { toggleHideMedia() }
        binding.composeScheduleButton.setOnClickListener { onScheduleClick() }
        binding.composeScheduleView.setResetOnClickListener { resetSchedule() }
        binding.composeScheduleView.setListener(this)
        binding.atButton.setOnClickListener { atButtonClicked() }
        binding.hashButton.setOnClickListener { hashButtonClicked() }
        binding.descriptionMissingWarningButton.setOnClickListener {
            displayTransientMessage(R.string.hint_media_description_missing)
        }

        val textColor = MaterialColors.getColor(binding.root, android.R.attr.textColorTertiary)

        val cameraIcon = IconicsDrawable(this, GoogleMaterial.Icon.gmd_camera_alt).apply { colorInt = textColor; sizeDp = 18 }
        binding.actionPhotoTake.setCompoundDrawablesRelativeWithIntrinsicBounds(cameraIcon, null, null, null)

        val imageIcon = IconicsDrawable(this, GoogleMaterial.Icon.gmd_image).apply { colorInt = textColor; sizeDp = 18 }
        binding.actionPhotoPick.setCompoundDrawablesRelativeWithIntrinsicBounds(imageIcon, null, null, null)

        val pollIcon = IconicsDrawable(this, GoogleMaterial.Icon.gmd_poll).apply { colorInt = textColor; sizeDp = 18 }
        binding.addPollTextActionTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(pollIcon, null, null, null)

        binding.actionPhotoTake.visible(Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(packageManager) != null)

        binding.actionPhotoTake.setOnClickListener { initiateCameraApp() }
        binding.actionPhotoPick.setOnClickListener { onMediaPick() }
        binding.addPollTextActionTextView.setOnClickListener { openPollDialog() }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (composeOptionsBehavior.state == BottomSheetBehavior.STATE_EXPANDED ||
                        addMediaBehavior.state == BottomSheetBehavior.STATE_EXPANDED ||
                        emojiBehavior.state == BottomSheetBehavior.STATE_EXPANDED ||
                        scheduleBehavior.state == BottomSheetBehavior.STATE_EXPANDED
                    ) {
                        composeOptionsBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                        addMediaBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                        emojiBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                        scheduleBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                        return
                    }

                    handleCloseButton()
                }
            }
        )
    }

    private fun setupLanguageSpinner(initialLanguages: List<String>) {
        binding.composePostLanguageButton.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                viewModel.postLanguage = (parent.adapter.getItem(position) as Locale).modernLanguageCode
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                parent.setSelection(0)
            }
        }
        binding.composePostLanguageButton.apply {
            adapter = LocaleAdapter(context, android.R.layout.simple_spinner_dropdown_item, getLocaleList(initialLanguages))
            setSelection(0)
        }
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.run {
            title = null
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_close_24dp)
        }
    }

    private fun setupAvatar(activeAccount: AccountEntity) {
        val actionBarSizeAttr = intArrayOf(androidx.appcompat.R.attr.actionBarSize)
        val avatarSize = obtainStyledAttributes(null, actionBarSizeAttr).use { a ->
            a.getDimensionPixelSize(0, 1)
        }

        val animateAvatars = preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false)
        loadAvatar(
            activeAccount.profilePictureUrl,
            binding.composeAvatar,
            avatarSize / 8,
            animateAvatars
        )
        binding.composeAvatar.contentDescription = getString(
            R.string.compose_active_account_description,
            activeAccount.fullName
        )
    }

    private fun replaceTextAtCaret(text: CharSequence) {
        // If you select "backward" in an editable, you get SelectionStart > SelectionEnd
        val start = binding.composeEditField.selectionStart.coerceAtMost(binding.composeEditField.selectionEnd)
        val end = binding.composeEditField.selectionStart.coerceAtLeast(binding.composeEditField.selectionEnd)
        val textToInsert = if (start > 0 && !binding.composeEditField.text[start - 1].isWhitespace()) {
            " $text"
        } else {
            text
        }
        binding.composeEditField.text.replace(start, end, textToInsert)

        // Set the cursor after the inserted text
        binding.composeEditField.setSelection(start + text.length)
    }

    fun prependSelectedWordsWith(text: CharSequence) {
        // If you select "backward" in an editable, you get SelectionStart > SelectionEnd
        val start = binding.composeEditField.selectionStart.coerceAtMost(binding.composeEditField.selectionEnd)
        val end = binding.composeEditField.selectionStart.coerceAtLeast(binding.composeEditField.selectionEnd)
        val editorText = binding.composeEditField.text

        if (start == end) {
            // No selection, just insert text at caret
            editorText.insert(start, text)
            // Set the cursor after the inserted text
            binding.composeEditField.setSelection(start + text.length)
        } else {
            var wasWord: Boolean
            var isWord = end < editorText.length && !Character.isWhitespace(editorText[end])
            var newEnd = end

            // Iterate the selection backward so we don't have to juggle indices on insertion
            var index = end - 1
            while (index >= start - 1 && index >= 0) {
                wasWord = isWord
                isWord = !Character.isWhitespace(editorText[index])
                if (wasWord && !isWord) {
                    // We've reached the beginning of a word, perform insert
                    editorText.insert(index + 1, text)
                    newEnd += text.length
                }
                --index
            }

            if (start == 0 && isWord) {
                // Special case when the selection includes the start of the text
                editorText.insert(0, text)
                newEnd += text.length
            }

            // Keep the same text (including insertions) selected
            binding.composeEditField.setSelection(start, newEnd)
        }
    }

    private fun atButtonClicked() {
        prependSelectedWordsWith("@")
    }

    private fun hashButtonClicked() {
        prependSelectedWordsWith("#")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(PHOTO_UPLOAD_URI_KEY, photoUploadUri)
        outState.putSerializable(VISIBILITY_KEY, viewModel.statusVisibility.value)
        outState.putBoolean(CONTENT_WARNING_VISIBLE_KEY, viewModel.showContentWarning.value)
        outState.putString(SCHEDULED_TIME_KEY, viewModel.scheduledAt.value)
        super.onSaveInstanceState(outState)
    }

    private fun displayTransientMessage(message: String) {
        val bar = Snackbar.make(binding.activityCompose, message, Snackbar.LENGTH_LONG)
        // necessary so snackbar is shown over everything
        bar.view.elevation = resources.getDimension(R.dimen.compose_activity_snackbar_elevation)
        bar.setAnchorView(R.id.composeBottomBar)
        bar.show()
    }
    private fun displayTransientMessage(@StringRes stringId: Int) {
        displayTransientMessage(getString(stringId))
    }

    private fun toggleHideMedia() {
        this.viewModel.toggleMarkSensitive()
    }

    private fun updateSensitiveMediaToggle(markMediaSensitive: Boolean, contentWarningShown: Boolean) {
        if (viewModel.media.value.isEmpty()) {
            binding.composeHideMediaButton.hide()
            binding.descriptionMissingWarningButton.hide()
        } else {
            binding.composeHideMediaButton.show()
            @ColorInt val color = if (contentWarningShown) {
                binding.composeHideMediaButton.setImageResource(R.drawable.ic_hide_media_24dp)
                binding.composeHideMediaButton.isClickable = false
                getColor(R.color.transparent_tusky_blue)
            } else {
                binding.composeHideMediaButton.isClickable = true
                if (markMediaSensitive) {
                    binding.composeHideMediaButton.setImageResource(R.drawable.ic_hide_media_24dp)
                    getColor(R.color.tusky_blue)
                } else {
                    binding.composeHideMediaButton.setImageResource(R.drawable.ic_eye_24dp)
                    MaterialColors.getColor(binding.composeHideMediaButton, android.R.attr.textColorTertiary)
                }
            }
            binding.composeHideMediaButton.drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)

            var oneMediaWithoutDescription = false
            for (media in viewModel.media.value) {
                if (media.description.isNullOrEmpty()) {
                    oneMediaWithoutDescription = true
                    break
                }
            }
            binding.descriptionMissingWarningButton.visibility = if (oneMediaWithoutDescription) View.VISIBLE else View.GONE
        }
    }

    private fun updateScheduleButton() {
        if (viewModel.editing) {
            // Can't reschedule a published status
            enableButton(binding.composeScheduleButton, clickable = false, colorActive = false)
        } else {
            @ColorInt val color = if (binding.composeScheduleView.time == null) {
                MaterialColors.getColor(binding.composeScheduleButton, android.R.attr.textColorTertiary)
            } else {
                getColor(R.color.tusky_blue)
            }
            binding.composeScheduleButton.drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
    }

    private fun enableButtons(enable: Boolean, editing: Boolean) {
        binding.composeAddMediaButton.isClickable = enable
        binding.composeToggleVisibilityButton.isClickable = enable && !editing
        binding.composeEmojiButton.isClickable = enable
        binding.composeHideMediaButton.isClickable = enable
        binding.composeScheduleButton.isClickable = enable && !editing
        binding.composeTootButton.isEnabled = enable
    }

    private fun setStatusVisibility(visibility: Status.Visibility) {
        binding.composeOptionsBottomSheet.setStatusVisibility(visibility)
        binding.composeTootButton.setStatusVisibility(visibility)

        val iconRes = when (visibility) {
            Status.Visibility.PUBLIC -> R.drawable.ic_public_24dp
            Status.Visibility.PRIVATE -> R.drawable.ic_lock_outline_24dp
            Status.Visibility.DIRECT -> R.drawable.ic_email_24dp
            Status.Visibility.UNLISTED -> R.drawable.ic_lock_open_24dp
            else -> R.drawable.ic_lock_open_24dp
        }
        binding.composeToggleVisibilityButton.setImageResource(iconRes)
        if (viewModel.editing) {
            // Can't update visibility on published status
            enableButton(binding.composeToggleVisibilityButton, clickable = false, colorActive = false)
        }
    }

    private fun showComposeOptions() {
        if (composeOptionsBehavior.state == BottomSheetBehavior.STATE_HIDDEN || composeOptionsBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
            composeOptionsBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            addMediaBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            emojiBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            scheduleBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        } else {
            composeOptionsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        }
    }

    private fun onScheduleClick() {
        if (viewModel.scheduledAt.value == null) {
            binding.composeScheduleView.openPickDateDialog()
        } else {
            showScheduleView()
        }
    }

    private fun showScheduleView() {
        if (scheduleBehavior.state == BottomSheetBehavior.STATE_HIDDEN || scheduleBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
            scheduleBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            composeOptionsBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            addMediaBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            emojiBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        } else {
            scheduleBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        }
    }

    private fun showEmojis() {
        binding.emojiView.adapter?.let {
            if (it.itemCount == 0) {
                val errorMessage = getString(R.string.error_no_custom_emojis, accountManager.activeAccount!!.domain)
                displayTransientMessage(errorMessage)
            } else {
                if (emojiBehavior.state == BottomSheetBehavior.STATE_HIDDEN || emojiBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                    emojiBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                    composeOptionsBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    addMediaBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    scheduleBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
                } else {
                    emojiBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
                }
            }
        }
    }

    private fun openPickDialog() {
        if (addMediaBehavior.state == BottomSheetBehavior.STATE_HIDDEN || addMediaBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
            addMediaBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            composeOptionsBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            emojiBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            scheduleBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        } else {
            addMediaBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        }
    }

    private fun onMediaPick() {
        addMediaBehavior.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    // Wait until bottom sheet is not collapsed and show next screen after
                    if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                        addMediaBehavior.removeBottomSheetCallback(this)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this@ComposeActivity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(
                                this@ComposeActivity,
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                                PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE
                            )
                        } else {
                            pickMediaFile.launch(true)
                        }
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            }
        )
        addMediaBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun openPollDialog() = lifecycleScope.launch {
        addMediaBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        val instanceParams = viewModel.instanceInfo.first()
        showAddPollDialog(
            context = this@ComposeActivity,
            poll = viewModel.poll.value,
            maxOptionCount = instanceParams.pollMaxOptions,
            maxOptionLength = instanceParams.pollMaxLength,
            minDuration = instanceParams.pollMinDuration,
            maxDuration = instanceParams.pollMaxDuration,
            onUpdatePoll = viewModel::updatePoll
        )
    }

    private fun setupPollView() {
        val margin = resources.getDimensionPixelSize(R.dimen.compose_media_preview_margin)
        val marginBottom = resources.getDimensionPixelSize(R.dimen.compose_media_preview_margin_bottom)

        val layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        layoutParams.setMargins(margin, margin, margin, marginBottom)
        binding.pollPreview.layoutParams = layoutParams

        binding.pollPreview.setOnClickListener {
            val popup = PopupMenu(this, binding.pollPreview)
            val editId = 1
            val removeId = 2
            popup.menu.add(0, editId, 0, R.string.edit_poll)
            popup.menu.add(0, removeId, 0, R.string.action_remove)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    editId -> openPollDialog()
                    removeId -> removePoll()
                }
                true
            }
            popup.show()
        }
    }

    private fun removePoll() {
        viewModel.poll.value = null
        binding.pollPreview.hide()
    }

    override fun onVisibilityChanged(visibility: Status.Visibility) {
        composeOptionsBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        viewModel.statusVisibility.value = visibility
    }

    @VisibleForTesting
    fun calculateTextLength(): Int {
        return statusLength(
            binding.composeEditField.text,
            binding.composeContentWarningField.text,
            charactersReservedPerUrl
        )
    }

    @VisibleForTesting
    val selectedLanguage: String?
        get() = viewModel.postLanguage

    private fun updateVisibleCharactersLeft() {
        val remainingLength = maximumTootCharacters - calculateTextLength()
        binding.composeCharactersLeftView.text = String.format(Locale.getDefault(), "%d", remainingLength)

        val textColor = if (remainingLength < 0) {
            getColor(R.color.tusky_red)
        } else {
            MaterialColors.getColor(binding.composeCharactersLeftView, android.R.attr.textColorTertiary)
        }
        binding.composeCharactersLeftView.setTextColor(textColor)
    }

    private fun onContentWarningChanged() {
        val showWarning = binding.composeContentWarningBar.isGone
        viewModel.contentWarningChanged(showWarning)
        updateVisibleCharactersLeft()
    }

    private fun verifyScheduledTime(): Boolean {
        return binding.composeScheduleView.verifyScheduledTime(binding.composeScheduleView.getDateTime(viewModel.scheduledAt.value))
    }

    private fun onSendClicked() {
        if (verifyScheduledTime()) {
            sendStatus()
        } else {
            showScheduleView()
        }
    }

    /** This is for the fancy keyboards which can insert images and stuff, and drag&drop etc */
    override fun onReceiveContent(view: View, contentInfo: ContentInfoCompat): ContentInfoCompat? {
        if (contentInfo.clip.description.hasMimeType("image/*")) {
            val split = contentInfo.partition { item: ClipData.Item -> item.uri != null }
            split.first?.let { content ->
                for (i in 0 until content.clip.itemCount) {
                    pickMedia(
                        content.clip.getItemAt(i).uri,
                        contentInfo.clip.description.label as String?
                    )
                }
            }
            return split.second
        }
        return contentInfo
    }

    private fun sendStatus() {
        enableButtons(false, viewModel.editing)
        val contentText = binding.composeEditField.text.toString()
        var spoilerText = ""
        if (viewModel.showContentWarning.value) {
            spoilerText = binding.composeContentWarningField.text.toString()
        }
        val characterCount = calculateTextLength()
        if ((characterCount <= 0 || contentText.isBlank()) && viewModel.media.value.isEmpty()) {
            binding.composeEditField.error = getString(R.string.error_empty)
            enableButtons(true, viewModel.editing)
        } else if (characterCount <= maximumTootCharacters) {
            lifecycleScope.launch {
                viewModel.sendStatus(contentText, spoilerText, activeAccount.id)
                deleteDraftAndFinish()
            }
        } else {
            binding.composeEditField.error = getString(R.string.error_compose_character_limit)
            enableButtons(true, viewModel.editing)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickMediaFile.launch(true)
            } else {
                Snackbar.make(
                    binding.activityCompose,
                    R.string.error_media_upload_permission,
                    Snackbar.LENGTH_SHORT
                ).apply {
                    setAction(R.string.action_retry) { onMediaPick() }
                    // necessary so snackbar is shown over everything
                    view.elevation = resources.getDimension(R.dimen.compose_activity_snackbar_elevation)
                    show()
                }
            }
        }
    }

    private fun initiateCameraApp() {
        addMediaBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        val photoFile: File = try {
            createNewImageFile(this)
        } catch (ex: IOException) {
            displayTransientMessage(R.string.error_media_upload_opening)
            return
        }

        // Continue only if the File was successfully created
        photoUploadUri = FileProvider.getUriForFile(
            this,
            BuildConfig.APPLICATION_ID + ".fileprovider",
            photoFile
        )
        takePicture.launch(photoUploadUri)
    }

    private fun enableButton(button: ImageButton, clickable: Boolean, colorActive: Boolean) {
        button.isEnabled = clickable
        setDrawableTint(
            this,
            button.drawable,
            if (colorActive) {
                android.R.attr.textColorTertiary
            } else {
                R.attr.textColorDisabled
            }
        )
    }

    private fun enablePollButton(enable: Boolean) {
        binding.addPollTextActionTextView.isEnabled = enable
        val textColor = MaterialColors.getColor(
            binding.addPollTextActionTextView,
            if (enable) {
                android.R.attr.textColorTertiary
            } else {
                R.attr.textColorDisabled
            }
        )
        binding.addPollTextActionTextView.setTextColor(textColor)
        binding.addPollTextActionTextView.compoundDrawablesRelative[0].colorFilter = PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN)
    }

    private fun editImageInQueue(item: QueuedMedia) {
        // If input image is lossless, output image should be lossless.
        // Currently the only supported lossless format is png.
        val mimeType: String? = contentResolver.getType(item.uri)
        val isPng: Boolean = mimeType != null && mimeType.endsWith("/png")
        val tempFile = createNewImageFile(this, if (isPng) ".png" else ".jpg")

        // "Authority" must be the same as the android:authorities string in AndroidManifest.xml
        val uriNew = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", tempFile)

        viewModel.cropImageItemOld = item

        cropImage.launch(
            options(uri = item.uri) {
                setOutputUri(uriNew)
                setOutputCompressFormat(if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG)
            }
        )
    }

    private fun removeMediaFromQueue(item: QueuedMedia) {
        viewModel.removeMediaFromQueue(item)
    }

    private fun sanitizePickMediaDescription(description: String?): String? {
        if (description == null) {
            return null
        }

        // The Gboard android keyboard attaches this text whenever the user
        // pastes something from the keyboard's suggestion bar.
        // Due to different end user locales, the exact text may vary, but at
        // least in version 13.4.08, all of the translations contained the
        // string "Gboard".
        if ("Gboard" in description) {
            return null
        }

        return description
    }

    private fun pickMedia(uri: Uri, description: String? = null) {
        var sanitizedDescription = sanitizePickMediaDescription(description)

        lifecycleScope.launch {
            viewModel.pickMedia(uri, sanitizedDescription).onFailure { throwable ->
                val errorString = when (throwable) {
                    is FileSizeException -> {
                        val decimalFormat = DecimalFormat("0.##")
                        val allowedSizeInMb = throwable.allowedSizeInBytes.toDouble() / (1024 * 1024)
                        val formattedSize = decimalFormat.format(allowedSizeInMb)
                        getString(R.string.error_multimedia_size_limit, formattedSize)
                    }
                    is VideoOrImageException -> getString(R.string.error_media_upload_image_or_video)
                    else -> getString(R.string.error_media_upload_opening)
                }
                displayTransientMessage(errorString)
            }
        }
    }

    private fun showContentWarning(show: Boolean) {
        TransitionManager.beginDelayedTransition(binding.composeContentWarningBar.parent as ViewGroup)
        @ColorInt val color = if (show) {
            binding.composeContentWarningBar.show()
            binding.composeContentWarningField.setSelection(binding.composeContentWarningField.text.length)
            binding.composeContentWarningField.requestFocus()
            getColor(R.color.tusky_blue)
        } else {
            binding.composeContentWarningBar.hide()
            binding.composeEditField.requestFocus()
            MaterialColors.getColor(binding.composeContentWarningButton, android.R.attr.textColorTertiary)
        }
        binding.composeContentWarningButton.drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            handleCloseButton()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.isCtrlPressed) {
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    // send toot by pressing CTRL + ENTER
                    this.onSendClicked()
                    return true
                }
            }

            if (keyCode == KeyEvent.KEYCODE_BACK) {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleCloseButton() {
        val contentText = binding.composeEditField.text.toString()
        val contentWarning = binding.composeContentWarningField.text.toString()
        when (viewModel.handleCloseButton(contentText, contentWarning)) {
            ConfirmationKind.NONE -> {
                viewModel.stopUploads()
                finishWithoutSlideOutAnimation()
            }
            ConfirmationKind.SAVE_OR_DISCARD ->
                getSaveAsDraftOrDiscardDialog(contentText, contentWarning).show()
            ConfirmationKind.UPDATE_OR_DISCARD ->
                getUpdateDraftOrDiscardDialog(contentText, contentWarning).show()
            ConfirmationKind.CONTINUE_EDITING_OR_DISCARD_CHANGES ->
                getContinueEditingOrDiscardDialog().show()
            ConfirmationKind.CONTINUE_EDITING_OR_DISCARD_DRAFT ->
                getDeleteEmptyDraftOrContinueEditing().show()
        }
    }

    /**
     * User is editing a new post, and can either save the changes as a draft or discard them.
     */
    private fun getSaveAsDraftOrDiscardDialog(contentText: String, contentWarning: String): AlertDialog.Builder {
        val warning = if (viewModel.media.value.isNotEmpty()) {
            R.string.compose_save_draft_loses_media
        } else {
            R.string.compose_save_draft
        }

        return AlertDialog.Builder(this)
            .setMessage(warning)
            .setPositiveButton(R.string.action_save) { _, _ ->
                viewModel.stopUploads()
                saveDraftAndFinish(contentText, contentWarning)
            }
            .setNegativeButton(R.string.action_delete) { _, _ ->
                viewModel.stopUploads()
                deleteDraftAndFinish()
            }
    }

    /**
     * User is editing an existing draft, and can either update the draft with the new changes or
     * discard them.
     */
    private fun getUpdateDraftOrDiscardDialog(contentText: String, contentWarning: String): AlertDialog.Builder {
        val warning = if (viewModel.media.value.isNotEmpty()) {
            R.string.compose_save_draft_loses_media
        } else {
            R.string.compose_save_draft
        }

        return AlertDialog.Builder(this)
            .setMessage(warning)
            .setPositiveButton(R.string.action_save) { _, _ ->
                viewModel.stopUploads()
                saveDraftAndFinish(contentText, contentWarning)
            }
            .setNegativeButton(R.string.action_discard) { _, _ ->
                viewModel.stopUploads()
                finishWithoutSlideOutAnimation()
            }
    }

    /**
     * User is editing a post (scheduled, or posted), and can either go back to editing, or
     * discard the changes.
     */
    private fun getContinueEditingOrDiscardDialog(): AlertDialog.Builder {
        return AlertDialog.Builder(this)
            .setMessage(R.string.compose_unsaved_changes)
            .setPositiveButton(R.string.action_continue_edit) { _, _ ->
                // Do nothing, dialog will dismiss, user can continue editing
            }
            .setNegativeButton(R.string.action_discard) { _, _ ->
                viewModel.stopUploads()
                finishWithoutSlideOutAnimation()
            }
    }

    /**
     * User is editing an existing draft and making it empty.
     * The user can either delete the empty draft or go back to editing.
     */
    private fun getDeleteEmptyDraftOrContinueEditing(): AlertDialog.Builder {
        return AlertDialog.Builder(this)
            .setMessage(R.string.compose_delete_draft)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteDraft()
                viewModel.stopUploads()
                finishWithoutSlideOutAnimation()
            }
            .setNegativeButton(R.string.action_continue_edit) { _, _ ->
                // Do nothing, dialog will dismiss, user can continue editing
            }
    }

    private fun deleteDraftAndFinish() {
        viewModel.deleteDraft()
        finishWithoutSlideOutAnimation()
    }

    private fun saveDraftAndFinish(contentText: String, contentWarning: String) {
        lifecycleScope.launch {
            val dialog = if (viewModel.shouldShowSaveDraftDialog()) {
                ProgressDialog.show(
                    this@ComposeActivity,
                    null,
                    getString(R.string.saving_draft),
                    true,
                    false
                )
            } else {
                null
            }
            viewModel.saveDraft(contentText, contentWarning)
            dialog?.cancel()
            finishWithoutSlideOutAnimation()
        }
    }

    override fun search(token: String): List<ComposeAutoCompleteAdapter.AutocompleteResult> {
        return viewModel.searchAutocompleteSuggestions(token)
    }

    override fun onEmojiSelected(shortcode: String) {
        replaceTextAtCaret(":$shortcode: ")
    }

    private fun setEmojiList(emojiList: List<Emoji>?) {
        if (emojiList != null) {
            val animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)
            binding.emojiView.adapter = EmojiAdapter(emojiList, this@ComposeActivity, animateEmojis)
            enableButton(binding.composeEmojiButton, true, emojiList.isNotEmpty())
        }
    }

    data class QueuedMedia(
        val localId: Int,
        val uri: Uri,
        val type: Type,
        val mediaSize: Long,
        val uploadPercent: Int = 0,
        val id: String? = null,
        val description: String? = null,
        val focus: Attachment.Focus? = null,
        val state: State
    ) {
        enum class Type {
            IMAGE, VIDEO, AUDIO;
        }
        enum class State {
            UPLOADING, UNPROCESSED, PROCESSED, PUBLISHED
        }
    }

    override fun onTimeSet(time: String?) {
        viewModel.updateScheduledAt(time)
        if (verifyScheduledTime()) {
            scheduleBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        } else {
            showScheduleView()
        }
    }

    private fun resetSchedule() {
        viewModel.updateScheduledAt(null)
        scheduleBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    override fun onUpdateDescription(localId: Int, description: String) {
        viewModel.updateDescription(localId, description)
    }

    /**
     * Status' kind. This particularly affects how the status is handled if the user
     * backs out of the edit.
     */
    enum class ComposeKind {
        /** Status is new */
        NEW,

        /** Editing a posted status */
        EDIT_POSTED,

        /** Editing a status started as an existing draft */
        EDIT_DRAFT,

        /** Editing an an existing scheduled status */
        EDIT_SCHEDULED
    }

    @Parcelize
    data class ComposeOptions(
        // Let's keep fields var until all consumers are Kotlin
        var scheduledTootId: String? = null,
        var draftId: Int? = null,
        var content: String? = null,
        var mediaUrls: List<String>? = null,
        var mediaDescriptions: List<String>? = null,
        var mentionedUsernames: Set<String>? = null,
        var inReplyToId: String? = null,
        var replyVisibility: Status.Visibility? = null,
        var visibility: Status.Visibility? = null,
        var contentWarning: String? = null,
        var replyingStatusAuthor: String? = null,
        var replyingStatusContent: String? = null,
        var mediaAttachments: List<Attachment>? = null,
        var draftAttachments: List<DraftAttachment>? = null,
        var scheduledAt: String? = null,
        var sensitive: Boolean? = null,
        var poll: NewPoll? = null,
        var modifiedInitialState: Boolean? = null,
        var language: String? = null,
        var statusId: String? = null,
        var kind: ComposeKind? = null
    ) : Parcelable

    companion object {
        private const val TAG = "ComposeActivity" // logging tag
        private const val PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1

        internal const val COMPOSE_OPTIONS_EXTRA = "COMPOSE_OPTIONS"
        private const val PHOTO_UPLOAD_URI_KEY = "PHOTO_UPLOAD_URI"
        private const val VISIBILITY_KEY = "VISIBILITY"
        private const val SCHEDULED_TIME_KEY = "SCHEDULE"
        private const val CONTENT_WARNING_VISIBLE_KEY = "CONTENT_WARNING_VISIBLE"

        /**
         * @param options ComposeOptions to configure the ComposeActivity
         * @return an Intent to start the ComposeActivity
         */
        @JvmStatic
        fun startIntent(
            context: Context,
            options: ComposeOptions
        ): Intent {
            return Intent(context, ComposeActivity::class.java).apply {
                putExtra(COMPOSE_OPTIONS_EXTRA, options)
            }
        }

        fun canHandleMimeType(mimeType: String?): Boolean {
            return mimeType != null && (mimeType.startsWith("image/") || mimeType.startsWith("video/") || mimeType.startsWith("audio/") || mimeType == "text/plain")
        }

        /**
         * Calculate the effective status length.
         *
         * Some text is counted differently:
         *
         * In the status body:
         *
         * - URLs always count for [urlLength] characters irrespective of their actual length
         *   (https://docs.joinmastodon.org/user/posting/#links)
         * - Mentions ("@user@some.instance") only count the "@user" part
         *   (https://docs.joinmastodon.org/user/posting/#mentions)
         * - Hashtags are always treated as their actual length, including the "#"
         *   (https://docs.joinmastodon.org/user/posting/#hashtags)
         *
         * Content warning text is always treated as its full length, URLs and other entities
         * are not treated differently.
         *
         * @param body status body text
         * @param contentWarning optional content warning text
         * @param urlLength the number of characters attributed to URLs
         * @return the effective status length
         */
        @JvmStatic
        fun statusLength(body: Spanned, contentWarning: Spanned?, urlLength: Int): Int {
            var length = body.toString().perceivedCharacterLength() - body.getSpans(0, body.length, URLSpan::class.java)
                .fold(0) { acc, span ->
                    // Accumulate a count of characters to be *ignored* in the final length
                    acc + when (span) {
                        is MentionSpan -> {
                            // Ignore everything from the second "@" (if present)
                            span.url.length - (
                                span.url.indexOf("@", 1).takeIf { it >= 0 }
                                    ?: span.url.length
                                )
                        }
                        else -> {
                            // Expected to be negative if the URL length < maxUrlLength
                            span.url.perceivedCharacterLength() - urlLength
                        }
                    }
                }

            // Content warning text is treated as is, URLs or mentions there are not special
            contentWarning?.let { length += it.toString().perceivedCharacterLength() }
            return length
        }

        // String.length would count emojis as multiple characters but Mastodon counts them as 1, so we need this workaround
        private fun String.perceivedCharacterLength(): Int {
            val breakIterator = BreakIterator.getCharacterInstance()
            breakIterator.setText(this)
            var count = 0
            while (breakIterator.next() != BreakIterator.DONE) {
                count++
            }
            return count
        }
    }
}
