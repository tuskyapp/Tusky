package com.keylesspalace.tusky.network;

import android.support.annotation.NonNull;

import com.keylesspalace.tusky.data.CurrentUser;
import com.keylesspalace.tusky.db.AccountEntity;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by charlag on 31/10/17.
 */

public final class AuthInterceptor implements Interceptor {

    private CurrentUser currentUser;

    public AuthInterceptor(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request originalRequest = chain.request();

        Request.Builder builder = originalRequest.newBuilder();
        AccountEntity account = currentUser.getActiveAccount();
        if (account != null) {
            builder.header("Authorization", String.format("Bearer %s", account.getToken()));
        }
        Request newRequest = builder.build();

        return chain.proceed(newRequest);
    }
}
