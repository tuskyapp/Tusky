package com.keylesspalace.tusky.network

import at.connyduck.calladapter.networkresult.NetworkResultCallAdapterFactory
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.entity.Instance
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class ApiFactoryTest {

    private val mockWebServer = MockWebServer()
    private val okHttpClient = OkHttpClient.Builder().build()
    private val moshi = Moshi.Builder().build()

    @Before
    fun setup() {
        mockWebServer.start()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    private fun retrofit() = Retrofit.Builder()
        .baseUrl("http://${MastodonApi.PLACEHOLDER_DOMAIN}:${mockWebServer.port}")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .addCallAdapterFactory(NetworkResultCallAdapterFactory.create())
        .build()

    @Test
    fun `should make request to the active account's instance`() = runTest {
        mockInstanceResponse()

        val account = AccountEntity(
            id = 1,
            domain = mockWebServer.hostName,
            accessToken = "fakeToken",
            clientId = "fakeId",
            clientSecret = "fakeSecret",
            isActive = true
        )

        val retrofit = retrofit()
        val api: MastodonApi = apiForAccount(account, okHttpClient, retrofit, "http://", mockWebServer.port)

        val instanceResponse = api.getInstance()

        assertTrue(instanceResponse.isSuccess)
        assertEquals("Bearer fakeToken", mockWebServer.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `should make request to instance requested in special header when account active`() = runTest {
        mockInstanceResponse()

        val account = AccountEntity(
            id = 1,
            domain = mockWebServer.hostName,
            accessToken = "fakeToken",
            clientId = "fakeId",
            clientSecret = "fakeSecret",
            isActive = true
        )

        val retrofit = retrofit()
        val api: MastodonApi = apiForAccount(account, okHttpClient, retrofit, "http://", mockWebServer.port)

        val instanceResponse = api.getInstance(domain = mockWebServer.hostName)

        assertTrue(instanceResponse.isSuccess)
        assertNull(mockWebServer.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `should make request to instance requested in special header when no account active`() = runTest {
        mockInstanceResponse()

        val retrofit = retrofit()
        val api: MastodonApi = apiForAccount(null, okHttpClient, retrofit, "http://", mockWebServer.port)

        val instanceResponse = api.getInstance(domain = mockWebServer.hostName)

        assertTrue(instanceResponse.isSuccess)
        assertNull(mockWebServer.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `should fail when current instance is requested but no user is logged in`() = runTest {
        mockInstanceResponse()

        val retrofit = retrofit()
        val api: MastodonApi = apiForAccount(null, okHttpClient, retrofit, "http://", mockWebServer.port)

        val instanceResponse = api.getInstance()

        assertTrue(instanceResponse.isFailure)
        assertEquals(0, mockWebServer.requestCount)
    }

    private fun mockInstanceResponse() {
        mockWebServer.enqueue(
            MockResponse()
                .setBody(
                    moshi.adapter(Instance::class.java).toJson(
                        Instance(
                            domain = "example.org",
                            version = "1.0.0"
                        )
                    )
                )
        )
    }
}
