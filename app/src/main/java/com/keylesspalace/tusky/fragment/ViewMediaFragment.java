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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.chrisbanes.photoview.PhotoView;
import com.github.chrisbanes.photoview.PhotoViewAttacher;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.ViewMediaActivity;
import com.keylesspalace.tusky.entity.Attachment;
import com.squareup.picasso.Callback;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;

import java.util.Objects;

import kotlin.jvm.functions.Function0;

public final class ViewMediaFragment extends BaseFragment {
    public interface PhotoActionsListener {
        void onBringUp();

        void onDismiss();

        void onPhotoTap();
    }

    private PhotoViewAttacher attacher;
    private PhotoActionsListener photoActionsListener;
    private View rootView;
    private PhotoView photoView;
    private TextView descriptionView;

    private boolean showingDescription;
    private boolean isDescriptionVisible;
    private Function0 toolbarVisibiltyDisposable;

    private static final String ARG_START_POSTPONED_TRANSITION = "startPostponedTransition";
    private static final String ARG_ATTACHMENT = "attach";
    private static final String ARG_AVATAR_URL = "avatarUrl";

    public static ViewMediaFragment newInstance(@NonNull Attachment attachment,
                                                boolean shouldStartPostponedTransition) {
        Bundle arguments = new Bundle(2);
        ViewMediaFragment fragment = new ViewMediaFragment();
        arguments.putParcelable(ARG_ATTACHMENT, attachment);
        arguments.putBoolean(ARG_START_POSTPONED_TRANSITION, shouldStartPostponedTransition);

        fragment.setArguments(arguments);
        return fragment;
    }

    public static ViewMediaFragment newAvatarInstance(@NonNull String avatarUrl) {
        Bundle arguments = new Bundle(2);
        ViewMediaFragment fragment = new ViewMediaFragment();
        arguments.putString(ARG_AVATAR_URL, avatarUrl);
        arguments.putBoolean(ARG_START_POSTPONED_TRANSITION, true);

        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        photoActionsListener = (PhotoActionsListener) context;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_view_media, container, false);
        photoView = rootView.findViewById(R.id.view_media_image);
        descriptionView = rootView.findViewById(R.id.tv_media_description);

        final Bundle arguments = Objects.requireNonNull(getArguments(), "Empty arguments");
        final Attachment attachment = arguments.getParcelable(ARG_ATTACHMENT);
        final String url;

        if(attachment != null) {
            url = attachment.getUrl();

            @Nullable final String description = attachment.getDescription();

            descriptionView.setText(description);
            showingDescription = !TextUtils.isEmpty(description);
            isDescriptionVisible = showingDescription;
        } else {
            url = arguments.getString(ARG_AVATAR_URL);
            if(url == null) {
                throw new IllegalArgumentException("attachment or avatar url has to be set");
            }

            showingDescription = false;
            isDescriptionVisible = false;
        }

        // Setting visibility without animations so it looks nice when you scroll images
        //noinspection ConstantConditions
        descriptionView.setVisibility(showingDescription
        && (((ViewMediaActivity) getActivity())).isToolbarVisible()
                ? View.VISIBLE : View.GONE);

        attacher = new PhotoViewAttacher(photoView);

        // Clicking outside the photo closes the viewer.
        attacher.setOnOutsidePhotoTapListener(imageView -> photoActionsListener.onDismiss());

        attacher.setOnClickListener(v -> onMediaTap());

        /* A vertical swipe motion also closes the viewer. This is especially useful when the photo
         * mostly fills the screen so clicking outside is difficult. */
        attacher.setOnSingleFlingListener((e1, e2, velocityX, velocityY) -> {
            if (Math.abs(velocityY) > Math.abs(velocityX)) {
                photoActionsListener.onDismiss();
                return true;
            }
            return false;
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

        toolbarVisibiltyDisposable = ((ViewMediaActivity) getActivity())
                .addToolbarVisibilityListener(this::onToolbarVisibilityChange);

        return rootView;
    }

    @Override
    public void onDestroyView() {
        if (toolbarVisibiltyDisposable != null) toolbarVisibiltyDisposable.invoke();
        super.onDestroyView();
    }

    private void onMediaTap() {
        photoActionsListener.onPhotoTap();
    }

    private void onToolbarVisibilityChange(boolean visible) {
        isDescriptionVisible = showingDescription && visible;
        final int visibility = isDescriptionVisible ? View.VISIBLE : View.INVISIBLE;
        int alpha = isDescriptionVisible ? 1 : 0;
        descriptionView.animate().alpha(alpha)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        descriptionView.setVisibility(visibility);
                        animation.removeListener(this);
                    }
                })
                .start();
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
                .networkPolicy(NetworkPolicy.NO_STORE)
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
