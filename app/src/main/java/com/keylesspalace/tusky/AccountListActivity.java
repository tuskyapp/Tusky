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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import com.keylesspalace.tusky.fragment.AccountListFragment;

public final class AccountListActivity extends BaseActivity {

    private static final String TYPE_EXTRA = "type";
    private static final String ARG_EXTRA = "arg";

    public static Intent newIntent(@NonNull Context context, @NonNull Type type,
                                   @Nullable String argument) {
        Intent intent = new Intent(context, AccountListActivity.class);
        intent.putExtra(TYPE_EXTRA, type);
        if (argument != null) {
            intent.putExtra(ARG_EXTRA, argument);
        }
        return intent;
    }

    enum Type {
        BLOCKS,
        MUTES,
        FOLLOW_REQUESTS,
        FOLLOWERS,
        FOLLOWING,
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
                case BLOCKS: {
                    bar.setTitle(getString(R.string.title_blocks));
                    break;
                }
                case MUTES: {
                    bar.setTitle(getString(R.string.title_mutes));
                    break;
                }
                case FOLLOW_REQUESTS: {
                    bar.setTitle(getString(R.string.title_follow_requests));
                    break;
                }
                case FOLLOWERS:
                    bar.setTitle(getString(R.string.title_followers));
                    break;
                case FOLLOWING:
                    bar.setTitle(getString(R.string.title_follows));
            }
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setDisplayShowHomeEnabled(true);
        }

        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        AccountListFragment fragment;
        switch (type) {
            default:
            case BLOCKS: {
                fragment = AccountListFragment.newInstance(AccountListFragment.Type.BLOCKS);
                break;
            }
            case MUTES: {
                fragment = AccountListFragment.newInstance(AccountListFragment.Type.MUTES);
                break;
            }
            case FOLLOWERS: {
                String argument = intent.getStringExtra(ARG_EXTRA);
                fragment = AccountListFragment.newInstance(AccountListFragment.Type.FOLLOWERS, argument);
                break;
            }
            case FOLLOWING: {
                String argument = intent.getStringExtra(ARG_EXTRA);
                fragment = AccountListFragment.newInstance(AccountListFragment.Type.FOLLOWS, argument);
                break;
            }
            case FOLLOW_REQUESTS: {
                fragment = AccountListFragment.newInstance(AccountListFragment.Type.FOLLOW_REQUESTS);
                break;
            }
        }
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
