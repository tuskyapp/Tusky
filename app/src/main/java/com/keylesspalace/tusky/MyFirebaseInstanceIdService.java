package com.keylesspalace.tusky;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class MyFirebaseInstanceIdService extends FirebaseInstanceIdService {
    private static final String TAG = "MyFirebaseInstanceIdService";

    private TuskyAPI tuskyAPI;

    protected void createTuskyAPI() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(getString(R.string.tusky_api_url))
                .client(OkHttpUtils.getCompatibleClient())
                .build();

        tuskyAPI = retrofit.create(TuskyAPI.class);
    }

    @Override
    public void onTokenRefresh() {
        createTuskyAPI();

        String refreshedToken = FirebaseInstanceId.getInstance().getToken();
        SharedPreferences preferences = getSharedPreferences(getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        String accessToken = preferences.getString("accessToken", null);
        String domain = preferences.getString("domain", null);

        if (accessToken != null && domain != null) {
            tuskyAPI.unregister("https://" + domain, accessToken).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    Log.d(TAG, response.message());
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Log.d(TAG, t.getMessage());
                }
            });
            tuskyAPI.register("https://" + domain, accessToken, refreshedToken).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    Log.d(TAG, response.message());
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Log.d(TAG, t.getMessage());
                }
            });
        }
    }
}
