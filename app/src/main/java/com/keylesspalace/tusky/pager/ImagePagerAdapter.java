package com.keylesspalace.tusky.pager;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

import com.keylesspalace.tusky.fragment.ViewMediaFragment;

import java.util.Locale;

public class ImagePagerAdapter extends FragmentPagerAdapter {
    private String[] urls;
    private int initialPosition;

    public ImagePagerAdapter(FragmentManager fragmentManager, String[] urls, int initialPosition) {
        super(fragmentManager);
        this.urls = urls;
        this.initialPosition = initialPosition;
    }

    @Override
    public Fragment getItem(int position) {
        if (position >= 0 && position < urls.length) {
            return ViewMediaFragment.newInstance(urls[position], position == initialPosition);
        } else {
            return null;
        }
    }

    @Override
    public int getCount() {
        return urls.length;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return String.format(Locale.getDefault(), "%d/%d", position + 1, urls.length);
    }
}
