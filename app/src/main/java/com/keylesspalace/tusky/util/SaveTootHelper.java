package com.keylesspalace.tusky.util;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.keylesspalace.tusky.BuildConfig;
import com.keylesspalace.tusky.ComposeActivity;
import com.keylesspalace.tusky.db.TootDao;
import com.keylesspalace.tusky.db.TootEntity;
import com.keylesspalace.tusky.entity.Status;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SaveTootHelper {

    public static final String TAG = "SaveTootHelper";

    private TootDao tootDao;
    private Context context;

    public SaveTootHelper(@NonNull TootDao tootDao, @NonNull Context context) {
        this.tootDao = tootDao;
        this.context = context;
    }


    @SuppressLint("StaticFieldLeak")
    public boolean saveToot(@NonNull String content,
                             @NonNull String contentWarning,
                             @Nullable String savedJsonUrls,
                             @NonNull List<ComposeActivity.QueuedMedia> mediaQueued,
                             int savedTootUid,
                             @Nullable String inReplyToId,
                             @Nullable String replyingStatusContent,
                             @Nullable String replyingStatusAuthorUsername,
                             @NonNull Status.Visibility statusVisibility) {

        if (TextUtils.isEmpty(content) && mediaQueued.isEmpty()) {
            return false;
        }

        // Get any existing file's URIs.
        ArrayList<String> existingUris = null;
        if (!TextUtils.isEmpty(savedJsonUrls)) {
            existingUris = new Gson().fromJson(savedJsonUrls,
                    new TypeToken<ArrayList<String>>() {
                    }.getType());
        }

        String mediaUrlsSerialized = null;
        if (!ListUtils.isEmpty(mediaQueued)) {
            List<String> savedList = saveMedia(mediaQueued, existingUris);
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
        final TootEntity toot = new TootEntity(savedTootUid, content, mediaUrlsSerialized, contentWarning,
                inReplyToId,
                replyingStatusContent,
                replyingStatusAuthorUsername,
                statusVisibility);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                tootDao.insertOrReplace(toot);
                return null;
            }
        }.execute();
        return true;
    }

    @Nullable
    private List<String> saveMedia(@NonNull List<ComposeActivity.QueuedMedia> mediaQueued,
                                   @Nullable List<String> existingUris) {

        File imageDirectory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File videoDirectory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (imageDirectory == null || !(imageDirectory.exists() || imageDirectory.mkdirs())) {
            Log.e(TAG, "Image directory is not created.");
            return null;
        }
        if (videoDirectory == null || !(videoDirectory.exists() || videoDirectory.mkdirs())) {
            Log.e(TAG, "Video directory is not created.");
            return null;
        }
        ContentResolver contentResolver = context.getContentResolver();
        ArrayList<File> filesSoFar = new ArrayList<>();
        ArrayList<String> results = new ArrayList<>();
        for (ComposeActivity.QueuedMedia item : mediaQueued) {
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
            boolean copied = IOUtils.copyToFile(contentResolver, item.uri, file);
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
            Uri uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID+".fileprovider", file);
            results.add(uri.toString());
        }
        return results;
    }

    private void deleteMedia(List<String> mediaUris) {
        for (String uriString : mediaUris) {
            Uri uri = Uri.parse(uriString);
            if (context.getContentResolver().delete(uri, null, null) == 0) {
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

}
