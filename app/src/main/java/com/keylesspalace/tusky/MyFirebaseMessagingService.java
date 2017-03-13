package com.keylesspalace.tusky;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.Spanned;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.keylesspalace.tusky.entity.Notification;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private MastodonAPI mastodonAPI;
    private static final String TAG = "MyFirebaseMessagingService";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, remoteMessage.getFrom());
        Log.d(TAG, remoteMessage.toString());

        String notificationId = remoteMessage.getData().get("notification_id");

        if (notificationId == null) {
            Log.e(TAG, "No notification ID in payload!!");
            return;
        }

        Log.d(TAG, notificationId);

        createMastodonAPI();

        mastodonAPI.notification(notificationId).enqueue(new Callback<Notification>() {
            @Override
            public void onResponse(Call<Notification> call, Response<Notification> response) {
                buildNotification(response.body());
            }

            @Override
            public void onFailure(Call<Notification> call, Throwable t) {

            }
        });
    }

    private void createMastodonAPI() {
        SharedPreferences preferences = getSharedPreferences(getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        final String domain = preferences.getString("domain", null);
        final String accessToken = preferences.getString("accessToken", null);

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public okhttp3.Response intercept(Chain chain) throws IOException {
                        Request originalRequest = chain.request();

                        Request.Builder builder = originalRequest.newBuilder()
                                .header("Authorization", String.format("Bearer %s", accessToken));

                        Request newRequest = builder.build();

                        return chain.proceed(newRequest);
                    }
                })
                .build();

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Spanned.class, new SpannedTypeAdapter())
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://" + domain)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        mastodonAPI = retrofit.create(MastodonAPI.class);
    }

    private String truncateWithEllipses(String string, int limit) {
        if (string.length() < limit) {
            return string;
        } else {
            return string.substring(0, limit - 3) + "...";
        }
    }

    private void buildNotification(Notification body) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        Intent resultIntent = new Intent(this, MainActivity.class);
        resultIntent.putExtra("tab_position", 1);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_notify)
                .setAutoCancel(true)
                .setContentIntent(resultPendingIntent)
                .setDefaults(0); // So it doesn't ring twice, notify only in Target callback

        final Integer mId = (int)(System.currentTimeMillis() / 1000);

        Target mTarget = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                builder.setLargeIcon(bitmap);

                if (preferences.getBoolean("notificationAlertSound", true)) {
                    builder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI);
                }

                if (preferences.getBoolean("notificationStyleVibrate", false)) {
                    builder.setVibrate(new long[] { 500, 500 });
                }

                if (preferences.getBoolean("notificationStyleLight", false)) {
                    builder.setLights(0xFF00FF8F, 300, 1000);
                }

                ((NotificationManager) (getSystemService(NOTIFICATION_SERVICE))).notify(mId, builder.build());
            }

            @Override
            public void onBitmapFailed(Drawable errorDrawable) {

            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {

            }
        };

        Picasso.with(this)
                .load(body.account.avatar)
                .placeholder(R.drawable.avatar_default)
                .transform(new RoundedTransformation(7, 0))
                .into(mTarget);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setVisibility(android.app.Notification.VISIBILITY_PRIVATE);
            builder.setCategory(android.app.Notification.CATEGORY_SOCIAL);
        }

        switch (body.type) {
            case MENTION:
                builder.setContentTitle(String.format(getString(R.string.notification_mention_format), body.account.getDisplayName()))
                        .setContentText(truncateWithEllipses(body.status.content.toString(), 40));
                break;
            case FOLLOW:
                builder.setContentTitle(String.format(getString(R.string.notification_follow_format), body.account.getDisplayName()))
                        .setContentText(truncateWithEllipses(body.account.username, 40));
                break;
            case FAVOURITE:
                builder.setContentTitle(String.format(getString(R.string.notification_favourite_format), body.account.getDisplayName()))
                        .setContentText(truncateWithEllipses(body.status.content.toString(), 40));
                break;
            case REBLOG:
                builder.setContentTitle(String.format(getString(R.string.notification_reblog_format), body.account.getDisplayName()))
                        .setContentText(truncateWithEllipses(body.status.content.toString(), 40));
                break;
        }

        ((NotificationManager) (getSystemService(NOTIFICATION_SERVICE))).notify(mId, builder.build());
    }
}
