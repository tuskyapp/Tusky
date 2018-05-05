package com.keylesspalace.tusky.pager;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

import com.keylesspalace.tusky.entity.Attachment;
import com.keylesspalace.tusky.fragment.ViewMediaFragment;

import java.util.List;
import java.util.Locale;

public final class ImagePagerAdapter extends FragmentPagerAdapter {

    private List<Attachment> attachments;
    private int initialPosition;

    public ImagePagerAdapter(FragmentManager fragmentManager, List<Attachment> attachments, int initialPosition) {
        super(fragmentManager);
        this.attachments = attachments;
        this.initialPosition = initialPosition;
    }

    @Override
    public Fragment getItem(int position) {
        if (position >= 0 && position < attachments.size()) {
            return ViewMediaFragment.newInstance(attachments.get(position), position == initialPosition);
        } else {
            return null;
        }
    }

    @Override
    public int getCount() {
        return attachments.size();
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return String.format(Locale.getDefault(), "%d/%d", position + 1, attachments.size());
    }
}
