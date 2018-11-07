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

package com.keylesspalace.tusky;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.arch.lifecycle.Lifecycle;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Px;
import android.support.annotation.StringRes;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.Snackbar;
import android.support.transition.TransitionManager;
import android.support.v13.view.inputmethod.InputConnectionCompat;
import android.support.v13.view.inputmethod.InputContentInfoCompat;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.TextViewCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.URLSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.keylesspalace.tusky.adapter.EmojiAdapter;
import com.keylesspalace.tusky.adapter.MentionAutoCompleteAdapter;
import com.keylesspalace.tusky.adapter.OnEmojiSelectedListener;
import com.keylesspalace.tusky.db.AccountEntity;
import com.keylesspalace.tusky.db.AppDatabase;
import com.keylesspalace.tusky.db.InstanceEntity;
import com.keylesspalace.tusky.di.Injectable;
import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.entity.Attachment;
import com.keylesspalace.tusky.entity.Emoji;
import com.keylesspalace.tusky.entity.Instance;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.network.MastodonApi;
import com.keylesspalace.tusky.network.ProgressRequestBody;
import com.keylesspalace.tusky.service.SendTootService;
import com.keylesspalace.tusky.util.CountUpDownLatch;
import com.keylesspalace.tusky.util.DownsizeImageTask;
import com.keylesspalace.tusky.util.ListUtils;
import com.keylesspalace.tusky.util.MentionTokenizer;
import com.keylesspalace.tusky.util.SaveTootHelper;
import com.keylesspalace.tusky.util.SpanUtilsKt;
import com.keylesspalace.tusky.util.StringUtils;
import com.keylesspalace.tusky.util.ThemeUtils;
import com.keylesspalace.tusky.view.ComposeOptionsListener;
import com.keylesspalace.tusky.view.ComposeOptionsView;
import com.keylesspalace.tusky.view.EditTextTyped;
import com.keylesspalace.tusky.view.ProgressImageView;
import com.keylesspalace.tusky.view.TootButton;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.squareup.picasso.Picasso;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import at.connyduck.sparkbutton.helpers.Utils;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.keylesspalace.tusky.util.MediaUtilsKt.MEDIA_SIZE_UNKNOWN;
import static com.keylesspalace.tusky.util.MediaUtilsKt.getImageSquarePixels;
import static com.keylesspalace.tusky.util.MediaUtilsKt.getImageThumbnail;
import static com.keylesspalace.tusky.util.MediaUtilsKt.getMediaSize;
import static com.keylesspalace.tusky.util.MediaUtilsKt.getSampledBitmap;
import static com.keylesspalace.tusky.util.MediaUtilsKt.getVideoThumbnail;
import static com.uber.autodispose.AutoDispose.autoDisposable;
import static com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider.from;

public final class ComposeActivity
        extends BaseActivity
        implements ComposeOptionsListener,
        MentionAutoCompleteAdapter.AccountSearchProvider,
        OnEmojiSelectedListener,
        Injectable, InputConnectionCompat.OnCommitContentListener {

    private static final String TAG = "ComposeActivity"; // logging tag
    static final int STATUS_CHARACTER_LIMIT = 500;
    private static final int STATUS_IMAGE_SIZE_LIMIT = 8388608; // 8MiB
    private static final int STATUS_VIDEO_SIZE_LIMIT = 41943040; // 40MiB
    private static final int STATUS_IMAGE_PIXEL_SIZE_LIMIT = 16777216; // 4096^2 Pixels
    private static final int MEDIA_PICK_RESULT = 1;
    private static final int MEDIA_TAKE_PHOTO_RESULT = 2;
    private static final int PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;

    private static final String SAVED_TOOT_UID_EXTRA = "saved_toot_uid";
    private static final String SAVED_TOOT_TEXT_EXTRA = "saved_toot_text";
    private static final String SAVED_JSON_URLS_EXTRA = "saved_json_urls";
    private static final String SAVED_TOOT_VISIBILITY_EXTRA = "saved_toot_visibility";
    private static final String IN_REPLY_TO_ID_EXTRA = "in_reply_to_id";
    private static final String REPLY_VISIBILITY_EXTRA = "reply_visibilty";
    private static final String CONTENT_WARNING_EXTRA = "content_warning";
    private static final String MENTIONED_USERNAMES_EXTRA = "netnioned_usernames";
    private static final String REPLYING_STATUS_AUTHOR_USERNAME_EXTRA = "replying_author_nickname_extra";
    private static final String REPLYING_STATUS_CONTENT_EXTRA = "replying_status_content";
    // Mastodon only counts URLs as this long in terms of status character limits
    static final int MAXIMUM_URL_LENGTH = 23;

    @Inject
    public MastodonApi mastodonApi;
    @Inject
    public AppDatabase database;

    private TextView replyTextView;
    private TextView replyContentTextView;
    private EditTextTyped textEditor;
    private LinearLayout mediaPreviewBar;
    private View contentWarningBar;
    private EditText contentWarningEditor;
    private TextView charactersLeft;
    private TootButton tootButton;
    private ImageButton pickButton;
    private ImageButton visibilityButton;
    private Button contentWarningButton;
    private ImageButton emojiButton;
    private ImageButton hideMediaToggle;

    private ComposeOptionsView composeOptionsView;
    private BottomSheetBehavior composeOptionsBehavior;
    private BottomSheetBehavior addMediaBehavior;
    private BottomSheetBehavior emojiBehavior;
    private RecyclerView emojiView;

    // this only exists when a status is trying to be sent, but uploads are still occurring
    private ProgressDialog finishingUploadDialog;
    private String inReplyToId;
    private List<QueuedMedia> mediaQueued = new ArrayList<>();
    private CountUpDownLatch waitForMediaLatch;
    private Status.Visibility statusVisibility;     // The current values of the options that will be applied
    private boolean statusMarkSensitive; // to the status being composed.
    private boolean statusHideText;
    private String startingText = "";
    private String startingContentWarning = "";
    private InputContentInfoCompat currentInputContentInfo;
    private int currentFlags;
    private Uri photoUploadUri;
    private int savedTootUid = 0;
    private List<Emoji> emojiList;
    private int maximumTootCharacters = STATUS_CHARACTER_LIMIT;
    private @Px int thumbnailViewSize;

    private SaveTootHelper saveTootHelper;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String theme = preferences.getString("appTheme", ThemeUtils.APP_THEME_DEFAULT);
        if (theme.equals("black")) {
            setTheme(R.style.TuskyDialogActivityBlackTheme);
        }
        setContentView(R.layout.activity_compose);

        replyTextView = findViewById(R.id.composeReplyView);
        replyContentTextView = findViewById(R.id.composeReplyContentView);
        textEditor = findViewById(R.id.composeEditField);
        mediaPreviewBar = findViewById(R.id.compose_media_preview_bar);
        contentWarningBar = findViewById(R.id.composeContentWarningBar);
        contentWarningEditor = findViewById(R.id.composeContentWarningField);
        charactersLeft = findViewById(R.id.composeCharactersLeftView);
        tootButton = findViewById(R.id.composeTootButton);
        pickButton = findViewById(R.id.composeAddMediaButton);
        visibilityButton = findViewById(R.id.composeToggleVisibilityButton);
        contentWarningButton = findViewById(R.id.composeContentWarningButton);
        emojiButton = findViewById(R.id.composeEmojiButton);
        hideMediaToggle = findViewById(R.id.composeHideMediaButton);
        emojiView = findViewById(R.id.emojiView);
        emojiList = Collections.emptyList();

        saveTootHelper = new SaveTootHelper(database.tootDao(), this);

        // Setup the toolbar.
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(null);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
            Drawable closeIcon = AppCompatResources.getDrawable(this, R.drawable.ic_close_24dp);
            ThemeUtils.setDrawableTint(this, closeIcon, R.attr.compose_close_button_tint);
            actionBar.setHomeAsUpIndicator(closeIcon);
        }

        // setup the account image
        final AccountEntity activeAccount = accountManager.getActiveAccount();

        if (activeAccount != null) {
            ImageView composeAvatar = findViewById(R.id.composeAvatar);

            if (TextUtils.isEmpty(activeAccount.getProfilePictureUrl())) {
                composeAvatar.setImageResource(R.drawable.avatar_default);
            } else {
                Picasso.with(this).load(activeAccount.getProfilePictureUrl())
                        .error(R.drawable.avatar_default)
                        .placeholder(R.drawable.avatar_default)
                        .into(composeAvatar);
            }

            composeAvatar.setContentDescription(
                    getString(R.string.compose_active_account_description,
                            activeAccount.getFullName()));

            mastodonApi.getInstance().enqueue(new Callback<Instance>() {
                @Override
                public void onResponse(@NonNull Call<Instance> call, @NonNull Response<Instance> response) {
                    if (response.isSuccessful() && response.body().getMaxTootChars() != null) {
                        maximumTootCharacters = response.body().getMaxTootChars();
                        updateVisibleCharactersLeft();
                        cacheInstanceMetadata(activeAccount);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<Instance> call, @NonNull Throwable t) {
                    Log.w(TAG, "error loading instance data", t);
                    loadCachedInstanceMetadata(activeAccount);
                }
            });

            mastodonApi.getCustomEmojis().enqueue(new Callback<List<Emoji>>() {
                @Override
                public void onResponse(@NonNull Call<List<Emoji>> call, @NonNull Response<List<Emoji>> response) {
                    emojiList = response.body();
                    Collections.sort(emojiList, (a, b) -> a.getShortcode().toLowerCase().compareTo(b.getShortcode().toLowerCase()));
                    setEmojiList(emojiList);
                    cacheInstanceMetadata(activeAccount);
                }

                @Override
                public void onFailure(@NonNull Call<List<Emoji>> call, @NonNull Throwable t) {
                    Log.w(TAG, "error loading custom emojis", t);
                    loadCachedInstanceMetadata(activeAccount);
                }
            });
        } else {
            // do not do anything when not logged in, activity will be finished in super.onCreate() anyway
            return;
        }

        composeOptionsView = findViewById(R.id.composeOptionsBottomSheet);
        composeOptionsView.setListener(this);

        composeOptionsBehavior = BottomSheetBehavior.from(composeOptionsView);
        composeOptionsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        addMediaBehavior = BottomSheetBehavior.from(findViewById(R.id.addMediaBottomSheet));

        emojiBehavior = BottomSheetBehavior.from(emojiView);

        emojiView.setLayoutManager(new GridLayoutManager(this, 3, GridLayoutManager.HORIZONTAL, false));

        enableButton(emojiButton, false, false);

        // Setup the interface buttons.
        tootButton.setOnClickListener(v -> onSendClicked());
        pickButton.setOnClickListener(v -> openPickDialog());
        visibilityButton.setOnClickListener(v -> showComposeOptions());
        contentWarningButton.setOnClickListener(v-> onContentWarningChanged());
        emojiButton.setOnClickListener(v -> showEmojis());
        hideMediaToggle.setOnClickListener(v -> toggleHideMedia());

        TextView actionPhotoTake = findViewById(R.id.action_photo_take);
        TextView actionPhotoPick = findViewById(R.id.action_photo_pick);

        int textColor = ThemeUtils.getColor(this, android.R.attr.textColorTertiary);

        Drawable cameraIcon = new IconicsDrawable(this, GoogleMaterial.Icon.gmd_camera_alt).color(textColor).sizeDp(18);
        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(actionPhotoTake, cameraIcon, null, null, null);

        Drawable imageIcon = new IconicsDrawable(this, GoogleMaterial.Icon.gmd_image).color(textColor).sizeDp(18);
        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(actionPhotoPick, imageIcon, null, null, null);

        actionPhotoTake.setOnClickListener(v -> initiateCameraApp());
        actionPhotoPick.setOnClickListener(v -> onMediaPick());

        thumbnailViewSize = getResources().getDimensionPixelSize(R.dimen.compose_media_preview_size);

        /* Initialise all the state, or restore it from a previous run, to determine a "starting"
         * state. */
        Status.Visibility startingVisibility = Status.Visibility.UNKNOWN;
        boolean startingHideText;
        ArrayList<SavedQueuedMedia> savedMediaQueued = null;
        if (savedInstanceState != null) {
            startingVisibility = Status.Visibility.byNum(
                    savedInstanceState.getInt("statusVisibility",
                            Status.Visibility.PUBLIC.getNum())
            );
            statusMarkSensitive = savedInstanceState.getBoolean("statusMarkSensitive");
            startingHideText = savedInstanceState.getBoolean("statusHideText");
            // Keep these until everything needed to put them in the queue is finished initializing.
            savedMediaQueued = savedInstanceState.getParcelableArrayList("savedMediaQueued");
            // These are for restoring an in-progress commit content operation.
            InputContentInfoCompat previousInputContentInfo = InputContentInfoCompat.wrap(
                    savedInstanceState.getParcelable("commitContentInputContentInfo"));
            int previousFlags = savedInstanceState.getInt("commitContentFlags");
            if (previousInputContentInfo != null) {
                onCommitContentInternal(previousInputContentInfo, previousFlags);
            }
            photoUploadUri = savedInstanceState.getParcelable("photoUploadUri");
        } else {
            statusMarkSensitive = activeAccount.getDefaultMediaSensitivity();
            startingHideText = false;
            photoUploadUri = null;
        }

        /* If the composer is started up as a reply to another post, override the "starting" state
         * based on what the intent from the reply request passes. */
        Intent intent = getIntent();

        String[] mentionedUsernames = null;
        ArrayList<String> loadedDraftMediaUris = null;
        inReplyToId = null;
        if (intent != null) {

            if (startingVisibility == Status.Visibility.UNKNOWN) {
                Status.Visibility preferredVisibility = activeAccount.getDefaultPostPrivacy();
                Status.Visibility replyVisibility = Status.Visibility.byNum(
                        intent.getIntExtra(REPLY_VISIBILITY_EXTRA, Status.Visibility.UNKNOWN.getNum()));

                startingVisibility = Status.Visibility.byNum(Math.max(preferredVisibility.getNum(), replyVisibility.getNum()));
            }

            inReplyToId = intent.getStringExtra(IN_REPLY_TO_ID_EXTRA);

            mentionedUsernames = intent.getStringArrayExtra(MENTIONED_USERNAMES_EXTRA);

            String contentWarning = intent.getStringExtra(CONTENT_WARNING_EXTRA);
            if (contentWarning != null) {
                startingHideText = !contentWarning.isEmpty();
                if (startingHideText) {
                    startingContentWarning = contentWarning;
                }
            }

            // If come from SavedTootActivity
            String savedTootText = intent.getStringExtra(SAVED_TOOT_TEXT_EXTRA);
            if (!TextUtils.isEmpty(savedTootText)) {
                startingText = savedTootText;
                textEditor.setText(savedTootText);
            }

            String savedJsonUrls = intent.getStringExtra(SAVED_JSON_URLS_EXTRA);
            if (!TextUtils.isEmpty(savedJsonUrls)) {
                // try to redo a list of media
                loadedDraftMediaUris = new Gson().fromJson(savedJsonUrls,
                        new TypeToken<ArrayList<String>>() {
                        }.getType());
            }

            int savedTootUid = intent.getIntExtra(SAVED_TOOT_UID_EXTRA, 0);
            if (savedTootUid != 0) {
                this.savedTootUid = savedTootUid;
            }

            int savedTootVisibility = intent.getIntExtra(SAVED_TOOT_VISIBILITY_EXTRA, Status.Visibility.UNKNOWN.getNum());
            if (savedTootVisibility != Status.Visibility.UNKNOWN.getNum()) {
                startingVisibility = Status.Visibility.byNum(savedTootVisibility);
            }

            if (intent.hasExtra(REPLYING_STATUS_AUTHOR_USERNAME_EXTRA)) {
                replyTextView.setVisibility(View.VISIBLE);
                String username = intent.getStringExtra(REPLYING_STATUS_AUTHOR_USERNAME_EXTRA);
                replyTextView.setText(getString(R.string.replying_to, username));
                Drawable arrowDownIcon = new IconicsDrawable(this, GoogleMaterial.Icon.gmd_arrow_drop_down).sizeDp(12);

                ThemeUtils.setDrawableTint(this, arrowDownIcon, android.R.attr.textColorTertiary);
                TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(replyTextView, null, null, arrowDownIcon, null);

                replyTextView.setOnClickListener(v -> {
                    TransitionManager.beginDelayedTransition((ViewGroup)replyContentTextView.getParent());

                    if (replyContentTextView.getVisibility() != View.VISIBLE) {
                        replyContentTextView.setVisibility(View.VISIBLE);
                        Drawable arrowUpIcon = new IconicsDrawable(this, GoogleMaterial.Icon.gmd_arrow_drop_up).sizeDp(12);

                        ThemeUtils.setDrawableTint(this, arrowUpIcon, android.R.attr.textColorTertiary);
                        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(replyTextView, null, null, arrowUpIcon, null);
                    } else {
                        replyContentTextView.setVisibility(View.GONE);

                        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(replyTextView, null, null, arrowDownIcon, null);
                    }
                });
            }

            if (intent.hasExtra(REPLYING_STATUS_CONTENT_EXTRA)) {
                replyContentTextView.setText(intent.getStringExtra(REPLYING_STATUS_CONTENT_EXTRA));
            }
        }

        // After the starting state is finalised, the interface can be set to reflect this state.
        setStatusVisibility(startingVisibility);

        updateHideMediaToggle();
        updateVisibleCharactersLeft();

        // Setup the main text field.
        textEditor.setOnCommitContentListener(this);
        final int mentionColour = textEditor.getLinkTextColors().getDefaultColor();
        SpanUtilsKt.highlightSpans(textEditor.getText(), mentionColour);
        textEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                SpanUtilsKt.highlightSpans(editable, mentionColour);
                updateVisibleCharactersLeft();
            }
        });

        textEditor.setAdapter(
                new MentionAutoCompleteAdapter(this, R.layout.item_autocomplete, this));
        textEditor.setTokenizer(new MentionTokenizer());

        // Add any mentions to the text field when a reply is first composed.
        if (mentionedUsernames != null) {
            StringBuilder builder = new StringBuilder();
            for (String name : mentionedUsernames) {
                builder.append('@');
                builder.append(name);
                builder.append(' ');
            }
            startingText = builder.toString();
            textEditor.setText(startingText);
            textEditor.setSelection(textEditor.length());
        }

        // work around Android platform bug -> https://issuetracker.google.com/issues/67102093
        if(Build.VERSION.SDK_INT == Build.VERSION_CODES.O || Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1 ) {
            textEditor.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        // Initialise the content warning editor.
        contentWarningEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateVisibleCharactersLeft();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        showContentWarning(startingHideText);
        if (startingContentWarning != null) {
            contentWarningEditor.setText(startingContentWarning);
        }

        // Initialise the empty media queue state.
        waitForMediaLatch = new CountUpDownLatch();

        // These can only be added after everything affected by the media queue is initialized.
        if (!ListUtils.isEmpty(loadedDraftMediaUris)) {
            for (String uriString : loadedDraftMediaUris) {
                Uri uri = Uri.parse(uriString);
                long mediaSize = getMediaSize(getContentResolver(), uri);
                pickMedia(uri, mediaSize);
            }
        } else if (savedMediaQueued != null) {
            for (SavedQueuedMedia item : savedMediaQueued) {
                Bitmap preview = getImageThumbnail(getContentResolver(), item.uri, thumbnailViewSize);
                addMediaToQueue(item.id, item.type, preview, item.uri, item.mediaSize, item.readyStage, item.description);
            }
        } else if (intent != null && savedInstanceState == null) {
            /* Get incoming images being sent through a share action from another app. Only do this
             * when savedInstanceState is null, otherwise both the images from the intent and the
             * instance state will be re-queued. */
            String type = intent.getType();
            if (type != null) {
                if (type.startsWith("image/") || type.startsWith("video/")) {
                    List<Uri> uriList = new ArrayList<>();
                    if (intent.getAction() != null) {
                        switch (intent.getAction()) {
                            case Intent.ACTION_SEND: {
                                Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                                if (uri != null) {
                                    uriList.add(uri);
                                }
                                break;
                            }
                            case Intent.ACTION_SEND_MULTIPLE: {
                                ArrayList<Uri> list = intent.getParcelableArrayListExtra(
                                        Intent.EXTRA_STREAM);
                                if (list != null) {
                                    for (Uri uri : list) {
                                        if (uri != null) {
                                            uriList.add(uri);
                                        }
                                    }
                                }
                                break;
                            }
                        }
                    }
                    for (Uri uri : uriList) {
                        long mediaSize = getMediaSize(getContentResolver(), uri);
                        pickMedia(uri, mediaSize);
                    }
                } else if (type.equals("text/plain")) {
                    String action = intent.getAction();
                    if (action != null && action.equals(Intent.ACTION_SEND)) {
                        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                        if (text != null) {
                            int start = Math.max(textEditor.getSelectionStart(), 0);
                            int end = Math.max(textEditor.getSelectionEnd(), 0);
                            int left = Math.min(start, end);
                            int right = Math.max(start, end);
                            textEditor.getText().replace(left, right, text, 0, text.length());
                        }
                    }
                }
            }
        }

        textEditor.requestFocus();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        ArrayList<SavedQueuedMedia> savedMediaQueued = new ArrayList<>();
        for (QueuedMedia item : mediaQueued) {
            savedMediaQueued.add(new SavedQueuedMedia(item.id, item.type, item.uri,
                    item.mediaSize, item.readyStage, item.description));
        }
        outState.putParcelableArrayList("savedMediaQueued", savedMediaQueued);
        outState.putBoolean("statusMarkSensitive", statusMarkSensitive);
        outState.putBoolean("statusHideText", statusHideText);
        if (currentInputContentInfo != null) {
            outState.putParcelable("commitContentInputContentInfo",
                    (Parcelable) currentInputContentInfo.unwrap());
            outState.putInt("commitContentFlags", currentFlags);
        }
        currentInputContentInfo = null;
        currentFlags = 0;
        outState.putParcelable("photoUploadUri", photoUploadUri);
        outState.putInt("statusVisibility", statusVisibility.getNum());
        super.onSaveInstanceState(outState);
    }

    private void doErrorDialog(@StringRes int descriptionId, @StringRes int actionId,
                               View.OnClickListener listener) {
        Snackbar bar = Snackbar.make(findViewById(R.id.activity_compose), getString(descriptionId),
                Snackbar.LENGTH_SHORT);
        bar.setAction(actionId, listener);
        //necessary so snackbar is shown over everything
        ViewCompat.setElevation(bar.getView(), getResources().getDimensionPixelSize(R.dimen.compose_activity_snackbar_elevation));
        bar.show();
    }

    private void displayTransientError(@StringRes int stringId) {
        Snackbar bar = Snackbar.make(findViewById(R.id.activity_compose), stringId, Snackbar.LENGTH_LONG);
        //necessary so snackbar is shown over everything
        ViewCompat.setElevation(bar.getView(), getResources().getDimensionPixelSize(R.dimen.compose_activity_snackbar_elevation));
        bar.show();
    }

    private void toggleHideMedia() {
        statusMarkSensitive = !statusMarkSensitive;
        updateHideMediaToggle();
    }

    private void updateHideMediaToggle() {
        TransitionManager.beginDelayedTransition((ViewGroup)hideMediaToggle.getParent());

        @ColorInt int color;
        if(mediaQueued.size() == 0) {
            hideMediaToggle.setVisibility(View.GONE);
        } else {
            hideMediaToggle.setVisibility(View.VISIBLE);
            if (statusMarkSensitive) {
                hideMediaToggle.setImageResource(R.drawable.ic_hide_media_24dp);
                if (statusHideText) {
                    hideMediaToggle.setClickable(false);
                    color = ContextCompat.getColor(this, R.color.compose_media_visible_button_disabled_blue);
                } else {
                    hideMediaToggle.setClickable(true);
                    color = ContextCompat.getColor(this, R.color.primary);
                }
            } else {
                hideMediaToggle.setClickable(true);
                hideMediaToggle.setImageResource(R.drawable.ic_eye_24dp);
                color = ThemeUtils.getColor(this, android.R.attr.textColorTertiary);
            }
            hideMediaToggle.getDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        }
    }

    private void disableButtons() {
        pickButton.setClickable(false);
        visibilityButton.setClickable(false);
        emojiButton.setClickable(false);
        hideMediaToggle.setClickable(false);
        tootButton.setEnabled(false);
    }

    private void enableButtons() {
        pickButton.setClickable(true);
        visibilityButton.setClickable(true);
        emojiButton.setClickable(true);
        hideMediaToggle.setClickable(true);
        tootButton.setEnabled(true);
    }

    private void setStatusVisibility(Status.Visibility visibility) {
        statusVisibility = visibility;
        composeOptionsView.setStatusVisibility(visibility);
        tootButton.setStatusVisibility(visibility);

        switch (visibility) {
            case PUBLIC: {
                Drawable globe = AppCompatResources.getDrawable(this, R.drawable.ic_public_24dp);
                if (globe != null) {
                    visibilityButton.setImageDrawable(globe);
                }
                break;
            }
            case PRIVATE: {
                Drawable lock = AppCompatResources.getDrawable(this,
                        R.drawable.ic_lock_outline_24dp);
                if (lock != null) {
                    visibilityButton.setImageDrawable(lock);
                }
                break;
            }
            case DIRECT: {
                Drawable envelope = AppCompatResources.getDrawable(this, R.drawable.ic_email_24dp);
                if (envelope != null) {
                    visibilityButton.setImageDrawable(envelope);
                }
                break;
            }
            case UNLISTED:
            default: {
                Drawable openLock = AppCompatResources.getDrawable(this, R.drawable.ic_lock_open_24dp);
                if (openLock != null) {
                    visibilityButton.setImageDrawable(openLock);
                }
                break;
            }
        }
    }

    private void showComposeOptions() {
        if (composeOptionsBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN || composeOptionsBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
            composeOptionsBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            addMediaBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            emojiBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        } else {
            composeOptionsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        }
    }

    private void showEmojis() {

        if(emojiView.getAdapter() != null) {
            if(emojiView.getAdapter().getItemCount() == 0) {
                String errorMessage = getString(R.string.error_no_custom_emojis, accountManager.getActiveAccount().getDomain());
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
            } else {
                if (emojiBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN || emojiBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
                    emojiBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                    composeOptionsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                    addMediaBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

                } else {
                    emojiBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                }
            }

        }

    }

    private void openPickDialog() {
        if (addMediaBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN || addMediaBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
            addMediaBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            composeOptionsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            emojiBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        } else {
            addMediaBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        }

    }

    private void onMediaPick() {
        addMediaBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
        } else {
            initiateMediaPicking();
        }
    }

    @Override
    public void onVisibilityChanged(@NonNull Status.Visibility visibility) {
        composeOptionsBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        setStatusVisibility(visibility);
    }

    int calculateTextLength() {
        int offset = 0;
        URLSpan[] urlSpans = textEditor.getUrls();
        if (urlSpans != null) {
            for (URLSpan span : urlSpans) {
                offset += Math.max(0, span.getURL().length() - MAXIMUM_URL_LENGTH);
            }
        }
        int length = textEditor.length() - offset;
        if (statusHideText) {
            length += contentWarningEditor.length();
        }
        return length;
    }

    private void updateVisibleCharactersLeft() {
        this.charactersLeft.setText(String.format(Locale.getDefault(), "%d", maximumTootCharacters - calculateTextLength()));
    }

    private void onContentWarningChanged() {
        boolean showWarning = contentWarningBar.getVisibility() != View.VISIBLE;
        showContentWarning(showWarning);
        updateVisibleCharactersLeft();
    }

    private void onSendClicked() {
        disableButtons();
        readyStatus(statusVisibility, statusMarkSensitive);
    }

    @Override
    public boolean onCommitContent(InputContentInfoCompat inputContentInfo, int flags, Bundle opts) {
        try {
            if (currentInputContentInfo != null) {
                currentInputContentInfo.releasePermission();
            }
        } catch (Exception e) {
            Log.e(TAG, "InputContentInfoCompat#releasePermission() failed." + e.getMessage());
        } finally {
            currentInputContentInfo = null;
        }

        // Verify the returned content's type is of the correct MIME type
        boolean supported = inputContentInfo.getDescription().hasMimeType("image/*");

        return supported && onCommitContentInternal(inputContentInfo, flags);
    }

    private boolean onCommitContentInternal(InputContentInfoCompat inputContentInfo, int flags) {
        if ((flags & InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
            try {
                inputContentInfo.requestPermission();
            } catch (Exception e) {
                Log.e(TAG, "InputContentInfoCompat#requestPermission() failed." + e.getMessage());
                return false;
            }
        }

        // Determine the file size before putting handing it off to be put in the queue.
        Uri uri = inputContentInfo.getContentUri();
        long mediaSize;
        AssetFileDescriptor descriptor = null;
        try {
            descriptor = getContentResolver().openAssetFileDescriptor(uri, "r");
        } catch (FileNotFoundException e) {
            Log.d(TAG, Log.getStackTraceString(e));
            // Eat this exception, having the descriptor be null is sufficient.
        }
        if (descriptor != null) {
            mediaSize = descriptor.getLength();
            try {
                descriptor.close();
            } catch (IOException e) {
                // Just eat this exception.
            }
        } else {
            mediaSize = MEDIA_SIZE_UNKNOWN;
        }
        pickMedia(uri, mediaSize);

        currentInputContentInfo = inputContentInfo;
        currentFlags = flags;

        return true;
    }

    private void sendStatus(String content, Status.Visibility visibility, boolean sensitive,
                            String spoilerText) {
        ArrayList<String> mediaIds = new ArrayList<>();
        ArrayList<Uri> mediaUris = new ArrayList<>();
        for (QueuedMedia item : mediaQueued) {
            mediaIds.add(item.id);
            mediaUris.add(item.uri);
        }

        Intent sendIntent = SendTootService.sendTootIntent(this, content, spoilerText,
                visibility, sensitive, mediaIds, mediaUris, inReplyToId,
                getIntent().getStringExtra(REPLYING_STATUS_CONTENT_EXTRA),
                getIntent().getStringExtra(REPLYING_STATUS_AUTHOR_USERNAME_EXTRA),
                getIntent().getStringExtra(SAVED_JSON_URLS_EXTRA),
                accountManager.getActiveAccount(), savedTootUid);

        startService(sendIntent);

        finishWithoutSlideOutAnimation();

    }

    private void readyStatus(final Status.Visibility visibility, final boolean sensitive) {
        finishingUploadDialog = ProgressDialog.show(
                this, getString(R.string.dialog_title_finishing_media_upload),
                getString(R.string.dialog_message_uploading_media), true, true);
        @SuppressLint("StaticFieldLeak") final AsyncTask<Void, Void, Boolean> waitForMediaTask =
                new AsyncTask<Void, Void, Boolean>() {
                    @Override
                    protected Boolean doInBackground(Void... params) {
                        try {
                            waitForMediaLatch.await();
                        } catch (InterruptedException e) {
                            return false;
                        }
                        return true;
                    }

                    @Override
                    protected void onPostExecute(Boolean successful) {
                        super.onPostExecute(successful);
                        finishingUploadDialog.dismiss();
                        finishingUploadDialog = null;
                        if (successful) {
                            onReadySuccess(visibility, sensitive);
                        } else {
                            onReadyFailure(visibility, sensitive);
                        }
                    }

                    @Override
                    protected void onCancelled() {
                        removeAllMediaFromQueue();
                        enableButtons();
                        super.onCancelled();
                    }
                };
        finishingUploadDialog.setOnCancelListener(dialog -> {
            /* Generating an interrupt by passing true here is important because an interrupt
             * exception is the only thing that will kick the latch out of its waiting loop
             * early. */
            waitForMediaTask.cancel(true);
        });
        waitForMediaTask.execute();
    }

    private void onReadySuccess(Status.Visibility visibility, boolean sensitive) {
        /* Validate the status meets the character limit. */
        String contentText = textEditor.getText().toString();
        String spoilerText = "";
        if (statusHideText) {
            spoilerText = contentWarningEditor.getText().toString();
        }
        int characterCount = calculateTextLength();
        if (characterCount <= 0 && mediaQueued.size()==0) {
            textEditor.setError(getString(R.string.error_empty));
            enableButtons();
        } else if (characterCount <= maximumTootCharacters) {
            sendStatus(contentText, visibility, sensitive, spoilerText);

        } else {
            textEditor.setError(getString(R.string.error_compose_character_limit));
            enableButtons();
        }
    }

    private void onReadyFailure(final Status.Visibility visibility, final boolean sensitive) {
        doErrorDialog(R.string.error_media_upload_sending, R.string.action_retry,
                v -> readyStatus(visibility, sensitive));
        enableButtons();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initiateMediaPicking();
                } else {
                    doErrorDialog(R.string.error_media_upload_permission, R.string.action_retry,
                            v -> onMediaPick());
                }
                break;
            }
        }
    }

    @NonNull
    private File createNewImageFile() throws IOException {
        // Create an image file name
        String randomId = StringUtils.randomAlphanumericString(12);
        String imageFileName = "Tusky_" + randomId + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
    }

    private void initiateCameraApp() {
        addMediaBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        // We don't need to ask for permission in this case, because the used calls require
        // android.permission.WRITE_EXTERNAL_STORAGE only on SDKs *older* than Kitkat, which was
        // way before permission dialogues have been introduced.
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createNewImageFile();
            } catch (IOException ex) {
                displayTransientError(R.string.error_media_upload_opening);
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                photoUploadUri = FileProvider.getUriForFile(this,
                        BuildConfig.APPLICATION_ID+".fileprovider",
                        photoFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUploadUri);
                startActivityForResult(intent, MEDIA_TAKE_PHOTO_RESULT);
            }
        }
    }

    private void initiateMediaPicking() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        String[] mimeTypes = new String[]{"image/*", "video/*"};
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, MEDIA_PICK_RESULT);
    }

    private void enableButton(ImageButton button, boolean clickable, boolean colorActive) {
        button.setEnabled(clickable);
        ThemeUtils.setDrawableTint(this, button.getDrawable(),
                colorActive ? android.R.attr.textColorTertiary : R.attr.compose_media_button_disabled_tint);
    }

    private void addMediaToQueue(QueuedMedia.Type type, Bitmap preview, Uri uri, long mediaSize) {
        addMediaToQueue(null, type, preview, uri, mediaSize, null, null);
    }

    private void addMediaToQueue(@Nullable String id, QueuedMedia.Type type, Bitmap preview, Uri uri,
                                 long mediaSize, QueuedMedia.ReadyStage readyStage, @Nullable String description) {
        final QueuedMedia item = new QueuedMedia(type, uri, new ProgressImageView(this),
                mediaSize, description);
        item.id = id;
        item.readyStage = readyStage;
        ImageView view = item.preview;
        Resources resources = getResources();
        int margin = resources.getDimensionPixelSize(R.dimen.compose_media_preview_margin);
        int marginBottom = resources.getDimensionPixelSize(
                R.dimen.compose_media_preview_margin_bottom);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(thumbnailViewSize, thumbnailViewSize);
        layoutParams.setMargins(margin, 0, margin, marginBottom);
        view.setLayoutParams(layoutParams);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setImageBitmap(preview);
        view.setOnClickListener(v -> onMediaClick(item, v));
        view.setContentDescription(getString(R.string.action_delete));
        mediaPreviewBar.addView(view);
        mediaQueued.add(item);
        int queuedCount = mediaQueued.size();
        if (queuedCount == 1) {
            // If there's one video in the queue it is full, so disable the button to queue more.
            if (item.type == QueuedMedia.Type.VIDEO) {
                enableButton(pickButton, false, false);
            }
        } else if (queuedCount >= Status.MAX_MEDIA_ATTACHMENTS) {
            // Limit the total media attachments, also.
            enableButton(pickButton, false, false);
        }

        updateHideMediaToggle();

        if (item.readyStage != QueuedMedia.ReadyStage.UPLOADED) {
            waitForMediaLatch.countUp();

            try {
                if (type == QueuedMedia.Type.IMAGE &&
                        (mediaSize > STATUS_IMAGE_SIZE_LIMIT || getImageSquarePixels(getContentResolver(), item.uri) > STATUS_IMAGE_PIXEL_SIZE_LIMIT)) {
                    downsizeMedia(item);
                } else {
                    uploadMedia(item);
                }
            } catch (IOException e) {
                onUploadFailure(item, false);
            }
        }
    }

    private void onMediaClick(QueuedMedia item, View view) {
        PopupMenu popup = new PopupMenu(this, view);
        final int addCaptionId = 1;
        final int removeId = 2;
        popup.getMenu().add(0, addCaptionId, 0, R.string.action_set_caption);
        popup.getMenu().add(0, removeId, 0, R.string.action_remove_media);
        popup.setOnMenuItemClickListener(menuItem -> {
            switch (menuItem.getItemId()) {
                case addCaptionId:
                    makeCaptionDialog(item);
                    break;
                case removeId:
                    removeMediaFromQueue(item);
                    break;
            }
            return true;
        });
        popup.show();
    }

    private void makeCaptionDialog(QueuedMedia item) {
        LinearLayout dialogLayout = new LinearLayout(this);
        int padding = Utils.dpToPx(this, 8);
        dialogLayout.setPadding(padding, padding, padding, padding);

        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        ImageView imageView = new ImageView(this);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        Single.fromCallable(() ->
                getSampledBitmap(getContentResolver(), item.uri, displayMetrics.widthPixels, displayMetrics.heightPixels))
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .as(autoDisposable(from(this, Lifecycle.Event.ON_DESTROY)))
                .subscribe(new SingleObserver<Bitmap>() {
                    @Override
                    public void onSubscribe(Disposable d) {}

                    @Override
                    public void onSuccess(Bitmap bitmap) {
                        imageView.setImageBitmap(bitmap);
                    }

                    @Override
                    public void onError(Throwable e) { }
                });


        int margin = Utils.dpToPx(this, 4);
        dialogLayout.addView(imageView);
        ((LinearLayout.LayoutParams) imageView.getLayoutParams()).weight = 1;
        imageView.getLayoutParams().height = 0;
        ((LinearLayout.LayoutParams) imageView.getLayoutParams()).setMargins(0, margin, 0, 0);

        EditText input = new EditText(this);
        input.setHint(R.string.hint_describe_for_visually_impaired);
        dialogLayout.addView(input);
        ((LinearLayout.LayoutParams) input.getLayoutParams()).setMargins(margin, margin, margin, margin);
        input.setLines(1);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(item.description);

        DialogInterface.OnClickListener okListener = (dialog, which) -> {
            mastodonApi.updateMedia(item.id, input.getText().toString())
                    .enqueue(new Callback<Attachment>() {
                        @Override
                        public void onResponse(@NonNull Call<Attachment> call, @NonNull Response<Attachment> response) {
                            Attachment attachment = response.body();
                            if (response.isSuccessful() && attachment != null) {
                                item.description = attachment.getDescription();
                                item.preview.setChecked(item.description != null && !item.description.isEmpty());
                                dialog.dismiss();
                            } else {
                                showFailedCaptionMessage();
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<Attachment> call, @NonNull Throwable t) {
                            showFailedCaptionMessage();
                        }
                    });
        };

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogLayout)
                .setPositiveButton(android.R.string.ok, okListener)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        dialog.show();
    }

    private void showFailedCaptionMessage() {
        Toast.makeText(this, R.string.error_failed_set_caption, Toast.LENGTH_SHORT).show();
    }

    private void removeMediaFromQueue(QueuedMedia item) {
        mediaPreviewBar.removeView(item.preview);
        mediaQueued.remove(item);
        if (mediaQueued.size() == 0) {
            updateHideMediaToggle();
        }

        enableButton(pickButton, true, true);
        cancelReadyingMedia(item);
    }

    private void removeAllMediaFromQueue() {
        for (Iterator<QueuedMedia> it = mediaQueued.iterator(); it.hasNext(); ) {
            QueuedMedia item = it.next();
            it.remove();
            removeMediaFromQueue(item);
        }
    }

    private void downsizeMedia(final QueuedMedia item) throws IOException {
        item.readyStage = QueuedMedia.ReadyStage.DOWNSIZING;

        new DownsizeImageTask(STATUS_IMAGE_SIZE_LIMIT, getContentResolver(), createNewImageFile(),
                new DownsizeImageTask.Listener() {
                    @Override
                    public void onSuccess(File tempFile) {
                        item.uri = FileProvider.getUriForFile(
                                ComposeActivity.this,
                                BuildConfig.APPLICATION_ID+".fileprovider",
                                tempFile);
                        uploadMedia(item);
                    }

                    @Override
                    public void onFailure() {
                        onMediaDownsizeFailure(item);
                    }
                }).execute(item.uri);
    }

    private void onMediaDownsizeFailure(QueuedMedia item) {
        displayTransientError(R.string.error_image_upload_size);
        removeMediaFromQueue(item);
    }

    private void uploadMedia(final QueuedMedia item) {
        item.readyStage = QueuedMedia.ReadyStage.UPLOADING;

        String mimeType = getContentResolver().getType(item.uri);
        MimeTypeMap map = MimeTypeMap.getSingleton();
        String fileExtension = map.getExtensionFromMimeType(mimeType);
        final String filename = String.format("%s_%s_%s.%s",
                getString(R.string.app_name),
                String.valueOf(new Date().getTime()),
                StringUtils.randomAlphanumericString(10),
                fileExtension);

        InputStream stream;

        try {
            stream = getContentResolver().openInputStream(item.uri);
        } catch (FileNotFoundException e) {
            Log.w(TAG, e);
            return;
        }

        if (mimeType == null) mimeType = "multipart/form-data";

        item.preview.setProgress(0);

        ProgressRequestBody fileBody = new ProgressRequestBody(stream, getMediaSize(getContentResolver(), item.uri), MediaType.parse(mimeType),
                new ProgressRequestBody.UploadCallback() { // may reference activity longer than I would like to
                    int lastProgress = -1;

                    @Override
                    public void onProgressUpdate(final int percentage) {
                        if (percentage != lastProgress) {
                            runOnUiThread(() -> item.preview.setProgress(percentage));
                        }
                        lastProgress = percentage;
                    }
                });

        MultipartBody.Part body = MultipartBody.Part.createFormData("file", filename, fileBody);

        item.uploadRequest = mastodonApi.uploadMedia(body);

        item.uploadRequest.enqueue(new Callback<Attachment>() {
            @Override
            public void onResponse(@NonNull Call<Attachment> call, @NonNull retrofit2.Response<Attachment> response) {
                if (response.isSuccessful()) {
                    onUploadSuccess(item, response.body());
                } else {
                    Log.d(TAG, "Upload request failed. " + response.message());
                    onUploadFailure(item, call.isCanceled());
                }
            }

            @Override
            public void onFailure(@NonNull Call<Attachment> call, @NonNull Throwable t) {
                Log.d(TAG, "Upload request failed. " + t.getMessage());
                onUploadFailure(item, call.isCanceled());
            }
        });
    }

    private void onUploadSuccess(final QueuedMedia item, Attachment media) {
        item.id = media.getId();
        item.preview.setProgress(-1);
        item.readyStage = QueuedMedia.ReadyStage.UPLOADED;

        waitForMediaLatch.countDown();
    }

    private void onUploadFailure(QueuedMedia item, boolean isCanceled) {
        if (!isCanceled) {
            /* if the upload was voluntarily cancelled, such as if the user clicked on it to remove
             * it from the queue, then don't display this error message. */
            displayTransientError(R.string.error_media_upload_sending);
        }
        if (finishingUploadDialog != null && finishingUploadDialog.isShowing()) {
            finishingUploadDialog.cancel();
        }
        if (!isCanceled) {
            // If it is canceled, it's already been removed, otherwise do it.
            removeMediaFromQueue(item);
        }
    }

    private void cancelReadyingMedia(QueuedMedia item) {
        if (item.readyStage == QueuedMedia.ReadyStage.UPLOADING) {
            item.uploadRequest.cancel();
        }
        if (item.id == null) {
            /* The presence of an upload id is used to detect if it finished uploading or not, to
             * prevent counting down twice on the same media item. */
            waitForMediaLatch.countDown();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (resultCode == RESULT_OK && requestCode == MEDIA_PICK_RESULT && intent != null) {
            Uri uri = intent.getData();
            long mediaSize = getMediaSize(getContentResolver(), uri);
            pickMedia(uri, mediaSize);
        } else if (resultCode == RESULT_OK && requestCode == MEDIA_TAKE_PHOTO_RESULT) {
            long mediaSize = getMediaSize(getContentResolver(), photoUploadUri);
            pickMedia(photoUploadUri, mediaSize);
        }
    }


    private void pickMedia(Uri uri, long mediaSize) {
        if (mediaSize == MEDIA_SIZE_UNKNOWN) {
            displayTransientError(R.string.error_media_upload_opening);
            return;
        }
        ContentResolver contentResolver = getContentResolver();
        String mimeType = contentResolver.getType(uri);
        if (mimeType != null) {
            String topLevelType = mimeType.substring(0, mimeType.indexOf('/'));
            switch (topLevelType) {
                case "video": {
                    if (mediaSize > STATUS_VIDEO_SIZE_LIMIT) {
                        displayTransientError(R.string.error_image_upload_size);
                        return;
                    }
                    if (mediaQueued.size() > 0
                            && mediaQueued.get(0).type == QueuedMedia.Type.IMAGE) {
                        displayTransientError(R.string.error_media_upload_image_or_video);
                        return;
                    }
                    Bitmap bitmap = getVideoThumbnail(this, uri, thumbnailViewSize);
                    if (bitmap != null) {
                        addMediaToQueue(QueuedMedia.Type.VIDEO, bitmap, uri, mediaSize);
                    } else {
                        displayTransientError(R.string.error_media_upload_opening);
                    }
                    break;
                }
                case "image": {
                    Bitmap bitmap = getImageThumbnail(contentResolver, uri, thumbnailViewSize);
                    if (bitmap != null) {
                        addMediaToQueue(QueuedMedia.Type.IMAGE, bitmap, uri, mediaSize);
                    } else {
                        displayTransientError(R.string.error_media_upload_opening);
                    }
                    break;
                }
                default: {
                    displayTransientError(R.string.error_media_upload_type);
                    break;
                }
            }
        } else {
            displayTransientError(R.string.error_media_upload_type);
        }
    }

    private void showContentWarning(boolean show) {
        statusHideText = show;
        TransitionManager.beginDelayedTransition((ViewGroup)contentWarningBar.getParent());
        if (show) {
            statusMarkSensitive = true;
            contentWarningBar.setVisibility(View.VISIBLE);
            contentWarningButton.setTextColor(ContextCompat.getColor(this, R.color.primary));
            contentWarningEditor.setSelection(contentWarningEditor.getText().length());
            contentWarningEditor.requestFocus();
        } else {
            contentWarningBar.setVisibility(View.GONE);
            contentWarningButton.setTextColor(ThemeUtils.getColor(this, android.R.attr.textColorTertiary));
        }
        updateHideMediaToggle();

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                handleCloseButton();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // Acting like a teen: deliberately ignoring parent.
        handleCloseButton();
    }

    private void handleCloseButton() {
        CharSequence contentText = textEditor.getText();
        CharSequence contentWarning = contentWarningEditor.getText();

        boolean textChanged = !(TextUtils.isEmpty(contentText) || startingText.startsWith(contentText.toString()));
        boolean contentWarningChanged = contentWarningBar.getVisibility() == View.VISIBLE &&
                !TextUtils.isEmpty(contentWarning) && !startingContentWarning.startsWith(contentWarning.toString());
        boolean mediaChanged = !mediaQueued.isEmpty();

        if (textChanged || contentWarningChanged || mediaChanged) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.compose_save_draft)
                    .setPositiveButton(R.string.action_save, (d, w) -> saveDraftAndFinish())
                    .setNegativeButton(R.string.action_delete, (d, w) -> finishWithoutSlideOutAnimation())
                    .show();
        } else {
            finishWithoutSlideOutAnimation();
        }
    }

    private void saveDraftAndFinish() {
        ArrayList<String> mediaUris = new ArrayList<>();
        for (QueuedMedia item : mediaQueued) {
            mediaUris.add(item.uri.toString());
        }

        saveTootHelper.saveToot(textEditor.getText().toString(),
                contentWarningEditor.getText().toString(),
                getIntent().getStringExtra("saved_json_urls"),
                mediaUris,
                savedTootUid,
                inReplyToId,
                getIntent().getStringExtra(REPLYING_STATUS_CONTENT_EXTRA),
                getIntent().getStringExtra(REPLYING_STATUS_AUTHOR_USERNAME_EXTRA),
                statusVisibility);
        finishWithoutSlideOutAnimation();
    }

    @Override
    public List<Account> searchAccounts(String mention) {
        ArrayList<Account> resultList = new ArrayList<>();
        try {
            List<Account> accountList = mastodonApi.searchAccounts(mention, false, 40)
                    .execute()
                    .body();
            if (accountList != null) {
                resultList.addAll(accountList);
            }
        } catch (IOException e) {
            Log.e(TAG, String.format("Autocomplete search for %s failed.", mention));
        }
        return resultList;
    }

    @Override
    public void onEmojiSelected(@NotNull String shortcode) {
        textEditor.getText().insert(textEditor.getSelectionStart(), ":"+shortcode+": ");
    }

    private void loadCachedInstanceMetadata(@NotNull AccountEntity activeAccount) {
        InstanceEntity instanceEntity = database.instanceDao()
                .loadMetadataForInstance(activeAccount.getDomain());

        if(instanceEntity != null) {
            Integer max = instanceEntity.getMaximumTootCharacters();
            maximumTootCharacters = (max == null ? STATUS_CHARACTER_LIMIT : max);
            emojiList = instanceEntity.getEmojiList();
            setEmojiList(emojiList);
            updateVisibleCharactersLeft();
        }
    }

    private void setEmojiList(@Nullable List<Emoji> emojiList) {
        if (emojiList != null) {
            emojiView.setAdapter(new EmojiAdapter(emojiList, ComposeActivity.this));
            enableButton(emojiButton, true, emojiList.size() > 0);
        }
    }

    private void cacheInstanceMetadata(@NotNull AccountEntity activeAccount) {
        InstanceEntity instanceEntity = new InstanceEntity(activeAccount.getDomain(), emojiList, maximumTootCharacters);
        database.instanceDao().insertOrReplace(instanceEntity);
    }

    // Accessors for testing, hence package scope
    int getMaximumTootCharacters()
    {
        return maximumTootCharacters;
    }

    public static final class QueuedMedia {
        Type type;
        ProgressImageView preview;
        Uri uri;
        String id;
        Call<Attachment> uploadRequest;
        ReadyStage readyStage;
        long mediaSize;
        String description;

        QueuedMedia(Type type, Uri uri, ProgressImageView preview, long mediaSize,
                    String description) {
            this.type = type;
            this.uri = uri;
            this.preview = preview;
            this.mediaSize = mediaSize;
            this.description = description;
        }

        public enum Type {
            IMAGE,
            VIDEO
        }

        enum ReadyStage {
            DOWNSIZING,
            UPLOADING,
            UPLOADED
        }
    }

    /**
     * This saves enough information to re-enqueue an attachment when restoring the activity.
     */
    private static class SavedQueuedMedia implements Parcelable {
        public static final Parcelable.Creator<SavedQueuedMedia> CREATOR
                = new Parcelable.Creator<SavedQueuedMedia>() {
            public SavedQueuedMedia createFromParcel(Parcel in) {
                return new SavedQueuedMedia(in);
            }

            public SavedQueuedMedia[] newArray(int size) {
                return new SavedQueuedMedia[size];
            }
        };
        String id;
        QueuedMedia.Type type;
        Uri uri;
        long mediaSize;
        QueuedMedia.ReadyStage readyStage;
        String description;

        SavedQueuedMedia(String id, QueuedMedia.Type type, Uri uri, long mediaSize, QueuedMedia.ReadyStage readyStage, String description) {
            this.id = id;
            this.type = type;
            this.uri = uri;
            this.mediaSize = mediaSize;
            this.readyStage = readyStage;
            this.description = description;
        }

        SavedQueuedMedia(Parcel parcel) {
            id = parcel.readString();
            type = (QueuedMedia.Type) parcel.readSerializable();
            uri = parcel.readParcelable(Uri.class.getClassLoader());
            mediaSize = parcel.readLong();
            readyStage = QueuedMedia.ReadyStage.valueOf(parcel.readString());
            description = parcel.readString();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(id);
            dest.writeSerializable(type);
            dest.writeParcelable(uri, flags);
            dest.writeLong(mediaSize);
            dest.writeString(readyStage.name());
            dest.writeString(description);
        }
    }

    public static final class IntentBuilder {
        @Nullable
        private Integer savedTootUid;
        @Nullable
        private String savedTootText;
        @Nullable
        private String savedJsonUrls;
        @Nullable
        private Collection<String> mentionedUsernames;
        @Nullable
        private String inReplyToId;
        @Nullable
        private Status.Visibility replyVisibility;
        @Nullable
        private Status.Visibility savedVisibility;
        @Nullable
        private String contentWarning;
        @Nullable
        private String replyingStatusAuthor;
        @Nullable
        private String replyingStatusContent;

        public IntentBuilder savedTootUid(int uid) {
            this.savedTootUid = uid;
            return this;
        }

        public IntentBuilder savedTootText(String savedTootText) {
            this.savedTootText = savedTootText;
            return this;
        }

        public IntentBuilder savedJsonUrls(String jsonUrls) {
            this.savedJsonUrls = jsonUrls;
            return this;
        }

        public IntentBuilder savedVisibility(Status.Visibility savedVisibility) {
            this.savedVisibility = savedVisibility;
            return this;
        }

        public IntentBuilder mentionedUsernames(Collection<String> mentionedUsernames) {
            this.mentionedUsernames = mentionedUsernames;
            return this;
        }

        public IntentBuilder inReplyToId(String inReplyToId) {
            this.inReplyToId = inReplyToId;
            return this;
        }

        public IntentBuilder replyVisibility(Status.Visibility replyVisibility) {
            this.replyVisibility = replyVisibility;
            return this;
        }

        public IntentBuilder contentWarning(String contentWarning) {
            this.contentWarning = contentWarning;
            return this;
        }

        public IntentBuilder repyingStatusAuthor(String username) {
            this.replyingStatusAuthor = username;
            return this;
        }

        public IntentBuilder replyingStatusContent(String content) {
            this.replyingStatusContent = content;
            return this;
        }

        public Intent build(Context context) {
            Intent intent = new Intent(context, ComposeActivity.class);

            if (savedTootUid != null) {
                intent.putExtra(SAVED_TOOT_UID_EXTRA, (int) savedTootUid);
            }
            if (savedTootText != null) {
                intent.putExtra(SAVED_TOOT_TEXT_EXTRA, savedTootText);
            }
            if (savedJsonUrls != null) {
                intent.putExtra(SAVED_JSON_URLS_EXTRA, savedJsonUrls);
            }
            if (mentionedUsernames != null) {
                String[] usernames = mentionedUsernames.toArray(new String[0]);
                intent.putExtra(MENTIONED_USERNAMES_EXTRA, usernames);
            }
            if (inReplyToId != null) {
                intent.putExtra(IN_REPLY_TO_ID_EXTRA, inReplyToId);
            }
            if (replyVisibility != null) {
                intent.putExtra(REPLY_VISIBILITY_EXTRA, replyVisibility.getNum());
            }
            if (savedVisibility != null) {
                intent.putExtra(SAVED_TOOT_VISIBILITY_EXTRA, savedVisibility.getNum());
            }
            if (contentWarning != null) {
                intent.putExtra(CONTENT_WARNING_EXTRA, contentWarning);
            }
            if (replyingStatusContent != null) {
                intent.putExtra(REPLYING_STATUS_CONTENT_EXTRA, replyingStatusContent);
            }
            if (replyingStatusAuthor != null) {
                intent.putExtra(REPLYING_STATUS_AUTHOR_USERNAME_EXTRA, replyingStatusAuthor);
            }
            return intent;
        }
    }
}
