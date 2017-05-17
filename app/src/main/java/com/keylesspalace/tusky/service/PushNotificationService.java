package com.keylesspalace.tusky.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.text.Spanned;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Notification;
import com.keylesspalace.tusky.json.SpannedTypeAdapter;
import com.keylesspalace.tusky.json.StringWithEmoji;
import com.keylesspalace.tusky.json.StringWithEmojiTypeAdapter;
import com.keylesspalace.tusky.network.MastodonAPI;
import com.keylesspalace.tusky.util.Log;
import com.keylesspalace.tusky.util.NotificationMaker;
import com.keylesspalace.tusky.util.OkHttpUtils;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class PushNotificationService extends Service {
    private class LocalBinder extends Binder {
        PushNotificationService getService() {
            return PushNotificationService.this;
        }
    }

    private static final String TAG = "PushNotificationService";
    private static final String CLIENT_NAME = "TuskyMastodonClient";
    private static final String TOPIC = "tusky/notification";
    private static final int NOTIFY_ID = 666;

    private final IBinder binder = new LocalBinder();
    private MqttAndroidClient mqttAndroidClient;
    private MastodonAPI mastodonApi;

    @Override
    public void onCreate() {
        super.onCreate();

        // Create the MQTT client.
        String clientId = String.format(Locale.getDefault(), "%s/%s/%s", CLIENT_NAME,
                System.currentTimeMillis(), UUID.randomUUID().toString());
        String serverUri = getString(R.string.tusky_api_url);
        mqttAndroidClient = new MqttAndroidClient(this, serverUri, clientId);
        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                    subscribeToTopic();
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                onConnectionLost();
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                onMessageReceived(new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // This client is read-only, so this is unused.
            }
        });

        // Open the MQTT connection.
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);
        try {
            mqttAndroidClient.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions options = new DisconnectedBufferOptions();
                    options.setBufferEnabled(true);
                    options.setBufferSize(100);
                    options.setPersistBuffer(false);
                    options.setDeleteOldestMessages(false);
                    mqttAndroidClient.setBufferOpts(options);
                    onConnectionSuccess();
                    subscribeToTopic();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    onConnectionFailure();
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "An exception occurred while connecting. " + e.getMessage());
            onConnectionFailure();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /** Subscribe to the push notification topic. */
    public void subscribeToTopic() {
        try {
            mqttAndroidClient.subscribe(TOPIC, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    onConnectionSuccess();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    onConnectionFailure();
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "An exception occurred while subscribing." + e.getMessage());
            onConnectionFailure();
        }
    }

    /** Unsubscribe from the push notification topic. */
    public void unsubscribeToTopic() {
        try {
            mqttAndroidClient.unsubscribe(TOPIC);
        } catch (MqttException e) {
            Log.e(TAG, "An exception occurred while unsubscribing." + e.getMessage());
            onConnectionFailure();
        }
    }

    private void onConnectionSuccess() {

    }

    private void onConnectionFailure() {

    }

    private void onConnectionLost() {

    }

    private void onMessageReceived(String message) {
        String notificationId = message; // TODO: finalize the form the messages will be received

        Log.d(TAG, "Notification received: " + notificationId);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext());
        boolean enabled = preferences.getBoolean("notificationsEnabled", true);
        if (!enabled) {
            return;
        }

        createMastodonAPI();

        mastodonApi.notification(notificationId).enqueue(new Callback<Notification>() {
            @Override
            public void onResponse(Call<Notification> call, Response<Notification> response) {
                if (response.isSuccessful()) {
                    NotificationMaker.make(PushNotificationService.this, NOTIFY_ID,
                            response.body());
                }
            }

            @Override
            public void onFailure(Call<Notification> call, Throwable t) {}
        });
    }

    /** Disconnect from the MQTT broker. */
    public void disconnect() {
        try {
            mqttAndroidClient.disconnect();
        } catch (MqttException ex) {
            Log.e(TAG, "An exception occurred while disconnecting.");
            onDisconnectFailed();
        }
    }

    private void onDisconnectFailed() {

    }

    private void createMastodonAPI() {
        SharedPreferences preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        final String domain = preferences.getString("domain", null);
        final String accessToken = preferences.getString("accessToken", null);

        OkHttpClient okHttpClient = OkHttpUtils.getCompatibleClientBuilder()
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
                .registerTypeAdapter(StringWithEmoji.class, new StringWithEmojiTypeAdapter())
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://" + domain)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        mastodonApi = retrofit.create(MastodonAPI.class);
    }
}
