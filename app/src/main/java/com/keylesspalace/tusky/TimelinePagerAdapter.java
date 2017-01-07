package com.keylesspalace.tusky;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

public class TimelinePagerAdapter extends FragmentPagerAdapter {
    private String[] pageTitles;

    public TimelinePagerAdapter(FragmentManager manager) {
        super(manager);
    }

    public void setPageTitles(String[] titles) {
        pageTitles = titles;
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
                return TimelineFragment.newInstance(TimelineFragment.Kind.PUBLIC);
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
}
