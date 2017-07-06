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

import android.app.Application;
import android.net.Uri;
import android.util.Log;

import com.keylesspalace.tusky.util.OkHttpUtils;
import com.squareup.picasso.Picasso;
import com.jakewharton.picasso.OkHttp3Downloader;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Provider;
import java.security.Security;

public class TuskyApplication extends Application {
    private static final String TAG = "TuskyApplication"; // logging tag

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize Picasso configuration
        Picasso.Builder builder = new Picasso.Builder(this);
        builder.downloader(new OkHttp3Downloader(OkHttpUtils.getCompatibleClient()));
        if (BuildConfig.DEBUG) {
            builder.listener(new Picasso.Listener() {
                @Override
                public void onImageLoadFailed(Picasso picasso, Uri uri, Exception exception) {
                    exception.printStackTrace();
                }
            });
        }

        try {
            Picasso.setSingletonInstance(builder.build());
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        }

        if (BuildConfig.DEBUG) {
            Picasso.with(this).setLoggingEnabled(true);
        }

        /* Install the new provider or, if there's a pre-existing older version, replace the
         * existing version of it. */
        final String providerName = "BC";
        Provider existingProvider = Security.getProvider(providerName);
        if (existingProvider == null) {
            try {
                Security.addProvider(new BouncyCastleProvider());
            } catch (SecurityException e) {
                Log.d(TAG, "Permission to replace the security provider was denied.");
            }
        } else {
            Provider replacement = new BouncyCastleProvider();
            if (existingProvider.getVersion() < replacement.getVersion()) {
                Provider[] providers = Security.getProviders();
                int priority = 1;
                for (int i = 0; i < providers.length; i++) {
                    if (providers[i].getName().equals(providerName)) {
                        priority = i + 1;
                    }
                }
                try {
                    Security.removeProvider(providerName);
                    Security.insertProviderAt(replacement, priority);
                } catch (SecurityException e) {
                    Log.d(TAG, "Permission to replace the security provider was denied.");
                }
            }
        }
    }
}