package com.keylesspalace.tusky.util;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.keylesspalace.tusky.BuildConfig;
import com.keylesspalace.tusky.db.TootDao;
import com.keylesspalace.tusky.db.TootEntity;
import com.keylesspalace.tusky.entity.Status;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class SaveTootHelper {

    private static final String TAG = "SaveTootHelper";

    private TootDao tootDao;
    private Context context;
    private Gson gson = new Gson();

    public SaveTootHelper(@NonNull TootDao tootDao, @NonNull Context context) {
        this.tootDao = tootDao;
        this.context = context;
    }

    @SuppressLint("StaticFieldLeak")
    public boolean saveToot(@NonNull String content,
                             @NonNull String contentWarning,
                             @Nullable String savedJsonUrls,
                             @NonNull List<String> mediaUris,
                             @NonNull List<String> mediaDescriptions,
                             int savedTootUid,
                             @Nullable String inReplyToId,
                             @Nullable String replyingStatusContent,
                             @Nullable String replyingStatusAuthorUsername,
                             @NonNull Status.Visibility statusVisibility) {

        if (TextUtils.isEmpty(content) && mediaUris.isEmpty()) {
            return false;
        }

        // Get any existing file's URIs.
        ArrayList<String> existingUris = null;
        if (!TextUtils.isEmpty(savedJsonUrls)) {
            existingUris = gson.fromJson(savedJsonUrls,
                    new TypeToken<ArrayList<String>>() {
                    }.getType());
        }

        String mediaUrlsSerialized = null;
        String mediaDescriptionsSerialized = null;

        if (!ListUtils.isEmpty(mediaUris)) {
            List<String> savedList = saveMedia(mediaUris, existingUris);
            if (!ListUtils.isEmpty(savedList)) {
                mediaUrlsSerialized = gson.toJson(savedList);
                if (!ListUtils.isEmpty(existingUris)) {
                    deleteMedia(setDifference(existingUris, savedList));
                }
            } else {
                return false;
            }
            mediaDescriptionsSerialized = gson.toJson(mediaDescriptions);
        } else if (!ListUtils.isEmpty(existingUris)) {
            /* If there were URIs in the previous draft, but they've now been removed, those files
             * can be deleted. */
            deleteMedia(existingUris);
        }
        final TootEntity toot = new TootEntity(savedTootUid, content, mediaUrlsSerialized, mediaDescriptionsSerialized, contentWarning,
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

    public void deleteDraft(int tootId) {
        TootEntity item = tootDao.find(tootId);
        if(item != null) {
            deleteDraft(item);
        }
    }

    public void deleteDraft(@NonNull TootEntity item){
        // Delete any media files associated with the status.
        ArrayList<String> uris = gson.fromJson(item.getUrls(),
                new TypeToken<ArrayList<String>>() {}.getType());
        if (uris != null) {
            for (String uriString : uris) {
                Uri uri = Uri.parse(uriString);
                if (context.getContentResolver().delete(uri, null, null) == 0) {
                    Log.e(TAG, String.format("Did not delete file %s.", uriString));
                }
            }
        }
        // update DB
        tootDao.delete(item.getUid());
    }

    @Nullable
    private List<String> saveMedia(@NonNull List<String> mediaUris,
                                   @Nullable List<String> existingUris) {

        File directory = context.getExternalFilesDir("Tusky");

        if (directory == null || !(directory.exists())) {
            Log.e(TAG, "Error obtaining directory to save media.");
            return null;
        }

        ContentResolver contentResolver = context.getContentResolver();
        ArrayList<File> filesSoFar = new ArrayList<>();
        ArrayList<String> results = new ArrayList<>();
        for (String mediaUri : mediaUris) {
            /* If the media was already saved in a previous draft, there's no need to save another
             * copy, just add the existing URI to the results. */
            if (existingUris != null) {
                int index = existingUris.indexOf(mediaUri);
                if (index != -1) {
                    results.add(mediaUri);
                    continue;
                }
            }
            // Otherwise, save the media.

            Uri uri = Uri.parse(mediaUri);

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

            String mimeType = contentResolver.getType(uri);
            MimeTypeMap map = MimeTypeMap.getSingleton();
            String fileExtension = map.getExtensionFromMimeType(mimeType);
            String filename = String.format("Tusky_Draft_Media_%s.%s", timeStamp, fileExtension);
            File file = new File(directory, filename);
            filesSoFar.add(file);
            boolean copied = IOUtils.copyToFile(contentResolver, uri, file);
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
            Uri resultUri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID+".fileprovider", file);
            results.add(resultUri.toString());
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
