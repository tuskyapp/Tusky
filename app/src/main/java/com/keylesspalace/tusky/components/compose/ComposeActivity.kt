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
import android.app.Activity
import android.app.ProgressDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.preference.PreferenceManager
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.TransitionManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.ComposeAutoCompleteAdapter
import com.keylesspalace.tusky.adapter.EmojiAdapter
import com.keylesspalace.tusky.adapter.OnEmojiSelectedListener
import com.keylesspalace.tusky.components.compose.dialog.makeCaptionDialog
import com.keylesspalace.tusky.components.compose.view.ComposeOptionsListener
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.NewPoll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.*
import com.keylesspalace.tusky.components.compose.dialog.showAddPollDialog
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.activity_compose.*
import java.io.File
import java.io.IOException
import java.util.*
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class ComposeActivity : BaseActivity(),
        ComposeOptionsListener,
        ComposeAutoCompleteAdapter.AutocompletionProvider,
        OnEmojiSelectedListener,
        Injectable,
        InputConnectionCompat.OnCommitContentListener,
        TimePickerDialog.OnTimeSetListener {

    @Inject
    lateinit var mastodonApi: MastodonApi
    @Inject
    lateinit var database: AppDatabase
    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private lateinit var composeOptionsBehavior: BottomSheetBehavior<*>
    private lateinit var addMediaBehavior: BottomSheetBehavior<*>
    private lateinit var emojiBehavior: BottomSheetBehavior<*>
    private lateinit var scheduleBehavior: BottomSheetBehavior<*>

    // this only exists when a status is trying to be sent, but uploads are still occurring
    private var finishingUploadDialog: ProgressDialog? = null
    private var currentInputContentInfo: InputContentInfoCompat? = null
    private var currentFlags: Int = 0
    private var photoUploadUri: Uri? = null
    // Accessors for testing, hence package scope
    private var maximumTootCharacters = DEFAULT_CHARACTER_LIMIT

    private var composeOptions: ComposeOptions? = null
    private lateinit var viewModel: ComposeViewModel

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val theme = preferences.getString("appTheme", ThemeUtils.APP_THEME_DEFAULT)
        if (theme == "black") {
            setTheme(R.style.TuskyDialogActivityBlackTheme)
        }
        setContentView(R.layout.activity_compose)

        setupActionBar()
        // do not do anything when not logged in, activity will be finished in super.onCreate() anyway
        val activeAccount = accountManager.activeAccount ?: return

        setupAvatar(preferences, activeAccount)
        val mediaAdapter = MediaPreviewAdapter(
                this,
                onAddCaption = { item ->
                    makeCaptionDialog(item.description, item.uri) { newDescription ->
                        viewModel.updateDescription(item.localId, newDescription)
                    }
                },
                onRemove = this::removeMediaFromQueue
        )
        composeMediaPreviewBar.layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        composeMediaPreviewBar.adapter = mediaAdapter
        composeMediaPreviewBar.itemAnimator = null

        viewModel = ViewModelProviders.of(this, viewModelFactory)[ComposeViewModel::class.java]

        subscribeToUpdates(mediaAdapter)
        setupButtons()

        /* If the composer is started up as a reply to another post, override the "starting" state
         * based on what the intent from the reply request passes. */
        if (intent != null) {
            this.composeOptions = intent.getParcelableExtra<ComposeOptions?>(COMPOSE_OPTIONS_EXTRA)
            viewModel.setup(composeOptions)
            setupReplyViews(composeOptions?.replyingStatusAuthor)
            val tootText = composeOptions?.tootText
            if (!tootText.isNullOrEmpty()) {
                composeEditField.setText(tootText)
            }
        }

        if (!TextUtils.isEmpty(composeOptions?.scheduledAt)) {
            composeScheduleView.setDateTime(composeOptions?.scheduledAt)
        }

        updateScheduleButton()
        updateVisibleCharactersLeft()

        setupComposeField(viewModel.startingText)
        setupContentWarningField(composeOptions?.contentWarning)
        setupPollView()
        applyShareIntent(intent, savedInstanceState)

        composeEditField.requestFocus()
    }

    private fun applyShareIntent(intent: Intent?, savedInstanceState: Bundle?) {
        if (intent != null && savedInstanceState == null) {
            /* Get incoming images being sent through a share action from another app. Only do this
             * when savedInstanceState is null, otherwise both the images from the intent and the
             * instance state will be re-queued. */
            val type = intent.type
            if (type != null) {
                if (type.startsWith("image/") || type.startsWith("video/")) {
                    val uriList = ArrayList<Uri>()
                    if (intent.action != null) {
                        when (intent.action) {
                            Intent.ACTION_SEND -> {
                                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                                if (uri != null) {
                                    uriList.add(uri)
                                }
                            }
                            Intent.ACTION_SEND_MULTIPLE -> {
                                val list = intent.getParcelableArrayListExtra<Uri>(
                                        Intent.EXTRA_STREAM)
                                if (list != null) {
                                    for (uri in list) {
                                        if (uri != null) {
                                            uriList.add(uri)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    for (uri in uriList) {
                        pickMedia(uri)
                    }
                } else if (type == "text/plain") {
                    val action = intent.action
                    if (action != null && action == Intent.ACTION_SEND) {
                        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                        val shareBody = if (subject != null && text != null) {
                            if (subject != text && !text.contains(subject)) {
                                String.format("%s\n%s", subject, text)
                            } else {
                                text
                            }
                        } else text ?: subject

                        if (shareBody != null) {
                            val start = composeEditField.selectionStart.coerceAtLeast(0)
                            val end = composeEditField.selectionEnd.coerceAtLeast(0)
                            val left = min(start, end)
                            val right = max(start, end)
                            composeEditField.text.replace(left, right, shareBody, 0, shareBody.length)
                        }
                    }
                }
            }
        }
    }

    private fun setupReplyViews(replyingStatusAuthor: String?) {
        if (replyingStatusAuthor != null) {
            composeReplyView.show()
            composeReplyView.text = getString(R.string.replying_to, replyingStatusAuthor)
            val arrowDownIcon = IconicsDrawable(this, GoogleMaterial.Icon.gmd_arrow_drop_down).sizeDp(12)

            ThemeUtils.setDrawableTint(this, arrowDownIcon, android.R.attr.textColorTertiary)
            composeReplyView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, arrowDownIcon, null)

            composeReplyView.setOnClickListener {
                TransitionManager.beginDelayedTransition(composeReplyContentView.parent as ViewGroup)

                if (composeReplyContentView.isVisible) {
                    composeReplyContentView.hide()
                    composeReplyView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, arrowDownIcon, null)
                } else {
                    composeReplyContentView.show()
                    val arrowUpIcon = IconicsDrawable(this, GoogleMaterial.Icon.gmd_arrow_drop_up).sizeDp(12)

                    ThemeUtils.setDrawableTint(this, arrowUpIcon, android.R.attr.textColorTertiary)
                    composeReplyView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, arrowUpIcon, null)
                }
            }
        }
        composeOptions?.replyingStatusContent?.let { composeReplyContentView.text = it }
    }

    private fun setupContentWarningField(startingContentWarning: String?) {
        composeContentWarningField.onTextChanged { _, _, _, _ -> updateVisibleCharactersLeft() }
        if (startingContentWarning != null) {
            composeContentWarningField.setText(startingContentWarning)
        }
    }

    private fun setupComposeField(startingText: String?) {
        composeEditField.setOnCommitContentListener(this)
        val mentionColour = composeEditField.linkTextColors.defaultColor
        highlightSpans(composeEditField.text, mentionColour)
        composeEditField.afterTextChanged { editable ->
            highlightSpans(editable, mentionColour)
            updateVisibleCharactersLeft()
        }

        composeEditField.setOnKeyListener { _, keyCode, event -> this.onKeyDown(keyCode, event) }

        composeEditField.setAdapter(
                ComposeAutoCompleteAdapter(this))
        composeEditField.setTokenizer(ComposeTokenizer())

        composeEditField.setText(startingText)
        composeEditField.setSelection(composeEditField.length())

        // work around Android platform bug -> https://issuetracker.google.com/issues/67102093
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O
                || Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
            composeEditField.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
    }

    private fun subscribeToUpdates(mediaAdapter: MediaPreviewAdapter) {
        withLifecycleContext {
            viewModel.instanceParams.observe { instanceData ->
                maximumTootCharacters = instanceData.maxChars
                updateVisibleCharactersLeft()
                composeScheduleButton.visible(instanceData.supportsScheduled)
            }
            viewModel.emoji.observe { emoji -> setEmojiList(emoji) }
            combineLiveData(viewModel.markMediaAsSensitive, viewModel.showContentWarning) { markSensitive, showContentWarning ->
                updateSensitiveMediaToggle(markSensitive, showContentWarning)
                showContentWarning(showContentWarning)
            }.subscribe()
            viewModel.statusVisibility.observe { visibility ->
                setStatusVisibility(visibility)
            }
            viewModel.media.observe { media ->
                composeMediaPreviewBar.visible(media.isNotEmpty())
                mediaAdapter.submitList(media)
                updateSensitiveMediaToggle(viewModel.markMediaAsSensitive.value != false, viewModel.showContentWarning.value != false)
            }
            viewModel.poll.observe { poll ->
                pollPreview.visible(poll != null)
                poll?.let(pollPreview::setPoll)
            }
            combineOptionalLiveData(viewModel.media, viewModel.poll) { media, poll ->
                val active = poll == null
                        && media!!.size != 4
                        && media.firstOrNull()?.type != QueuedMedia.Type.VIDEO
                enableButton(composeAddMediaButton, active, active)
                enablePollButton(media.isNullOrEmpty())
            }.subscribe()
            viewModel.uploadError.observe {
                displayTransientError(R.string.error_media_upload_sending)
            }
        }
    }

    private fun setupButtons() {
        composeOptionsBottomSheet.listener = this

        composeOptionsBehavior = BottomSheetBehavior.from(composeOptionsBottomSheet)
        composeOptionsBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        addMediaBehavior = BottomSheetBehavior.from(addMediaBottomSheet)
        scheduleBehavior = BottomSheetBehavior.from(composeScheduleView)
        emojiBehavior = BottomSheetBehavior.from(emojiView)

        emojiView.layoutManager = GridLayoutManager(this, 3, GridLayoutManager.HORIZONTAL, false)
        enableButton(composeEmojiButton, clickable = false, colorActive = false)

        // Setup the interface buttons.
        composeTootButton.setOnClickListener { onSendClicked() }
        composeAddMediaButton.setOnClickListener { openPickDialog() }
        composeToggleVisibilityButton.setOnClickListener { showComposeOptions() }
        composeContentWarningButton.setOnClickListener { onContentWarningChanged() }
        composeEmojiButton.setOnClickListener { showEmojis() }
        composeHideMediaButton.setOnClickListener { toggleHideMedia() }
        composeScheduleButton.setOnClickListener { showScheduleView() }
        composeScheduleView.setResetOnClickListener { resetSchedule() }
        atButton.setOnClickListener { atButtonClicked() }
        hashButton.setOnClickListener { hashButtonClicked() }

        val textColor = ThemeUtils.getColor(this, android.R.attr.textColorTertiary)

        val cameraIcon = IconicsDrawable(this, GoogleMaterial.Icon.gmd_camera_alt).color(textColor).sizeDp(18)
        actionPhotoTake.setCompoundDrawablesRelativeWithIntrinsicBounds(cameraIcon, null, null, null)

        val imageIcon = IconicsDrawable(this, GoogleMaterial.Icon.gmd_image).color(textColor).sizeDp(18)
        actionPhotoPick.setCompoundDrawablesRelativeWithIntrinsicBounds(imageIcon, null, null, null)

        val pollIcon = IconicsDrawable(this, GoogleMaterial.Icon.gmd_poll).color(textColor).sizeDp(18)
        addPollTextActionTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(pollIcon, null, null, null)

        actionPhotoTake.setOnClickListener { initiateCameraApp() }
        actionPhotoPick.setOnClickListener { onMediaPick() }
        addPollTextActionTextView.setOnClickListener { openPollDialog() }
    }

    private fun setupActionBar() {
        setSupportActionBar(toolbar)
        supportActionBar?.run {
            title = null
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            val closeIcon = AppCompatResources.getDrawable(this@ComposeActivity, R.drawable.ic_close_24dp)
            ThemeUtils.setDrawableTint(this@ComposeActivity, closeIcon!!, R.attr.compose_close_button_tint)
            setHomeAsUpIndicator(closeIcon)
        }

    }

    private fun setupAvatar(preferences: SharedPreferences, activeAccount: AccountEntity) {
        val actionBarSizeAttr = intArrayOf(R.attr.actionBarSize)
        val a = obtainStyledAttributes(null, actionBarSizeAttr)
        val avatarSize = a.getDimensionPixelSize(0, 1)
        a.recycle()

        val animateAvatars = preferences.getBoolean("animateGifAvatars", false)
        loadAvatar(
                activeAccount.profilePictureUrl,
                composeAvatar,
                avatarSize / 8,
                animateAvatars
        )
        composeAvatar.contentDescription = getString(R.string.compose_active_account_description,
                activeAccount.fullName)
    }

    private fun replaceTextAtCaret(text: CharSequence) {
        // If you select "backward" in an editable, you get SelectionStart > SelectionEnd
        val start = composeEditField.selectionStart.coerceAtMost(composeEditField.selectionEnd)
        val end = composeEditField.selectionStart.coerceAtLeast(composeEditField.selectionEnd)
        composeEditField.text.replace(start, end, text)

        // Set the cursor after the inserted text
        composeEditField.setSelection(start + text.length)
    }

    private fun atButtonClicked() {
        replaceTextAtCaret("@")
    }

    private fun hashButtonClicked() {
        replaceTextAtCaret("#")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (currentInputContentInfo != null) {
            outState.putParcelable("commitContentInputContentInfo",
                    currentInputContentInfo!!.unwrap() as Parcelable?)
            outState.putInt("commitContentFlags", currentFlags)
        }
        currentInputContentInfo = null
        currentFlags = 0
        outState.putParcelable("photoUploadUri", photoUploadUri)
        super.onSaveInstanceState(outState)
    }

    private fun displayTransientError(@StringRes stringId: Int) {
        val bar = Snackbar.make(activityCompose, stringId, Snackbar.LENGTH_LONG)
        //necessary so snackbar is shown over everything
        bar.view.elevation = resources.getDimension(R.dimen.compose_activity_snackbar_elevation)
        bar.show()
    }

    private fun toggleHideMedia() {
        this.viewModel.toggleMarkSensitive()
    }

    private fun updateSensitiveMediaToggle(markMediaSensitive: Boolean, contentWarningShown: Boolean) {
        TransitionManager.beginDelayedTransition(composeHideMediaButton.parent as ViewGroup)

        if (viewModel.media.value.isNullOrEmpty()) {
            composeHideMediaButton.hide()
        } else {
            composeHideMediaButton.show()
            @ColorInt val color = if (contentWarningShown) {
                composeHideMediaButton.setImageResource(R.drawable.ic_hide_media_24dp)
                composeHideMediaButton.isClickable = false
                ContextCompat.getColor(this, R.color.compose_media_visible_button_disabled_blue)

            } else {
                composeHideMediaButton.isClickable = true
                if (markMediaSensitive) {
                    composeHideMediaButton.setImageResource(R.drawable.ic_hide_media_24dp)
                    ContextCompat.getColor(this, R.color.tusky_blue)
                } else {
                    composeHideMediaButton.setImageResource(R.drawable.ic_eye_24dp)
                    ThemeUtils.getColor(this, android.R.attr.textColorTertiary)
                }
            }
            composeHideMediaButton.drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
    }

    private fun updateScheduleButton() {
        @ColorInt val color = if (composeScheduleView.time == null) {
            ThemeUtils.getColor(this, android.R.attr.textColorTertiary)
        } else {
            ContextCompat.getColor(this, R.color.tusky_blue)
        }
        composeScheduleButton.drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    private fun enableButtons(enable: Boolean) {
        composeAddMediaButton.isClickable = enable
        composeToggleVisibilityButton.isClickable = enable
        composeEmojiButton.isClickable = enable
        composeHideMediaButton.isClickable = enable
        composeScheduleButton.isClickable = enable
        composeTootButton.isEnabled = enable
    }

    private fun setStatusVisibility(visibility: Status.Visibility) {
        composeOptionsBottomSheet.setStatusVisibility(visibility)
        composeTootButton.setStatusVisibility(visibility)

        val iconRes = when (visibility) {
            Status.Visibility.PUBLIC -> R.drawable.ic_public_24dp
            Status.Visibility.PRIVATE -> R.drawable.ic_lock_outline_24dp
            Status.Visibility.DIRECT -> R.drawable.ic_email_24dp
            Status.Visibility.UNLISTED -> R.drawable.ic_lock_open_24dp
            else -> R.drawable.ic_lock_open_24dp
        }
        val drawable = ThemeUtils.getTintedDrawable(this, iconRes, android.R.attr.textColorTertiary)
        composeToggleVisibilityButton.setImageDrawable(drawable)
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
        emojiView.adapter?.let {
            if (it.itemCount == 0) {
                val errorMessage = getString(R.string.error_no_custom_emojis, accountManager.activeAccount!!.domain)
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
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
        addMediaBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                //Wait until bottom sheet is not collapsed and show next screen after
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    addMediaBehavior.removeBottomSheetCallback(this)
                    if (ContextCompat.checkSelfPermission(this@ComposeActivity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this@ComposeActivity,
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                                PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE)
                    } else {
                        initiateMediaPicking()
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        }
        )
        addMediaBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun openPollDialog() {
        addMediaBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        val instanceParams = viewModel.instanceParams.value!!
        showAddPollDialog(this, viewModel.poll.value, instanceParams.pollMaxOptions,
                instanceParams.pollMaxLength, viewModel::updatePoll)
    }

    private fun setupPollView() {
        val margin = resources.getDimensionPixelSize(R.dimen.compose_media_preview_margin)
        val marginBottom = resources.getDimensionPixelSize(R.dimen.compose_media_preview_margin_bottom)

        val layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        layoutParams.setMargins(margin, margin, margin, marginBottom)
        pollPreview.layoutParams = layoutParams

        pollPreview.setOnClickListener {
            val popup = PopupMenu(this, pollPreview)
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
        pollPreview.hide()
    }

    override fun onVisibilityChanged(visibility: Status.Visibility) {
        composeOptionsBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        viewModel.statusVisibility.value = visibility
    }

    private fun calculateTextLength(): Int {
        var offset = 0
        val urlSpans = composeEditField.urls
        if (urlSpans != null) {
            for (span in urlSpans) {
                offset += max(0, span.url.length - MAXIMUM_URL_LENGTH)
            }
        }
        var length = composeEditField.length() - offset
        if (viewModel.showContentWarning.value!!) {
            length += composeContentWarningField.length()
        }
        return length
    }

    private fun updateVisibleCharactersLeft() {
        composeCharactersLeftView.text = String.format(Locale.getDefault(), "%d", maximumTootCharacters - calculateTextLength())
    }

    private fun onContentWarningChanged() {
        val showWarning = composeContentWarningBar.isGone
        viewModel.showContentWarning.value = showWarning
        updateVisibleCharactersLeft()
    }

    private fun onSendClicked() {
        enableButtons(false)
        sendStatus()
    }

    /** This is for the fancy keyboards which can insert images and stuff. */
    override fun onCommitContent(inputContentInfo: InputContentInfoCompat, flags: Int, opts: Bundle): Boolean {
        try {
            currentInputContentInfo?.releasePermission()
        } catch (e: Exception) {
            Log.e(TAG, "InputContentInfoCompat#releasePermission() failed." + e.message)
        } finally {
            currentInputContentInfo = null
        }

        // Verify the returned content's type is of the correct MIME type
        val supported = inputContentInfo.description.hasMimeType("image/*")

        return supported && onCommitContentInternal(inputContentInfo, flags)
    }

    private fun onCommitContentInternal(inputContentInfo: InputContentInfoCompat, flags: Int): Boolean {
        if (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0) {
            try {
                inputContentInfo.requestPermission()
            } catch (e: Exception) {
                Log.e(TAG, "InputContentInfoCompat#requestPermission() failed." + e.message)
                return false
            }
        }

        // Determine the file size before putting handing it off to be put in the queue.
        pickMedia(inputContentInfo.contentUri)

        currentInputContentInfo = inputContentInfo
        currentFlags = flags

        return true
    }

    private fun sendStatus() {
        val contentText = composeEditField.text.toString()
        var spoilerText = ""
        if (viewModel.showContentWarning.value!!) {
            spoilerText = composeContentWarningField.text.toString()
        }
        val characterCount = calculateTextLength()
        if ((characterCount <= 0 || contentText.isBlank()) && viewModel.media.value!!.isEmpty()) {
            composeEditField.error = getString(R.string.error_empty)
            enableButtons(true)
        } else if (characterCount <= maximumTootCharacters) {
            finishingUploadDialog = ProgressDialog.show(
                    this, getString(R.string.dialog_title_finishing_media_upload),
                    getString(R.string.dialog_message_uploading_media), true, true)

            viewModel.sendStatus(contentText, spoilerText).observe(this, androidx.lifecycle.Observer {
                finishingUploadDialog?.dismiss()
                finishWithoutSlideOutAnimation()
            })

        } else {
            composeEditField.error = getString(R.string.error_compose_character_limit)
            enableButtons(true)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode == PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initiateMediaPicking()
            } else {
                val bar = Snackbar.make(activityCompose, R.string.error_media_upload_permission,
                        Snackbar.LENGTH_SHORT)
                bar.setAction(R.string.action_retry) { onMediaPick()}
                //necessary so snackbar is shown over everything
                bar.view.elevation = resources.getDimension(R.dimen.compose_activity_snackbar_elevation)
                bar.show()
            }
        }
    }

    private fun initiateCameraApp() {
        addMediaBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        // We don't need to ask for permission in this case, because the used calls require
        // android.permission.WRITE_EXTERNAL_STORAGE only on SDKs *older* than Kitkat, which was
        // way before permission dialogues have been introduced.
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            val photoFile: File = try {
                createNewImageFile(this)
            } catch (ex: IOException) {
                displayTransientError(R.string.error_media_upload_opening)
                return
            }

            // Continue only if the File was successfully created
            photoUploadUri = FileProvider.getUriForFile(this,
                    BuildConfig.APPLICATION_ID + ".fileprovider",
                    photoFile)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUploadUri)
            startActivityForResult(intent, MEDIA_TAKE_PHOTO_RESULT)
        }
    }

    private fun initiateMediaPicking() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        val mimeTypes = arrayOf("image/*", "video/*")
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        startActivityForResult(intent, MEDIA_PICK_RESULT)
    }

    private fun enableButton(button: ImageButton, clickable: Boolean, colorActive: Boolean) {
        button.isEnabled = clickable
        ThemeUtils.setDrawableTint(this, button.drawable,
                if (colorActive) android.R.attr.textColorTertiary
                else R.attr.compose_media_button_disabled_tint)
    }

    private fun enablePollButton(enable: Boolean) {
        addPollTextActionTextView.isEnabled = enable
        val textColor = ThemeUtils.getColor(this,
                if (enable) android.R.attr.textColorTertiary
                else R.attr.compose_media_button_disabled_tint)
        addPollTextActionTextView.setTextColor(textColor)
        addPollTextActionTextView.compoundDrawablesRelative[0].colorFilter = PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN)
    }

    private fun removeMediaFromQueue(item: QueuedMedia) {
        viewModel.removeMediaFromQueue(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (resultCode == Activity.RESULT_OK && requestCode == MEDIA_PICK_RESULT && intent != null) {
            pickMedia(intent.data!!)
        } else if (resultCode == Activity.RESULT_OK && requestCode == MEDIA_TAKE_PHOTO_RESULT) {
            pickMedia(photoUploadUri!!)
        }
    }

    private fun pickMedia(uri: Uri) {
        withLifecycleContext {
            viewModel.pickMedia(uri).observe { exceptionOrItem ->
                exceptionOrItem.asLeftOrNull()?.let {
                    val errorId = when (it) {
                        is VideoSizeException -> {
                            R.string.error_video_upload_size
                        }
                        is VideoOrImageException -> {
                            R.string.error_media_upload_image_or_video
                        }
                        else -> {
                            R.string.error_media_upload_opening
                        }
                    }
                    displayTransientError(errorId)
                }

            }
        }
    }

    private fun showContentWarning(show: Boolean) {
        TransitionManager.beginDelayedTransition(composeContentWarningBar.parent as ViewGroup)
        @ColorInt val color = if (show) {
            composeContentWarningBar.show()
            composeContentWarningField.setSelection(composeContentWarningField.text.length)
            composeContentWarningField.requestFocus()
            ContextCompat.getColor(this, R.color.tusky_blue)
        } else {
            composeContentWarningBar.hide()
            composeEditField.requestFocus()
            ThemeUtils.getColor(this, android.R.attr.textColorTertiary)
        }
        composeContentWarningButton.drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            handleCloseButton()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        // Acting like a teen: deliberately ignoring parent.
        if (composeOptionsBehavior.state == BottomSheetBehavior.STATE_EXPANDED ||
                addMediaBehavior.state == BottomSheetBehavior.STATE_EXPANDED ||
                emojiBehavior.state == BottomSheetBehavior.STATE_EXPANDED ||
                scheduleBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            composeOptionsBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            addMediaBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            emojiBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            scheduleBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            return
        }

        handleCloseButton()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        Log.d(TAG, event.toString())
        if (event.isCtrlPressed) {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                // send toot by pressing CTRL + ENTER
                this.onSendClicked()
                return true
            }
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            onBackPressed()
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun handleCloseButton() {
        val contentText = composeEditField.text.toString()
        val contentWarning = composeContentWarningField.text.toString()
        if (viewModel.didChange(contentText, contentWarning)) {
            AlertDialog.Builder(this)
                    .setMessage(R.string.compose_save_draft)
                    .setPositiveButton(R.string.action_save) { _, _ ->
                        saveDraftAndFinish(contentText, contentWarning)
                    }
                    .setNegativeButton(R.string.action_delete) { _, _ -> deleteDraftAndFinish() }
                    .show()
        } else {
            finishWithoutSlideOutAnimation()
        }
    }

    private fun deleteDraftAndFinish() {
        viewModel.deleteDraft()
        finishWithoutSlideOutAnimation()
    }

    private fun saveDraftAndFinish(contentText: String, contentWarning: String) {
        viewModel.saveDraft(contentText, contentWarning)
        finishWithoutSlideOutAnimation()
    }

    override fun search(token: String): List<ComposeAutoCompleteAdapter.AutocompleteResult> {
        return viewModel.search(token)
    }

    override fun onEmojiSelected(shortcode: String) {
        replaceTextAtCaret(":$shortcode: ")
    }

    private fun setEmojiList(emojiList: List<Emoji>?) {
        if (emojiList != null) {
            emojiView.adapter = EmojiAdapter(emojiList, this@ComposeActivity)
            enableButton(composeEmojiButton, true, emojiList.isNotEmpty())
        }
    }

    data class QueuedMedia(
            val localId: Long,
            val uri: Uri,
            val type: Type,
            val mediaSize: Long,
            val uploadPercent: Int = 0,
            val id: String? = null,
            val description: String? = null
    ) {
        enum class Type {
            IMAGE, VIDEO;
        }
    }

    override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int) {
        composeScheduleView.onTimeSet(hourOfDay, minute)
        updateScheduleButton()
        scheduleBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun resetSchedule() {
        composeScheduleView.resetSchedule()
        updateScheduleButton()
        scheduleBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    @Parcelize
    data class ComposeOptions(
            // Let's keep fields var until all consumers are Kotlin
            var savedTootUid: Int? = null,
            var tootText: String? = null,
            var savedJsonUrls: List<String>? = null,
            var savedJsonDescriptions: List<String>? = null,
            var mentionedUsernames: Set<String>? = null,
            var inReplyToId: String? = null,
            var replyVisibility: Status.Visibility? = null,
            var visibility: Status.Visibility? = null,
            var contentWarning: String? = null,
            var replyingStatusAuthor: String? = null,
            var replyingStatusContent: String? = null,
            var mediaAttachments: ArrayList<Attachment>? = null,
            var scheduledAt: String? = null,
            var sensitive: Boolean? = null,
            var poll: NewPoll? = null
    ) : Parcelable

    companion object {
        private const val TAG = "ComposeActivity" // logging tag
        internal const val STATUS_IMAGE_SIZE_LIMIT = 8388608 // 8MiB
        private const val STATUS_VIDEO_SIZE_LIMIT = 41943040 // 40MiB
        internal const val STATUS_IMAGE_PIXEL_SIZE_LIMIT = 16777216 // 4096^2 Pixels
        private const val MEDIA_PICK_RESULT = 1
        private const val MEDIA_TAKE_PHOTO_RESULT = 2
        private const val PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1

        private const val COMPOSE_OPTIONS_EXTRA = "COMPOSE_OPTIONS"

        // Mastodon only counts URLs as this long in terms of status character limits
        internal const val MAXIMUM_URL_LENGTH = 23

        @JvmStatic
        fun startIntent(context: Context, options: ComposeOptions): Intent {
            return Intent(context, ComposeActivity::class.java).apply {
                putExtra(COMPOSE_OPTIONS_EXTRA, options)
            }
        }

        @JvmStatic
        fun canHandleMimeType(mimeType: String?): Boolean {
            return mimeType != null && (mimeType.startsWith("image/") || mimeType.startsWith("video/") || mimeType == "text/plain")
        }
    }
}
