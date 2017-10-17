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

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.keylesspalace.tusky.adapter.SavedTootAdapter;
import com.keylesspalace.tusky.db.TootDao;
import com.keylesspalace.tusky.db.TootEntity;
import com.keylesspalace.tusky.util.ThemeUtils;

import java.util.ArrayList;
import java.util.List;

public class SavedTootActivity extends BaseActivity implements SavedTootAdapter.SavedTootAction {
    private static final String TAG = "SavedTootActivity"; // logging tag

    // dao
    private static TootDao tootDao = TuskyApplication.getDB().tootDao();

    // ui
    private SavedTootAdapter adapter;
    private TextView noContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_toot);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setTitle(getString(R.string.title_saved_toot));
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setDisplayShowHomeEnabled(true);
        }

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        noContent = findViewById(R.id.no_content);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        DividerItemDecoration divider = new DividerItemDecoration(
                this, layoutManager.getOrientation());
        Drawable drawable = ThemeUtils.getDrawable(this, R.attr.status_divider_drawable,
                R.drawable.status_divider_dark);
        divider.setDrawable(drawable);
        recyclerView.addItemDecoration(divider);
        adapter = new SavedTootAdapter(this);
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // req
        getAllToot();
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

    private void getAllToot() {
        new AsyncTask<Void, Void, List<TootEntity>>() {
            @Override
            protected List<TootEntity> doInBackground(Void... params) {
                return tootDao.loadAll();
            }

            @Override
            protected void onPostExecute(List<TootEntity> tootEntities) {
                super.onPostExecute(tootEntities);
                // set ui
                setNoContent(tootEntities.size());
                if (adapter != null) {
                    adapter.setItems(tootEntities);
                    adapter.notifyDataSetChanged();
                }
            }
        }.execute();
    }

    private void setNoContent(int size) {
        if (size == 0) {
            noContent.setVisibility(View.VISIBLE);
        } else {
            noContent.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void delete(int position, TootEntity item) {
        // Delete any media files associated with the status.
        ArrayList<String> uris = new Gson().fromJson(item.getUrls(),
                new TypeToken<ArrayList<String>>() {}.getType());
        if (uris != null) {
            for (String uriString : uris) {
                Uri uri = Uri.parse(uriString);
                if (getContentResolver().delete(uri, null, null) == 0) {
                    Log.e(TAG, String.format("Did not delete file %s.", uriString));
                }
            }
        }
        // update DB
        tootDao.delete(item);
        // update adapter
        if (adapter != null) {
            adapter.removeItem(position);
            setNoContent(adapter.getItemCount());
        }
    }

    @Override
    public void click(int position, TootEntity item) {
        Intent intent = new Intent(this, ComposeActivity.class);
        intent.putExtra("saved_toot_uid", item.getUid());
        intent.putExtra("saved_toot_text", item.getText());
        intent.putExtra("saved_toot_content_warning", item.getContentWarning());
        intent.putExtra("saved_json_urls", item.getUrls());
        startActivity(intent);
    }
}
