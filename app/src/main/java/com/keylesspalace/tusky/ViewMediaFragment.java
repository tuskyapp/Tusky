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

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.io.File;

import butterknife.BindView;
import butterknife.ButterKnife;
import uk.co.senab.photoview.PhotoView;
import uk.co.senab.photoview.PhotoViewAttacher;

public class ViewMediaFragment extends DialogFragment {

    private PhotoViewAttacher attacher;
    private DownloadManager downloadManager;

    private static final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;

    @BindView(R.id.view_media_image) PhotoView photoView;

    public static ViewMediaFragment newInstance(String url) {
        Bundle arguments = new Bundle();
        ViewMediaFragment fragment = new ViewMediaFragment();
        arguments.putString("url", url);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.Dialog_FullScreen);
    }

    @Override
    public void onResume() {
        ViewGroup.LayoutParams params = getDialog().getWindow().getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        getDialog().getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);
        super.onResume();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_view_media, container, false);
        ButterKnife.bind(this, rootView);

        Bundle arguments = getArguments();
        String url = arguments.getString("url");

        attacher = new PhotoViewAttacher(photoView);

        // Clicking outside the photo closes the viewer.
        attacher.setOnPhotoTapListener(new PhotoViewAttacher.OnPhotoTapListener() {
            @Override
            public void onPhotoTap(View view, float x, float y) {

            }

            @Override
            public void onOutsidePhotoTap() {
                dismiss();
            }
        });

        /* A vertical swipe motion also closes the viewer. This is especially useful when the photo
         * mostly fills the screen so clicking outside is difficult. */
        attacher.setOnSingleFlingListener(new PhotoViewAttacher.OnSingleFlingListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                    float velocityY) {
                if (Math.abs(velocityY) > Math.abs(velocityX)) {
                    dismiss();
                    return true;
                }
                return false;
            }
        });

        attacher.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {

                AlertDialog downloadDialog = new AlertDialog.Builder(getContext()).create();

                downloadDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.dialog_download_image),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();

                                downloadImage();
                            }
                        });
                downloadDialog.show();
                return false;
            }
        });

        Picasso.with(getContext())
                .load(url)
                .into(photoView, new Callback() {
                    @Override
                    public void onSuccess() {
                        attacher.update();
                    }

                    @Override
                    public void onError() {

                    }
                });

        return rootView;
    }

    @Override
    public void onDestroyView() {
        attacher.cleanup();
        super.onDestroyView();
    }

    private void downloadImage(){

        //Permission stuff

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN &&
                ContextCompat.checkSelfPermission(this.getContext(), android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            android.support.v4.app.ActivityCompat.requestPermissions(getActivity(),
                    new String[] { android.Manifest.permission.WRITE_EXTERNAL_STORAGE },
                    PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
        } else {


            //download stuff
            String url = getArguments().getString("url");
            Uri uri = Uri.parse(url);

            String filename = new File(url).getName();

            downloadManager = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);

            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.allowScanningByMediaScanner();
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, getString(R.string.app_name) + "/" + filename);

            downloadManager.enqueue(request);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    downloadImage();
                } else {
                    doErrorDialog(R.string.error_media_download_permission, R.string.action_retry,
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    downloadImage();
                                }
                            });
                }
                break;
            }
        }
    }

    private void doErrorDialog(@StringRes int descriptionId, @StringRes int actionId,
                               View.OnClickListener listener) {
        Snackbar bar = Snackbar.make(getView(), getString(descriptionId),
                Snackbar.LENGTH_SHORT);
        bar.setAction(actionId, listener);
        bar.show();
    }
}
