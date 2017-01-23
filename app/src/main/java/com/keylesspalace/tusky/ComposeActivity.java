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
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ComposeActivity extends AppCompatActivity {
    private static final int STATUS_CHARACTER_LIMIT = 500;
    private static final int STATUS_MEDIA_SIZE_LIMIT = 4000000; // 4MB
    private static final int MEDIA_PICK_RESULT = 1;
    private static final int PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;
    private static final Pattern mentionPattern = Pattern.compile("\\B@[^\\s@]+@?[^\\s@]+");

    private String inReplyToId;
    private String domain;
    private String accessToken;
    private EditText textEditor;
    private ImageButton mediaPick;
    private CheckBox markSensitive;
    private LinearLayout mediaPreviewBar;
    private List<QueuedMedia> mediaQueued;
    private CountUpDownLatch waitForMediaLatch;

    private static class QueuedMedia {
        public enum Type {
            IMAGE,
            VIDEO
        }

        public enum ReadyStage {
            DOWNSIZING,
            UPLOADING,
        }

        private Type type;
        private ImageView preview;
        private Uri uri;
        private String id;
        private ReadyStage readyStage;
        private byte[] content;

        public QueuedMedia(Type type, Uri uri, ImageView preview) {
            this.type = type;
            this.uri = uri;
            this.preview = preview;
        }

        public Type getType() {
            return type;
        }

        public ImageView getPreview() {
            return preview;
        }

        public Uri getUri() {
            return uri;
        }

        public String getId() {
            return id;
        }

        public byte[] getContent() {
            return content;
        }

        public ReadyStage getReadyStage() {
            return readyStage;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setReadyStage(ReadyStage readyStage) {
            this.readyStage = readyStage;
        }

        public void setContent(byte[] content) {
            this.content = content;
        }
    }

    private void doErrorDialog(int descriptionId, int actionId, View.OnClickListener listener) {
        Snackbar bar = Snackbar.make(findViewById(R.id.activity_compose), getString(descriptionId),
                Snackbar.LENGTH_SHORT);
        bar.setAction(actionId, listener);
        bar.show();
    }

    private void displayTransientError(int stringId) {
        Snackbar.make(findViewById(R.id.activity_compose), stringId, Snackbar.LENGTH_LONG).show();
    }

    private static class Interval {
        public int start;
        public int end;
    }

    private static void colourMentions(Spannable text, int colour) {
        // Strip all existing colour spans.
        int n = text.length();
        ForegroundColorSpan[] oldSpans = text.getSpans(0, n, ForegroundColorSpan.class);
        for (int i = oldSpans.length - 1; i >= 0; i--) {
            text.removeSpan(oldSpans[i]);
        }
        // Match a list of new colour spans.
        List<Interval> intervals = new ArrayList<>();
        Matcher matcher = mentionPattern.matcher(text);
        while (matcher.find()) {
            Interval interval = new Interval();
            interval.start = matcher.start();
            interval.end = matcher.end();
            intervals.add(interval);
        }
        // Make sure intervals don't overlap.
        Collections.sort(intervals, new Comparator<Interval>() {
            @Override
            public int compare(Interval a, Interval b) {
                return a.start - b.start;
            }
        });
        for (int i = 0, j = 0; i < intervals.size() - 1; i++, j++) {
            if (j != 0) {
                Interval a = intervals.get(j - 1);
                Interval b = intervals.get(i);
                if (a.start <= b.end) {
                    while (j != 0 && a.start <= b.end) {
                        a = intervals.get(j - 1);
                        b = intervals.get(i);
                        a.end = Math.max(a.end, b.end);
                        a.start = Math.min(a.start, b.start);
                        j--;
                    }
                } else {
                    intervals.set(j, b);
                }
            } else {
                intervals.set(j, intervals.get(i));
            }
        }
        // Finally, set the spans.
        for (Interval interval : intervals) {
            text.setSpan(new ForegroundColorSpan(colour), interval.start, interval.end,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compose);

        Intent intent = getIntent();
        String[] mentionedUsernames = null;
        if (intent != null) {
            inReplyToId = intent.getStringExtra("in_reply_to_id");
            mentionedUsernames = intent.getStringArrayExtra("mentioned_usernames");
        }

        SharedPreferences preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        domain = preferences.getString("domain", null);
        accessToken = preferences.getString("accessToken", null);
        assert(domain != null);
        assert(accessToken != null);

        textEditor = (EditText) findViewById(R.id.field_status);
        final TextView charactersLeft = (TextView) findViewById(R.id.characters_left);
        final int mentionColour = ContextCompat.getColor(this, R.color.compose_mention);
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
                colourMentions(editable, mentionColour);
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

        final RadioGroup radio = (RadioGroup) findViewById(R.id.radio_visibility);
        final Button sendButton = (Button) findViewById(R.id.button_send);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Editable editable = textEditor.getText();
                if (editable.length() <= STATUS_CHARACTER_LIMIT) {
                    int id = radio.getCheckedRadioButtonId();
                    String visibility;
                    switch (id) {
                        default:
                        case R.id.radio_public: {
                            visibility = "public";
                            break;
                        }
                        case R.id.radio_unlisted: {
                            visibility = "unlisted";
                            break;
                        }
                        case R.id.radio_private: {
                            visibility = "private";
                            break;
                        }
                    }
                    readyStatus(editable.toString(), visibility, markSensitive.isChecked());
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
        markSensitive = (CheckBox) findViewById(R.id.compose_mark_sensitive);
        markSensitive.setVisibility(View.GONE);
    }

    private void onSendSuccess() {
        Toast.makeText(this, getString(R.string.confirmation_send), Toast.LENGTH_SHORT).show();
        finish();
    }

    private void onSendFailure(Exception exception) {
        textEditor.setError(getString(R.string.error_sending_status));
    }

    private void sendStatus(String content, String visibility, boolean sensitive) {
        String endpoint = getString(R.string.endpoint_status);
        String url = "https://" + domain + endpoint;
        JSONObject parameters = new JSONObject();
        try {
            parameters.put("status", content);
            parameters.put("visibility", visibility);
            parameters.put("sensitive", sensitive);
            if (inReplyToId != null) {
                parameters.put("in_reply_to_id", inReplyToId);
            }
            JSONArray media_ids = new JSONArray();
            for (QueuedMedia item : mediaQueued) {
                media_ids.put(item.getId());
            }
            if (media_ids.length() > 0) {
                parameters.put("media_ids", media_ids);
            }
        } catch (JSONException e) {
            onSendFailure(e);
            return;
        }
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, parameters,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        onSendSuccess();
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                onSendFailure(error);
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

    private void readyStatus(final String content, final String visibility,
                             final boolean sensitive) {
        final ProgressDialog dialog = ProgressDialog.show(this, "Finishing Media Upload",
                "Uploading...", true, true);
        final AsyncTask<Void, Void, Boolean> waitForMediaTask =
                new AsyncTask<Void, Void, Boolean>() {
                    private Exception exception;

                    @Override
                    protected Boolean doInBackground(Void... params) {
                        try {
                            waitForMediaLatch.await();
                        } catch (InterruptedException e) {
                            exception = e;
                            return false;
                        }
                        return true;
                    }

                    @Override
                    protected void onPostExecute(Boolean successful) {
                        super.onPostExecute(successful);
                        dialog.dismiss();
                        if (successful) {
                            sendStatus(content, visibility, sensitive);
                        } else {
                            onReadyFailure(exception, content, visibility, sensitive);
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

    private void onReadyFailure(Exception exception, final String content, final String visibility,
                                final boolean sensitive) {
        doErrorDialog(R.string.error_media_upload_sending, R.string.action_retry,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        readyStatus(content, visibility, sensitive);
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

    /** A replacement for View.setPaddingRelative to use under API level 16. */
    private static void setPaddingRelative(View view, int left, int top, int right, int bottom) {
        view.setPadding(
                view.getPaddingLeft() + left,
                view.getPaddingTop() + top,
                view.getPaddingRight() + right,
                view.getPaddingBottom() + bottom);
    }

    private void enableMediaPicking() {
        mediaPick.setEnabled(true);
    }

    private void disableMediaPicking() {
        mediaPick.setEnabled(false);
    }

    private void addMediaToQueue(QueuedMedia.Type type, Bitmap preview, Uri uri, long mediaSize) {
        assert(mediaQueued.size() < Status.MAX_MEDIA_ATTACHMENTS);
        final QueuedMedia item = new QueuedMedia(type, uri, new ImageView(this));
        ImageView view = item.getPreview();
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
            setPaddingRelative(textEditor, 0, 0, 0, totalHeight);
            // If there's one video in the queue it is full, so disable the button to queue more.
            if (item.getType() == QueuedMedia.Type.VIDEO) {
                disableMediaPicking();
            }
        } else if (queuedCount >= Status.MAX_MEDIA_ATTACHMENTS) {
            // Limit the total media attachments, also.
            disableMediaPicking();
        }
        if (queuedCount >= 1) {
            markSensitive.setVisibility(View.VISIBLE);
        }
        waitForMediaLatch.countUp();
        if (mediaSize > STATUS_MEDIA_SIZE_LIMIT && type == QueuedMedia.Type.IMAGE) {
            downsizeMedia(item);
        } else {
            uploadMedia(item);
        }
    }

    private void removeMediaFromQueue(QueuedMedia item) {
        int moveBottom = mediaPreviewBar.getMeasuredHeight();
        mediaPreviewBar.removeView(item.getPreview());
        mediaQueued.remove(item);
        if (mediaQueued.size() == 0) {
            markSensitive.setVisibility(View.GONE);
            /* If there are no image previews to show, the extra padding that was added to the
             * EditText can be removed so there isn't unnecessary empty space. */
            setPaddingRelative(textEditor, 0, 0, 0, moveBottom);
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
        item.setReadyStage(QueuedMedia.ReadyStage.DOWNSIZING);
        InputStream stream;
        try {
            stream = getContentResolver().openInputStream(item.getUri());
        } catch (FileNotFoundException e) {
            onMediaDownsizeFailure(item);
            return;
        }
        Bitmap bitmap = BitmapFactory.decodeStream(stream);
        IOUtils.closeQuietly(stream);
        new DownsizeImageTask(STATUS_MEDIA_SIZE_LIMIT, new DownsizeImageTask.Listener() {
            @Override
            public void onSuccess(List<byte[]> contentList) {
                item.setContent(contentList.get(0));
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
        item.setReadyStage(QueuedMedia.ReadyStage.UPLOADING);

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
                            item.setId(response.getString("id"));
                        } catch (JSONException e) {
                            onUploadFailure(item, e);
                            return;
                        }
                        waitForMediaLatch.countDown();
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        onUploadFailure(item, error);
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
                byte[] content = item.getContent();
                if (content == null) {
                    InputStream stream;
                    try {
                        stream = getContentResolver().openInputStream(item.getUri());
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
        request.addMarker("media_" + item.getUri().toString());
        VolleySingleton.getInstance(this).addToRequestQueue(request);
    }

    private void onUploadFailure(QueuedMedia item, @Nullable Exception exception) {
        displayTransientError(R.string.error_media_upload_sending);
        removeMediaFromQueue(item);
    }

    private void cancelReadyingMedia(QueuedMedia item) {
        if (item.getReadyStage() == QueuedMedia.ReadyStage.UPLOADING) {
            VolleySingleton.getInstance(this).cancelRequest("media_" + item.getUri().toString());
        }
        waitForMediaLatch.countDown();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MEDIA_PICK_RESULT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            ContentResolver contentResolver = getContentResolver();
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
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
                                && mediaQueued.get(0).getType() == QueuedMedia.Type.IMAGE) {
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
}
