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

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

import com.keylesspalace.tusky.fragment.NotificationsFragment;
import com.keylesspalace.tusky.fragment.TimelineFragment;

public class TimelinePagerAdapter extends FragmentPagerAdapter {
    public TimelinePagerAdapter(FragmentManager manager) {
        super(manager);
    }

    @Override
    public Fragment getItem(int i) {
        switch (i) {
            case 0: {
                return TimelineFragment.newInstance(TimelineFragment.Kind.HOME);
            }
            case 1: {
                return NotificationsFragment.newInstance();
            }
            case 2: {
                return TimelineFragment.newInstance(TimelineFragment.Kind.PUBLIC_LOCAL);
            }
            case 3: {
                return TimelineFragment.newInstance(TimelineFragment.Kind.PUBLIC_FEDERATED);
            }
            default: {
                return null;
            }
        }
    }

    @Override
    public int getCount() {
        return 4;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return null;
    }
}
