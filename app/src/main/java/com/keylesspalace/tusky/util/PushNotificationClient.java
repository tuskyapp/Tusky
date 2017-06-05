package com.keylesspalace.tusky.util;

import android.app.NotificationManager;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.util.ArraySet;
import android.text.Spanned;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Notification;
import com.keylesspalace.tusky.json.SpannedTypeAdapter;
import com.keylesspalace.tusky.json.StringWithEmoji;
import com.keylesspalace.tusky.json.StringWithEmojiTypeAdapter;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.InputStream;
import java.util.ArrayDeque;

import static android.content.Context.NOTIFICATION_SERVICE;

public class PushNotificationClient {
    private static final String TAG = "PushNotificationClient";
    private static final int NOTIFY_ID = 666;

    private static class QueuedAction {
        enum Type {
            SUBSCRIBE,
            UNSUBSCRIBE,
            DISCONNECT,
        }

        Type type;
        String topic;

        QueuedAction(Type type, String topic) {
            this.type = type;
            this.topic = topic;
        }
    }

    private MqttAndroidClient mqttAndroidClient;
    private ArrayDeque<QueuedAction> queuedActions;
    private ArraySet<String> subscribedTopics;

    public PushNotificationClient(final @NonNull Context applicationContext,
                                  @NonNull String serverUri) {
        queuedActions = new ArrayDeque<>();
        subscribedTopics = new ArraySet<>();

        // Create the MQTT client.
        String clientId = MqttClient.generateClientId();
        mqttAndroidClient = new MqttAndroidClient(applicationContext, serverUri, clientId);
        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                    flushQueuedActions();
                    for (String topic : subscribedTopics) {
                        subscribeToTopic(topic);
                    }
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                onConnectionLost();
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                onMessageReceived(applicationContext, new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // This client is read-only, so this is unused.
            }
        });
    }

    private void queueAction(QueuedAction.Type type, String topic) {
        // Search queue for duplicates and if one is found, return before it's added to the queue.
        for (QueuedAction action : queuedActions) {
            if (action.type == type) {
                switch (type) {
                    case SUBSCRIBE:
                    case UNSUBSCRIBE:
                        if (!action.topic.equals(topic)) {
                            return;
                        }
                        break;
                    case DISCONNECT:
                        return;
                }
            }
        }
        // Add the new unique action.
        queuedActions.add(new QueuedAction(type, topic));
    }

    private void flushQueuedActions() {
        while (!queuedActions.isEmpty()) {
            QueuedAction action = queuedActions.pop();
            switch (action.type) {
                case SUBSCRIBE:   subscribeToTopic(action.topic);   break;
                case UNSUBSCRIBE: unsubscribeToTopic(action.topic); break;
                case DISCONNECT:  disconnect();                     break;
            }
        }
    }

    /** Connect to the MQTT broker. */
    public void connect(Context context) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);
        try {
            String password = context.getString(R.string.tusky_api_keystore_password);
            InputStream keystore = context.getResources().openRawResource(R.raw.keystore_tusky_api);
            try {
                options.setSocketFactory(mqttAndroidClient.getSSLSocketFactory(keystore, password));
            } finally {
                IOUtils.closeQuietly(keystore);
            }
            mqttAndroidClient.connect(options).setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions bufferOptions = new DisconnectedBufferOptions();
                    bufferOptions.setBufferEnabled(true);
                    bufferOptions.setBufferSize(100);
                    bufferOptions.setPersistBuffer(false);
                    bufferOptions.setDeleteOldestMessages(false);
                    mqttAndroidClient.setBufferOpts(bufferOptions);
                    onConnectionSuccess();
                    flushQueuedActions();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "An exception occurred while connecting. " + exception.getMessage()
                            + " " + exception.getCause());
                    onConnectionFailure();
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "An exception occurred while connecpting. " + e.getMessage());
            onConnectionFailure();
        }
    }

    private void onConnectionSuccess() {
        Log.v(TAG, "The connection succeeded.");
    }

    private void onConnectionFailure() {
        Log.v(TAG, "The connection failed.");
    }

    private void onConnectionLost() {
        Log.v(TAG, "The connection was lost.");
    }

    /** Disconnect from the MQTT broker. */
    public void disconnect() {
        if (!mqttAndroidClient.isConnected()) {
            queueAction(QueuedAction.Type.DISCONNECT, null);
            return;
        }
        try {
            mqttAndroidClient.disconnect();
        } catch (MqttException ex) {
            Log.e(TAG, "An exception occurred while disconnecting.");
            onDisconnectFailed();
        }
    }

    private void onDisconnectFailed() {
        Log.v(TAG, "Failed while disconnecting from the broker.");
    }

    /** Subscribe to the push notification topic. */
    public void subscribeToTopic(final String topic) {
        if (!mqttAndroidClient.isConnected()) {
            queueAction(QueuedAction.Type.SUBSCRIBE, topic);
            return;
        }
        if (subscribedTopics.contains(topic)) {
            return;
        }
        try {
            mqttAndroidClient.subscribe(topic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    subscribedTopics.add(topic);
                    onConnectionSuccess();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "An exception occurred while subscribing." + exception.getMessage());
                    onConnectionFailure();
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "An exception occurred while subscribing." + e.getMessage());
            onConnectionFailure();
        }
    }

    /** Unsubscribe from the push notification topic. */
    public void unsubscribeToTopic(String topic) {
        if (!mqttAndroidClient.isConnected()) {
            queueAction(QueuedAction.Type.UNSUBSCRIBE, topic);
            return;
        }
        try {
            mqttAndroidClient.unsubscribe(topic);
            subscribedTopics.remove(topic);
        } catch (MqttException e) {
            Log.e(TAG, "An exception occurred while unsubscribing." + e.getMessage());
            onConnectionFailure();
        }
    }

    private void onMessageReceived(final Context context, String message) {
        Log.v(TAG, "Notification received: " + message);

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Spanned.class, new SpannedTypeAdapter())
                .registerTypeAdapter(StringWithEmoji.class, new StringWithEmojiTypeAdapter())
                .create();
        Notification notification = gson.fromJson(message, Notification.class);

        NotificationMaker.make(context, NOTIFY_ID, notification);
    }

    public void clearNotifications(Context context) {
        ((NotificationManager) (context.getSystemService(NOTIFICATION_SERVICE))).cancel(NOTIFY_ID);
    }

    public String getDeviceToken() {
        return mqttAndroidClient.getClientId();
    }
}
