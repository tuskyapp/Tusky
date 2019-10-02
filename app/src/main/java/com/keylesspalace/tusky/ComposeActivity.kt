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
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ProgressDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.net.Uri
import android.os.*
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.text.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.annotation.ColorInt
import androidx.annotation.Px
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.transition.TransitionManager
import at.connyduck.sparkbutton.helpers.Utils
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.keylesspalace.tusky.adapter.ComposeAutoCompleteAdapter
import com.keylesspalace.tusky.adapter.EmojiAdapter
import com.keylesspalace.tusky.adapter.OnEmojiSelectedListener
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.InstanceEntity
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.*
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.network.ProgressRequestBody
import com.keylesspalace.tusky.service.SendTootService
import com.keylesspalace.tusky.util.*
import com.keylesspalace.tusky.view.ComposeOptionsListener
import com.keylesspalace.tusky.view.PollPreviewView
import com.keylesspalace.tusky.view.ProgressImageView
import com.keylesspalace.tusky.view.showAddPollDialog
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider.from
import com.uber.autodispose.autoDispose
import io.reactivex.Single
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.activity_compose.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.*
import java.util.*
import java.util.concurrent.CountDownLatch
import javax.inject.Inject

class ComposeActivity : BaseActivity(), ComposeOptionsListener, ComposeAutoCompleteAdapter.AutocompletionProvider, OnEmojiSelectedListener, Injectable, InputConnectionCompat.OnCommitContentListener, TimePickerDialog.OnTimeSetListener {

    @Inject
    lateinit var mastodonApi: MastodonApi
    @Inject
    lateinit var database: AppDatabase

    private var composeOptionsBehavior: BottomSheetBehavior<*>? = null
    private var addMediaBehavior: BottomSheetBehavior<*>? = null
    private var emojiBehavior: BottomSheetBehavior<*>? = null
    private var scheduleBehavior: BottomSheetBehavior<*>? = null

    private var pollPreview: PollPreviewView? = null

    // this only exists when a status is trying to be sent, but uploads are still occurring
    private var finishingUploadDialog: ProgressDialog? = null
    private var inReplyToId: String? = null
    private val mediaQueued = ArrayList<QueuedMedia>()
    private var waitForMediaLatch: CountUpDownLatch? = null
    private var poll: NewPoll? = null
    private var statusVisibility: Status.Visibility? = null     // The current values of the options that will be applied
    private var statusMarkSensitive: Boolean = false // to the status being composed.
    private var statusHideText: Boolean = false
    private var startingText = ""
    private var startingContentWarning: String? = ""
    private var currentInputContentInfo: InputContentInfoCompat? = null
    private var currentFlags: Int = 0
    private var photoUploadUri: Uri? = null
    private var savedTootUid = 0
    private var emojiList: List<Emoji>? = null
    private val emojiListRetrievalLatch = CountDownLatch(1)
    // Accessors for testing, hence package scope
    internal var maximumTootCharacters = STATUS_CHARACTER_LIMIT
        private set
    private var maxPollOptions: Int? = null
    private var maxPollOptionLength: Int? = null
    @Px
    private var thumbnailViewSize: Int = 0

    private var saveTootHelper: SaveTootHelper? = null
    private val gson = Gson()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val theme = preferences.getString("appTheme", ThemeUtils.APP_THEME_DEFAULT)
        if (theme == "black") {
            setTheme(R.style.TuskyDialogActivityBlackTheme)
        }
        setContentView(R.layout.activity_compose)

        emojiList = emptyList()

        saveTootHelper = SaveTootHelper(database.tootDao(), this)

        // Setup the toolbar.
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setTitle(null)
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setDisplayShowHomeEnabled(true)
            val closeIcon = AppCompatResources.getDrawable(this, R.drawable.ic_close_24dp)
            ThemeUtils.setDrawableTint(this, closeIcon!!, R.attr.compose_close_button_tint)
            actionBar.setHomeAsUpIndicator(closeIcon)
        }

        // setup the account image
        val activeAccount = accountManager.activeAccount

        if (activeAccount != null) {
            val composeAvatar = findViewById<ImageView>(R.id.composeAvatar)


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

            mastodonApi.getInstance()
                    .observeOn(AndroidSchedulers.mainThread())
                    .autoDispose(from(this, Lifecycle.Event.ON_DESTROY))
                    .subscribe(
                            { this.onFetchInstanceSuccess(it) },
                            { this.onFetchInstanceFailure(it) })

            mastodonApi.getCustomEmojis().enqueue(object : Callback<List<Emoji>> {
                override fun onResponse(call: Call<List<Emoji>>, response: Response<List<Emoji>>) {
                    val emojiList = response.body() ?: listOf()
                    val sortedEmojis = emojiList.sortedBy { it.shortcode.toLowerCase(Locale.ROOT) }
                    setEmojiList(sortedEmojis)
                    cacheInstanceMetadata(activeAccount)
                }

                override fun onFailure(call: Call<List<Emoji>>, t: Throwable) {
                    Log.w(TAG, "error loading custom emojis", t)
                    loadCachedInstanceMetadata(activeAccount)
                }
            })
        } else {
            // do not do anything when not logged in, activity will be finished in super.onCreate() anyway
            return
        }

        composeOptionsBottomSheet.listener = this

        composeOptionsBehavior = BottomSheetBehavior.from(composeOptionsBottomSheet)
        composeOptionsBehavior!!.state = BottomSheetBehavior.STATE_HIDDEN

        addMediaBehavior = BottomSheetBehavior.from(findViewById<View>(R.id.addMediaBottomSheet))

        scheduleBehavior = BottomSheetBehavior.from(composeScheduleView)

        emojiBehavior = BottomSheetBehavior.from(emojiView!!)

        emojiView!!.layoutManager = GridLayoutManager(this, 3, GridLayoutManager.HORIZONTAL, false)

        enableButton(composeEmojiButton, false, false)

        // Setup the interface buttons.
        composeTootButton.setOnClickListener { v -> onSendClicked() }
        composeAddMediaButton.setOnClickListener { v -> openPickDialog() }
        composeToggleVisibilityButton.setOnClickListener { v -> showComposeOptions() }
        composeContentWarningButton.setOnClickListener { v -> onContentWarningChanged() }
        composeEmojiButton.setOnClickListener { v -> showEmojis() }
        composeHideMediaButton.setOnClickListener { v -> toggleHideMedia() }
        composeScheduleButton.setOnClickListener { v -> showScheduleView() }
        composeScheduleView.setResetOnClickListener { v -> resetSchedule() }
        atButton!!.setOnClickListener { v -> atButtonClicked() }
        hashButton!!.setOnClickListener { v -> hashButtonClicked() }

        val actionPhotoTake = findViewById<TextView>(R.id.actionPhotoTake)
        val actionPhotoPick = findViewById<TextView>(R.id.actionPhotoPick)

        val textColor = ThemeUtils.getColor(this, android.R.attr.textColorTertiary)

        val cameraIcon = IconicsDrawable(this, GoogleMaterial.Icon.gmd_camera_alt).color(textColor).sizeDp(18)
        actionPhotoTake.setCompoundDrawablesRelativeWithIntrinsicBounds(cameraIcon, null, null, null)

        val imageIcon = IconicsDrawable(this, GoogleMaterial.Icon.gmd_image).color(textColor).sizeDp(18)
        actionPhotoPick.setCompoundDrawablesRelativeWithIntrinsicBounds(imageIcon, null, null, null)

        val pollIcon = IconicsDrawable(this, GoogleMaterial.Icon.gmd_poll).color(textColor).sizeDp(18)
        addPollTextActionTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(pollIcon, null, null, null)

        actionPhotoTake.setOnClickListener { v -> initiateCameraApp() }
        actionPhotoPick.setOnClickListener { v -> onMediaPick() }
        addPollTextActionTextView.setOnClickListener { v -> openPollDialog() }

        thumbnailViewSize = resources.getDimensionPixelSize(R.dimen.compose_media_preview_size)

        /* Initialise all the state, or restore it from a previous run, to determine a "starting"
         * state. */
        var startingVisibility: Status.Visibility = Status.Visibility.UNKNOWN
        var startingHideText: Boolean
        var savedMediaQueued: ArrayList<SavedQueuedMedia>? = null
        if (savedInstanceState != null) {
            startingVisibility = Status.Visibility.byNum(
                    savedInstanceState.getInt("statusVisibility",
                            Status.Visibility.PUBLIC.num)
            )
            statusMarkSensitive = savedInstanceState.getBoolean("statusMarkSensitive")
            startingHideText = savedInstanceState.getBoolean("statusHideText")
            // Keep these until everything needed to put them in the queue is finished initializing.
            savedMediaQueued = savedInstanceState.getParcelableArrayList("savedMediaQueued")
            // These are for restoring an in-progress commit content operation.
            val previousInputContentInfo = InputContentInfoCompat.wrap(
                    savedInstanceState.getParcelable("commitContentInputContentInfo"))
            val previousFlags = savedInstanceState.getInt("commitContentFlags")
            if (previousInputContentInfo != null) {
                onCommitContentInternal(previousInputContentInfo, previousFlags)
            }
            photoUploadUri = savedInstanceState.getParcelable("photoUploadUri")
        } else {
            statusMarkSensitive = activeAccount.defaultMediaSensitivity
            startingHideText = false
            photoUploadUri = null
        }

        /* If the composer is started up as a reply to another post, override the "starting" state
         * based on what the intent from the reply request passes. */
        val intent = intent

        var mentionedUsernames: Array<String>? = null
        var loadedDraftMediaUris: ArrayList<String>? = null
        var loadedDraftMediaDescriptions: ArrayList<String>? = null
        var mediaAttachments: ArrayList<Attachment>? = null
        inReplyToId = null
        if (intent != null) {

            if (startingVisibility === Status.Visibility.UNKNOWN) {
                val preferredVisibility = activeAccount.defaultPostPrivacy
                val replyVisibility = Status.Visibility.byNum(
                        intent.getIntExtra(REPLY_VISIBILITY_EXTRA, Status.Visibility.UNKNOWN.num))

                startingVisibility = Status.Visibility.byNum(Math.max(preferredVisibility.num, replyVisibility.num))
            }

            inReplyToId = intent.getStringExtra(IN_REPLY_TO_ID_EXTRA)

            mentionedUsernames = intent.getStringArrayExtra(MENTIONED_USERNAMES_EXTRA)

            val contentWarning = intent.getStringExtra(CONTENT_WARNING_EXTRA)
            if (contentWarning != null) {
                startingHideText = !contentWarning.isEmpty()
                if (startingHideText) {
                    startingContentWarning = contentWarning
                }
            }

            val tootText = intent.getStringExtra(TOOT_TEXT_EXTRA)
            if (!TextUtils.isEmpty(tootText)) {
                composeEditField.setText(tootText)
            }

            // try to redo a list of media
            // If come from SavedTootActivity
            val savedJsonUrls = intent.getStringExtra(SAVED_JSON_URLS_EXTRA)
            val savedJsonDescriptions = intent.getStringExtra(SAVED_JSON_DESCRIPTIONS_EXTRA)
            if (!TextUtils.isEmpty(savedJsonUrls)) {
                loadedDraftMediaUris = gson.fromJson<ArrayList<String>>(savedJsonUrls,
                        object : TypeToken<ArrayList<String>>() {

                        }.type)
            }
            if (!TextUtils.isEmpty(savedJsonDescriptions)) {
                loadedDraftMediaDescriptions = gson.fromJson<ArrayList<String>>(savedJsonDescriptions,
                        object : TypeToken<ArrayList<String>>() {

                        }.type)
            }
            // If come from redraft
            mediaAttachments = intent.getParcelableArrayListExtra(MEDIA_ATTACHMENTS_EXTRA)

            val savedTootUid = intent.getIntExtra(SAVED_TOOT_UID_EXTRA, 0)
            if (savedTootUid != 0) {
                this.savedTootUid = savedTootUid

                // If come from SavedTootActivity
                startingText = tootText
            }

            val tootVisibility = intent.getIntExtra(TOOT_VISIBILITY_EXTRA, Status.Visibility.UNKNOWN.num)
            if (tootVisibility != Status.Visibility.UNKNOWN.num) {
                startingVisibility = Status.Visibility.byNum(tootVisibility)
            }

            if (intent.hasExtra(REPLYING_STATUS_AUTHOR_USERNAME_EXTRA)) {
                composeReplyView.visibility = View.VISIBLE
                val username = intent.getStringExtra(REPLYING_STATUS_AUTHOR_USERNAME_EXTRA)
                composeReplyView.text = getString(R.string.replying_to, username)
                val arrowDownIcon = IconicsDrawable(this, GoogleMaterial.Icon.gmd_arrow_drop_down).sizeDp(12)

                ThemeUtils.setDrawableTint(this, arrowDownIcon, android.R.attr.textColorTertiary)
                composeReplyView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, arrowDownIcon, null)

                composeReplyView.setOnClickListener { v ->
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

            if (intent.hasExtra(REPLYING_STATUS_CONTENT_EXTRA)) {
                composeReplyContentView.text = intent.getStringExtra(REPLYING_STATUS_CONTENT_EXTRA)
            }

            val scheduledAt = intent.getStringExtra(SCHEDULED_AT_EXTRA)
            if (!TextUtils.isEmpty(scheduledAt)) {
                composeScheduleView.setDateTime(scheduledAt)
            }

            statusMarkSensitive = intent.getBooleanExtra(SENSITIVE_EXTRA, statusMarkSensitive)

            if (intent.hasExtra(POLL_EXTRA) && (mediaAttachments == null || mediaAttachments.size == 0)) {
                updatePoll(intent.getParcelableExtra(POLL_EXTRA))
            }

            if (mediaAttachments != null && mediaAttachments.size > 0) {
                enablePollButton(false)
            }
        }

        // After the starting state is finalised, the interface can be set to reflect this state.
        setStatusVisibility(startingVisibility)

        updateHideMediaToggle()
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

        composeEditField.setOnKeyListener { view, keyCode, event -> this.onKeyDown(keyCode, event) }

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
        showContentWarning(startingHideText)
        if (startingContentWarning != null) {
            composeContentWarningField.setText(startingContentWarning)
        }

        // Initialise the empty media queue state.
        waitForMediaLatch = CountUpDownLatch()

        // These can only be added after everything affected by the media queue is initialized.
        if (!isEmpty(loadedDraftMediaUris)) {
            for (mediaIndex in loadedDraftMediaUris!!.indices) {
                val uri = Uri.parse(loadedDraftMediaUris[mediaIndex])
                val mediaSize = getMediaSize(contentResolver, uri)
                var description: String? = null
                if (loadedDraftMediaDescriptions != null && mediaIndex < loadedDraftMediaDescriptions.size) {
                    description = loadedDraftMediaDescriptions[mediaIndex]
                }
                pickMedia(uri, mediaSize, description)
            }
        } else if (!isEmpty(mediaAttachments)) {
            for (mediaIndex in mediaAttachments!!.indices) {
                val (id, _, previewUrl, _, type1, description) = mediaAttachments[mediaIndex]
                val type: QueuedMedia.Type
                when (type1) {
                    Attachment.Type.UNKNOWN, Attachment.Type.IMAGE -> {
                        type = QueuedMedia.Type.IMAGE
                    }
                    Attachment.Type.VIDEO, Attachment.Type.GIFV -> {
                        type = QueuedMedia.Type.VIDEO
                    }
                    else -> {
                        type = QueuedMedia.Type.IMAGE
                    }
                }
                addMediaToQueue(id, type, previewUrl, description)
            }
        } else if (savedMediaQueued != null) {
            for (item in savedMediaQueued) {
                val preview = getImageThumbnail(contentResolver, item.uri, thumbnailViewSize)
                addMediaToQueue(item.id, item.type, preview, item.uri, item.mediaSize, item.readyStage, item.description)
            }
        } else if (intent != null && savedInstanceState == null) {
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
                        pickMedia(uri, mediaSize, null)
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
        for (item in mediaQueued) {
            item.preview!!.setChecked(!TextUtils.isEmpty(item.description))
        }

        composeEditField.requestFocus()
    }

    private fun replaceTextAtCaret(text: CharSequence) {
        // If you select "backward" in an editable, you get SelectionStart > SelectionEnd
        val start = Math.min(composeEditField.selectionStart, composeEditField.selectionEnd)
        val end = Math.max(composeEditField.selectionStart, composeEditField.selectionEnd)
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
        for (item in mediaQueued) {
            savedMediaQueued.add(SavedQueuedMedia(item.id, item.type, item.uri,
                    item.mediaSize, item.readyStage, item.description))
        }
        outState.putParcelableArrayList("savedMediaQueued", savedMediaQueued)
        outState.putBoolean("statusMarkSensitive", statusMarkSensitive)
        outState.putBoolean("statusHideText", statusHideText)
        if (currentInputContentInfo != null) {
            outState.putParcelable("commitContentInputContentInfo",
                    currentInputContentInfo!!.unwrap() as Parcelable?)
            outState.putInt("commitContentFlags", currentFlags)
        }
        currentInputContentInfo = null
        currentFlags = 0
        outState.putParcelable("photoUploadUri", photoUploadUri)
        outState.putInt("statusVisibility", statusVisibility!!.num)
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
        statusMarkSensitive = !statusMarkSensitive
        updateHideMediaToggle()
    }

    private fun updateHideMediaToggle() {
        TransitionManager.beginDelayedTransition(composeHideMediaButton.parent as ViewGroup)

        @ColorInt val color: Int
        if (mediaQueued.size == 0) {
            composeHideMediaButton.visibility = View.GONE
        } else {
            composeHideMediaButton.visibility = View.VISIBLE
            if (statusMarkSensitive) {
                composeHideMediaButton.setImageResource(R.drawable.ic_hide_media_24dp)
                if (statusHideText) {
                    composeHideMediaButton.isClickable = false
                    color = ContextCompat.getColor(this, R.color.compose_media_visible_button_disabled_blue)
                } else {
                    composeHideMediaButton.isClickable = true
                    color = ContextCompat.getColor(this, R.color.tusky_blue)
                }
            } else {
                composeHideMediaButton.isClickable = true
                composeHideMediaButton.setImageResource(R.drawable.ic_eye_24dp)
                color = ThemeUtils.getColor(this, android.R.attr.textColorTertiary)
            }
            composeHideMediaButton.drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
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
        statusVisibility = visibility
        composeOptionsBottomSheet.setStatusVisibility(visibility)
        composeTootButton.setStatusVisibility(visibility)

        when (visibility) {
            Status.Visibility.PUBLIC -> {
                val globe = AppCompatResources.getDrawable(this, R.drawable.ic_public_24dp)
                if (globe != null) {
                    composeToggleVisibilityButton.setImageDrawable(globe)
                }
            }
            Status.Visibility.PRIVATE -> {
                val lock = AppCompatResources.getDrawable(this,
                        R.drawable.ic_lock_outline_24dp)
                if (lock != null) {
                    composeToggleVisibilityButton.setImageDrawable(lock)
                }
            }
            Status.Visibility.DIRECT -> {
                val envelope = AppCompatResources.getDrawable(this, R.drawable.ic_email_24dp)
                if (envelope != null) {
                    composeToggleVisibilityButton.setImageDrawable(envelope)
                }
            }
            Status.Visibility.UNLISTED -> {
                val openLock = AppCompatResources.getDrawable(this, R.drawable.ic_lock_open_24dp)
                if (openLock != null) {
                    composeToggleVisibilityButton.setImageDrawable(openLock)
                }
            }
            else -> {
                val openLock = AppCompatResources.getDrawable(this, R.drawable.ic_lock_open_24dp)
                if (openLock != null) {
                    composeToggleVisibilityButton.setImageDrawable(openLock)
                }
            }
        }
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
        addMediaBehavior!!.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                //Wait until bottom sheet is not collapsed and show next screen after
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    addMediaBehavior!!.setBottomSheetCallback(null)
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
        })
        addMediaBehavior!!.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun openPollDialog() {
        addMediaBehavior!!.state = BottomSheetBehavior.STATE_COLLAPSED
        showAddPollDialog(this, poll, maxPollOptions, maxPollOptionLength)
    }

    fun updatePoll(poll: NewPoll) {
        this.poll = poll

        enableButton(composeAddMediaButton, false, false)

        if (pollPreview == null) {

            pollPreview = PollPreviewView(this)

            val resources = resources
            val margin = resources.getDimensionPixelSize(R.dimen.compose_media_preview_margin)
            val marginBottom = resources.getDimensionPixelSize(R.dimen.compose_media_preview_margin_bottom)

            val layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams.setMargins(margin, margin, margin, marginBottom)
            pollPreview!!.layoutParams = layoutParams

            composeMediaPreviewBar.addView(pollPreview)

            pollPreview!!.setOnClickListener { v ->
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
        poll = null
        pollPreview = null
        enableButton(composeAddMediaButton, true, true)
        composeMediaPreviewBar.removeAllViews()
    }

    override fun onVisibilityChanged(visibility: Status.Visibility) {
        composeOptionsBehavior!!.state = BottomSheetBehavior.STATE_COLLAPSED
        setStatusVisibility(visibility)
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
        if (statusHideText) {
            length += composeContentWarningField.length()
        }
        return length
    }

    private fun updateVisibleCharactersLeft() {
        this.composeCharactersLeftView.text = String.format(Locale.getDefault(), "%d", maximumTootCharacters - calculateTextLength())
    }

    private fun onContentWarningChanged() {
        val showWarning = composeContentWarningBar.visibility != View.VISIBLE
        showContentWarning(showWarning)
        updateVisibleCharactersLeft()
    }

    private fun onSendClicked() {
        disableButtons()
        readyStatus(statusVisibility, statusMarkSensitive)
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
        pickMedia(uri, mediaSize, null)

        currentInputContentInfo = inputContentInfo
        currentFlags = flags

        return true
    }

    private fun sendStatus(content: String, visibility: Status.Visibility?, sensitive: Boolean,
                           spoilerText: String) {
        val mediaIds = ArrayList<String>()
        val mediaUris = ArrayList<Uri>()
        val mediaDescriptions = ArrayList<String>()
        for (item in mediaQueued) {
            mediaIds.add(item.id!!)
            mediaUris.add(item.uri)
            mediaDescriptions.add(item.description ?: "")
        }

        val sendIntent = SendTootService.sendTootIntent(this, content, spoilerText,
                visibility!!, mediaUris.isNotEmpty() && sensitive, mediaIds, mediaUris, mediaDescriptions,
                composeScheduleView.time, inReplyToId, poll,
                intent.getStringExtra(REPLYING_STATUS_CONTENT_EXTRA),
                intent.getStringExtra(REPLYING_STATUS_AUTHOR_USERNAME_EXTRA),
                intent.getStringExtra(SAVED_JSON_URLS_EXTRA),
                accountManager.activeAccount!!, savedTootUid)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(sendIntent)
        } else {
            startService(sendIntent)
        }

        finishWithoutSlideOutAnimation()

    }

    private fun readyStatus(visibility: Status.Visibility?, sensitive: Boolean) {
        if (waitForMediaLatch!!.isEmpty) {
            onReadySuccess(visibility, sensitive)
            return
        }
        finishingUploadDialog = ProgressDialog.show(
                this, getString(R.string.dialog_title_finishing_media_upload),
                getString(R.string.dialog_message_uploading_media), true, true)
        @SuppressLint("StaticFieldLeak") val waitForMediaTask = object : AsyncTask<Void, Void, Boolean>() {
            override fun doInBackground(vararg params: Void): Boolean {
                try {
                    waitForMediaLatch!!.await()
                } catch (e: InterruptedException) {
                    return false
                }

                return true
            }

            override fun onPostExecute(successful: Boolean?) {
                super.onPostExecute(successful)
                finishingUploadDialog!!.dismiss()
                finishingUploadDialog = null
                if (successful!!) {
                    onReadySuccess(visibility, sensitive)
                } else {
                    onReadyFailure(visibility, sensitive)
                }
            }

            override fun onCancelled() {
                removeAllMediaFromQueue()
                enableButtons()
                super.onCancelled()
            }
        }
        finishingUploadDialog!!.setOnCancelListener { dialog ->
            /* Generating an interrupt by passing true here is important because an interrupt
             * exception is the only thing that will kick the latch out of its waiting loop
             * early. */
            waitForMediaTask.cancel(true)
        }
        waitForMediaTask.execute()
    }

    private fun onReadySuccess(visibility: Status.Visibility?, sensitive: Boolean) {
        /* Validate the status meets the character limit. */
        val contentText = composeEditField.text.toString()
        var spoilerText = ""
        if (statusHideText) {
            spoilerText = composeContentWarningField.text.toString()
        }
        val characterCount = calculateTextLength()
        if ((characterCount <= 0 || contentText.trim { it <= ' ' }.length <= 0) && mediaQueued.size == 0) {
            composeEditField.error = getString(R.string.error_empty)
            enableButtons()
        } else if (characterCount <= maximumTootCharacters) {
            sendStatus(contentText, visibility, sensitive, spoilerText)

        } else {
            composeEditField.error = getString(R.string.error_compose_character_limit)
            enableButtons()
        }
    }

    private fun onReadyFailure(visibility: Status.Visibility?, sensitive: Boolean) {
        doErrorDialog(R.string.error_media_upload_sending, R.string.action_retry,
                View.OnClickListener { readyStatus(visibility, sensitive) })
        enableButtons()
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

    @Throws(IOException::class)
    private fun createNewImageFile(): File {
        // Create an image file name
        val randomId = randomAlphanumericString(12)
        val imageFileName = "Tusky_" + randomId + "_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
                imageFileName, /* prefix */
                ".jpg", /* suffix */
                storageDir      /* directory */
        )
    }

    private fun initiateCameraApp() {
        addMediaBehavior!!.state = BottomSheetBehavior.STATE_COLLAPSED

        // We don't need to ask for permission in this case, because the used calls require
        // android.permission.WRITE_EXTERNAL_STORAGE only on SDKs *older* than Kitkat, which was
        // way before permission dialogues have been introduced.
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            var photoFile: File? = null
            try {
                photoFile = createNewImageFile()
            } catch (ex: IOException) {
                displayTransientError(R.string.error_media_upload_opening)
            }

            // Continue only if the File was successfully created
            if (photoFile != null) {
                photoUploadUri = FileProvider.getUriForFile(this,
                        BuildConfig.APPLICATION_ID + ".fileprovider",
                        photoFile)
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUploadUri)
                startActivityForResult(intent, MEDIA_TAKE_PHOTO_RESULT)
            }
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

    private fun addMediaToQueue(type: QueuedMedia.Type, preview: Bitmap, uri: Uri, mediaSize: Long, description: String?) {
        addMediaToQueue(null, type, preview, uri, mediaSize, null, description)
    }

    private fun addMediaToQueue(id: String, type: QueuedMedia.Type, previewUrl: String, description: String?) {
        addMediaToQueue(id, type, null, Uri.parse(previewUrl), 0,
                QueuedMedia.ReadyStage.UPLOADED, description)
    }

    private fun addMediaToQueue(id: String?, type: QueuedMedia.Type, preview: Bitmap?, uri: Uri,
                                mediaSize: Long, readyStage: QueuedMedia.ReadyStage?, description: String?) {
        val item = QueuedMedia(type, uri, ProgressImageView(this),
                mediaSize, description)
        item.id = id
        item.readyStage = readyStage
        val view = item.preview
        val resources = resources
        val margin = resources.getDimensionPixelSize(R.dimen.compose_media_preview_margin)
        val marginBottom = resources.getDimensionPixelSize(
                R.dimen.compose_media_preview_margin_bottom)
        val layoutParams = LinearLayout.LayoutParams(thumbnailViewSize, thumbnailViewSize)
        layoutParams.setMargins(margin, 0, margin, marginBottom)
        view!!.layoutParams = layoutParams
        view.scaleType = ImageView.ScaleType.CENTER_CROP
        if (preview != null) {
            view.setImageBitmap(preview)
        } else {
            Glide.with(this)
                    .load(uri)
                    .placeholder(null)
                    .into(view)
        }
        view.setOnClickListener { v -> onMediaClick(item, v) }
        composeMediaPreviewBar.addView(view)
        mediaQueued.add(item)
        updateContentDescription(item)
        val queuedCount = mediaQueued.size
        if (queuedCount == 1) {
            // If there's one video in the queue it is full, so disable the button to queue more.
            if (item.type == QueuedMedia.Type.VIDEO) {
                enableButton(composeAddMediaButton, false, false)
            }
        } else if (queuedCount >= Status.MAX_MEDIA_ATTACHMENTS) {
            // Limit the total media attachments, also.
            enableButton(composeAddMediaButton, false, false)
        }

        updateHideMediaToggle()
        enablePollButton(false)

        if (item.readyStage != QueuedMedia.ReadyStage.UPLOADED) {
            waitForMediaLatch!!.countUp()

            try {
                if (type == QueuedMedia.Type.IMAGE && (mediaSize > STATUS_IMAGE_SIZE_LIMIT || getImageSquarePixels(contentResolver, item.uri) > STATUS_IMAGE_PIXEL_SIZE_LIMIT)) {
                    downsizeMedia(item)
                } else {
                    uploadMedia(item)
                }
            } catch (e: IOException) {
                onUploadFailure(item, false)
            }

        }
    }

    private fun updateContentDescriptionForAllImages() {
        val items = ArrayList(mediaQueued)
        for (media in items) {
            updateContentDescription(media)
        }
    }

    private fun updateContentDescription(item: QueuedMedia) {
        if (item.preview != null) {
            val imageId: String?
            if (!TextUtils.isEmpty(item.description)) {
                imageId = item.description
            } else {
                val idx = getImageIdx(item)
                if (idx < 0)
                    imageId = null
                else
                    imageId = Integer.toString(idx + 1)
            }
            item.preview!!.contentDescription = getString(R.string.compose_preview_image_description, imageId)
        }
    }

    private fun getImageIdx(item: QueuedMedia): Int {
        return mediaQueued.indexOf(item)
    }

    private fun onMediaClick(item: QueuedMedia, view: View) {
        val popup = PopupMenu(this, view)
        val addCaptionId = 1
        val removeId = 2
        popup.menu.add(0, addCaptionId, 0, R.string.action_set_caption)
        popup.menu.add(0, removeId, 0, R.string.action_remove)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                addCaptionId -> makeCaptionDialog(item)
                removeId -> removeMediaFromQueue(item)
            }
            true
        }
        popup.show()
    }

    private fun makeCaptionDialog(item: QueuedMedia) {
        val dialogLayout = LinearLayout(this)
        val padding = Utils.dpToPx(this, 8)
        dialogLayout.setPadding(padding, padding, padding, padding)

        dialogLayout.orientation = LinearLayout.VERTICAL
        val imageView = ImageView(this)

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        Single.fromCallable<Bitmap> { getSampledBitmap(contentResolver, item.uri, displayMetrics.widthPixels, displayMetrics.heightPixels) }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .autoDispose(from(this, Lifecycle.Event.ON_DESTROY))
                .subscribe(object : SingleObserver<Bitmap> {
                    override fun onSubscribe(d: Disposable) {}

                    override fun onSuccess(bitmap: Bitmap) {
                        imageView.setImageBitmap(bitmap)
                    }

                    override fun onError(e: Throwable) {}
                })


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
            val updateDescription = Runnable {
                mastodonApi.updateMedia(item.id!!, input.text.toString()).enqueue(object : Callback<Attachment> {
                    override fun onResponse(call: Call<Attachment>, response: Response<Attachment>) {
                        val attachment = response.body()
                        if (response.isSuccessful && attachment != null) {
                            item.description = attachment.description
                            item.preview!!.setChecked(item.description != null && !item.description!!.isEmpty())
                            dialog.dismiss()
                            updateContentDescription(item)
                        } else {
                            showFailedCaptionMessage()
                        }
                        item.updateDescription = null
                    }

                    override fun onFailure(call: Call<Attachment>, t: Throwable) {
                        showFailedCaptionMessage()
                        item.updateDescription = null
                    }
                })
            }

            if (item.readyStage == QueuedMedia.ReadyStage.UPLOADED) {
                updateDescription.run()
            } else {
                // media is still uploading, queue description update for when it finishes
                item.updateDescription = updateDescription
            }
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
        composeMediaPreviewBar.removeView(item.preview)
        mediaQueued.remove(item)
        if (mediaQueued.size == 0) {
            updateHideMediaToggle()
            enablePollButton(true)
        }
        updateContentDescriptionForAllImages()
        enableButton(composeAddMediaButton, true, true)
        cancelReadyingMedia(item)
    }

    private fun removeAllMediaFromQueue() {
        val it = mediaQueued.iterator()
        while (it.hasNext()) {
            val item = it.next()
            it.remove()
            removeMediaFromQueue(item)
        }
    }

    @Throws(IOException::class)
    private fun downsizeMedia(item: QueuedMedia) {
        item.readyStage = QueuedMedia.ReadyStage.DOWNSIZING

        DownsizeImageTask(STATUS_IMAGE_SIZE_LIMIT, contentResolver, createNewImageFile(),
                object : DownsizeImageTask.Listener {
                    override fun onSuccess(tempFile: File) {
                        item.uri = FileProvider.getUriForFile(
                                this@ComposeActivity,
                                BuildConfig.APPLICATION_ID + ".fileprovider",
                                tempFile)
                        uploadMedia(item)
                    }

                    override fun onFailure() {
                        onMediaDownsizeFailure(item)
                    }
                }).execute(item.uri)
    }

    private fun onMediaDownsizeFailure(item: QueuedMedia) {
        displayTransientError(R.string.error_image_upload_size)
        removeMediaFromQueue(item)
    }

    private fun uploadMedia(item: QueuedMedia) {
        item.readyStage = QueuedMedia.ReadyStage.UPLOADING

        var mimeType = contentResolver.getType(item.uri)
        val map = MimeTypeMap.getSingleton()
        val fileExtension = map.getExtensionFromMimeType(mimeType)
        val filename = String.format("%s_%s_%s.%s",
                getString(R.string.app_name),
                Date().time.toString(),
                randomAlphanumericString(10),
                fileExtension)

        val stream: InputStream?

        try {
            stream = contentResolver.openInputStream(item.uri)
        } catch (e: FileNotFoundException) {
            Log.w(TAG, e)
            return
        }

        if (mimeType == null) mimeType = "multipart/form-data"

        item.preview!!.setProgress(0)

        val fileBody = ProgressRequestBody(stream, getMediaSize(contentResolver, item.uri), mimeType.toMediaTypeOrNull(),
                object : ProgressRequestBody.UploadCallback { // may reference activity longer than I would like to
                    var lastProgress = -1

                    override fun onProgressUpdate(percentage: Int) {
                        if (percentage != lastProgress) {
                            runOnUiThread { item.preview!!.setProgress(percentage) }
                        }
                        lastProgress = percentage
                    }
                })

        val body = MultipartBody.Part.createFormData("file", filename, fileBody)

        item.uploadRequest = mastodonApi.uploadMedia(body)

        item.uploadRequest!!.enqueue(object : Callback<Attachment> {
            override fun onResponse(call: Call<Attachment>, response: Response<Attachment>) {
                if (response.isSuccessful) {
                    onUploadSuccess(item, response.body()!!)
                    if (item.updateDescription != null) {
                        item.updateDescription!!.run()
                    }
                } else {
                    Log.d(TAG, "Upload request failed. " + response.message())
                    onUploadFailure(item, call.isCanceled)
                }
            }

            override fun onFailure(call: Call<Attachment>, t: Throwable) {
                Log.d(TAG, "Upload request failed. " + t.message)
                onUploadFailure(item, call.isCanceled)
                item.updateDescription = null
            }
        })
    }

    private fun onUploadSuccess(item: QueuedMedia, media: Attachment) {
        item.id = media.id
        item.preview!!.setProgress(-1)
        item.readyStage = QueuedMedia.ReadyStage.UPLOADED

        waitForMediaLatch!!.countDown()
    }

    private fun onUploadFailure(item: QueuedMedia, isCanceled: Boolean) {
        if (!isCanceled) {
            /* if the upload was voluntarily cancelled, such as if the user clicked on it to remove
             * it from the queue, then don't display this error message. */
            displayTransientError(R.string.error_media_upload_sending)
        }
        if (finishingUploadDialog != null && finishingUploadDialog!!.isShowing) {
            finishingUploadDialog!!.cancel()
        }
        if (!isCanceled) {
            // If it is canceled, it's already been removed, otherwise do it.
            removeMediaFromQueue(item)
        }
    }

    private fun cancelReadyingMedia(item: QueuedMedia) {
        if (item.readyStage == QueuedMedia.ReadyStage.UPLOADING) {
            item.uploadRequest!!.cancel()
        }
        if (item.id == null) {
            /* The presence of an upload id is used to detect if it finished uploading or not, to
             * prevent counting down twice on the same media item. */
            waitForMediaLatch!!.countDown()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (resultCode == Activity.RESULT_OK && requestCode == MEDIA_PICK_RESULT && intent != null) {
            val uri = intent.data
            val mediaSize = getMediaSize(contentResolver, uri)
            pickMedia(uri!!, mediaSize, null)
        } else if (resultCode == Activity.RESULT_OK && requestCode == MEDIA_TAKE_PHOTO_RESULT) {
            val mediaSize = getMediaSize(contentResolver, photoUploadUri)
            pickMedia(photoUploadUri!!, mediaSize, null)
        }
    }


    private fun pickMedia(inUri: Uri, mediaSize: Long, description: String?) {
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
                    if (mediaSize > STATUS_VIDEO_SIZE_LIMIT) {
                        displayTransientError(R.string.error_video_upload_size)
                        return
                    }
                    if (mediaQueued.size > 0 && mediaQueued[0].type == QueuedMedia.Type.IMAGE) {
                        displayTransientError(R.string.error_media_upload_image_or_video)
                        return
                    }
                    val bitmap = getVideoThumbnail(this, uri, thumbnailViewSize)
                    if (bitmap != null) {
                        addMediaToQueue(QueuedMedia.Type.VIDEO, bitmap, uri, mediaSize, description)
                    } else {
                        displayTransientError(R.string.error_media_upload_opening)
                    }
                }
                "image" -> {
                    val bitmap = getImageThumbnail(contentResolver, uri, thumbnailViewSize)
                    if (bitmap != null) {
                        addMediaToQueue(QueuedMedia.Type.IMAGE, bitmap, uri, mediaSize, description)
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
        statusHideText = show
        TransitionManager.beginDelayedTransition(composeContentWarningBar.parent as ViewGroup)
        val color: Int
        if (show) {
            statusMarkSensitive = true
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

        updateHideMediaToggle()

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                handleCloseButton()
                return true
            }
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

        val contentText = composeEditField.text
        val contentWarning = composeContentWarningField.text

        val textChanged = !(TextUtils.isEmpty(contentText) || startingText.startsWith(contentText.toString()))
        val contentWarningChanged = composeContentWarningBar.visibility == View.VISIBLE &&
                !TextUtils.isEmpty(contentWarning) && !startingContentWarning!!.startsWith(contentWarning.toString())
        val mediaChanged = !mediaQueued.isEmpty()
        val pollChanged = poll != null

        if (textChanged || contentWarningChanged || mediaChanged || pollChanged) {
            AlertDialog.Builder(this)
                    .setMessage(R.string.compose_save_draft)
                    .setPositiveButton(R.string.action_save) { d, w -> saveDraftAndFinish() }
                    .setNegativeButton(R.string.action_delete) { d, w -> deleteDraftAndFinish() }
                    .show()
        } else {
            finishWithoutSlideOutAnimation()
        }
    }

    private fun deleteDraftAndFinish() {
        for (media in mediaQueued) {
            if (media.uploadRequest != null)
                media.uploadRequest!!.cancel()
        }
        finishWithoutSlideOutAnimation()
    }

    private fun saveDraftAndFinish() {
        val mediaUris = ArrayList<String>()
        val mediaDescriptions = ArrayList<String?>()
        for (item in mediaQueued) {
            mediaUris.add(item.uri.toString())
            mediaDescriptions.add(item.description)
        }

        saveTootHelper!!.saveToot(composeEditField.text.toString(),
                composeContentWarningField.text.toString(),
                intent.getStringExtra("saved_json_urls"),
                mediaUris,
                mediaDescriptions,
                savedTootUid,
                inReplyToId,
                intent.getStringExtra(REPLYING_STATUS_CONTENT_EXTRA),
                intent.getStringExtra(REPLYING_STATUS_AUTHOR_USERNAME_EXTRA),
                statusVisibility!!,
                poll)
        finishWithoutSlideOutAnimation()
    }

    override fun search(token: String): List<ComposeAutoCompleteAdapter.AutocompleteResult> {
        when (token[0]) {
            '@' -> {
                return try {
                    val accountList = mastodonApi
                            .searchAccounts(token.substring(1), false, 20, null)
                            .blockingGet()
                    accountList.map<Account, ComposeAutoCompleteAdapter.AutocompleteResult> { account: Account -> ComposeAutoCompleteAdapter.AccountResult(account) }
                } catch (e: Throwable) {
                    emptyList()
                }
            }
            '#' -> {
                return try {
                    val (_, _, hashtags) = mastodonApi.searchObservable(token, null, false, null, null, null).blockingGet()
                    hashtags.map { hashtag -> ComposeAutoCompleteAdapter.HashtagResult(hashtag) }
                } catch (e: Throwable) {
                    Log.e(TAG, String.format("Autocomplete search for %s failed.", token), e)
                    emptyList()
                }
            }
            ':' -> {
                try {
                    emojiListRetrievalLatch.await()
                } catch (e: InterruptedException) {
                    Log.e(TAG, String.format("Autocomplete search for %s was interrupted.", token))
                    return emptyList()
                }

                if (emojiList != null) {
                    val incomplete = token.substring(1).toLowerCase()
                    val results = ArrayList<ComposeAutoCompleteAdapter.AutocompleteResult>()
                    val resultsInside = ArrayList<ComposeAutoCompleteAdapter.AutocompleteResult>()
                    for (emoji in emojiList!!) {
                        val shortcode = emoji.shortcode.toLowerCase()
                        if (shortcode.startsWith(incomplete)) {
                            results.add(ComposeAutoCompleteAdapter.EmojiResult(emoji))
                        } else if (shortcode.indexOf(incomplete, 1) != -1) {
                            resultsInside.add(ComposeAutoCompleteAdapter.EmojiResult(emoji))
                        }
                    }
                    if (!results.isEmpty() && !resultsInside.isEmpty()) {
                        results.add(ComposeAutoCompleteAdapter.ResultSeparator())
                    }
                    results.addAll(resultsInside)
                    return results
                } else {
                    return emptyList()
                }
            }
            else -> {
                Log.w(TAG, "Unexpected autocompletion token: $token")
                return emptyList()
            }
        }
    }

    override fun onEmojiSelected(shortcode: String) {
        replaceTextAtCaret(":$shortcode: ")
    }

    private fun loadCachedInstanceMetadata(activeAccount: AccountEntity) {
        val instanceEntity = database.instanceDao()
                .loadMetadataForInstance(activeAccount.domain)

        if (instanceEntity != null) {
            val max = instanceEntity.maximumTootCharacters
            maximumTootCharacters = max ?: STATUS_CHARACTER_LIMIT
            maxPollOptions = instanceEntity.maxPollOptions
            maxPollOptionLength = instanceEntity.maxPollOptionLength
            setEmojiList(instanceEntity.emojiList)
            updateVisibleCharactersLeft()
        }
    }

    private fun setEmojiList(emojiList: List<Emoji>?) {
        this.emojiList = emojiList

        emojiListRetrievalLatch.countDown()

        if (emojiList != null) {
            emojiView!!.adapter = EmojiAdapter(emojiList, this@ComposeActivity)
            enableButton(composeEmojiButton, true, emojiList.size > 0)
        }
    }

    private fun cacheInstanceMetadata(activeAccount: AccountEntity) {
        val instanceEntity = InstanceEntity(
                activeAccount.domain, emojiList, maximumTootCharacters, maxPollOptions, maxPollOptionLength
        )
        database.instanceDao().insertOrReplace(instanceEntity)
    }

    private fun onFetchInstanceSuccess(instance: Instance?) {
        if (instance != null) {
            if (instance.maxTootChars != null) {
                maximumTootCharacters = instance.maxTootChars
                updateVisibleCharactersLeft()
            }

            if (!VersionUtils(instance.version).supportsScheduledToots()) {
                composeScheduleButton.visibility = View.GONE
            }

            if (instance.pollLimits != null) {
                maxPollOptions = instance.pollLimits.maxOptions
                maxPollOptionLength = instance.pollLimits.maxOptionChars
            }

            cacheInstanceMetadata(accountManager.activeAccount!!)
        }
    }

    private fun onFetchInstanceFailure(throwable: Throwable) {
        Log.w(TAG, "error loading instance data", throwable)
        loadCachedInstanceMetadata(accountManager.activeAccount!!)
    }

    data class QueuedMedia(
            var type: Type,
            var uri: Uri,
            var preview: ProgressImageView?,
            var mediaSize: Long,
            var description: String?
    ) {
        internal var id: String? = null
        internal var uploadRequest: Call<Attachment>? = null
        internal var readyStage: ReadyStage? = null
        internal var updateDescription: Runnable? = null

        enum class Type {
            IMAGE,
            VIDEO
        }

        internal enum class ReadyStage {
            DOWNSIZING,
            UPLOADING,
            UPLOADED
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
            var readyStage: QueuedMedia.ReadyStage?,
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

    class IntentBuilder {
        private var savedTootUid: Int? = null
        private var tootText: String? = null
        private var savedJsonUrls: String? = null
        private var savedJsonDescriptions: String? = null
        private var mentionedUsernames: Collection<String>? = null
        private var inReplyToId: String? = null
        private var replyVisibility: Status.Visibility? = null
        private var visibility: Status.Visibility? = null
        private var contentWarning: String? = null
        private var replyingStatusAuthor: String? = null
        private var replyingStatusContent: String? = null
        private var mediaAttachments: ArrayList<Attachment>? = null
        private var scheduledAt: String? = null
        private var sensitive: Boolean? = null
        private var poll: NewPoll? = null

        fun savedTootUid(uid: Int): IntentBuilder {
            this.savedTootUid = uid
            return this
        }

        fun tootText(tootText: String): IntentBuilder {
            this.tootText = tootText
            return this
        }

        fun savedJsonUrls(jsonUrls: String): IntentBuilder {
            this.savedJsonUrls = jsonUrls
            return this
        }

        fun savedJsonDescriptions(jsonDescriptions: String): IntentBuilder {
            this.savedJsonDescriptions = jsonDescriptions
            return this
        }

        fun visibility(visibility: Status.Visibility): IntentBuilder {
            this.visibility = visibility
            return this
        }

        fun mentionedUsernames(mentionedUsernames: Collection<String>): IntentBuilder {
            this.mentionedUsernames = mentionedUsernames
            return this
        }

        fun inReplyToId(inReplyToId: String?): IntentBuilder {
            this.inReplyToId = inReplyToId
            return this
        }

        fun replyVisibility(replyVisibility: Status.Visibility): IntentBuilder {
            this.replyVisibility = replyVisibility
            return this
        }

        fun contentWarning(contentWarning: String): IntentBuilder {
            this.contentWarning = contentWarning
            return this
        }

        fun replyingStatusAuthor(username: String): IntentBuilder {
            this.replyingStatusAuthor = username
            return this
        }

        fun replyingStatusContent(content: String): IntentBuilder {
            this.replyingStatusContent = content
            return this
        }

        fun mediaAttachments(mediaAttachments: ArrayList<Attachment>?): IntentBuilder {
            this.mediaAttachments = mediaAttachments
            return this
        }

        fun scheduledAt(scheduledAt: String): IntentBuilder {
            this.scheduledAt = scheduledAt
            return this
        }

        fun sensitive(sensitive: Boolean): IntentBuilder {
            this.sensitive = sensitive
            return this
        }

        fun poll(poll: NewPoll?): IntentBuilder {
            this.poll = poll
            return this
        }

        fun build(context: Context): Intent {
            val intent = Intent(context, ComposeActivity::class.java)

            if (savedTootUid != null) {
                intent.putExtra(SAVED_TOOT_UID_EXTRA, savedTootUid as Int)
            }
            if (tootText != null) {
                intent.putExtra(TOOT_TEXT_EXTRA, tootText)
            }
            if (savedJsonUrls != null) {
                intent.putExtra(SAVED_JSON_URLS_EXTRA, savedJsonUrls)
            }
            if (savedJsonDescriptions != null) {
                intent.putExtra(SAVED_JSON_DESCRIPTIONS_EXTRA, savedJsonDescriptions)
            }
            if (mentionedUsernames != null) {
                val usernames = mentionedUsernames!!.toTypedArray()
                intent.putExtra(MENTIONED_USERNAMES_EXTRA, usernames)
            }
            if (inReplyToId != null) {
                intent.putExtra(IN_REPLY_TO_ID_EXTRA, inReplyToId)
            }
            if (replyVisibility != null) {
                intent.putExtra(REPLY_VISIBILITY_EXTRA, replyVisibility!!.num)
            }
            if (visibility != null) {
                intent.putExtra(TOOT_VISIBILITY_EXTRA, visibility!!.num)
            }
            if (contentWarning != null) {
                intent.putExtra(CONTENT_WARNING_EXTRA, contentWarning)
            }
            if (replyingStatusContent != null) {
                intent.putExtra(REPLYING_STATUS_CONTENT_EXTRA, replyingStatusContent)
            }
            if (replyingStatusAuthor != null) {
                intent.putExtra(REPLYING_STATUS_AUTHOR_USERNAME_EXTRA, replyingStatusAuthor)
            }
            if (mediaAttachments != null) {
                intent.putParcelableArrayListExtra(MEDIA_ATTACHMENTS_EXTRA, mediaAttachments)
            }
            if (scheduledAt != null) {
                intent.putExtra(SCHEDULED_AT_EXTRA, scheduledAt)
            }
            if (sensitive != null) {
                intent.putExtra(SENSITIVE_EXTRA, sensitive!!)
            }
            if (poll != null) {
                intent.putExtra(POLL_EXTRA, poll)
            }
            return intent
        }
    }

    companion object {

        private const val TAG = "ComposeActivity" // logging tag
        internal const val STATUS_CHARACTER_LIMIT = 500
        private const val STATUS_IMAGE_SIZE_LIMIT = 8388608 // 8MiB
        private const val STATUS_VIDEO_SIZE_LIMIT = 41943040 // 40MiB
        private const val STATUS_IMAGE_PIXEL_SIZE_LIMIT = 16777216 // 4096^2 Pixels
        private const val MEDIA_PICK_RESULT = 1
        private const val MEDIA_TAKE_PHOTO_RESULT = 2
        private const val PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1

        private const val SAVED_TOOT_UID_EXTRA = "saved_toot_uid"
        private const val TOOT_TEXT_EXTRA = "toot_text"
        private const val SAVED_JSON_URLS_EXTRA = "saved_json_urls"
        private const val SAVED_JSON_DESCRIPTIONS_EXTRA = "saved_json_descriptions"
        private const val TOOT_VISIBILITY_EXTRA = "toot_visibility"
        private const val IN_REPLY_TO_ID_EXTRA = "in_reply_to_id"
        private const val REPLY_VISIBILITY_EXTRA = "reply_visibility"
        private const val CONTENT_WARNING_EXTRA = "content_warning"
        private const val MENTIONED_USERNAMES_EXTRA = "mentioned_usernames"
        private const val REPLYING_STATUS_AUTHOR_USERNAME_EXTRA = "replying_author_nickname_extra"
        private const val REPLYING_STATUS_CONTENT_EXTRA = "replying_status_content"
        private const val MEDIA_ATTACHMENTS_EXTRA = "media_attachments"
        private const val SCHEDULED_AT_EXTRA = "scheduled_at"
        private const val SENSITIVE_EXTRA = "sensitive"
        private const val POLL_EXTRA = "poll"
        // Mastodon only counts URLs as this long in terms of status character limits
        internal const val MAXIMUM_URL_LENGTH = 23
        // https://github.com/tootsuite/mastodon/blob/1656663/app/models/media_attachment.rb#L94
        private const val MEDIA_DESCRIPTION_CHARACTER_LIMIT = 420

        @JvmStatic
        fun canHandleMimeType(mimeType: String?): Boolean {
            return mimeType != null && (mimeType.startsWith("image/") || mimeType.startsWith("video/") || mimeType == "text/plain")
        }
    }
}
