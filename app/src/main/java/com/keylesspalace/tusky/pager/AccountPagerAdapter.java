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

import android.util.SparseArray;
import android.view.ViewGroup;

import com.keylesspalace.tusky.fragment.AccountMediaFragment;
import com.keylesspalace.tusky.fragment.TimelineFragment;
import com.keylesspalace.tusky.interfaces.RefreshableFragment;

import java.util.HashSet;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

public class AccountPagerAdapter extends FragmentPagerAdapter {
    private static final int TAB_COUNT = 4;
    private String accountId;
    private String[] pageTitles;

    private SparseArray<Fragment> fragments = new SparseArray<>(TAB_COUNT);

    private final Set<Integer> pagesToRefresh = new HashSet<>();

    public AccountPagerAdapter(FragmentManager manager, String accountId) {
        super(manager);
        this.accountId = accountId;
    }

    public void setPageTitles(String[] titles) {
        pageTitles = titles;
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        switch (position) {
            case 0: {
                return TimelineFragment.newInstance(TimelineFragment.Kind.USER, accountId,false);
            }
            case 1: {
                return TimelineFragment.newInstance(TimelineFragment.Kind.USER_WITH_REPLIES, accountId,false);
            }
            case 2: {
                return TimelineFragment.newInstance(TimelineFragment.Kind.USER_PINNED, accountId,false);
            }
            case 3: {
                return AccountMediaFragment.newInstance(accountId,false);
            }
            default: {
                throw new AssertionError("Page " + position + " is out of AccountPagerAdapter bounds");
            }
        }
    }

    @Override
    public int getCount() {
        return TAB_COUNT;
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        Object fragment = super.instantiateItem(container, position);
        if (fragment instanceof Fragment)
            fragments.put(position, (Fragment) fragment);
        if (pagesToRefresh.contains(position)) {
            if (fragment instanceof RefreshableFragment)
                ((RefreshableFragment) fragment).refreshContent();
            pagesToRefresh.remove(position);
        }
        return fragment;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        super.destroyItem(container, position, object);
        fragments.remove(position);
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return pageTitles[position];
    }

    @Nullable
    public Fragment getFragment(int position) {
        return fragments.get(position);
    }

    public void refreshContent(){
        for (int i=0;i<getCount();i++){
            Fragment fragment = getFragment(i);
            if (fragment instanceof RefreshableFragment){
                ((RefreshableFragment) fragment).refreshContent();
            }
            else{
                pagesToRefresh.add(i);
            }
        }
    }
}
