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

import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import com.keylesspalace.tusky.adapter.SavedTootAdapter;
import com.keylesspalace.tusky.db.TootDao;
import com.keylesspalace.tusky.db.TootEntity;
import com.keylesspalace.tusky.util.ThemeUtils;

import java.util.List;

public class SavedTootActivity extends BaseActivity {

    // dao
    private static TootDao tootDao = TuskyApplication.getDB().tootDao();

    // ui
    private SavedTootAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_toot);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        DividerItemDecoration divider = new DividerItemDecoration(
                this, layoutManager.getOrientation());
        Drawable drawable = ThemeUtils.getDrawable(this, R.attr.status_divider_drawable,
                R.drawable.status_divider_dark);
        divider.setDrawable(drawable);
        recyclerView.addItemDecoration(divider);
        adapter = new SavedTootAdapter();
        recyclerView.setAdapter(adapter);

        getAllToot();
    }

    public void getAllToot() {
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
                adapter.addItems(tootEntities);
            }
        }.execute();
    }

}
