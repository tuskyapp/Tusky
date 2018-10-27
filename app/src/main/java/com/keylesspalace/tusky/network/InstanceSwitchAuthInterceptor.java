/* Copyright 2018 charlag
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

package com.keylesspalace.tusky.network;

import android.support.annotation.NonNull;

import com.keylesspalace.tusky.db.AccountEntity;
import com.keylesspalace.tusky.db.AccountManager;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by charlag on 31/10/17.
 */

public final class InstanceSwitchAuthInterceptor implements Interceptor {
    private AccountManager accountManager;

    public InstanceSwitchAuthInterceptor(AccountManager accountManager) {
        this.accountManager = accountManager;
    }

    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {

        Request originalRequest = chain.request();

        // only switch domains if the request comes from retrofit
        if (originalRequest.url().host().equals(MastodonApi.PLACEHOLDER_DOMAIN)) {
            AccountEntity currentAccount = accountManager.getActiveAccount();

            Request.Builder builder = originalRequest.newBuilder();

            String instanceHeader = originalRequest.header(MastodonApi.DOMAIN_HEADER);
            if (instanceHeader != null) {
                // use domain explicitly specified in custom header
                builder.url(swapHost(originalRequest.url(), instanceHeader));
                builder.removeHeader(MastodonApi.DOMAIN_HEADER);
            } else if (currentAccount != null) {
                //use domain of current account
                builder.url(swapHost(originalRequest.url(), currentAccount.getDomain()))
                        .header("Authorization",
                                String.format("Bearer %s", currentAccount.getAccessToken()));
            }
            Request newRequest = builder.build();

            return chain.proceed(newRequest);

        } else {
            return chain.proceed(originalRequest);
        }
    }

    @NonNull
    private static HttpUrl swapHost(@NonNull HttpUrl url, @NonNull String host) {
        return url.newBuilder().host(host).build();
    }
}
