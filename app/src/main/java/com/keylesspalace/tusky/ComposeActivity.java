/* Copyright 2017 Andrew Dawson
 *
 * This file is part of Tusky.
 *
 * Tusky is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with Tusky. If
 * not, see <http://www.gnu.org/licenses/>. */

package com.keylesspalace.tusky;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
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
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v13.view.inputmethod.EditorInfoCompat;
import android.support.v13.view.inputmethod.InputConnectionCompat;
import android.support.v13.view.inputmethod.InputContentInfoCompat;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.keylesspalace.tusky.entity.Media;
import com.keylesspalace.tusky.entity.Status;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;

public class  ComposeActivity extends BaseActivity implements ComposeOptionsFragment.Listener {
    private static final String TAG = "ComposeActivity"; // logging tag
    private static final int STATUS_CHARACTER_LIMIT = 500;
    private static final int STATUS_MEDIA_SIZE_LIMIT = 4000000; // 4MB
    private static final int MEDIA_PICK_RESULT = 1;
    private static final int PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;
    private static final int MEDIA_SIZE_UNKNOWN = -1;

    private String inReplyToId;
    private EditText textEditor;
    private LinearLayout mediaPreviewBar;
    private ArrayList<QueuedMedia> mediaQueued;
    private CountUpDownLatch waitForMediaLatch;
    private boolean showMarkSensitive;
    private String statusVisibility;     // The current values of the options that will be applied
    private boolean statusMarkSensitive; // to the status being composed.
    private boolean statusHideText;      //
    private View contentWarningBar;
    private boolean statusAlreadyInFlight; // to prevent duplicate sends by mashing the send button
    private InputContentInfoCompat currentInputContentInfo;
    private int currentFlags;
    private ProgressDialog finishingUploadDialog;
    private EditText contentWarningEditor;
    private Button floatingBtn;
    private ImageButton pickBtn;
    private Button nsfwBtn;

    private static class QueuedMedia {
        enum Type {
            IMAGE,
            VIDEO
        }

        enum ReadyStage {
            DOWNSIZING,
            UPLOADING
        }

        Type type;
        ImageView preview;
        Uri uri;
        String id;
        Call<Media> uploadRequest;
        ReadyStage readyStage;
        byte[] content;
        long mediaSize;

        QueuedMedia(Type type, Uri uri, ImageView preview, long mediaSize) {
            this.type = type;
            this.uri = uri;
            this.preview = preview;
            this.mediaSize = mediaSize;
        }
    }

    /**This saves enough information to re-enqueue an attachment when restoring the activity. */
    private static class SavedQueuedMedia implements Parcelable {
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

        public static final Parcelable.Creator<SavedQueuedMedia> CREATOR
                = new Parcelable.Creator<SavedQueuedMedia>() {
            public SavedQueuedMedia createFromParcel(Parcel in) {
                return new SavedQueuedMedia(in);
            }

            public SavedQueuedMedia[] newArray(int size) {
                return new SavedQueuedMedia[size];
            }
        };
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

    private static class FindCharsResult {
        int charIndex;
        int stringIndex;

        FindCharsResult() {
            charIndex = -1;
            stringIndex = -1;
        }
    }

    private static FindCharsResult findChars(String string, int fromIndex, char[] chars) {
        FindCharsResult result = new FindCharsResult();
        final int length = string.length();
        for (int i = fromIndex; i < length; i++) {
            char c = string.charAt(i);
            for (int j = 0; j < chars.length; j++) {
                if (chars[j] == c) {
                    result.charIndex = j;
                    result.stringIndex = i;
                    return result;
                }
            }
        }
        return result;
    }

    private static FindCharsResult findStart(String string, int fromIndex, char[] chars) {
        final int length = string.length();
        while (fromIndex < length) {
            FindCharsResult found = findChars(string, fromIndex, chars);
            int i = found.stringIndex;
            if (i < 0) {
                break;
            } else if (i == 0 || i >= 1 && Character.isWhitespace(string.codePointBefore(i))) {
                return found;
            } else {
                fromIndex = i + 1;
            }
        }
        return new FindCharsResult();
    }

    private static int findEndOfHashtag(String string, int fromIndex) {
        final int length = string.length();
        for (int i = fromIndex + 1; i < length;) {
            int codepoint = string.codePointAt(i);
            if (Character.isWhitespace(codepoint)) {
                return i;
            } else if (codepoint == '#') {
                return -1;
            }
            i += Character.charCount(codepoint);
        }
        return length;
    }

    private static int findEndOfMention(String string, int fromIndex) {
        int atCount = 0;
        final int length = string.length();
        for (int i = fromIndex + 1; i < length;) {
            int codepoint = string.codePointAt(i);
            if (Character.isWhitespace(codepoint)) {
                return i;
            } else if (codepoint == '@') {
                atCount += 1;
                if (atCount >= 2) {
                    return -1;
                }
            }
            i += Character.charCount(codepoint);
        }
        return length;
    }

    private static void highlightSpans(Spannable text, int colour) {
        // Strip all existing colour spans.
        int n = text.length();
        ForegroundColorSpan[] oldSpans = text.getSpans(0, n, ForegroundColorSpan.class);
        for (int i = oldSpans.length - 1; i >= 0; i--) {
            text.removeSpan(oldSpans[i]);
        }
        // Colour the mentions and hashtags.
        String string = text.toString();
        int start;
        int end = 0;
        while (end < n) {
            char[] chars = { '#', '@' };
            FindCharsResult found = findStart(string, end, chars);
            start = found.stringIndex;
            if (start < 0) {
                break;
            }
            if (found.charIndex == 0) {
                end = findEndOfHashtag(string, start);
            } else if (found.charIndex == 1) {
                end = findEndOfMention(string, start);
            } else {
                break;
            }
            if (end < 0) {
                break;
            }
            text.setSpan(new ForegroundColorSpan(colour), start, end,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compose);

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

        SharedPreferences preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);

        floatingBtn = (Button) findViewById(R.id.floating_btn);
        pickBtn = (ImageButton) findViewById(R.id.compose_photo_pick);
        nsfwBtn = (Button) findViewById(R.id.action_toggle_nsfw);
        ImageButton visibilityBtn = (ImageButton) findViewById(R.id.action_toggle_visibility);

        floatingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendStatus();
            }
        });
        pickBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onMediaPick();
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

        Intent intent = getIntent();

        String startingVisibility;
        boolean startingHideText;
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

        updateNsfwButtonColor();

        String[] mentionedUsernames = null;
        inReplyToId = null;
        if (intent != null) {
            inReplyToId = intent.getStringExtra("in_reply_to_id");
            String replyVisibility = intent.getStringExtra("reply_visibility");

            if (replyVisibility != null && startingVisibility != null) {
                // Lowest possible visibility setting in response
                if (startingVisibility.equals("private") || replyVisibility.equals("private")) {
                    startingVisibility = "private";
                } else if (startingVisibility.equals("unlisted") || replyVisibility.equals("unlisted")) {
                    startingVisibility = "unlisted";
                } else {
                    startingVisibility = replyVisibility;
                }
            }

            mentionedUsernames = intent.getStringArrayExtra("mentioned_usernames");
        }
        /* Only after the starting visibility is determined and the send button is initialised can
         * the status visibility be set. */
        setStatusVisibility(startingVisibility);

        textEditor = createEditText(null); // new String[] { "image/gif", "image/webp" }
        final int mentionColour = ThemeUtils.getColor(this, R.attr.compose_mention_color);
        if (savedInstanceState != null) {
            restoreTextEditorState(savedInstanceState.getParcelable("textEditorState"));
            highlightSpans(textEditor.getText(), mentionColour);
        }
        RelativeLayout editArea = (RelativeLayout) findViewById(R.id.compose_edit_area);
        /* Adding this at index zero because it implicitly gives it the lowest input priority. So,
         * when media previews are added in front of the editor, they can receive click events
         * without the text editor stealing the events from behind them. */
        editArea.addView(textEditor, 0);
        contentWarningEditor = (EditText) findViewById(R.id.field_content_warning);
        final TextView charactersLeft = (TextView) findViewById(R.id.characters_left);
        textEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int left = STATUS_CHARACTER_LIMIT - s.length() - contentWarningEditor.length();
                charactersLeft.setText(String.format(Locale.getDefault(), "%d", left));
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable editable) {
                highlightSpans(editable, mentionColour);
            }
        });

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

        mediaPreviewBar = (LinearLayout) findViewById(R.id.compose_media_preview_bar);
        mediaQueued = new ArrayList<>();
        waitForMediaLatch = new CountUpDownLatch();

        contentWarningBar = findViewById(R.id.compose_content_warning_bar);
        contentWarningEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int left = STATUS_CHARACTER_LIMIT - s.length() - textEditor.length();
                charactersLeft.setText(String.format(Locale.getDefault(), "%d", left));
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        showContentWarning(startingHideText);

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
                        }
                    }
                }
            }
        }
    }

    private void toggleNsfw() {
        statusMarkSensitive = !statusMarkSensitive;
        updateNsfwButtonColor();
    }

    private void updateNsfwButtonColor() {
        if (statusMarkSensitive) {
            nsfwBtn.setTextColor(ThemeUtils.getColor(this, R.attr.compose_nsfw_button_selected_color));
        } else {
            nsfwBtn.setTextColor(ThemeUtils.getColor(this, R.attr.compose_nsfw_button_color));
        }
    }

    private void setStatusVisibility(String visibility) {
        statusVisibility = visibility;
        switch (visibility) {
            case "public": {
                floatingBtn.setText(R.string.action_send_public);
                floatingBtn.setCompoundDrawables(null, null, null, null);
                break;
            }
            case "private": {
                floatingBtn.setText(R.string.action_send);
                Drawable lock = AppCompatResources.getDrawable(this, R.drawable.send_private);
                if (lock != null) {
                    lock.setBounds(0, 0, lock.getIntrinsicWidth(), lock.getIntrinsicHeight());
                    floatingBtn.setCompoundDrawables(null, null, lock, null);
                }
                break;
            }
            default: {
                floatingBtn.setText(R.string.action_send);
                floatingBtn.setCompoundDrawables(null, null, null, null);
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

    public void onContentWarningChanged(boolean hideText) {
        showContentWarning(hideText);
    }

    private void sendStatus() {
        if (statusAlreadyInFlight) {
            return;
        }
        String contentText = textEditor.getText().toString();
        String spoilerText = "";
        if (statusHideText) {
            spoilerText = contentWarningEditor.getText().toString();
        }
        if (contentText.length() + spoilerText.length() <= STATUS_CHARACTER_LIMIT) {
            statusAlreadyInFlight = true;
            readyStatus(contentText, statusVisibility, statusMarkSensitive, spoilerText);
        } else {
            textEditor.setError(getString(R.string.error_compose_character_limit));
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
        outState.putParcelable("textEditorState", saveTextEditorState());
        if (currentInputContentInfo != null) {
            outState.putParcelable("commitContentInputContentInfo",
                    (Parcelable) currentInputContentInfo.unwrap());
            outState.putInt("commitContentFlags", currentFlags);
        }
        currentInputContentInfo = null;
        currentFlags = 0;
        super.onSaveInstanceState(outState);
    }

    private Parcelable saveTextEditorState() {
        Bundle bundle = new Bundle();
        bundle.putString("text", textEditor.getText().toString());
        bundle.putInt("selectionStart", textEditor.getSelectionStart());
        bundle.putInt("selectionEnd", textEditor.getSelectionEnd());
        return bundle;
    }

    private void restoreTextEditorState(Parcelable state) {
        Bundle bundle = (Bundle) state;
        textEditor.setText(bundle.getString("text"));
        int start = bundle.getInt("selectionStart");
        int end = bundle.getInt("selectionEnd");
        if (start != -1) {
            if (end != -1) {
                textEditor.setSelection(start, end);
            } else {
                textEditor.setSelection(start);
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (inReplyToId != null) {
            /* Don't save the visibility setting for replies because they adopt the visibility of
             * the status they reply to and that behaviour needs to be kept separate. */
            return;
        }
        SharedPreferences preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("rememberedVisibility", statusVisibility);
        editor.apply();
    }

    private EditText createEditText(String[] contentMimeTypes) {
        final String[] mimeTypes;
        if (contentMimeTypes == null || contentMimeTypes.length == 0) {
            mimeTypes = new String[0];
        } else {
            mimeTypes = Arrays.copyOf(contentMimeTypes, contentMimeTypes.length);
        }
        EditText editText = new android.support.v7.widget.AppCompatEditText(this) {
            @Override
            public InputConnection onCreateInputConnection(EditorInfo editorInfo) {
                final InputConnection ic = super.onCreateInputConnection(editorInfo);
                EditorInfoCompat.setContentMimeTypes(editorInfo, mimeTypes);
                final InputConnectionCompat.OnCommitContentListener callback =
                        new InputConnectionCompat.OnCommitContentListener() {
                            @Override
                            public boolean onCommitContent(InputContentInfoCompat inputContentInfo,
                                    int flags, Bundle opts) {
                                return ComposeActivity.this.onCommitContent(inputContentInfo, flags,
                                        mimeTypes);
                            }
                        };
                return InputConnectionCompat.createWrapper(ic, editorInfo, callback);
            }
        };
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        editText.setLayoutParams(layoutParams);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setEms(10);
        editText.setBackgroundColor(0);
        editText.setGravity(Gravity.START | Gravity.TOP);
        editText.setHint(R.string.hint_compose);
        return editText;
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
            public void onResponse(Call<Status> call, retrofit2.Response<Status> response) {
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
        finish();
    }

    private void onSendFailure() {
        textEditor.setError(getString(R.string.error_generic));
        statusAlreadyInFlight = false;
    }

    private void readyStatus(final String content, final String visibility, final boolean sensitive,
            final String spoilerText) {
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
                            sendStatus(content, visibility, sensitive, spoilerText);
                        } else {
                            onReadyFailure(content, visibility, sensitive, spoilerText);
                        }
                    }

                    @Override
                    protected void onCancelled() {
                        removeAllMediaFromQueue();
                        statusAlreadyInFlight = false;
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

    private void onReadyFailure(final String content, final String visibility,
            final boolean sensitive, final String spoilerText) {
        doErrorDialog(R.string.error_media_upload_sending, R.string.action_retry,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        readyStatus(content, visibility, sensitive, spoilerText);
                    }
                });
        statusAlreadyInFlight = false;
    }

    private void onMediaPick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.READ_EXTERNAL_STORAGE },
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

    private void initiateMediaPicking() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            intent.setType("image/* video/*");
        } else {
            String[] mimeTypes = new String[] { "image/*", "video/*" };
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        }
        startActivityForResult(intent, MEDIA_PICK_RESULT);
    }

    private void enableMediaPicking() {
        pickBtn.setEnabled(true);
        ThemeUtils.setDrawableTint(this, pickBtn.getDrawable(),
                R.attr.compose_media_button_tint);
    }

    private void disableMediaPicking() {
        pickBtn.setEnabled(false);
        ThemeUtils.setDrawableTint(this, pickBtn.getDrawable(),
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
                disableMediaPicking();
            }
        } else if (queuedCount >= Status.MAX_MEDIA_ATTACHMENTS) {
            // Limit the total media attachments, also.
            disableMediaPicking();
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
        enableMediaPicking();
        cancelReadyingMedia(item);
    }

    private void removeAllMediaFromQueue() {
        for (Iterator<QueuedMedia> it = mediaQueued.iterator(); it.hasNext();) {
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

    private static String randomAlphanumericString(int count) {
        char[] chars = new char[count];
        Random random = new Random();
        final String POSSIBLE_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        for (int i = 0; i < count; i++) {
            chars[i] = POSSIBLE_CHARS.charAt(random.nextInt(POSSIBLE_CHARS.length()));
        }
        return new String(chars);
    }

    @Nullable
    private static byte[] inputStreamGetBytes(InputStream stream) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int read;
        byte[] data = new byte[16384];
        try {
            while ((read = stream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, read);
            }
            buffer.flush();
        } catch (IOException e) {
            return null;
        }
        return buffer.toByteArray();
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
                    item.id = response.body().id;
                    waitForMediaLatch.countDown();
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

    private static long getMediaSize(ContentResolver contentResolver, Uri uri) {
        long mediaSize;
        Cursor cursor = contentResolver.query(uri, null, null, null, null);
        if (cursor != null) {
            int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
            cursor.moveToFirst();
            mediaSize = cursor.getLong(sizeIndex);
            cursor.close();
        } else {
            mediaSize = MEDIA_SIZE_UNKNOWN;
        }
        return mediaSize;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MEDIA_PICK_RESULT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            long mediaSize = getMediaSize(getContentResolver(), uri);
            pickMedia(uri, mediaSize);
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
                    Bitmap bitmap = ThumbnailUtils.extractThumbnail(source, 128, 128);
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
                    Bitmap bitmap = ThumbnailUtils.extractThumbnail(source, 128, 128);
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

        if(!showMarkSensitive) {
            statusMarkSensitive = false;
            nsfwBtn.setTextColor(ThemeUtils.getColor(this, R.attr.compose_nsfw_button_color));
        }

        if(show) {
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
}
