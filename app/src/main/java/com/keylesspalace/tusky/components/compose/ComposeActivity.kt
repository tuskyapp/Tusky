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

package com.keylesspalace.tusky.components.compose

import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.preference.PreferenceManager
import android.text.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.*
import androidx.annotation.ColorInt
import androidx.annotation.Px
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.*
import androidx.transition.TransitionManager
import at.connyduck.sparkbutton.helpers.Utils
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.ComposeAutoCompleteAdapter
import com.keylesspalace.tusky.adapter.EmojiAdapter
import com.keylesspalace.tusky.adapter.OnEmojiSelectedListener
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.NewPoll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.*
import com.keylesspalace.tusky.view.ComposeOptionsListener
import com.keylesspalace.tusky.view.PollPreviewView
import com.keylesspalace.tusky.view.ProgressImageView
import com.keylesspalace.tusky.view.showAddPollDialog
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.activity_compose.*
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import javax.inject.Inject

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

    private var composeOptionsBehavior: BottomSheetBehavior<*>? = null
    private var addMediaBehavior: BottomSheetBehavior<*>? = null
    private var emojiBehavior: BottomSheetBehavior<*>? = null
    private var scheduleBehavior: BottomSheetBehavior<*>? = null

    private var pollPreview: PollPreviewView? = null

    // this only exists when a status is trying to be sent, but uploads are still occurring
    private var finishingUploadDialog: ProgressDialog? = null
    private var inReplyToId: String? = null
    private var startingText = ""
    private var startingContentWarning: String? = ""
    private var currentInputContentInfo: InputContentInfoCompat? = null
    private var currentFlags: Int = 0
    private var photoUploadUri: Uri? = null
    private var savedTootUid = 0
    private var emojiList: List<Emoji>? = null
    // Accessors for testing, hence package scope
    internal var maximumTootCharacters = STATUS_CHARACTER_LIMIT
        private set
    private var maxPollOptions: Int? = null
    private var maxPollOptionLength: Int? = null
    @Px
    private var thumbnailViewSize: Int = 0

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

        emojiList = emptyList()


        // Setup the toolbar.
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.title = null
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setDisplayShowHomeEnabled(true)
            val closeIcon = AppCompatResources.getDrawable(this, R.drawable.ic_close_24dp)
            ThemeUtils.setDrawableTint(this, closeIcon!!, R.attr.compose_close_button_tint)
            actionBar.setHomeAsUpIndicator(closeIcon)
        }

        // setup the account image
        val activeAccount = accountManager.activeAccount

        if (activeAccount != null) {
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
            val mediaApater = MediaPreviewAdapter(
                    this,
                    onAddCaption = this::makeCaptionDialog,
                    onRemove = this::removeMediaFromQueue
            )
            composeMediaPreviewBar.layoutManager =
                    LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            composeMediaPreviewBar.adapter = mediaApater

            viewModel = ViewModelProviders.of(this, viewModelFactory).get(ComposeViewModel::class.java)

            withLifecycleContext {
                viewModel.instanceParams.observe { instanceData ->
                    maximumTootCharacters = instanceData.maxChars
                    updateVisibleCharactersLeft()
                    composeScheduleButton.visible(instanceData.supportsScheduled)
                    maxPollOptions = instanceData.pollMaxOptions
                    maxPollOptionLength = instanceData.pollMaxLength
                }
                viewModel.emoji.observe { emoji ->
                    setEmojiList(emoji)
                }
                combineLiveData(viewModel.markMediaAsSensitive, viewModel.hideStatustext) { markSensitive, hideStatusText ->
                    updateHideMediaToggle(markSensitive, hideStatusText)
                    showContentWarning(hideStatusText)
                }.subscribe()
                viewModel.statusVisibility.observe { visibility ->
                    setStatusVisibility(visibility)
                }
                viewModel.media.observe { media ->
                    mediaApater.submitList(media)
                }
            }
        } else {
            // do not do anything when not logged in, activity will be finished in super.onCreate() anyway
            return
        }

        composeOptionsBottomSheet.listener = this

        composeOptionsBehavior = BottomSheetBehavior.from(composeOptionsBottomSheet)
        composeOptionsBehavior!!.state = BottomSheetBehavior.STATE_HIDDEN

        addMediaBehavior = BottomSheetBehavior.from(addMediaBottomSheet)

        scheduleBehavior = BottomSheetBehavior.from(composeScheduleView)

        emojiBehavior = BottomSheetBehavior.from(emojiView)

        emojiView.layoutManager = GridLayoutManager(this, 3, GridLayoutManager.HORIZONTAL, false)

        enableButton(composeEmojiButton, false, false)

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

        thumbnailViewSize = resources.getDimensionPixelSize(R.dimen.compose_media_preview_size)

        var startingVisibility = Status.Visibility.UNKNOWN
        photoUploadUri = null

        /* If the composer is started up as a reply to another post, override the "starting" state
         * based on what the intent from the reply request passes. */
        val intent = intent

        var mentionedUsernames: List<String>? = null
        var loadedDraftMediaUris: List<String>? = null
        var loadedDraftMediaDescriptions: List<String>? = null
        var mediaAttachments: List<Attachment>? = null
        inReplyToId = null
        if (intent != null) {
            val composeOptions = intent.getParcelableExtra<ComposeOptions?>(COMPOSE_OPTIONS_EXTRA)
            this.composeOptions = composeOptions

            if (startingVisibility === Status.Visibility.UNKNOWN) {
                val preferredVisibility = activeAccount.defaultPostPrivacy

                val replyVisibility = composeOptions?.replyVisibility ?: Status.Visibility.UNKNOWN
                startingVisibility = Status.Visibility.byNum(
                        preferredVisibility.num.coerceAtLeast(replyVisibility.num))
            }

            inReplyToId = composeOptions?.inReplyToId

            mentionedUsernames = composeOptions?.mentionedUsernames

            val contentWarning = composeOptions?.contentWarning
            if (contentWarning != null) {
                startingContentWarning = contentWarning
            }

            val tootText = composeOptions?.tootText
            if (!TextUtils.isEmpty(tootText)) {
                composeEditField.setText(tootText)
            }

            // try to redo a list of media
            // If come from SavedTootActivity
            loadedDraftMediaUris = composeOptions?.savedJsonUrls
            loadedDraftMediaDescriptions = composeOptions?.savedJsonDescriptions
            // If come from redraft
            mediaAttachments = composeOptions?.mediaAttachments

            val savedTootUid = composeOptions?.savedTootUid
            if (savedTootUid != null) {
                this.savedTootUid = savedTootUid

                // If come from SavedTootActivity
                startingText = tootText ?: ""
            }

            val tootVisibility = composeOptions?.visibility ?: Status.Visibility.UNKNOWN
            if (tootVisibility.num != Status.Visibility.UNKNOWN.num) {
                startingVisibility = tootVisibility
            }

            if (composeOptions?.replyingStatusAuthor != null) {
                composeReplyView.visibility = View.VISIBLE
                val username = composeOptions.replyingStatusAuthor
                composeReplyView.text = getString(R.string.replying_to, username)
                val arrowDownIcon = IconicsDrawable(this, GoogleMaterial.Icon.gmd_arrow_drop_down).sizeDp(12)

                ThemeUtils.setDrawableTint(this, arrowDownIcon, android.R.attr.textColorTertiary)
                composeReplyView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, arrowDownIcon, null)

                composeReplyView.setOnClickListener {
                    TransitionManager.beginDelayedTransition(composeReplyContentView.parent as ViewGroup)

                    if (composeReplyContentView.visibility != View.VISIBLE) {
                        composeReplyContentView.visibility = View.VISIBLE
                        val arrowUpIcon = IconicsDrawable(this, GoogleMaterial.Icon.gmd_arrow_drop_up).sizeDp(12)

                        ThemeUtils.setDrawableTint(this, arrowUpIcon, android.R.attr.textColorTertiary)
                        composeReplyView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, arrowUpIcon, null)
                    } else {
                        composeReplyContentView.visibility = View.GONE
                        composeReplyView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, arrowDownIcon, null)
                    }
                }
            }

            composeOptions?.replyingStatusContent?.let {
                composeReplyContentView.text = it
            }

            if (!TextUtils.isEmpty(composeOptions?.scheduledAt)) {
                composeScheduleView.setDateTime(composeOptions?.scheduledAt)
            }

            composeOptions?.sensitive?.let { viewModel.markMediaAsSensitive.value = it }

            val poll = composeOptions?.poll
            if (poll != null && (mediaAttachments == null || mediaAttachments.isEmpty())) {
                updatePoll(poll)
            }

            if (mediaAttachments != null && mediaAttachments.isNotEmpty()) {
                enablePollButton(false)
            }
        }

        // After the starting state is finalised, the interface can be set to reflect this state.
        viewModel.statusVisibility.value = startingVisibility

        updateScheduleButton()
        updateVisibleCharactersLeft()

        // Setup the main text field.
        composeEditField.setOnCommitContentListener(this)
        val mentionColour = composeEditField.linkTextColors.defaultColor
        highlightSpans(composeEditField.text, mentionColour)
        composeEditField.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun afterTextChanged(editable: Editable) {
                highlightSpans(editable, mentionColour)
                updateVisibleCharactersLeft()
            }
        })

        composeEditField.setOnKeyListener { _, keyCode, event -> this.onKeyDown(keyCode, event) }

        composeEditField.setAdapter(
                ComposeAutoCompleteAdapter(this))
        composeEditField.setTokenizer(ComposeTokenizer())

        // Add any mentions to the text field when a reply is first composed.
        if (mentionedUsernames != null) {
            val builder = StringBuilder()
            for (name in mentionedUsernames) {
                builder.append('@')
                builder.append(name)
                builder.append(' ')
            }
            startingText = builder.toString()
            composeEditField.setText(startingText)
            composeEditField.setSelection(composeEditField.length())
        }

        // work around Android platform bug -> https://issuetracker.google.com/issues/67102093
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O || Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
            composeEditField.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        // Initialise the content warning editor.
        composeContentWarningField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                updateVisibleCharactersLeft()
            }

            override fun afterTextChanged(s: Editable) {}
        })
        if (startingContentWarning != null) {
            composeContentWarningField.setText(startingContentWarning)
        }

        // Initialise the empty media queue state.

//        // These can only be added after everything affected by the media queue is initialized.
//        if (!isEmpty(loadedDraftMediaUris)) {
//            for (mediaIndex in loadedDraftMediaUris!!.indices) {
//                val uri = Uri.parse(loadedDraftMediaUris[mediaIndex])
//                val mediaSize = getMediaSize(contentResolver, uri)
//                var description: String? = null
//                if (loadedDraftMediaDescriptions != null && mediaIndex < loadedDraftMediaDescriptions.size) {
//                    description = loadedDraftMediaDescriptions[mediaIndex]
//                }
//                pickMedia(uri, mediaSize)
//            }
//        } else if (!isEmpty(mediaAttachments)) {
//            for (mediaIndex in mediaAttachments!!.indices) {
//                val (id, _, previewUrl, _, type1, description) = mediaAttachments[mediaIndex]
//                val type: QueuedMedia.Type
//                when (type1) {
//                    Attachment.Type.UNKNOWN, Attachment.Type.IMAGE -> {
//                        type = QueuedMedia.Type.IMAGE
//                    }
//                    Attachment.Type.VIDEO, Attachment.Type.GIFV -> {
//                        type = QueuedMedia.Type.VIDEO
//                    }
//                    else -> {
//                        type = QueuedMedia.Type.IMAGE
//                    }
//                }
//                addMediaToQueue(id, type, previewUrl)
//            }
//        } else
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
                        val mediaSize = getMediaSize(contentResolver, uri)
                        pickMedia(uri, mediaSize)
                    }
                } else if (type == "text/plain") {
                    val action = intent.action
                    if (action != null && action == Intent.ACTION_SEND) {
                        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                        var shareBody: String? = null
                        if (subject != null && text != null) {
                            if (subject != text && !text.contains(subject)) {
                                shareBody = String.format("%s\n%s", subject, text)
                            } else {
                                shareBody = text
                            }
                        } else if (text != null) {
                            shareBody = text
                        } else if (subject != null) {
                            shareBody = subject
                        }

                        if (shareBody != null) {
                            val start = Math.max(composeEditField.selectionStart, 0)
                            val end = Math.max(composeEditField.selectionEnd, 0)
                            val left = Math.min(start, end)
                            val right = Math.max(start, end)
                            composeEditField.text.replace(left, right, shareBody, 0, shareBody.length)
                        }
                    }
                }
            }
        }

        composeEditField.requestFocus()
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
        val savedMediaQueued = ArrayList<SavedQueuedMedia>()
        outState.putParcelableArrayList("savedMediaQueued", savedMediaQueued)
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

    private fun doErrorDialog(@StringRes descriptionId: Int, @StringRes actionId: Int,
                              listener: View.OnClickListener) {
        val bar = Snackbar.make(findViewById(R.id.activity_compose), getString(descriptionId),
                Snackbar.LENGTH_SHORT)
        bar.setAction(actionId, listener)
        //necessary so snackbar is shown over everything
        bar.view.elevation = resources.getDimensionPixelSize(R.dimen.compose_activity_snackbar_elevation).toFloat()
        bar.show()
    }

    private fun displayTransientError(@StringRes stringId: Int) {
        val bar = Snackbar.make(findViewById(R.id.activity_compose), stringId, Snackbar.LENGTH_LONG)
        //necessary so snackbar is shown over everything
        bar.view.elevation = resources.getDimensionPixelSize(R.dimen.compose_activity_snackbar_elevation).toFloat()
        bar.show()
    }

    private fun toggleHideMedia() {
        this.viewModel.toggleMarkSensitive()
    }

    private fun updateHideMediaToggle(statusMarkSensitive: Boolean, hideStatusText: Boolean) {
        // TODO
//        TransitionManager.beginDelayedTransition(composeHideMediaButton.parent as ViewGroup)
//
//        @ColorInt val color: Int
//        if (mediaQueued.size == 0) {
//            composeHideMediaButton.visibility = View.GONE
//        } else {
//            composeHideMediaButton.visibility = View.VISIBLE
//            if (statusMarkSensitive) {
//                composeHideMediaButton.setImageResource(R.drawable.ic_hide_media_24dp)
//                if (hideStatusText) {
//                    composeHideMediaButton.isClickable = false
//                    color = ContextCompat.getColor(this, R.color.compose_media_visible_button_disabled_blue)
//                } else {
//                    composeHideMediaButton.isClickable = true
//                    color = ContextCompat.getColor(this, R.color.tusky_blue)
//                }
//            } else {
//                composeHideMediaButton.isClickable = true
//                composeHideMediaButton.setImageResource(R.drawable.ic_eye_24dp)
//                color = ThemeUtils.getColor(this, android.R.attr.textColorTertiary)
//            }
//            composeHideMediaButton.drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
//        }
    }

    private fun updateScheduleButton() {
        @ColorInt val color: Int
        if (composeScheduleView.time == null) {
            color = ThemeUtils.getColor(this, android.R.attr.textColorTertiary)
        } else {
            color = ContextCompat.getColor(this, R.color.tusky_blue)
        }
        composeScheduleButton.drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    private fun disableButtons() {
        composeAddMediaButton.isClickable = false
        composeToggleVisibilityButton.isClickable = false
        composeEmojiButton.isClickable = false
        composeHideMediaButton.isClickable = false
        composeScheduleButton.isClickable = false
        composeTootButton.isEnabled = false
    }

    private fun enableButtons() {
        composeAddMediaButton.isClickable = true
        composeToggleVisibilityButton.isClickable = true
        composeEmojiButton.isClickable = true
        composeHideMediaButton.isClickable = true
        composeScheduleButton.isClickable = true
        composeTootButton.isEnabled = true
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
        val drawable = AppCompatResources.getDrawable(this, iconRes)!!
        composeToggleVisibilityButton.setImageDrawable(drawable)
    }

    private fun showComposeOptions() {
        if (composeOptionsBehavior!!.state == BottomSheetBehavior.STATE_HIDDEN || composeOptionsBehavior!!.state == BottomSheetBehavior.STATE_COLLAPSED) {
            composeOptionsBehavior!!.state = BottomSheetBehavior.STATE_EXPANDED
            addMediaBehavior!!.state = BottomSheetBehavior.STATE_HIDDEN
            emojiBehavior!!.state = BottomSheetBehavior.STATE_HIDDEN
            scheduleBehavior!!.setState(BottomSheetBehavior.STATE_HIDDEN)
        } else {
            composeOptionsBehavior!!.setState(BottomSheetBehavior.STATE_HIDDEN)
        }
    }

    private fun showScheduleView() {
        if (scheduleBehavior!!.state == BottomSheetBehavior.STATE_HIDDEN || scheduleBehavior!!.state == BottomSheetBehavior.STATE_COLLAPSED) {
            scheduleBehavior!!.state = BottomSheetBehavior.STATE_EXPANDED
            composeOptionsBehavior!!.state = BottomSheetBehavior.STATE_HIDDEN
            addMediaBehavior!!.state = BottomSheetBehavior.STATE_HIDDEN
            emojiBehavior!!.setState(BottomSheetBehavior.STATE_HIDDEN)
        } else {
            scheduleBehavior!!.setState(BottomSheetBehavior.STATE_HIDDEN)
        }
    }

    private fun showEmojis() {

        if (emojiView!!.adapter != null) {
            if (emojiView!!.adapter!!.itemCount == 0) {
                val errorMessage = getString(R.string.error_no_custom_emojis, accountManager.activeAccount!!.domain)
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
            } else {
                if (emojiBehavior!!.state == BottomSheetBehavior.STATE_HIDDEN || emojiBehavior!!.state == BottomSheetBehavior.STATE_COLLAPSED) {
                    emojiBehavior!!.state = BottomSheetBehavior.STATE_EXPANDED
                    composeOptionsBehavior!!.state = BottomSheetBehavior.STATE_HIDDEN
                    addMediaBehavior!!.state = BottomSheetBehavior.STATE_HIDDEN
                    scheduleBehavior!!.setState(BottomSheetBehavior.STATE_HIDDEN)
                } else {
                    emojiBehavior!!.setState(BottomSheetBehavior.STATE_HIDDEN)
                }
            }

        }

    }

    private fun openPickDialog() {
        if (addMediaBehavior!!.state == BottomSheetBehavior.STATE_HIDDEN || addMediaBehavior!!.state == BottomSheetBehavior.STATE_COLLAPSED) {
            addMediaBehavior!!.state = BottomSheetBehavior.STATE_EXPANDED
            composeOptionsBehavior!!.state = BottomSheetBehavior.STATE_HIDDEN
            emojiBehavior!!.state = BottomSheetBehavior.STATE_HIDDEN
            scheduleBehavior!!.setState(BottomSheetBehavior.STATE_HIDDEN)
        } else {
            addMediaBehavior!!.setState(BottomSheetBehavior.STATE_HIDDEN)
        }

    }

    private fun onMediaPick() {
        addMediaBehavior!!.bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                //Wait until bottom sheet is not collapsed and show next screen after
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    addMediaBehavior!!.bottomSheetCallback = null
                    if (ContextCompat.checkSelfPermission(this@ComposeActivity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this@ComposeActivity,
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                                PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE)
                    } else {
                        initiateMediaPicking()
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {

            }
        }
        addMediaBehavior!!.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun openPollDialog() {
        addMediaBehavior!!.state = BottomSheetBehavior.STATE_COLLAPSED
        showAddPollDialog(this, viewModel.poll.value, maxPollOptions, maxPollOptionLength)
    }

    fun updatePoll(poll: NewPoll) {

        enableButton(composeAddMediaButton, false, false)

        if (pollPreview == null) {

            pollPreview = PollPreviewView(this)

            val resources = resources
            val margin = resources.getDimensionPixelSize(R.dimen.compose_media_preview_margin)
            val marginBottom = resources.getDimensionPixelSize(R.dimen.compose_media_preview_margin_bottom)

            val layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams.setMargins(margin, margin, margin, marginBottom)
            pollPreview!!.layoutParams = layoutParams

            // TODO
//            composeMediaPreviewBar.addView(pollPreview)

            pollPreview!!.setOnClickListener {
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

        pollPreview!!.setPoll(poll)

    }

    private fun removePoll() {
        viewModel.poll.value = null
        pollPreview = null
        enableButton(composeAddMediaButton, true, true)
        composeMediaPreviewBar.removeAllViews()
    }

    override fun onVisibilityChanged(visibility: Status.Visibility) {
        composeOptionsBehavior!!.state = BottomSheetBehavior.STATE_COLLAPSED
        viewModel.statusVisibility.value = visibility
    }

    internal fun calculateTextLength(): Int {
        var offset = 0
        val urlSpans = composeEditField.urls
        if (urlSpans != null) {
            for (span in urlSpans) {
                offset += Math.max(0, span.url.length - MAXIMUM_URL_LENGTH)
            }
        }
        var length = composeEditField.length() - offset
        if (viewModel.hideStatustext.value!!) {
            length += composeContentWarningField.length()
        }
        return length
    }

    private fun updateVisibleCharactersLeft() {
        this.composeCharactersLeftView.text = String.format(Locale.getDefault(), "%d", maximumTootCharacters - calculateTextLength())
    }

    private fun onContentWarningChanged() {
        val showWarning = composeContentWarningBar.visibility != View.VISIBLE
        viewModel.hideStatustext.value = showWarning
        updateVisibleCharactersLeft()
    }

    private fun onSendClicked() {
        disableButtons()
        sendStatus()
    }

    override fun onCommitContent(inputContentInfo: InputContentInfoCompat, flags: Int, opts: Bundle): Boolean {
        try {
            if (currentInputContentInfo != null) {
                currentInputContentInfo!!.releasePermission()
            }
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
        val uri = inputContentInfo.contentUri
        val mediaSize: Long
        var descriptor: AssetFileDescriptor? = null
        try {
            descriptor = contentResolver.openAssetFileDescriptor(uri, "r")
        } catch (e: FileNotFoundException) {
            Log.d(TAG, Log.getStackTraceString(e))
            // Eat this exception, having the descriptor be null is sufficient.
        }

        if (descriptor != null) {
            mediaSize = descriptor.length
            try {
                descriptor.close()
            } catch (e: IOException) {
                // Just eat this exception.
            }

        } else {
            mediaSize = MEDIA_SIZE_UNKNOWN
        }
        pickMedia(uri, mediaSize)

        currentInputContentInfo = inputContentInfo
        currentFlags = flags

        return true
    }

    private fun sendStatus() {
        val contentText = composeEditField.text.toString()
        var spoilerText = ""
        if (viewModel.hideStatustext.value!!) {
            spoilerText = composeContentWarningField.text.toString()
        }
        val characterCount = calculateTextLength()
        if ((characterCount <= 0 || contentText.isBlank()) && viewModel.media.value!!.isEmpty()) {
            composeEditField.error = getString(R.string.error_empty)
            enableButtons()
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
            enableButtons()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        when (requestCode) {
            PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initiateMediaPicking()
                } else {
                    doErrorDialog(R.string.error_media_upload_permission, R.string.action_retry,
                            View.OnClickListener { onMediaPick() })
                }
            }
        }
    }


    private fun initiateCameraApp() {
//        addMediaBehavior!!.state = BottomSheetBehavior.STATE_COLLAPSED
//
//        // We don't need to ask for permission in this case, because the used calls require
//        // android.permission.WRITE_EXTERNAL_STORAGE only on SDKs *older* than Kitkat, which was
//        // way before permission dialogues have been introduced.
//        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
//        if (intent.resolveActivity(packageManager) != null) {
//            var photoFile: File? = null
//            try {
//                photoFile = createNewImageFile()
//            } catch (ex: IOException) {
//                displayTransientError(R.string.error_media_upload_opening)
//            }
//
//            // Continue only if the File was successfully created
//            if (photoFile != null) {
//                photoUploadUri = FileProvider.getUriForFile(this,
//                        BuildConfig.APPLICATION_ID + ".fileprovider",
//                        photoFile)
//                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUploadUri)
//                startActivityForResult(intent, MEDIA_TAKE_PHOTO_RESULT)
//            }
//        }
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
                if (colorActive) android.R.attr.textColorTertiary else R.attr.compose_media_button_disabled_tint)
    }

    private fun enablePollButton(enable: Boolean) {
        addPollTextActionTextView.isEnabled = enable
        val textColor: Int
        if (enable) {
            textColor = ThemeUtils.getColor(this, android.R.attr.textColorTertiary)
        } else {
            textColor = ThemeUtils.getColor(this, R.attr.compose_media_button_disabled_tint)
        }
        addPollTextActionTextView.setTextColor(textColor)
        addPollTextActionTextView.compoundDrawablesRelative[0].setColorFilter(textColor, PorterDuff.Mode.SRC_IN)
    }

    private fun addMediaToQueue(type: QueuedMedia.Type, uri: Uri, mediaSize: Long) {
        viewModel.addMediaToQueue(type, uri, mediaSize)
    }

    private fun makeCaptionDialog(item: QueuedMedia) {
        val dialogLayout = LinearLayout(this)
        val padding = Utils.dpToPx(this, 8)
        dialogLayout.setPadding(padding, padding, padding, padding)

        dialogLayout.orientation = LinearLayout.VERTICAL
        val imageView = ImageView(this)

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        Glide.with(this)
                .load(item.uri)
                .into(imageView)

        val margin = Utils.dpToPx(this, 4)
        dialogLayout.addView(imageView)
        (imageView.layoutParams as LinearLayout.LayoutParams).weight = 1f
        imageView.layoutParams.height = 0
        (imageView.layoutParams as LinearLayout.LayoutParams).setMargins(0, margin, 0, 0)

        val input = EditText(this)
        input.hint = getString(R.string.hint_describe_for_visually_impaired, MEDIA_DESCRIPTION_CHARACTER_LIMIT)
        dialogLayout.addView(input)
        (input.layoutParams as LinearLayout.LayoutParams).setMargins(margin, margin, margin, margin)
        input.setLines(2)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.setText(item.description)
        input.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(MEDIA_DESCRIPTION_CHARACTER_LIMIT))

        val okListener = { dialog: DialogInterface, _: Int ->
//            val updateDescription = Runnable {
//                mastodonApi.updateMedia(item.id!!, input.text.toString()).enqueue(object : Callback<Attachment> {
//                    override fun onResponse(call: Call<Attachment>, response: Response<Attachment>) {
//                        val attachment = response.body()
//                        if (response.isSuccessful && attachment != null) {
//                            item.description = attachment.description
//                            item.preview!!.setChecked(item.description != null && item.description!!.isNotEmpty())
//                            dialog.dismiss()
//                            updateContentDescription(item)
//                        } else {
//                            showFailedCaptionMessage()
//                        }
//                        item.updateDescription = null
//                    }
//
//                    override fun onFailure(call: Call<Attachment>, t: Throwable) {
//                        showFailedCaptionMessage()
//                        item.updateDescription = null
//                    }
//                })
//            }
//
//            if (item.readyStage == QueuedMedia.ReadyStage.UPLOADED) {
//                updateDescription.run()
//            } else {
//                // media is still uploading, queue description update for when it finishes
//                item.updateDescription = updateDescription
//            }
            viewModel.updateDescription(item, input.text.toString())
            dialog.dismiss()
        }

        val dialog = AlertDialog.Builder(this)
                .setView(dialogLayout)
                .setPositiveButton(android.R.string.ok, okListener)
                .setNegativeButton(android.R.string.cancel, null)
                .create()

        val window = dialog.window
        window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        dialog.show()
    }

    private fun showFailedCaptionMessage() {
        Toast.makeText(this, R.string.error_failed_set_caption, Toast.LENGTH_SHORT).show()
    }

    private fun removeMediaFromQueue(item: QueuedMedia) {
        viewModel.removeMediaFromQueue(item)
    }

    private fun removeAllMediaFromQueue() {
        viewModel.media.value!!.forEach {
            removeMediaFromQueue(it)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (resultCode == Activity.RESULT_OK && requestCode == MEDIA_PICK_RESULT && intent != null) {
            val uri = intent.data
            val mediaSize = getMediaSize(contentResolver, uri)
            pickMedia(uri!!, mediaSize)
        } else if (resultCode == Activity.RESULT_OK && requestCode == MEDIA_TAKE_PHOTO_RESULT) {
            val mediaSize = getMediaSize(contentResolver, photoUploadUri)
            pickMedia(photoUploadUri!!, mediaSize)
        }
    }


    private fun pickMedia(inUri: Uri, mediaSize: Long) {
        var mediaSize = mediaSize
        var uri = inUri
        val contentResolver = contentResolver
        val mimeType = contentResolver.getType(uri)

        val filename = inUri.toString().substring(inUri.toString().lastIndexOf("/"))
        val suffixPosition = filename.lastIndexOf(".")
        var suffix = ""
        if (suffixPosition > 0) suffix = filename.substring(suffixPosition)
        try {
            getContentResolver().openInputStream(inUri).use { input ->
                if (input == null) {
                    Log.w(TAG, "Media input is null")
                    uri = inUri
                    return@use
                }
                val file = File.createTempFile("randomTemp1", suffix, cacheDir)
                FileOutputStream(file.absoluteFile).use { out ->
                    input.copyTo(out)
                    uri = FileProvider.getUriForFile(this,
                            BuildConfig.APPLICATION_ID + ".fileprovider",
                            file)
                    mediaSize = getMediaSize(getContentResolver(), uri)
                }

            }
        } catch (e: IOException) {
            Log.w(TAG, e)
            uri = inUri
        }
        if (mediaSize == MEDIA_SIZE_UNKNOWN) {
            displayTransientError(R.string.error_media_upload_opening)
            return
        }
        if (mimeType != null) {
            val topLevelType = mimeType.substring(0, mimeType.indexOf('/'))
            when (topLevelType) {
                "video" -> {
//                    if (mediaSize > STATUS_VIDEO_SIZE_LIMIT) {
//                        displayTransientError(R.string.error_video_upload_size)
//                        return
//                    }
//                    if (mediaQueued.size > 0 && mediaQueued[0].type == QueuedMedia.Type.IMAGE) {
//                        displayTransientError(R.string.error_media_upload_image_or_video)
//                        return
//                    }
//                    val bitmap = getVideoThumbnail(this, uri, thumbnailViewSize)
//                    if (bitmap != null) {
//                        addMediaToQueue(QueuedMedia.Type.VIDEO, uri, mediaSize)
//                    } else {
//                        displayTransientError(R.string.error_media_upload_opening)
//                    }
                }
                "image" -> {
                    val bitmap = getImageThumbnail(contentResolver, uri, thumbnailViewSize)
                    if (bitmap != null) {
                        addMediaToQueue(QueuedMedia.Type.IMAGE, uri, mediaSize)
                    } else {
                        displayTransientError(R.string.error_media_upload_opening)
                    }
                }
                else -> {
                    displayTransientError(R.string.error_media_upload_type)
                }
            }
        } else {
            displayTransientError(R.string.error_media_upload_type)
        }
    }

    private fun showContentWarning(show: Boolean) {
        TransitionManager.beginDelayedTransition(composeContentWarningBar.parent as ViewGroup)
        val color: Int
        if (show) {
            viewModel.markMediaAsSensitive.value = true
            composeContentWarningBar.visibility = View.VISIBLE
            composeContentWarningField.setSelection(composeContentWarningField.text.length)
            composeContentWarningField.requestFocus()
            color = ContextCompat.getColor(this, R.color.tusky_blue)
        } else {
            composeContentWarningBar.visibility = View.GONE
            composeEditField.requestFocus()
            color = ThemeUtils.getColor(this, android.R.attr.textColorTertiary)
        }
        composeContentWarningButton.drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)

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
        if (composeOptionsBehavior!!.state == BottomSheetBehavior.STATE_EXPANDED ||
                addMediaBehavior!!.state == BottomSheetBehavior.STATE_EXPANDED ||
                emojiBehavior!!.state == BottomSheetBehavior.STATE_EXPANDED ||
                scheduleBehavior!!.state == BottomSheetBehavior.STATE_EXPANDED) {
            composeOptionsBehavior!!.state = BottomSheetBehavior.STATE_HIDDEN
            addMediaBehavior!!.state = BottomSheetBehavior.STATE_HIDDEN
            emojiBehavior!!.state = BottomSheetBehavior.STATE_HIDDEN
            scheduleBehavior!!.state = BottomSheetBehavior.STATE_HIDDEN
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
        // TODO
//        val contentText = composeEditField.text
//        val contentWarning = composeContentWarningField.text
//
//        val textChanged = !(TextUtils.isEmpty(contentText) || startingText.startsWith(contentText.toString()))
//        val contentWarningChanged = composeContentWarningBar.visibility == View.VISIBLE &&
//                !TextUtils.isEmpty(contentWarning) && !startingContentWarning!!.startsWith(contentWarning.toString())
//        val mediaChanged = !mediaQueued.isEmpty()
//        val pollChanged = viewModel.poll.value != null
//
//        if (textChanged || contentWarningChanged || mediaChanged || pollChanged) {
//            AlertDialog.Builder(this)
//                    .setMessage(R.string.compose_save_draft)
//                    .setPositiveButton(R.string.action_save) { _, _ -> saveDraftAndFinish() }
//                    .setNegativeButton(R.string.action_delete) { _, _ -> deleteDraftAndFinish() }
//                    .show()
//        } else {
        finishWithoutSlideOutAnimation()
//        }
    }

    private fun deleteDraftAndFinish() {
        viewModel.deleteDraft()
        finishWithoutSlideOutAnimation()
    }

    private fun saveDraftAndFinish() {
        viewModel.saveDraft()
        finishWithoutSlideOutAnimation()
    }

    override fun search(token: String): List<ComposeAutoCompleteAdapter.AutocompleteResult> {
        return viewModel.search(token)
    }

    override fun onEmojiSelected(shortcode: String) {
        replaceTextAtCaret(":$shortcode: ")
    }

    private fun setEmojiList(emojiList: List<Emoji>?) {
        this.emojiList = emojiList


        if (emojiList != null) {
            emojiView!!.adapter = EmojiAdapter(emojiList, this@ComposeActivity)
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
            IMAGE,
            VIDEO
        }
    }

    /**
     * This saves enough information to re-enqueue an attachment when restoring the activity.
     */
    @Parcelize
    private data class SavedQueuedMedia(
            var id: String?,
            var type: QueuedMedia.Type,
            var uri: Uri,
            var mediaSize: Long,
            var description: String? = null
    ) : Parcelable

    override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int) {
        composeScheduleView.onTimeSet(hourOfDay, minute)
        updateScheduleButton()
        scheduleBehavior!!.state = BottomSheetBehavior.STATE_HIDDEN
    }

    fun resetSchedule() {
        composeScheduleView.resetSchedule()
        updateScheduleButton()
        scheduleBehavior!!.state = BottomSheetBehavior.STATE_HIDDEN
    }

    @Parcelize
    data class ComposeOptions(
            // Let's keep fields var until all consumers are Kotlin
            var savedTootUid: Int? = null,
            var tootText: String? = null,
            var savedJsonUrls: List<String>? = null,
            var savedJsonDescriptions: List<String>? = null,
            var mentionedUsernames: List<String>? = null,
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
        internal const val STATUS_CHARACTER_LIMIT = 500
        internal const val STATUS_IMAGE_SIZE_LIMIT = 8388608 // 8MiB
        private const val STATUS_VIDEO_SIZE_LIMIT = 41943040 // 40MiB
        internal const val STATUS_IMAGE_PIXEL_SIZE_LIMIT = 16777216 // 4096^2 Pixels
        private const val MEDIA_PICK_RESULT = 1
        private const val MEDIA_TAKE_PHOTO_RESULT = 2
        private const val PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1

        private const val COMPOSE_OPTIONS_EXTRA = "COMPOSE_OPTIONS"

        // Mastodon only counts URLs as this long in terms of status character limits
        internal const val MAXIMUM_URL_LENGTH = 23
        // https://github.com/tootsuite/mastodon/blob/1656663/app/models/media_attachment.rb#L94
        private const val MEDIA_DESCRIPTION_CHARACTER_LIMIT = 420

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

    class MediaPreviewAdapter(
            context: Context,
            private val onAddCaption: (QueuedMedia) -> Unit,
            private val onRemove: (QueuedMedia) -> Unit
    ) : RecyclerView.Adapter<MediaPreviewAdapter.PreviewViewHolder>() {

        fun submitList(list: List<QueuedMedia>) {
            Log.d(TAG, "new list of size ${list.size}")
            this.differ.submitList(list)
        }

        private fun onMediaClick(position: Int, view: View) {
            val item = differ.currentList[position]
            val popup = PopupMenu(view.context, view)
            val addCaptionId = 1
            val removeId = 2
            popup.menu.add(0, addCaptionId, 0, R.string.action_set_caption)
            popup.menu.add(0, removeId, 0, R.string.action_remove)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    addCaptionId -> onAddCaption(item)
                    removeId -> onRemove(item)
                }
                true
            }
            popup.show()
        }

        private val thumbnailViewSize =
                context.resources.getDimensionPixelSize(R.dimen.compose_media_preview_size)

        override fun getItemCount(): Int = differ.currentList.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewViewHolder {
            return PreviewViewHolder(ProgressImageView(parent.context))
        }

        override fun onBindViewHolder(holder: PreviewViewHolder, position: Int) {
            val item = differ.currentList[position]
            holder.progressImageView.setChecked(!item.description.isNullOrEmpty())
            holder.progressImageView.setProgress(item.uploadPercent)
            Glide.with(holder.itemView.context)
                    .load(item.uri)
                    .transform()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .dontAnimate()
                    .into(holder.progressImageView)
        }

        val differ = AsyncListDiffer(this, object : DiffUtil.ItemCallback<QueuedMedia>() {
            override fun areItemsTheSame(oldItem: QueuedMedia, newItem: QueuedMedia): Boolean {
                return oldItem.localId == newItem.localId
            }

            override fun areContentsTheSame(oldItem: QueuedMedia, newItem: QueuedMedia): Boolean {
                return oldItem == newItem
            }
        })

        inner class PreviewViewHolder(val progressImageView: ProgressImageView)
            : RecyclerView.ViewHolder(progressImageView) {
            init {
                val layoutParams = ConstraintLayout.LayoutParams(thumbnailViewSize, thumbnailViewSize)
                val margin = itemView.context.resources
                        .getDimensionPixelSize(R.dimen.compose_media_preview_margin)
                val marginBottom = itemView.context.resources
                        .getDimensionPixelSize(R.dimen.compose_media_preview_margin_bottom)
                layoutParams.setMargins(margin, 0, margin, marginBottom)
                progressImageView.layoutParams = layoutParams
                progressImageView.scaleType = ImageView.ScaleType.CENTER_CROP
                progressImageView.setOnClickListener {
                    onMediaClick(adapterPosition, progressImageView)
                }
            }
        }
    }
}
