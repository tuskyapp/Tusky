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
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;
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
import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Px;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v13.view.inputmethod.InputConnectionCompat;
import android.support.v13.view.inputmethod.InputContentInfoCompat;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.keylesspalace.tusky.adapter.MentionAutoCompleteAdapter;
import com.keylesspalace.tusky.db.AccountEntity;
import com.keylesspalace.tusky.db.TootDao;
import com.keylesspalace.tusky.db.TootEntity;
import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.entity.Attachment;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.fragment.ComposeOptionsFragment;
import com.keylesspalace.tusky.network.ProgressRequestBody;
import com.keylesspalace.tusky.util.CountUpDownLatch;
import com.keylesspalace.tusky.util.DownsizeImageTask;
import com.keylesspalace.tusky.util.IOUtils;
import com.keylesspalace.tusky.util.ListUtils;
import com.keylesspalace.tusky.util.MediaUtils;
import com.keylesspalace.tusky.util.MentionTokenizer;
import com.keylesspalace.tusky.util.SpanUtils;
import com.keylesspalace.tusky.util.StringUtils;
import com.keylesspalace.tusky.util.ThemeUtils;
import com.keylesspalace.tusky.view.EditTextTyped;
import com.keylesspalace.tusky.view.ProgressImageView;
import com.keylesspalace.tusky.view.RoundedTransformation;
import com.squareup.picasso.Picasso;
import com.varunest.sparkbutton.helpers.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class ComposeActivity extends BaseActivity
        implements ComposeOptionsFragment.Listener, MentionAutoCompleteAdapter.AccountSearchProvider {
    private static final String TAG = "ComposeActivity"; // logging tag
    private static final int STATUS_CHARACTER_LIMIT = 500;
    private static final int STATUS_MEDIA_SIZE_LIMIT = 8388608; // 8MiB
    private static final int MEDIA_PICK_RESULT = 1;
    private static final int MEDIA_TAKE_PHOTO_RESULT = 2;
    private static final int PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;
    private static final int COMPOSE_SUCCESS = -1;
    @Px
    private static final int THUMBNAIL_SIZE = 128;

    private static final String SAVED_TOOT_UID_EXTRA = "saved_toot_uid";
    private static final String SAVED_TOOT_TEXT_EXTRA = "saved_toot_text";
    private static final String SAVED_JSON_URLS_EXTRA = "saved_json_urls";
    private static final String IN_REPLY_TO_ID_EXTRA = "in_reply_to_id";
    private static final String REPLY_VISIBILITY_EXTRA = "reply_visibilty";
    private static final String CONTENT_WARNING_EXTRA = "content_warning";
    private static final String MENTIONED_USERNAMES_EXTRA = "netnioned_usernames";
    private static final String REPLYING_STATUS_AUTHOR_USERNAME_EXTRA = "replying_author_nickname_extra";
    private static final String REPLYING_STATUS_CONTENT_EXTRA = "replying_status_content";

    private static TootDao tootDao = TuskyApplication.getDB().tootDao();

    private TextView replyTextView;
    private TextView replyContentTextView;
    private EditTextTyped textEditor;
    private LinearLayout mediaPreviewBar;
    private View contentWarningBar;
    private EditText contentWarningEditor;
    private TextView charactersLeft;
    private Button floatingBtn;
    private ImageButton pickButton;
    private ImageButton visibilityBtn;
    private ImageButton saveButton;
    private ImageButton hideMediaToggle;
    private ProgressBar postProgress;
    // this only exists when a status is trying to be sent, but uploads are still occurring
    private ProgressDialog finishingUploadDialog;
    private String inReplyToId;
    private ArrayList<QueuedMedia> mediaQueued;
    private CountUpDownLatch waitForMediaLatch;
    private boolean showMarkSensitive;
    private Status.Visibility statusVisibility;     // The current values of the options that will be applied
    private boolean statusMarkSensitive; // to the status being composed.
    private boolean statusHideText;
    private boolean statusAlreadyInFlight; // to prevent duplicate sends by mashing the send button
    private InputContentInfoCompat currentInputContentInfo;
    private int currentFlags;
    private Uri photoUploadUri;
    private int savedTootUid = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compose);

        replyTextView = findViewById(R.id.reply_tv);
        replyContentTextView = findViewById(R.id.reply_content_tv);
        textEditor = findViewById(R.id.compose_edit_field);
        mediaPreviewBar = findViewById(R.id.compose_media_preview_bar);
        contentWarningBar = findViewById(R.id.compose_content_warning_bar);
        contentWarningEditor = findViewById(R.id.field_content_warning);
        charactersLeft = findViewById(R.id.characters_left);
        floatingBtn = findViewById(R.id.floating_btn);
        pickButton = findViewById(R.id.compose_photo_pick);
        visibilityBtn = findViewById(R.id.action_toggle_visibility);
        saveButton = findViewById(R.id.compose_save_draft);
        hideMediaToggle = findViewById(R.id.action_hide_media);
        postProgress = findViewById(R.id.postProgress);

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
        AccountEntity activeAccount = TuskyApplication.getAccountManager().getActiveAccount();

        if(activeAccount != null) {

            ImageView composeAvatar = findViewById(R.id.composeAvatar);

            if(TextUtils.isEmpty(activeAccount.getProfilePictureUrl())) {
                composeAvatar.setImageResource(R.drawable.avatar_default);
            } else {
                Picasso.with(this).load(activeAccount.getProfilePictureUrl())
                        .transform(new RoundedTransformation(7, 0))
                        .error(R.drawable.avatar_default)
                        .placeholder(R.drawable.avatar_default)
                        .into(composeAvatar);
            }

            composeAvatar.setContentDescription(
                    getString(R.string.compose_active_account_description,
                            activeAccount.getFullName()));

        } else {
            // do not do anything when not logged in, activity will be finished in super.onCreate() anyway
            return;
        }

        // Setup the interface buttons.
        floatingBtn.setOnClickListener(v -> onSendClicked());
        floatingBtn.setOnLongClickListener(v -> saveDraft());
        pickButton.setOnClickListener(v -> openPickDialog());
        visibilityBtn.setOnClickListener(v -> showComposeOptions());
        saveButton.setOnClickListener(v -> saveDraft());
        hideMediaToggle.setOnClickListener(v -> toggleHideMedia());

        //fix a bug with autocomplete and some keyboards
        int newInputType = textEditor.getInputType() & (textEditor.getInputType() ^ InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
        textEditor.setInputType(newInputType);

        /* Initialise all the state, or restore it from a previous run, to determine a "starting"
         * state. */
        Status.Visibility startingVisibility = Status.Visibility.UNKNOWN;
        boolean startingHideText;
        String startingContentWarning = null;
        ArrayList<SavedQueuedMedia> savedMediaQueued = null;
        if (savedInstanceState != null) {
            showMarkSensitive = savedInstanceState.getBoolean("showMarkSensitive");
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
            showMarkSensitive = false;
            statusMarkSensitive = false;
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
                Status.Visibility replyVisibility = Status.Visibility.byNum(
                        intent.getIntExtra(REPLY_VISIBILITY_EXTRA, Status.Visibility.UNKNOWN.getNum()));

                if (replyVisibility != Status.Visibility.UNKNOWN) {
                    startingVisibility = replyVisibility;
                } else {
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                    startingVisibility = Status.Visibility.byString(
                            preferences.getString("defaultPostPrivacy",
                                    Status.Visibility.PUBLIC.serverString()));
                }
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
                textEditor.append(savedTootText);
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

            if (intent.hasExtra(REPLYING_STATUS_AUTHOR_USERNAME_EXTRA)) {
                replyTextView.setVisibility(View.VISIBLE);
                String username = intent.getStringExtra(REPLYING_STATUS_AUTHOR_USERNAME_EXTRA);
                replyTextView.setText(getString(R.string.replying_to, username));
                replyTextView.setOnClickListener(v -> {
                    if (replyContentTextView.getVisibility() != View.VISIBLE) {
                        replyContentTextView.setVisibility(View.VISIBLE);
                    } else {
                        replyContentTextView.setVisibility(View.GONE);
                    }
                });
            }

            if (intent.hasExtra(REPLYING_STATUS_CONTENT_EXTRA)) {
                replyContentTextView.setText(intent.getStringExtra(REPLYING_STATUS_CONTENT_EXTRA));
            }
        }

        // After the starting state is finalised, the interface can be set to reflect this state.
        setStatusVisibility(startingVisibility);

        postProgress.setVisibility(View.INVISIBLE);
        updateHideMediaToggleColor();
        updateVisibleCharactersLeft();

        // Setup the main text field.
        setEditTextMimeTypes(); // new String[] { "image/gif", "image/webp" }
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
        if (!ListUtils.isEmpty(loadedDraftMediaUris)) {
            for (String uriString : loadedDraftMediaUris) {
                Uri uri = Uri.parse(uriString);
                long mediaSize = MediaUtils.getMediaSize(getContentResolver(), uri);
                pickMedia(uri, mediaSize);
            }
        } else if (savedMediaQueued != null) {
            for (SavedQueuedMedia item : savedMediaQueued) {
                Bitmap preview = MediaUtils.getImageThumbnail(getContentResolver(), item.uri, THUMBNAIL_SIZE);
                addMediaToQueue(item.type, preview, item.uri, item.mediaSize, item.readyStage, item.description);
            }
        } else if (intent != null && savedInstanceState == null) {
            /* Get incoming images being sent through a share action from another app. Only do this
             * when savedInstanceState is null, otherwise both the images from the intent and the
             * instance state will be re-queued. */
            String type = intent.getType();
            if (type != null) {
                if (type.startsWith("image/")) {
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
                        long mediaSize = MediaUtils.getMediaSize(getContentResolver(), uri);
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

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        ArrayList<SavedQueuedMedia> savedMediaQueued = new ArrayList<>();
        for (QueuedMedia item : mediaQueued) {
            savedMediaQueued.add(new SavedQueuedMedia(item.type, item.uri,
                    item.mediaSize, item.readyStage, item.description));
        }
        outState.putParcelableArrayList("savedMediaQueued", savedMediaQueued);
        outState.putBoolean("showMarkSensitive", showMarkSensitive);
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
        bar.show();
    }

    private void displayTransientError(@StringRes int stringId) {
        Snackbar.make(findViewById(R.id.activity_compose), stringId, Snackbar.LENGTH_LONG).show();
    }

    private void toggleHideMedia() {
        statusMarkSensitive = !statusMarkSensitive;
        updateHideMediaToggleColor();
    }

    private void updateHideMediaToggleColor() {
        @AttrRes int attribute;
        if (statusMarkSensitive) {
            attribute = R.attr.compose_hide_media_button_selected_color;
        } else {
            attribute = R.attr.compose_hide_media_button_color;
        }
        ThemeUtils.setDrawableTint(this, hideMediaToggle.getDrawable(), attribute);
    }

    private void disableButtons() {
        pickButton.setClickable(false);
        visibilityBtn.setClickable(false);
        saveButton.setClickable(false);
        hideMediaToggle.setClickable(false);
        floatingBtn.setEnabled(false);
    }

    private void enableButtons() {
        pickButton.setClickable(true);
        visibilityBtn.setClickable(true);
        saveButton.setClickable(true);
        hideMediaToggle.setClickable(true);
        floatingBtn.setEnabled(true);
    }

    private boolean saveDraft() {
        String contentWarning = null;
        if (statusHideText) {
            contentWarning = contentWarningEditor.getText().toString();
        }
        Editable textToSave = textEditor.getEditableText();
        /* Discard any upload URLs embedded in the text because they'll be re-uploaded when
         * the draft is loaded and replaced with new URLs. */
        if (mediaQueued != null) {
            for (QueuedMedia item : mediaQueued) {
                textToSave = removeUrlFromEditable(textToSave, item.uploadUrl);
            }
        }
        boolean didSaveSuccessfully = saveTheToot(textToSave.toString(), contentWarning);
        if (didSaveSuccessfully) {
            Toast.makeText(ComposeActivity.this, R.string.action_save_one_toot, Toast.LENGTH_SHORT)
                    .show();
        }
        return didSaveSuccessfully;
    }

    private static boolean copyToFile(ContentResolver contentResolver, Uri uri, File file) {
        InputStream from;
        FileOutputStream to;
        try {
            from = contentResolver.openInputStream(uri);
            to = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            return false;
        }
        if (from == null) {
            return false;
        }
        byte[] chunk = new byte[16384];
        try {
            while (true) {
                int bytes = from.read(chunk, 0, chunk.length);
                if (bytes < 0) {
                    break;
                }
                to.write(chunk, 0, bytes);
            }
        } catch (IOException e) {
            return false;
        }
        IOUtils.closeQuietly(from);
        IOUtils.closeQuietly(to);
        return true;
    }

    @Nullable
    private List<String> saveMedia(@Nullable ArrayList<String> existingUris) {
        File imageDirectory = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File videoDirectory = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (imageDirectory == null || !(imageDirectory.exists() || imageDirectory.mkdirs())) {
            Log.e(TAG, "Image directory is not created.");
            return null;
        }
        if (videoDirectory == null || !(videoDirectory.exists() || videoDirectory.mkdirs())) {
            Log.e(TAG, "Video directory is not created.");
            return null;
        }
        ContentResolver contentResolver = getContentResolver();
        ArrayList<File> filesSoFar = new ArrayList<>();
        ArrayList<String> results = new ArrayList<>();
        for (QueuedMedia item : mediaQueued) {
            /* If the media was already saved in a previous draft, there's no need to save another
             * copy, just add the existing URI to the results. */
            if (existingUris != null) {
                String uri = item.uri.toString();
                int index = existingUris.indexOf(uri);
                if (index != -1) {
                    results.add(uri);
                    continue;
                }
            }
            // Otherwise, save the media.
            File directory;
            switch (item.type) {
                default:
                case IMAGE:
                    directory = imageDirectory;
                    break;
                case VIDEO:
                    directory = videoDirectory;
                    break;
            }
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date());
            String mimeType = contentResolver.getType(item.uri);
            MimeTypeMap map = MimeTypeMap.getSingleton();
            String fileExtension = map.getExtensionFromMimeType(mimeType);
            String filename = String.format("Tusky_Draft_Media_%s.%s", timeStamp, fileExtension);
            File file = new File(directory, filename);
            filesSoFar.add(file);
            boolean copied = copyToFile(contentResolver, item.uri, file);
            if (!copied) {
                /* If any media files were created in prior iterations, delete those before
                 * returning. */
                for (File earlierFile : filesSoFar) {
                    boolean deleted = earlierFile.delete();
                    if (!deleted) {
                        Log.i(TAG, "Could not delete the file " + earlierFile.toString());
                    }
                }
                return null;
            }
            Uri uri = FileProvider.getUriForFile(this, "com.keylesspalace.tusky.fileprovider",
                    file);
            results.add(uri.toString());
        }
        return results;
    }

    private void deleteMedia(List<String> mediaUris) {
        for (String uriString : mediaUris) {
            Uri uri = Uri.parse(uriString);
            if (getContentResolver().delete(uri, null, null) == 0) {
                Log.e(TAG, String.format("Did not delete file %s.", uriString));
            }
        }
    }

    /**
     * A∖B={x∈A|x∉B}
     *
     * @return all elements of set A that are not in set B.
     */
    private static List<String> setDifference(List<String> a, List<String> b) {
        List<String> c = new ArrayList<>();
        for (String s : a) {
            if (!b.contains(s)) {
                c.add(s);
            }
        }
        return c;
    }

    @SuppressLint("StaticFieldLeak")
    private boolean saveTheToot(String s, @Nullable String contentWarning) {
        if (TextUtils.isEmpty(s)) {
            return false;
        }

        // Get any existing file's URIs.
        ArrayList<String> existingUris = null;
        String savedJsonUrls = getIntent().getStringExtra("saved_json_urls");
        if (!TextUtils.isEmpty(savedJsonUrls)) {
            existingUris = new Gson().fromJson(savedJsonUrls,
                    new TypeToken<ArrayList<String>>() {
                    }.getType());
        }

        String mediaUrlsSerialized = null;
        if (!ListUtils.isEmpty(mediaQueued)) {
            List<String> savedList = saveMedia(existingUris);
            if (!ListUtils.isEmpty(savedList)) {
                mediaUrlsSerialized = new Gson().toJson(savedList);
                if (!ListUtils.isEmpty(existingUris)) {
                    deleteMedia(setDifference(existingUris, savedList));
                }
            } else {
                return false;
            }
        } else if (!ListUtils.isEmpty(existingUris)) {
            /* If there were URIs in the previous draft, but they've now been removed, those files
             * can be deleted. */
            deleteMedia(existingUris);
        }
        final TootEntity toot = new TootEntity(savedTootUid, s, mediaUrlsSerialized, contentWarning,
                inReplyToId,
                getIntent().getStringExtra(REPLYING_STATUS_CONTENT_EXTRA),
                getIntent().getStringExtra(REPLYING_STATUS_AUTHOR_USERNAME_EXTRA), statusVisibility);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                tootDao.insertOrReplace(toot);
                return null;
            }
        }.execute();
        return true;
    }

    private void addLockToSendButton() {
        floatingBtn.setText(R.string.action_send);
        Drawable lock = AppCompatResources.getDrawable(this, R.drawable.send_private);
        if (lock != null) {
            lock.setBounds(0, 0, lock.getIntrinsicWidth(), lock.getIntrinsicHeight());
            floatingBtn.setCompoundDrawables(null, null, lock, null);
        }
    }

    private void setStatusVisibility(Status.Visibility visibility) {
        statusVisibility = visibility;
        switch (visibility) {
            case PUBLIC: {
                floatingBtn.setText(R.string.action_send_public);
                floatingBtn.setCompoundDrawables(null, null, null, null);
                Drawable globe = AppCompatResources.getDrawable(this, R.drawable.ic_public_24dp);
                if (globe != null) {
                    visibilityBtn.setImageDrawable(globe);
                }
                break;
            }
            case PRIVATE: {
                addLockToSendButton();
                Drawable lock = AppCompatResources.getDrawable(this,
                        R.drawable.ic_lock_outline_24dp);
                if (lock != null) {
                    visibilityBtn.setImageDrawable(lock);
                }
                break;
            }
            case DIRECT: {
                addLockToSendButton();
                Drawable envelope = AppCompatResources.getDrawable(this, R.drawable.ic_email_24dp);
                if (envelope != null) {
                    visibilityBtn.setImageDrawable(envelope);
                }
                break;
            }
            case UNLISTED:
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
                statusVisibility, statusHideText);
        fragment.show(getSupportFragmentManager(), null);
    }

    @Override
    public void onVisibilityChanged(Status.Visibility visibility) {
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

    private void setStateToReadying() {
        statusAlreadyInFlight = true;
        disableButtons();
        postProgress.setVisibility(View.VISIBLE);
    }

    private void setStateToNotReadying() {
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

    private void setEditTextMimeTypes() {
        final String[] mimeTypes = new String[]{"image/*"};
        textEditor.setMimeTypes(mimeTypes,
                (inputContentInfo, flags, opts) ->
                        ComposeActivity.this.onCommitContent(inputContentInfo, flags,
                                mimeTypes));
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
            mediaSize = MediaUtils.MEDIA_SIZE_UNKNOWN;
        }
        pickMedia(uri, mediaSize);

        currentInputContentInfo = inputContentInfo;
        currentFlags = flags;

        return true;
    }

    private void sendStatus(String content, Status.Visibility visibility, boolean sensitive,
                            String spoilerText) {
        ArrayList<String> mediaIds = new ArrayList<>();

        for (QueuedMedia item : mediaQueued) {
            mediaIds.add(item.id);
        }

        Callback<Status> callback = new Callback<Status>() {
            @Override
            public void onResponse(@NonNull Call<Status> call, @NonNull Response<Status> response) {
                if (response.isSuccessful()) {
                    onSendSuccess();
                } else {
                    onSendFailure(response);
                }
            }

            @Override
            public void onFailure(@NonNull Call<Status> call, @NonNull Throwable t) {
                onSendFailure(null);
            }
        };
        mastodonApi.createStatus(content, inReplyToId, spoilerText, visibility.serverString(),
                sensitive, mediaIds).enqueue(callback);
    }

    private void onSendSuccess() {
        // If the status was loaded from a draft, delete the draft and associated media files.
        if (savedTootUid != 0) {
            tootDao.delete(savedTootUid);
            for (QueuedMedia item : mediaQueued) {
                try {
                    if (getContentResolver().delete(item.uri, null, null) == 0) {
                        Log.e(TAG, String.format("Did not delete file %s.", item.uri.toString()));
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, String.format("Did not delete file %s.", item.uri.toString()), e);
                }

            }
        }
        Snackbar bar = Snackbar.make(findViewById(R.id.activity_compose),
                getString(R.string.confirmation_send), Snackbar.LENGTH_SHORT);
        bar.show();
        setResult(COMPOSE_SUCCESS);
        finish();
    }

    private void onSendFailure(@Nullable Response<Status> response) {
        setStateToNotReadying();

        if (response != null && inReplyToId != null && response.code() == 404) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.dialog_reply_not_found)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        inReplyToId = null;
                        replyContentTextView.setVisibility(View.GONE);
                        replyTextView.setVisibility(View.GONE);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else {
            textEditor.setError(getString(R.string.error_generic));
        }
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
                        setStateToNotReadying();
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

    private void onReadyFailure(final Status.Visibility visibility, final boolean sensitive) {
        doErrorDialog(R.string.error_media_upload_sending, R.string.action_retry,
                v -> readyStatus(visibility, sensitive));
        setStateToNotReadying();
    }

    private void openPickDialog() {
        final int CHOICE_TAKE = 0;
        final int CHOICE_PICK = 1;
        CharSequence[] choices = new CharSequence[2];
        choices[CHOICE_TAKE] = getString(R.string.action_photo_take);
        choices[CHOICE_PICK] = getString(R.string.action_photo_pick);
        DialogInterface.OnClickListener listener = (dialog, which) -> {
            switch (which) {
                case CHOICE_TAKE: {
                    initiateCameraApp();
                    break;
                }
                case CHOICE_PICK: {
                    onMediaPick();
                    break;
                }
            }
        };
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setItems(choices, listener)
                .create();
        dialog.show();
    }

    private void onMediaPick() {
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
        pickButton.setEnabled(true);
        ThemeUtils.setDrawableTint(this, pickButton.getDrawable(),
                R.attr.compose_media_button_tint);
    }

    private void disableMediaButtons() {
        pickButton.setEnabled(false);
        ThemeUtils.setDrawableTint(this, pickButton.getDrawable(),
                R.attr.compose_media_button_disabled_tint);
    }

    private void addMediaToQueue(QueuedMedia.Type type, Bitmap preview, Uri uri, long mediaSize,
                                 QueuedMedia.ReadyStage readyStage, @Nullable String description) {
        final QueuedMedia item = new QueuedMedia(type, uri, new ProgressImageView(this),
                mediaSize, description);
        item.readyStage = readyStage;
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
        view.setOnClickListener(v -> onMediaClick(item, v));
        view.setContentDescription(getString(R.string.action_delete));
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
        if (item.readyStage != QueuedMedia.ReadyStage.UPLOADED) {
            waitForMediaLatch.countUp();
            if (mediaSize > STATUS_MEDIA_SIZE_LIMIT && type == QueuedMedia.Type.IMAGE) {
                downsizeMedia(item);
            } else {
                uploadMedia(item);
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
        Picasso.with(this)
                .load(item.uri)
                .into(imageView);

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

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogLayout)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        Window window = dialog.getWindow();
        if (window != null) {
            //noinspection ConstantConditions
            window.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> mastodonApi.updateMedia(item.id,
                    input.getText().toString()).enqueue(new Callback<Attachment>() {
                @Override
                public void onResponse(@NonNull Call<Attachment> call, @NonNull Response<Attachment> response) {
                    Attachment attachment = response.body();
                    if (response.isSuccessful() && attachment != null) {
                        item.description = attachment.description;
                        dialog.dismiss();
                    } else {
                        showFailedCaptionMessage();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<Attachment> call, @NonNull Throwable t) {
                    showFailedCaptionMessage();
                }
            }));
        });
        dialog.show();
    }

    private void showFailedCaptionMessage() {
        Toast.makeText(this, R.string.error_failed_set_caption, Toast.LENGTH_SHORT).show();
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
        textEditor.setText(removeUrlFromEditable(textEditor.getEditableText(), item.uploadUrl));
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

    private static Editable removeUrlFromEditable(Editable editable, @Nullable URLSpan urlSpan) {
        if (urlSpan == null) {
            return editable;
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(editable);
        int start = builder.getSpanStart(urlSpan);
        int end = builder.getSpanEnd(urlSpan);
        if (start != -1 && end != -1) {
            builder.delete(start, end);
        }
        return builder;
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

        String mimeType = getContentResolver().getType(item.uri);
        MimeTypeMap map = MimeTypeMap.getSingleton();
        String fileExtension = map.getExtensionFromMimeType(mimeType);
        final String filename = String.format("%s_%s_%s.%s",
                getString(R.string.app_name),
                String.valueOf(new Date().getTime()),
                StringUtils.randomAlphanumericString(10),
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

            content = MediaUtils.inputStreamGetBytes(stream);
            IOUtils.closeQuietly(stream);

            if (content == null) {
                return;
            }
        }

        if (mimeType == null) mimeType = "multipart/form-data";

        item.preview.setProgress(0);

        ProgressRequestBody fileBody = new ProgressRequestBody(content, MediaType.parse(mimeType),
                false, // If request body logging is enabled, pass true
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
        item.id = media.id;
        item.preview.setProgress(-1);
        item.readyStage = QueuedMedia.ReadyStage.UPLOADED;

        /* Add the upload URL to the text field. Also, keep a reference to the span so if the user
         * chooses to remove the media, the URL is also automatically removed. */
        item.uploadUrl = new URLSpan(media.textUrl);
        int end = 1 + media.textUrl.length();
        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append(' ');
        builder.append(media.textUrl);
        builder.setSpan(item.uploadUrl, 1, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int cursorStart = textEditor.getSelectionStart();
        int cursorEnd = textEditor.getSelectionEnd();
        textEditor.append(builder);
        textEditor.setSelection(cursorStart, cursorEnd);

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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == MEDIA_PICK_RESULT && data != null) {
            Uri uri = data.getData();
            long mediaSize = MediaUtils.getMediaSize(getContentResolver(), uri);
            pickMedia(uri, mediaSize);
        } else if (resultCode == RESULT_OK && requestCode == MEDIA_TAKE_PHOTO_RESULT) {
            long mediaSize = MediaUtils.getMediaSize(getContentResolver(), photoUploadUri);
            pickMedia(photoUploadUri, mediaSize);
        }
    }


    private void pickMedia(Uri uri, long mediaSize) {
        if (mediaSize == MediaUtils.MEDIA_SIZE_UNKNOWN) {
            displayTransientError(R.string.error_media_upload_opening);
            return;
        }
        ContentResolver contentResolver = getContentResolver();
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
                    Bitmap bitmap = MediaUtils.getVideoThumbnail(this, uri, THUMBNAIL_SIZE);
                    if (bitmap != null) {
                        addMediaToQueue(QueuedMedia.Type.VIDEO, bitmap, uri, mediaSize, null, null);
                    } else {
                        displayTransientError(R.string.error_media_upload_opening);
                    }
                    break;
                }
                case "image": {
                    Bitmap bitmap = MediaUtils.getImageThumbnail(contentResolver, uri, THUMBNAIL_SIZE);
                    if (bitmap != null) {
                        addMediaToQueue(QueuedMedia.Type.IMAGE, bitmap, uri, mediaSize, null, null);
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

    private void showMarkSensitive(boolean show) {
        showMarkSensitive = show;

        if (!showMarkSensitive) {
            statusMarkSensitive = false;
            ThemeUtils.setDrawableTint(this, hideMediaToggle.getDrawable(),
                    R.attr.compose_hide_media_button_color);
        }

        if (show) {
            hideMediaToggle.setVisibility(View.VISIBLE);
        } else {
            hideMediaToggle.setVisibility(View.GONE);
        }
    }

    private void showContentWarning(boolean show) {
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

    private static final class QueuedMedia {
        Type type;
        ProgressImageView preview;
        Uri uri;
        String id;
        Call<Attachment> uploadRequest;
        URLSpan uploadUrl;
        ReadyStage readyStage;
        byte[] content;
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

        enum Type {
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
        QueuedMedia.Type type;
        Uri uri;
        long mediaSize;
        QueuedMedia.ReadyStage readyStage;
        String description;

        SavedQueuedMedia(QueuedMedia.Type type, Uri uri, long mediaSize, QueuedMedia.ReadyStage readyStage, String description) {
            this.type = type;
            this.uri = uri;
            this.mediaSize = mediaSize;
            this.readyStage = readyStage;
            this.description = description;
        }

        SavedQueuedMedia(Parcel parcel) {
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
            dest.writeSerializable(type);
            dest.writeParcelable(uri, flags);
            dest.writeLong(mediaSize);
            dest.writeString(readyStage.name());
            dest.writeString(description);
        }
    }

    @SuppressWarnings("WeakerAccess")
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
