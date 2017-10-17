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
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import com.keylesspalace.tusky.fragment.AccountListFragment;

public class AccountListActivity extends BaseActivity {
    enum Type {
        BLOCKS,
        MUTES,
        FOLLOW_REQUESTS,
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_list);

        Type type;
        Intent intent = getIntent();
        if (intent != null) {
            type = (Type) intent.getSerializableExtra("type");
        } else {
            type = Type.BLOCKS;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            switch (type) {
                case BLOCKS: { bar.setTitle(getString(R.string.title_blocks)); break; }
                case MUTES:  { bar.setTitle(getString(R.string.title_mutes));  break; }
                case FOLLOW_REQUESTS: {
                    bar.setTitle(getString(R.string.title_follow_requests));
                    break;
                }
            }
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setDisplayShowHomeEnabled(true);
        }

        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        AccountListFragment.Type fragmentType;
        switch (type) {
            default:
            case BLOCKS: { fragmentType = AccountListFragment.Type.BLOCKS; break; }
            case MUTES:  { fragmentType = AccountListFragment.Type.MUTES;  break; }
            case FOLLOW_REQUESTS: {
                fragmentType = AccountListFragment.Type.FOLLOW_REQUESTS;
                break;
            }
        }
        Fragment fragment = AccountListFragment.newInstance(fragmentType);
        fragmentTransaction.replace(R.id.fragment_container, fragment);
        fragmentTransaction.commit();
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
}
