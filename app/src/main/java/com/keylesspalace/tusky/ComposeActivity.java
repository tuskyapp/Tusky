/* Copyright 2017 Andrew Dawson
 *
 * This file is part of Tusky.
 *
 * Tusky is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky. If not, see
 * <http://www.gnu.org/licenses/>. */

package com.keylesspalace.tusky;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.OpenableColumns;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ComposeActivity extends BaseActivity {
    private static final String TAG = "ComposeActivity"; // logging tag, and volley request tag
    private static final int STATUS_CHARACTER_LIMIT = 500;
    private static final int STATUS_MEDIA_SIZE_LIMIT = 4000000; // 4MB
    private static final int MEDIA_PICK_RESULT = 1;
    private static final int PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;

    private String inReplyToId;
    private String domain;
    private String accessToken;
    private EditText textEditor;
    private ImageButton mediaPick;
    private LinearLayout mediaPreviewBar;
    private ArrayList<QueuedMedia> mediaQueued;
    private CountUpDownLatch waitForMediaLatch;
    private boolean showMarkSensitive;
    private String statusVisibility;     // The current values of the options that will be applied
    private boolean statusMarkSensitive; // to the status being composed.
    private boolean statusHideText;      //
    private View contentWarningBar;

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
        Request uploadRequest;
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

        SharedPreferences preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);

        ArrayList<SavedQueuedMedia> savedMediaQueued = null;
        if (savedInstanceState != null) {
            showMarkSensitive = savedInstanceState.getBoolean("showMarkSensitive");
            statusVisibility = savedInstanceState.getString("statusVisibility");
            statusMarkSensitive = savedInstanceState.getBoolean("statusMarkSensitive");
            statusHideText = savedInstanceState.getBoolean("statusHideText");
            // Keep these until everything needed to put them in the queue is finished initializing.
            savedMediaQueued = savedInstanceState.getParcelableArrayList("savedMediaQueued");
        } else {
            showMarkSensitive = false;
            statusVisibility = preferences.getString("rememberedVisibility", "public");
            statusMarkSensitive = false;
            statusHideText = false;
        }

        Intent intent = getIntent();
        String[] mentionedUsernames = null;
        if (intent != null) {
            inReplyToId = intent.getStringExtra("in_reply_to_id");
            mentionedUsernames = intent.getStringArrayExtra("mentioned_usernames");
        }

        domain = preferences.getString("domain", null);
        accessToken = preferences.getString("accessToken", null);

        textEditor = (EditText) findViewById(R.id.field_status);
        final TextView charactersLeft = (TextView) findViewById(R.id.characters_left);
        final int mentionColour = ThemeUtils.getColor(this, R.attr.compose_mention_color);
        TextWatcher textEditorWatcher = new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int left = STATUS_CHARACTER_LIMIT - s.length();
                charactersLeft.setText(Integer.toString(left));
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable editable) {
                highlightSpans(editable, mentionColour);
            }
        };
        textEditor.addTextChangedListener(textEditorWatcher);

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
        @DrawableRes int drawableId = ThemeUtils.getDrawableId(this,
                R.attr.compose_content_warning_bar_background, R.drawable.border_background_dark);
        contentWarningBar.setBackgroundResource(drawableId);
        final EditText contentWarningEditor = (EditText) findViewById(R.id.field_content_warning);
        showContentWarning(false);

        final Button sendButton = (Button) findViewById(R.id.button_send);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Editable editable = textEditor.getText();
                if (editable.length() <= STATUS_CHARACTER_LIMIT) {
                    String spoilerText = "";
                    if (statusHideText) {
                        spoilerText = contentWarningEditor.getText().toString();
                    }
                    readyStatus(editable.toString(), statusVisibility, statusMarkSensitive,
                            spoilerText);
                } else {
                    textEditor.setError(getString(R.string.error_compose_character_limit));
                }
            }
        });

        mediaPick = (ImageButton) findViewById(R.id.compose_photo_pick);
        mediaPick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onMediaPick();
            }
        });

        ImageButton options = (ImageButton) findViewById(R.id.compose_options);
        options.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ComposeOptionsFragment fragment = ComposeOptionsFragment.newInstance(
                        statusVisibility, statusMarkSensitive, statusHideText,
                        showMarkSensitive,
                        new ComposeOptionsFragment.Listener() {
                            @Override
                            public int describeContents() {
                                return 0;
                            }

                            @Override
                            public void writeToParcel(Parcel dest, int flags) {}

                            @Override
                            public void onVisibilityChanged(String visibility) {
                                statusVisibility = visibility;
                            }

                            @Override
                            public void onMarkSensitiveChanged(boolean markSensitive) {
                                statusMarkSensitive = markSensitive;
                            }

                            @Override
                            public void onContentWarningChanged(boolean hideText) {
                                showContentWarning(hideText);
                            }
                        });
                fragment.show(getSupportFragmentManager(), null);
            }
        });

        // These can only be added after everything affected by the media queue is initialized.
        if (savedMediaQueued != null) {
            for (SavedQueuedMedia item : savedMediaQueued) {
                addMediaToQueue(item.type, item.preview, item.uri, item.mediaSize);
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
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("rememberedVisibility", statusVisibility);
        editor.apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        VolleySingleton.getInstance(this).cancelAll(TAG);
    }

    private void sendStatus(String content, String visibility, boolean sensitive,
                            String spoilerText) {
        String endpoint = getString(R.string.endpoint_status);
        String url = "https://" + domain + endpoint;
        JSONObject parameters = new JSONObject();
        try {
            parameters.put("status", content);
            parameters.put("visibility", visibility);
            parameters.put("sensitive", sensitive);
            parameters.put("spoiler_text", spoilerText);
            if (inReplyToId != null) {
                parameters.put("in_reply_to_id", inReplyToId);
            }
            JSONArray mediaIds = new JSONArray();
            for (QueuedMedia item : mediaQueued) {
                mediaIds.put(item.id);
            }
            if (mediaIds.length() > 0) {
                parameters.put("media_ids", mediaIds);
            }
        } catch (JSONException e) {
            onSendFailure();
            return;
        }
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, parameters,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        onSendSuccess();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        onSendFailure();
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                return headers;
            }
        };
        VolleySingleton.getInstance(this).addToRequestQueue(request);
    }

    private void onSendSuccess() {
        Toast.makeText(this, getString(R.string.confirmation_send), Toast.LENGTH_SHORT).show();
        finish();
    }

    private void onSendFailure() {
        textEditor.setError(getString(R.string.error_sending_status));
    }

    private void readyStatus(final String content, final String visibility, final boolean sensitive,
            final String spoilerText) {
        final ProgressDialog dialog = ProgressDialog.show(
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
                        dialog.dismiss();
                        if (successful) {
                            sendStatus(content, visibility, sensitive, spoilerText);
                        } else {
                            onReadyFailure(content, visibility, sensitive, spoilerText);
                        }
                    }

                    @Override
                    protected void onCancelled() {
                        removeAllMediaFromQueue();
                        super.onCancelled();
                    }
                };
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
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
        mediaPick.setEnabled(true);
        ThemeUtils.setImageViewTint(mediaPick, R.attr.compose_media_button_tint);
        mediaPick.setImageResource(R.drawable.ic_media);
    }

    private void disableMediaPicking() {
        mediaPick.setEnabled(false);
        ThemeUtils.setImageViewTint(mediaPick, R.attr.compose_media_button_disabled_tint);
        mediaPick.setImageResource(R.drawable.ic_media_disabled);
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
        layoutParams.setMargins(margin, margin, margin, marginBottom);
        view.setLayoutParams(layoutParams);
        view.setImageBitmap(preview);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
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
        InputStream stream;
        try {
            stream = getContentResolver().openInputStream(item.uri);
        } catch (FileNotFoundException e) {
            onMediaDownsizeFailure(item);
            return;
        }
        Bitmap bitmap = BitmapFactory.decodeStream(stream);
        IOUtils.closeQuietly(stream);
        new DownsizeImageTask(STATUS_MEDIA_SIZE_LIMIT, new DownsizeImageTask.Listener() {
            @Override
            public void onSuccess(List<byte[]> contentList) {
                item.content = contentList.get(0);
                uploadMedia(item);
            }

            @Override
            public void onFailure() {
                onMediaDownsizeFailure(item);
            }
        }).execute(bitmap);
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

        String endpoint = getString(R.string.endpoint_media);
        String url = "https://" + domain + endpoint;

        final String mimeType = getContentResolver().getType(item.uri);
        MimeTypeMap map = MimeTypeMap.getSingleton();
        String fileExtension = map.getExtensionFromMimeType(mimeType);
        final String filename = String.format("%s_%s_%s.%s",
                getString(R.string.app_name),
                String.valueOf(new Date().getTime()),
                randomAlphanumericString(10),
                fileExtension);

        MultipartRequest request = new MultipartRequest(Request.Method.POST, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            item.id = response.getString("id");
                        } catch (JSONException e) {
                            onUploadFailure(item);
                            return;
                        }
                        waitForMediaLatch.countDown();
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        onUploadFailure(item);
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                return headers;
            }

            @Override
            public DataItem getData() {
                byte[] content = item.content;
                if (content == null) {
                    InputStream stream;
                    try {
                        stream = getContentResolver().openInputStream(item.uri);
                    } catch (FileNotFoundException e) {
                        return null;
                    }
                    content = inputStreamGetBytes(stream);
                    IOUtils.closeQuietly(stream);
                    if (content == null) {
                        return null;
                    }
                }
                DataItem data = new DataItem();
                data.name = "file";
                data.filename = filename;
                data.mimeType = mimeType;
                data.content = content;
                return data;
            }
        };
        request.setTag(TAG);
        item.uploadRequest = request;
        VolleySingleton.getInstance(this).addToRequestQueue(request);
    }

    private void onUploadFailure(QueuedMedia item) {
        displayTransientError(R.string.error_media_upload_sending);
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
            ContentResolver contentResolver = getContentResolver();
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor == null) {
                displayTransientError(R.string.error_media_upload_opening);
                return;
            }
            int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
            cursor.moveToFirst();
            long mediaSize = cursor.getLong(sizeIndex);
            cursor.close();
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
                        Bitmap bitmap = ThumbnailUtils.extractThumbnail(source, 96, 96);
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
                        Bitmap bitmap = ThumbnailUtils.extractThumbnail(source, 96, 96);
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
    }

    void showMarkSensitive(boolean show) {
        showMarkSensitive = show;
        if(!showMarkSensitive) {
            statusMarkSensitive = false;
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
}
