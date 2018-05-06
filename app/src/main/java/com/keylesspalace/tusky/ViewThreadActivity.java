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

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.keylesspalace.tusky.fragment.ViewThreadFragment;
import com.keylesspalace.tusky.network.MastodonApi;
import com.keylesspalace.tusky.util.LinkHelper;

import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.support.HasSupportFragmentInjector;

public class ViewThreadActivity extends BottomSheetActivity implements HasSupportFragmentInjector {

    public static final int REVEAL_BUTTON_HIDDEN = 1;
    public static final int REVEAL_BUTTON_REVEAL = 2;
    public static final int REVEAL_BUTTON_HIDE = 3;

    private int revealButtonState = REVEAL_BUTTON_HIDDEN;

    @Inject
    public MastodonApi mastodonApi;
    @Inject
    public DispatchingAndroidInjector<Fragment> dispatchingAndroidInjector;

    private ViewThreadFragment fragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_thread);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.title_view_thread);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }

        String id = getIntent().getStringExtra("id");
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragment = ViewThreadFragment.newInstance(id);
        fragmentTransaction.replace(R.id.fragment_container, fragment);
        fragmentTransaction.commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.view_thread_toolbar, menu);
        MenuItem menuItem = menu.findItem(R.id.action_reveal);
        menuItem.setVisible(revealButtonState != REVEAL_BUTTON_HIDDEN);
        menuItem.setIcon(revealButtonState == REVEAL_BUTTON_REVEAL ?
        R.drawable.ic_eye_24dp : R.drawable.ic_hide_media_24dp);
        return super.onCreateOptionsMenu(menu);
    }

    public void setRevealButtonState(int state) {
        switch (state) {
            case REVEAL_BUTTON_HIDDEN:
            case REVEAL_BUTTON_REVEAL:
            case REVEAL_BUTTON_HIDE:
                this.revealButtonState = state;
                invalidateOptionsMenu();
                break;
            default:
                throw new IllegalArgumentException("Invalid reveal button state: " + state);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                onBackPressed();
                return true;
            }
            case R.id.action_open_in_web: {
                LinkHelper.openLink(getIntent().getStringExtra("url"), this);
                return true;
            }
            case R.id.action_reveal: {
                fragment.onRevealPressed();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public AndroidInjector<Fragment> supportFragmentInjector() {
        return dispatchingAndroidInjector;
    }

    @NotNull
    @Override
    public MastodonApi getMastodonApi() {
        return mastodonApi;
    }
}
