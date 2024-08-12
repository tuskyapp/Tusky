package com.keylesspalace.tusky.network

import com.keylesspalace.tusky.db.entity.AccountEntity
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.create

/**
 * Creates an instance of an Api that will only make requests as the provided account.
 * @param account The account to make requests as.
 * When null, request without additional DOMAIN_HEADER will fail.
 * @param httpClient The OkHttpClient to make requests as
 * @param retrofit The Retrofit instance to derive the api from
 * @param scheme The scheme to use. Only used in tests.
 * @param port The port to use. Only used in tests.
 */
inline fun <reified T> apiForAccount(
    account: AccountEntity?,
    httpClient: OkHttpClient,
    retrofit: Retrofit,
    scheme: String = "https://",
    port: Int? = null
): T {
    return retrofit.newBuilder()
        .apply {
            if (account != null) {
                baseUrl("$scheme${account.domain}${ if (port == null) "" else ":$port"}")
            }
        }
        .callFactory { originalRequest ->
            var request = originalRequest

            val domainHeader = originalRequest.header(MastodonApi.DOMAIN_HEADER)
            if (domainHeader != null) {
                request = originalRequest.newBuilder()
                    .url(
                        originalRequest.url.newBuilder().host(domainHeader).build()
                    )
                    .removeHeader(MastodonApi.DOMAIN_HEADER)
                    .build()
            }

            if (account != null && request.url.host == account.domain) {
                request = request.newBuilder()
                    .header("Authorization", "Bearer ${account.accessToken}")
                    .build()
            }

            if (request.url.host == MastodonApi.PLACEHOLDER_DOMAIN) {
                FailingCall(request)
            } else {
                httpClient.newCall(request)
            }
        }
        .build()
        .create()
}
