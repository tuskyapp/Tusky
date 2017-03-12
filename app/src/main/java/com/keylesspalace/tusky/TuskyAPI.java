package com.keylesspalace.tusky;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;

public interface TuskyAPI {
    @FormUrlEncoded
    @POST("/register")
    Call<ResponseBody> register(@Field("instance_url") String instanceUrl, @Field("access_token") String accessToken, @Field("device_token") String deviceToken);
    @FormUrlEncoded
    @POST("/unregister")
    Call<ResponseBody> unregister(@Field("instance_url") String instanceUrl, @Field("access_token") String accessToken);
}
