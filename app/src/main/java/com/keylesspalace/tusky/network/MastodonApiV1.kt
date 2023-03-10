package com.keylesspalace.tusky.network

import at.connyduck.calladapter.networkresult.NetworkResult
import com.keylesspalace.tusky.entity.Instance
import retrofit2.http.GET
import retrofit2.http.Header

@JvmSuppressWildcards
interface MastodonApiV1 {
    @GET("/api/v1/instance")
    suspend fun instance(
        @Header(MastodonApi.DOMAIN_HEADER) domain: String? = null
    ): NetworkResult<Instance>
}
