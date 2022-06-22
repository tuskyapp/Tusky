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

import android.util.Log;
import androidx.annotation.NonNull;

import com.keylesspalace.tusky.db.AccountEntity;
import com.keylesspalace.tusky.db.AccountManager;

import java.io.IOException;

import okhttp3.*;

/**
 * Created by charlag on 31/10/17.
 */

public final class InstanceSwitchAuthInterceptor implements Interceptor {
    private final AccountManager accountManager;

    public InstanceSwitchAuthInterceptor(AccountManager accountManager) {
        this.accountManager = accountManager;
    }

    @NonNull
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
                String accessToken = currentAccount.getAccessToken();
                if (!accessToken.isEmpty()) {
                    //use domain of current account
                    builder.url(swapHost(originalRequest.url(), currentAccount.getDomain()))
                            .header("Authorization",
                                    String.format("Bearer %s", currentAccount.getAccessToken()));
                }
            }
            Request newRequest = builder.build();

            if (MastodonApi.PLACEHOLDER_DOMAIN.equals(newRequest.url().host())) {
                Log.w("ISAInterceptor", "no user logged in or no domain header specified - can't make request to " + newRequest.url());
                return new Response.Builder()
                        .code(400)
                        .message("Bad Request")
                        .protocol(Protocol.HTTP_2)
                        .body(ResponseBody.create("", MediaType.parse("text/plain")))
                        .request(chain.request())
                        .build();
            }
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
