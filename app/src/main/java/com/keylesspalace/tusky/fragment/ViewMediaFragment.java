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

package com.keylesspalace.tusky.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.github.chrisbanes.photoview.OnOutsidePhotoTapListener;
import com.github.chrisbanes.photoview.OnSingleFlingListener;
import com.github.chrisbanes.photoview.PhotoView;
import com.github.chrisbanes.photoview.PhotoViewAttacher;
import com.keylesspalace.tusky.R;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

public class ViewMediaFragment extends BaseFragment {
    public interface OnDismissListener {
        void onDismiss();
    }

    private PhotoViewAttacher attacher;
    private OnDismissListener onDismissListener;

    public static ViewMediaFragment newInstance(String url) {
        Bundle arguments = new Bundle();
        ViewMediaFragment fragment = new ViewMediaFragment();
        arguments.putString("url", url);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        onDismissListener = (OnDismissListener) context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container,
            Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_view_media, container, false);

        PhotoView photoView = (PhotoView) rootView.findViewById(R.id.view_media_image);

        Bundle arguments = getArguments();
        String url = arguments.getString("url");

        attacher = new PhotoViewAttacher(photoView);

        // Clicking outside the photo closes the viewer.
        attacher.setOnOutsidePhotoTapListener(new OnOutsidePhotoTapListener() {
            @Override
            public void onOutsidePhotoTap(ImageView imageView) {
                onDismissListener.onDismiss();
            }
        });

        /* A vertical swipe motion also closes the viewer. This is especially useful when the photo
         * mostly fills the screen so clicking outside is difficult. */
        attacher.setOnSingleFlingListener(new OnSingleFlingListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                                   float velocityY) {
                if (Math.abs(velocityY) > Math.abs(velocityX)) {
                    onDismissListener.onDismiss();
                    return true;
                }
                return false;
            }
        });

        Picasso.with(getContext())
                .load(url)
                .into(photoView, new Callback() {
                    @Override
                    public void onSuccess() {
                        rootView.findViewById(R.id.view_media_progress).setVisibility(View.GONE);
                        attacher.update();
                    }

                    @Override
                    public void onError() {}
                });

        return rootView;
    }
}
