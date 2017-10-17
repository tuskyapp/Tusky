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
import android.support.v4.view.ViewCompat;
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
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;

public class ViewMediaFragment extends BaseFragment {
    public interface PhotoActionsListener {
        void onBringUp();
        void onDismiss();
        void onPhotoTap();
    }

    private PhotoViewAttacher attacher;
    private PhotoActionsListener photoActionsListener;
    private View rootView;
    private PhotoView photoView;

    private static final String ARG_START_POSTPONED_TRANSITION = "startPostponedTransition";

    public static ViewMediaFragment newInstance(String url, boolean shouldStartPostponedTransition) {
        Bundle arguments = new Bundle();
        ViewMediaFragment fragment = new ViewMediaFragment();
        arguments.putString("url", url);
        arguments.putBoolean(ARG_START_POSTPONED_TRANSITION, shouldStartPostponedTransition);

        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        photoActionsListener = (PhotoActionsListener) context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_view_media, container, false);
        photoView = rootView.findViewById(R.id.view_media_image);

        final Bundle arguments = getArguments();
        final String url = arguments.getString("url");

        attacher = new PhotoViewAttacher(photoView);

        // Clicking outside the photo closes the viewer.
        attacher.setOnOutsidePhotoTapListener(new OnOutsidePhotoTapListener() {
            @Override
            public void onOutsidePhotoTap(ImageView imageView) {
                photoActionsListener.onDismiss();
            }
        });

        attacher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                photoActionsListener.onPhotoTap();
            }
        });

        /* A vertical swipe motion also closes the viewer. This is especially useful when the photo
         * mostly fills the screen so clicking outside is difficult. */
        attacher.setOnSingleFlingListener(new OnSingleFlingListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                                   float velocityY) {
                if (Math.abs(velocityY) > Math.abs(velocityX)) {
                    photoActionsListener.onDismiss();
                    return true;
                }
                return false;
            }
        });

        ViewCompat.setTransitionName(photoView, url);

        // If we are the view to be shown initially...
        if (arguments.getBoolean(ARG_START_POSTPONED_TRANSITION)) {
            // Try to load image from disk.
            Picasso.with(getContext())
                    .load(url)
                    .noFade()
                    .networkPolicy(NetworkPolicy.OFFLINE)
                    .into(photoView, new Callback() {
                        @Override
                        public void onSuccess() {
                            // if we loaded image from disk, we should check that view is attached.
                            if (ViewCompat.isAttachedToWindow(photoView)) {
                                finishLoadingSuccessfully();
                            } else {
                                // if view is not attached yet, wait for an attachment and
                                // start transition when it's finally ready.
                                photoView.addOnAttachStateChangeListener(
                                        new View.OnAttachStateChangeListener() {
                                            @Override
                                            public void onViewAttachedToWindow(View v) {
                                                finishLoadingSuccessfully();
                                                photoView.removeOnAttachStateChangeListener(this);
                                            }

                                            @Override
                                            public void onViewDetachedFromWindow(View v) {
                                            }
                                        });
                            }
                        }

                        @Override
                        public void onError() {
                            // if there's no image in cache, load from network and start transition
                            // immediately.
                            photoActionsListener.onBringUp();

                            loadImageFromNetwork(url, photoView);
                        }
                    });
        } else {
            // if we're not initial page, don't bother.
            loadImageFromNetwork(url, photoView);
        }

        return rootView;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Picasso.with(getContext())
                .cancelRequest(photoView);
    }

    private void loadImageFromNetwork(String url, ImageView photoView) {
        Picasso.with(getContext())
                .load(url)
                .noPlaceholder()
                .into(photoView, new Callback() {
                    @Override
                    public void onSuccess() {
                        finishLoadingSuccessfully();
                    }

                    @Override
                    public void onError() {
                        rootView.findViewById(R.id.view_media_progress).setVisibility(View.GONE);
                    }
                });
    }

    private void finishLoadingSuccessfully() {
        rootView.findViewById(R.id.view_media_progress).setVisibility(View.GONE);
        attacher.update();
        photoActionsListener.onBringUp();
    }
}
