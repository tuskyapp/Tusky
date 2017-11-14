package com.keylesspalace.tusky;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spanned;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.keylesspalace.tusky.entity.Notification;
import com.keylesspalace.tusky.json.SpannedTypeAdapter;
import com.keylesspalace.tusky.network.AuthInterceptor;
import com.keylesspalace.tusky.network.MastodonApi;
import com.keylesspalace.tusky.util.NotificationMaker;
import com.keylesspalace.tusky.util.OkHttpUtils;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by charlag on 31/10/17.
 */

public final class NotificationPullJobCreator implements JobCreator {

    static final String NOTIFICATIONS_JOB_TAG = "notifications_job_tag";
    static final int NOTIFY_ID = 6; // chosen by fair dice roll, guaranteed to be random

    private MastodonApi mastodonApi;
    private Context context;

    NotificationPullJobCreator(Context context) {
        this.mastodonApi = createMastodonApi(context);
        this.context = context;
    }

    @Nullable
    @Override
    public Job create(@NonNull String tag) {
        if (tag.equals(NOTIFICATIONS_JOB_TAG)) {
            return new NotificationPullJob(mastodonApi, context);
        }
        return null;
    }

    private MastodonApi createMastodonApi(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(
                context.getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        final String domain = preferences.getString("domain", null);

        OkHttpClient okHttpClient = OkHttpUtils.getCompatibleClientBuilder()
                .addInterceptor(new AuthInterceptor(TuskyApplication.getCurrentUser()))
                .build();

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Spanned.class, new SpannedTypeAdapter())
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://" + domain)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        return retrofit.create(MastodonApi.class);
    }

    private final static class NotificationPullJob extends Job {

        @NonNull private MastodonApi mastodonApi;
        private Context context;

        NotificationPullJob(@NonNull MastodonApi mastodonApi, Context context) {
            this.mastodonApi = mastodonApi;
            this.context = context;
        }

        @NonNull
        @Override
        protected Result onRunJob(Params params) {
            try {
                Response<List<Notification>> notifications =
                        mastodonApi.notifications(null, null, null).execute();
                if (notifications.isSuccessful()) {
                    onNotificationsReceived(notifications.body());
                } else {
                    return Result.FAILURE;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return Result.FAILURE;
            }
            return Result.SUCCESS;
        }

        private void onNotificationsReceived(List<Notification> notificationList) {
            SharedPreferences notificationsPreferences = context.getSharedPreferences(
                    "Notifications", Context.MODE_PRIVATE);
            Set<String> currentIds = notificationsPreferences.getStringSet(
                    "current_ids", new HashSet<String>());
            for (Notification notification : notificationList) {
                String id = notification.id;
                if (!currentIds.contains(id)) {
                    currentIds.add(id);
                    NotificationMaker.make(context, NOTIFY_ID, notification);
                }
            }
            notificationsPreferences.edit()
                    .putStringSet("current_ids", currentIds)
                    .apply();
        }
    }
}
