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
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

class AccountPagerAdapter extends FragmentPagerAdapter {
    private Context context;
    private String accountId;
    private String[] pageTitles;
    private List<Fragment> registeredFragments;

    AccountPagerAdapter(FragmentManager manager, Context context, String accountId) {
        super(manager);
        this.context = context;
        this.accountId = accountId;
        registeredFragments = new ArrayList<>();
    }

    void setPageTitles(String[] titles) {
        pageTitles = titles;
    }

    @Override
    public Fragment getItem(int position) {
        switch (position) {
            case 0: {
                return TimelineFragment.newInstance(TimelineFragment.Kind.USER, accountId);
            }
            case 1: {
                return AccountFragment.newInstance(AccountFragment.Type.FOLLOWS, accountId);
            }
            case 2: {
                return AccountFragment.newInstance(AccountFragment.Type.FOLLOWERS, accountId);
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

    View getTabView(int position, ViewGroup root) {
        View view = LayoutInflater.from(context).inflate(R.layout.tab_account, root, false);
        TextView title = (TextView) view.findViewById(R.id.title);
        title.setText(pageTitles[position]);
        return view;
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        Fragment fragment = (Fragment) super.instantiateItem(container, position);
        registeredFragments.add(fragment);
        return fragment;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        registeredFragments.remove((Fragment) object);
        super.destroyItem(container, position, object);
    }

    List<Fragment> getRegisteredFragments() {
        return registeredFragments;
    }
}
