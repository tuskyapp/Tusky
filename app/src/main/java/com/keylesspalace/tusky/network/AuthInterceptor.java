package com.keylesspalace.tusky.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.TuskyApplication;
import com.keylesspalace.tusky.db.AccountEntity;
import com.keylesspalace.tusky.db.AccountManager;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by charlag on 31/10/17.
 */

public final class AuthInterceptor implements Interceptor {

    public AuthInterceptor() { }

    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {

        AccountEntity currentAccount = TuskyApplication.getAccountManager().getActiveAccount();

        Request originalRequest = chain.request();

        Request.Builder builder = originalRequest.newBuilder();
        if (currentAccount != null) {
            builder.header("Authorization", String.format("Bearer %s", currentAccount.getAccessToken()));
        }
        Request newRequest = builder.build();

        return chain.proceed(newRequest);
    }

}
