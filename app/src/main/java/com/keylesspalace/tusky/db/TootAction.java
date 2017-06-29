package com.keylesspalace.tusky.db;

import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import com.keylesspalace.tusky.TuskyApplication;

import java.util.List;

/**
 * Created by cto3543 on 29/06/2017.
 *
 */

public class TootAction {

    private static TootDao tootDao = TuskyApplication.getDB().tootDao();

    public static void getAllToot() {
        new AsyncTask<Void, Void, List<TootEntity>>() {
            @Override
            protected List<TootEntity> doInBackground(Void... params) {
                return tootDao.loadAll();
            }

            @Override
            protected void onPostExecute(List<TootEntity> tootEntities) {
                super.onPostExecute(tootEntities);
                for (TootEntity t : tootEntities) {
                    Log.e("toot", "id=" + t.getUid() + "text=" + t.getText());
                }
            }
        }.execute();
    }

    public static void saveTheToot(String s) {
        if (!TextUtils.isEmpty(s)) {
            final TootEntity toot = new TootEntity();
            toot.setText(s);
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    tootDao.insert(toot);
                    return null;
                }
            }.execute();
        }
    }

}
