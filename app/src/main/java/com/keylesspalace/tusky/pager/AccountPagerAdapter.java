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

package com.keylesspalace.tusky.pager;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.fragment.AccountListFragment;
import com.keylesspalace.tusky.fragment.TimelineFragment;

public class AccountPagerAdapter extends FragmentPagerAdapter {
    private Context context;
    private String accountId;
    private String[] pageTitles;

    public AccountPagerAdapter(FragmentManager manager, Context context, String accountId) {
        super(manager);
        this.context = context;
        this.accountId = accountId;
    }

    public void setPageTitles(String[] titles) {
        pageTitles = titles;
    }

    @Override
    public Fragment getItem(int position) {
        switch (position) {
            case 0: {
                return TimelineFragment.newInstance(TimelineFragment.Kind.USER, accountId);
            }
            case 1: {
                return AccountListFragment.newInstance(AccountListFragment.Type.FOLLOWS, accountId);
            }
            case 2: {
                return AccountListFragment.newInstance(AccountListFragment.Type.FOLLOWERS, accountId);
            }
            default: {
                return null;
            }
        }
    }

    @Override
    public int getCount() {
        return 3;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return pageTitles[position];
    }

    public View getTabView(int position, ViewGroup root) {
        View view = LayoutInflater.from(context).inflate(R.layout.tab_account, root, false);
        TextView title = (TextView) view.findViewById(R.id.title);
        title.setText(pageTitles[position]);
        return view;
    }
}
