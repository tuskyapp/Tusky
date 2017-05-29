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
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v13.view.inputmethod.InputConnectionCompat;
import android.support.v13.view.inputmethod.InputContentInfoCompat;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.ActionBar;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.keylesspalace.tusky.entity.Media;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.fragment.ComposeOptionsFragment;
import com.keylesspalace.tusky.util.CountUpDownLatch;
import com.keylesspalace.tusky.util.DownsizeImageTask;
import com.keylesspalace.tusky.util.IOUtils;
import com.keylesspalace.tusky.util.MediaUtils;
import com.keylesspalace.tusky.util.ParserUtils;
import com.keylesspalace.tusky.util.SpanUtils;
import com.keylesspalace.tusky.util.ThemeUtils;
import com.keylesspalace.tusky.view.EditTextTyped;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.keylesspalace.tusky.util.MediaUtils.MEDIA_SIZE_UNKNOWN;
import static com.keylesspalace.tusky.util.MediaUtils.getMediaSize;
import static com.keylesspalace.tusky.util.MediaUtils.inputStreamGetBytes;
import static com.keylesspalace.tusky.util.StringUtils.carriageReturn;
import static com.keylesspalace.tusky.util.StringUtils.randomAlphanumericString;

public class ComposeActivity extends BaseActivity implements ComposeOptionsFragment.Listener, ParserUtils.ParserListener {
    private static final String TAG = "ComposeActivity"; // logging tag
    private static final int STATUS_CHARACTER_LIMIT = 500;
    private static final int STATUS_MEDIA_SIZE_LIMIT = 4000000; // 4MB
    private static final int MEDIA_PICK_RESULT = 1;
    private static final int MEDIA_TAKE_PHOTO_RESULT = 2;
    private static final int PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;
    private static final int COMPOSE_SUCCESS = -1;
    private static final int THUMBNAIL_SIZE = 128; // pixels
    @BindView(R.id.compose_edit_field)
    EditTextTyped textEditor;
    @BindView(R.id.compose_media_preview_bar)
    LinearLayout mediaPreviewBar;
    @BindView(R.id.compose_content_warning_bar)
    View contentWarningBar;
    @BindView(R.id.field_content_warning)
    EditText contentWarningEditor;
    @BindView(R.id.characters_left)
    TextView charactersLeft;
    @BindView(R.id.floating_btn)
    Button floatingBtn;
    @BindView(R.id.compose_photo_pick)
    ImageButton pickBtn;
    @BindView(R.id.compose_photo_take)
    ImageButton takeBtn;
    @BindView(R.id.action_toggle_nsfw)
    Button nsfwBtn;
    @BindView(R.id.postProgress)
    ProgressBar postProgress;
    @BindView(R.id.action_toggle_visibility)
    ImageButton visibilityBtn;
    private String inReplyToId;
    private ArrayList<QueuedMedia> mediaQueued;
    private CountUpDownLatch waitForMediaLatch;
    private boolean showMarkSensitive;
    private String statusVisibility;     // The current values of the options that will be applied
    private boolean statusMarkSensitive; // to the status being composed.
    private boolean statusHideText;      //
    private boolean statusAlreadyInFlight; // to prevent duplicate sends by mashing the send button
    private InputContentInfoCompat currentInputContentInfo;
    private int currentFlags;
    private Uri photoUploadUri;
    // this only exists when a status is trying to be sent, but uploads are still occurring
    private ProgressDialog finishingUploadDialog;

    /**
     * The Target object must be stored as a member field or method and cannot be an anonymous class otherwise this won't work as expected. The reason is that Picasso accepts this parameter as a weak memory reference. Because anonymous classes are eligible for garbage collection when there are no more references, the network request to fetch the image may finish after this anonymous class has already been reclaimed. See this Stack Overflow discussion for more details.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private Target target;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compose);
        ButterKnife.bind(this);

        // Setup the toolbar.
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
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

        // Setup the interface buttons.
        floatingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSendClicked();
            }
        });
        pickBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onMediaPick();
            }
        });
        takeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                initiateCameraApp();
            }
        });
        nsfwBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleNsfw();
            }
        });
        visibilityBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showComposeOptions();
            }
        });

        /* Initialise all the state, or restore it from a previous run, to determine a "starting"
         * state. */
        SharedPreferences preferences = getPrivatePreferences();

        String startingVisibility;
        boolean startingHideText;
        String startingContentWarning = null;
        ArrayList<SavedQueuedMedia> savedMediaQueued = null;
        if (savedInstanceState != null) {
            showMarkSensitive = savedInstanceState.getBoolean("showMarkSensitive");
            startingVisibility = savedInstanceState.getString("statusVisibility");
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
        } else {
            showMarkSensitive = false;
            startingVisibility = preferences.getString("rememberedVisibility", "public");
            statusMarkSensitive = false;
            startingHideText = false;
        }

        /* If the composer is started up as a reply to another post, override the "starting" state
         * based on what the intent from the reply request passes. */
        Intent intent = getIntent();

        String[] mentionedUsernames = null;
        inReplyToId = null;
        if (intent != null) {
            inReplyToId = intent.getStringExtra("in_reply_to_id");
            String replyVisibility = intent.getStringExtra("reply_visibility");

            if (replyVisibility != null && startingVisibility != null) {
                // Lowest possible visibility setting in response
                if (startingVisibility.equals("direct") || replyVisibility.equals("direct")) {
                    startingVisibility = "direct";
                } else if (startingVisibility.equals("private") || replyVisibility.equals("private")) {
                    startingVisibility = "private";
                } else if (startingVisibility.equals("unlisted") || replyVisibility.equals("unlisted")) {
                    startingVisibility = "unlisted";
                } else {
                    startingVisibility = replyVisibility;
                }
            }

            mentionedUsernames = intent.getStringArrayExtra("mentioned_usernames");

            if (inReplyToId != null) {
                startingHideText = !intent.getStringExtra("content_warning").equals("");
                if (startingHideText) {
                    startingContentWarning = intent.getStringExtra("content_warning");
                }
            }
        }

        /* If the currently logged in account is locked, its posts should default to private. This
         * should override even the reply settings, so this must be done after those are set up. */
        if (preferences.getBoolean("loggedInAccountLocked", false)) {
            startingVisibility = "private";
        }

        // After the starting state is finalised, the interface can be set to reflect this state.
        setStatusVisibility(startingVisibility);
        postProgress.setVisibility(View.INVISIBLE);
        updateNsfwButtonColor();

        final ParserUtils parser = new ParserUtils(this);

        // Setup the main text field.
        setEditTextMimeTypes(null); // new String[] { "image/gif", "image/webp" }
        final int mentionColour = ThemeUtils.getColor(this, R.attr.compose_mention_color);
        SpanUtils.highlightSpans(textEditor.getText(), mentionColour);
        textEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateVisibleCharactersLeft();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                SpanUtils.highlightSpans(editable, mentionColour);
            }
        });

        textEditor.addOnPasteListener(new EditTextTyped.OnPasteListener() {
            @Override
            public void onPaste() {
                parser.getPastedURLText(ComposeActivity.this);
            }
        });

        // Add any mentions to the text field when a reply is first composed.
        if (mentionedUsernames != null) {
            StringBuilder builder = new StringBuilder();
            for (String name : mentionedUsernames) {
                builder.append('@');
                builder.append(name);
                builder.append(' ');
            }
            textEditor.setText(builder);
            textEditor.setSelection(textEditor.length());
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
        mediaQueued = new ArrayList<>();
        waitForMediaLatch = new CountUpDownLatch();
        statusAlreadyInFlight = false;

        // These can only be added after everything affected by the media queue is initialized.
        if (savedMediaQueued != null) {
            for (SavedQueuedMedia item : savedMediaQueued) {
                addMediaToQueue(item.type, item.preview, item.uri, item.mediaSize);
            }
        } else if (intent != null && savedInstanceState == null) {
            /* Get incoming images being sent through a share action from another app. Only do this
             * when savedInstanceState is null, otherwise both the images from the intent and the
             * instance state will be re-queued. */
            String type = intent.getType();
            if (type != null) {
                if (type.startsWith("image/")) {
                    List<Uri> uriList = new ArrayList<>();
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

                            parser.putInClipboardManager(this, text);
                            textEditor.onPaste();
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        ArrayList<SavedQueuedMedia> savedMediaQueued = new ArrayList<>();
        for (QueuedMedia item : mediaQueued) {
            savedMediaQueued.add(new SavedQueuedMedia(item.type, item.uri, item.preview,
                    item.mediaSize));
        }
        outState.putParcelableArrayList("savedMediaQueued", savedMediaQueued);
        outState.putBoolean("showMarkSensitive", showMarkSensitive);
        outState.putString("statusVisibility", statusVisibility);
        outState.putBoolean("statusMarkSensitive", statusMarkSensitive);
        outState.putBoolean("statusHideText", statusHideText);
        if (currentInputContentInfo != null) {
            outState.putParcelable("commitContentInputContentInfo",
                    (Parcelable) currentInputContentInfo.unwrap());
            outState.putInt("commitContentFlags", currentFlags);
        }
        currentInputContentInfo = null;
        currentFlags = 0;
        super.onSaveInstanceState(outState);
    }

    private void doErrorDialog(@StringRes int descriptionId, @StringRes int actionId,
                               View.OnClickListener listener) {
        Snackbar bar = Snackbar.make(findViewById(R.id.activity_compose), getString(descriptionId),
                Snackbar.LENGTH_SHORT);
        bar.setAction(actionId, listener);
        bar.show();
    }

    private void displayTransientError(@StringRes int stringId) {
        Snackbar.make(findViewById(R.id.activity_compose), stringId, Snackbar.LENGTH_LONG).show();
    }

    private void toggleNsfw() {
        statusMarkSensitive = !statusMarkSensitive;
        updateNsfwButtonColor();
    }

    private void updateNsfwButtonColor() {
        @AttrRes int attribute;
        if (statusMarkSensitive) {
            attribute = R.attr.compose_nsfw_button_selected_color;
        } else {
            attribute = R.attr.compose_nsfw_button_color;
        }
        nsfwBtn.setTextColor(ThemeUtils.getColor(this, attribute));
    }

    private void disableButtons() {
        pickBtn.setClickable(false);
        takeBtn.setClickable(false);
        nsfwBtn.setClickable(false);
        visibilityBtn.setClickable(false);
        floatingBtn.setEnabled(false);
    }

    private void enableButtons() {
        pickBtn.setClickable(true);
        takeBtn.setClickable(true);
        nsfwBtn.setClickable(true);
        visibilityBtn.setClickable(true);
        floatingBtn.setEnabled(true);
    }

    private void addLockToSendButton() {
        floatingBtn.setText(R.string.action_send);
        Drawable lock = AppCompatResources.getDrawable(this, R.drawable.send_private);
        if (lock != null) {
            lock.setBounds(0, 0, lock.getIntrinsicWidth(), lock.getIntrinsicHeight());
            floatingBtn.setCompoundDrawables(null, null, lock, null);
        }
    }

    private void setStatusVisibility(String visibility) {
        statusVisibility = visibility;
        switch (visibility) {
            case "public": {
                floatingBtn.setText(R.string.action_send_public);
                floatingBtn.setCompoundDrawables(null, null, null, null);
                Drawable globe = AppCompatResources.getDrawable(this, R.drawable.ic_public_24dp);
                if (globe != null) {
                    visibilityBtn.setImageDrawable(globe);
                }
                break;
            }
            case "private": {
                addLockToSendButton();
                Drawable lock = AppCompatResources.getDrawable(this,
                        R.drawable.ic_lock_outline_24dp);
                if (lock != null) {
                    visibilityBtn.setImageDrawable(lock);
                }
                break;
            }
            case "direct": {
                addLockToSendButton();
                Drawable envelope = AppCompatResources.getDrawable(this, R.drawable.ic_email_24dp);
                if (envelope != null) {
                    visibilityBtn.setImageDrawable(envelope);
                }
                break;
            }
            case "unlisted":
            default: {
                floatingBtn.setText(R.string.action_send);
                floatingBtn.setCompoundDrawables(null, null, null, null);
                Drawable openLock = AppCompatResources.getDrawable(this,
                        R.drawable.ic_lock_open_24dp);
                if (openLock != null) {
                    visibilityBtn.setImageDrawable(openLock);
                }
                break;
            }
        }
    }

    private void showComposeOptions() {
        ComposeOptionsFragment fragment = ComposeOptionsFragment.newInstance(
                statusVisibility, statusHideText, inReplyToId != null);
        fragment.show(getSupportFragmentManager(), null);
    }

    public void onVisibilityChanged(String visibility) {
        setStatusVisibility(visibility);
    }

    private void updateVisibleCharactersLeft() {
        int left = STATUS_CHARACTER_LIMIT - textEditor.length();
        if (statusHideText) {
            left -= contentWarningEditor.length();
        }
        charactersLeft.setText(String.format(Locale.getDefault(), "%d", left));
    }

    public void onContentWarningChanged(boolean hideText) {
        showContentWarning(hideText);
        updateVisibleCharactersLeft();
    }

    void setStateToReadying() {
        statusAlreadyInFlight = true;
        disableButtons();
        postProgress.setVisibility(View.VISIBLE);
    }

    void setStateToNotReadying() {
        postProgress.setVisibility(View.INVISIBLE);
        statusAlreadyInFlight = false;
        enableButtons();
    }

    private void onSendClicked() {
        if (statusAlreadyInFlight) {
            return;
        }
        setStateToReadying();
        readyStatus(statusVisibility, statusMarkSensitive);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (inReplyToId != null) {
            /* Don't save the visibility setting for replies because they adopt the visibility of
             * the status they reply to and that behaviour needs to be kept separate. */
            return;
        }
        getPrivatePreferences().edit()
                .putString("rememberedVisibility", statusVisibility)
                .apply();
    }

    private void setEditTextMimeTypes(String[] contentMimeTypes) {
        final String[] mimeTypes;
        if (contentMimeTypes == null || contentMimeTypes.length == 0) {
            mimeTypes = new String[0];
        } else {
            mimeTypes = Arrays.copyOf(contentMimeTypes, contentMimeTypes.length);
        }
        textEditor.setMimeTypes(mimeTypes, new InputConnectionCompat.OnCommitContentListener() {
            @Override
            public boolean onCommitContent(InputContentInfoCompat inputContentInfo,
                                           int flags, Bundle opts) {
                return ComposeActivity.this.onCommitContent(inputContentInfo, flags,
                        mimeTypes);
            }
        });
    }

    private boolean onCommitContent(InputContentInfoCompat inputContentInfo, int flags,
                                    String[] mimeTypes) {
        try {
            if (currentInputContentInfo != null) {
                currentInputContentInfo.releasePermission();
            }
        } catch (Exception e) {
            Log.e(TAG, "InputContentInfoCompat#releasePermission() failed." + e.getMessage());
        } finally {
            currentInputContentInfo = null;
        }

        // Verify the returned content's type is actually in the list of MIME types requested.
        boolean supported = false;
        for (final String mimeType : mimeTypes) {
            if (inputContentInfo.getDescription().hasMimeType(mimeType)) {
                supported = true;
                break;
            }
        }

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

    private void sendStatus(String content, String visibility, boolean sensitive,
                            String spoilerText) {
        ArrayList<String> mediaIds = new ArrayList<>();

        for (QueuedMedia item : mediaQueued) {
            mediaIds.add(item.id);
        }

        mastodonAPI.createStatus(content, inReplyToId, spoilerText, visibility, sensitive, mediaIds).enqueue(new Callback<Status>() {
            @Override
            public void onResponse(Call<Status> call, Response<Status> response) {
                if (response.isSuccessful()) {
                    onSendSuccess();
                } else {
                    onSendFailure();
                }
            }

            @Override
            public void onFailure(Call<Status> call, Throwable t) {
                onSendFailure();
            }
        });
    }

    private void onSendSuccess() {
        Snackbar bar = Snackbar.make(findViewById(R.id.activity_compose), getString(R.string.confirmation_send), Snackbar.LENGTH_SHORT);
        bar.show();
        setResult(COMPOSE_SUCCESS);
        finish();
    }

    private void onSendFailure() {
        textEditor.setError(getString(R.string.error_generic));
        setStateToNotReadying();
    }

    private void readyStatus(final String visibility, final boolean sensitive) {
        finishingUploadDialog = ProgressDialog.show(
                this, getString(R.string.dialog_title_finishing_media_upload),
                getString(R.string.dialog_message_uploading_media), true, true);
        final AsyncTask<Void, Void, Boolean> waitForMediaTask =
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
                        setStateToNotReadying();
                        super.onCancelled();
                    }
                };
        finishingUploadDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                /* Generating an interrupt by passing true here is important because an interrupt
                 * exception is the only thing that will kick the latch out of its waiting loop
                 * early. */
                waitForMediaTask.cancel(true);
            }
        });
        waitForMediaTask.execute();
    }

    private void onReadySuccess(String visibility, boolean sensitive) {
        /* Validate the status meets the character limit. This has to be delayed until after all
         * uploads finish because their links are added when the upload succeeds and that affects
         * whether the limit is met or not. */
        String contentText = textEditor.getText().toString();
        String spoilerText = "";
        if (statusHideText) {
            spoilerText = contentWarningEditor.getText().toString();
        }
        int characterCount = contentText.length() + spoilerText.length();
        if (characterCount > 0 && characterCount <= STATUS_CHARACTER_LIMIT) {
            sendStatus(contentText, visibility, sensitive, spoilerText);
        } else if (characterCount <= 0) {
            textEditor.setError(getString(R.string.error_empty));
            setStateToNotReadying();
        } else {
            textEditor.setError(getString(R.string.error_compose_character_limit));
            setStateToNotReadying();
        }
    }

    private void onReadyFailure(final String visibility, final boolean sensitive) {
        doErrorDialog(R.string.error_media_upload_sending, R.string.action_retry,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        readyStatus(visibility, sensitive);
                    }
                });
        setStateToNotReadying();
    }

    private void onMediaPick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
        } else {
            initiateMediaPicking();
        }
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
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    onMediaPick();
                                }
                            });
                }
                break;
            }
        }
    }

    private File createNewImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "Tusky_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
    }

    private void initiateCameraApp() {
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
                        "com.keylesspalace.tusky.fileprovider",
                        photoFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUploadUri);
                startActivityForResult(intent, MEDIA_TAKE_PHOTO_RESULT);
            }
        }
    }

    private void initiateMediaPicking() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            intent.setType("image/* video/*");
        } else {
            String[] mimeTypes = new String[]{"image/*", "video/*"};
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        }
        startActivityForResult(intent, MEDIA_PICK_RESULT);
    }

    private void enableMediaButtons() {
        pickBtn.setEnabled(true);
        ThemeUtils.setDrawableTint(this, pickBtn.getDrawable(),
                R.attr.compose_media_button_tint);
        takeBtn.setEnabled(true);
        ThemeUtils.setDrawableTint(this, takeBtn.getDrawable(),
                R.attr.compose_media_button_tint);
    }

    private void disableMediaButtons() {
        pickBtn.setEnabled(false);
        ThemeUtils.setDrawableTint(this, pickBtn.getDrawable(),
                R.attr.compose_media_button_disabled_tint);
        takeBtn.setEnabled(false);
        ThemeUtils.setDrawableTint(this, takeBtn.getDrawable(),
                R.attr.compose_media_button_disabled_tint);
    }

    private void addMediaToQueue(QueuedMedia.Type type, Bitmap preview, Uri uri, long mediaSize) {
        final QueuedMedia item = new QueuedMedia(type, uri, new ImageView(this), mediaSize);
        ImageView view = item.preview;
        Resources resources = getResources();
        int side = resources.getDimensionPixelSize(R.dimen.compose_media_preview_side);
        int margin = resources.getDimensionPixelSize(R.dimen.compose_media_preview_margin);
        int marginBottom = resources.getDimensionPixelSize(
                R.dimen.compose_media_preview_margin_bottom);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(side, side);
        layoutParams.setMargins(margin, 0, margin, marginBottom);
        view.setLayoutParams(layoutParams);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setImageBitmap(preview);

        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeMediaFromQueue(item);
            }
        });
        mediaPreviewBar.addView(view);
        mediaQueued.add(item);
        int queuedCount = mediaQueued.size();
        if (queuedCount == 1) {
            /* The media preview bar is actually not inset in the EditText, it just overlays it and
             * is aligned to the bottom. But, so that text doesn't get hidden under it, extra
             * padding is added at the bottom of the EditText. */
            int totalHeight = side + margin + marginBottom;
            textEditor.setPadding(textEditor.getPaddingLeft(), textEditor.getPaddingTop(),
                    textEditor.getPaddingRight(), totalHeight);
            // If there's one video in the queue it is full, so disable the button to queue more.
            if (item.type == QueuedMedia.Type.VIDEO) {
                disableMediaButtons();
            }
        } else if (queuedCount >= Status.MAX_MEDIA_ATTACHMENTS) {
            // Limit the total media attachments, also.
            disableMediaButtons();
        }
        if (queuedCount >= 1) {
            showMarkSensitive(true);
        }
        waitForMediaLatch.countUp();
        if (mediaSize > STATUS_MEDIA_SIZE_LIMIT && type == QueuedMedia.Type.IMAGE) {
            downsizeMedia(item);
        } else {
            uploadMedia(item);
        }
    }

    private void removeMediaFromQueue(QueuedMedia item) {
        mediaPreviewBar.removeView(item.preview);
        mediaQueued.remove(item);
        if (mediaQueued.size() == 0) {
            showMarkSensitive(false);
            /* If there are no image previews to show, the extra padding that was added to the
             * EditText can be removed so there isn't unnecessary empty space. */
            textEditor.setPadding(textEditor.getPaddingLeft(), textEditor.getPaddingTop(),
                    textEditor.getPaddingRight(), 0);
        }
        // Remove the text URL associated with this media.
        if (item.uploadUrl != null) {
            Editable text = textEditor.getText();
            int start = text.getSpanStart(item.uploadUrl);
            int end = text.getSpanEnd(item.uploadUrl);
            if (start != -1 && end != -1) {
                text.delete(start, end);
            }
        }
        enableMediaButtons();
        cancelReadyingMedia(item);
    }

    private void removeAllMediaFromQueue() {
        for (Iterator<QueuedMedia> it = mediaQueued.iterator(); it.hasNext(); ) {
            QueuedMedia item = it.next();
            it.remove();
            removeMediaFromQueue(item);
        }
    }

    private void downsizeMedia(final QueuedMedia item) {
        item.readyStage = QueuedMedia.ReadyStage.DOWNSIZING;

        new DownsizeImageTask(STATUS_MEDIA_SIZE_LIMIT, getContentResolver(),
                new DownsizeImageTask.Listener() {
                    @Override
                    public void onSuccess(List<byte[]> contentList) {
                        item.content = contentList.get(0);
                        uploadMedia(item);
                    }

                    @Override
                    public void onFailure() {
                        onMediaDownsizeFailure(item);
                    }
                }).execute(item.uri);
    }

    private void onMediaDownsizeFailure(QueuedMedia item) {
        displayTransientError(R.string.error_media_upload_size);
        removeMediaFromQueue(item);
    }

    private void uploadMedia(final QueuedMedia item) {
        item.readyStage = QueuedMedia.ReadyStage.UPLOADING;

        final String mimeType = getContentResolver().getType(item.uri);
        MimeTypeMap map = MimeTypeMap.getSingleton();
        String fileExtension = map.getExtensionFromMimeType(mimeType);
        final String filename = String.format("%s_%s_%s.%s",
                getString(R.string.app_name),
                String.valueOf(new Date().getTime()),
                randomAlphanumericString(10),
                fileExtension);

        byte[] content = item.content;

        if (content == null) {
            InputStream stream;

            try {
                stream = getContentResolver().openInputStream(item.uri);
            } catch (FileNotFoundException e) {
                Log.d(TAG, Log.getStackTraceString(e));
                return;
            }

            content = inputStreamGetBytes(stream);
            IOUtils.closeQuietly(stream);

            if (content == null) {
                return;
            }
        }

        RequestBody requestFile = RequestBody.create(MediaType.parse(mimeType), content);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", filename, requestFile);

        item.uploadRequest = mastodonAPI.uploadMedia(body);

        item.uploadRequest.enqueue(new Callback<Media>() {
            @Override
            public void onResponse(Call<Media> call, retrofit2.Response<Media> response) {
                if (response.isSuccessful()) {
                    onUploadSuccess(item, response.body());
                } else {
                    Log.d(TAG, "Upload request failed. " + response.message());
                    onUploadFailure(item, call.isCanceled());
                }
            }

            @Override
            public void onFailure(Call<Media> call, Throwable t) {
                Log.d(TAG, t.getMessage());
                onUploadFailure(item, false);
            }
        });
    }

    private void onUploadSuccess(final QueuedMedia item, Media media) {
        item.id = media.id;

        /* Add the upload URL to the text field. Also, keep a reference to the span so if the user
         * chooses to remove the media, the URL is also automatically removed. */
        item.uploadUrl = new URLSpan(media.textUrl);
        int end = 1 + media.textUrl.length();
        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append(' ');
        builder.append(media.textUrl);
        builder.setSpan(item.uploadUrl, 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textEditor.append(builder);

        waitForMediaLatch.countDown();
    }

    private void onUploadFailure(QueuedMedia item, boolean isCanceled) {
        if (!isCanceled) {
            /* if the upload was voluntarily cancelled, such as if the user clicked on it to remove
             * it from the queue, then don't display this error message. */
            displayTransientError(R.string.error_media_upload_sending);
        }
        if (finishingUploadDialog != null) {
            finishingUploadDialog.cancel();
        }
        removeMediaFromQueue(item);
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MEDIA_PICK_RESULT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            long mediaSize = getMediaSize(getContentResolver(), uri);
            pickMedia(uri, mediaSize);
        } else if (requestCode == MEDIA_TAKE_PHOTO_RESULT && resultCode == RESULT_OK) {
            long mediaSize = getMediaSize(getContentResolver(), photoUploadUri);
            pickMedia(photoUploadUri, mediaSize);
        }
    }

    private void pickMedia(Uri uri, long mediaSize) {
        ContentResolver contentResolver = getContentResolver();
        if (mediaSize == MEDIA_SIZE_UNKNOWN) {
            displayTransientError(R.string.error_media_upload_opening);
            return;
        }
        String mimeType = contentResolver.getType(uri);
        if (mimeType != null) {
            String topLevelType = mimeType.substring(0, mimeType.indexOf('/'));
            switch (topLevelType) {
                case "video": {
                    if (mediaSize > STATUS_MEDIA_SIZE_LIMIT) {
                        displayTransientError(R.string.error_media_upload_size);
                        return;
                    }
                    if (mediaQueued.size() > 0
                            && mediaQueued.get(0).type == QueuedMedia.Type.IMAGE) {
                        displayTransientError(R.string.error_media_upload_image_or_video);
                        return;
                    }
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    retriever.setDataSource(this, uri);
                    Bitmap source = retriever.getFrameAtTime();
                    Bitmap bitmap = ThumbnailUtils.extractThumbnail(source, THUMBNAIL_SIZE, THUMBNAIL_SIZE);
                    source.recycle();
                    addMediaToQueue(QueuedMedia.Type.VIDEO, bitmap, uri, mediaSize);
                    break;
                }
                case "image": {
                    InputStream stream;
                    try {
                        stream = contentResolver.openInputStream(uri);
                    } catch (FileNotFoundException e) {
                        displayTransientError(R.string.error_media_upload_opening);
                        return;
                    }

                    Bitmap source = BitmapFactory.decodeStream(stream);
                    Bitmap bitmap = ThumbnailUtils.extractThumbnail(source, THUMBNAIL_SIZE, THUMBNAIL_SIZE);
                    source.recycle();
                    try {
                        if (stream != null) {
                            stream.close();
                        }
                    } catch (IOException e) {
                        bitmap.recycle();
                        displayTransientError(R.string.error_media_upload_opening);
                        return;
                    }
                    addMediaToQueue(QueuedMedia.Type.IMAGE, bitmap, uri, mediaSize);
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

    void showMarkSensitive(boolean show) {
        showMarkSensitive = show;

        if (!showMarkSensitive) {
            statusMarkSensitive = false;
            nsfwBtn.setTextColor(ThemeUtils.getColor(this, R.attr.compose_nsfw_button_color));
        }

        if (show) {
            nsfwBtn.setVisibility(View.VISIBLE);
        } else {
            nsfwBtn.setVisibility(View.GONE);
        }
    }

    void showContentWarning(boolean show) {
        statusHideText = show;
        if (show) {
            contentWarningBar.setVisibility(View.VISIBLE);
        } else {
            contentWarningBar.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                onBackPressed();
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onReceiveHeaderInfo(ParserUtils.HeaderInfo headerInfo) {
        if (!TextUtils.isEmpty(headerInfo.title)) {
            cleanBaseUrl(headerInfo);
            textEditor.append(headerInfo.title);
            textEditor.append(carriageReturn);
            textEditor.append(headerInfo.baseUrl);
        }
        if (!TextUtils.isEmpty(headerInfo.image)) {
            Picasso.Builder builder = new Picasso.Builder(getApplicationContext());
            builder.listener(new Picasso.Listener() {
                @Override
                public void onImageLoadFailed(Picasso picasso, Uri uri, Exception exception) {
                    exception.printStackTrace();
                }
            });

            target = MediaUtils.picassoImageTarget(ComposeActivity.this, new MediaUtils.MediaListener() {
                @Override
                public void onCallback(final Uri headerInfo) {
                    if (headerInfo != null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                long mediaSize = getMediaSize(getContentResolver(), headerInfo);
                                pickMedia(headerInfo, mediaSize);
                            }
                        });
                    }
                }
            });
            Picasso.with(this).load(headerInfo.image).into(target);
        }
    }


    // remove the precedent paste from the edit text
    private void cleanBaseUrl(ParserUtils.HeaderInfo headerInfo) {
        int lengthBaseUrl = headerInfo.baseUrl.length();
        int total = textEditor.getText().length();
        int indexSubString = total - lengthBaseUrl;
        String text = textEditor.getText().toString();
        text = text.substring(0, indexSubString);
        textEditor.setText(text);
    }

    @Override
    public void onErrorHeaderInfo() {
        displayTransientError(R.string.error_generic);
    }

    private static class QueuedMedia {
        Type type;
        ImageView preview;
        Uri uri;
        String id;
        Call<Media> uploadRequest;
        URLSpan uploadUrl;
        ReadyStage readyStage;
        byte[] content;
        long mediaSize;

        QueuedMedia(Type type, Uri uri, ImageView preview, long mediaSize) {
            this.type = type;
            this.uri = uri;
            this.preview = preview;
            this.mediaSize = mediaSize;
        }

        enum Type {
            IMAGE,
            VIDEO
        }

        enum ReadyStage {
            DOWNSIZING,
            UPLOADING
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
        QueuedMedia.Type type;
        Uri uri;
        Bitmap preview;
        long mediaSize;

        SavedQueuedMedia(QueuedMedia.Type type, Uri uri, ImageView view, long mediaSize) {
            this.type = type;
            this.uri = uri;
            this.preview = ((BitmapDrawable) view.getDrawable()).getBitmap();
            this.mediaSize = mediaSize;
        }

        SavedQueuedMedia(Parcel parcel) {
            type = (QueuedMedia.Type) parcel.readSerializable();
            uri = parcel.readParcelable(Uri.class.getClassLoader());
            preview = parcel.readParcelable(Bitmap.class.getClassLoader());
            mediaSize = parcel.readLong();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeSerializable(type);
            dest.writeParcelable(uri, flags);
            dest.writeParcelable(preview, flags);
            dest.writeLong(mediaSize);
        }
    }
}
