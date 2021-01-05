package com.keylesspalace.tusky.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.keylesspalace.tusky.db.AppDatabase;
import com.keylesspalace.tusky.db.TootDao;
import com.keylesspalace.tusky.db.TootEntity;

import java.util.ArrayList;

import javax.inject.Inject;

public final class SaveTootHelper {

    private static final String TAG = "SaveTootHelper";

    private TootDao tootDao;
    private Context context;
    private Gson gson = new Gson();

    @Inject
    public SaveTootHelper(@NonNull AppDatabase appDatabase, @NonNull Context context) {
        this.tootDao = appDatabase.tootDao();
        this.context = context;
    }

    public void deleteDraft(int tootId) {
        TootEntity item = tootDao.find(tootId);
        if (item != null) {
            deleteDraft(item);
        }
    }

    public void deleteDraft(@NonNull TootEntity item) {
        // Delete any media files associated with the status.
        ArrayList<String> uris = gson.fromJson(item.getUrls(),
                new TypeToken<ArrayList<String>>() {
                }.getType());
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

}