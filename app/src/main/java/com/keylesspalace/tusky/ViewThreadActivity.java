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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.keylesspalace.tusky.fragment.ViewThreadFragment;
import com.keylesspalace.tusky.util.LinkHelper;

import javax.inject.Inject;

import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.support.HasSupportFragmentInjector;

public class ViewThreadActivity extends BottomSheetActivity implements HasSupportFragmentInjector {

    public static final int REVEAL_BUTTON_HIDDEN = 1;
    public static final int REVEAL_BUTTON_REVEAL = 2;
    public static final int REVEAL_BUTTON_HIDE = 3;

    public static Intent startIntent(Context context, String id, String url) {
        Intent intent = new Intent(context, ViewThreadActivity.class);
        intent.putExtra(ID_EXTRA, id);
        intent.putExtra(URL_EXTRA, url);
        return intent;
    }

    private static final String ID_EXTRA = "id";
    private static final String URL_EXTRA = "url";
    private static final String FRAGMENT_TAG = "ViewThreadFragment_";

    private int revealButtonState = REVEAL_BUTTON_HIDDEN;

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

        String id = getIntent().getStringExtra(ID_EXTRA);

        fragment = (ViewThreadFragment)getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG + id);
        if(fragment == null) {
            fragment = ViewThreadFragment.newInstance(id);
        }

        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, fragment, FRAGMENT_TAG + id);
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
                LinkHelper.openLink(getIntent().getStringExtra(URL_EXTRA), this);
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

}
