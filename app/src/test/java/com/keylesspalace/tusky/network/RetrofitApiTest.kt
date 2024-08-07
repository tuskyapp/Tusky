package com.keylesspalace.tusky.network

import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.di.NetworkModule
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock

class RetrofitApiTest {

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

    @Test
    fun `should make request to the active account's instance`() = runTest {
        mockInstanceResponse()

        val accountManager: AccountManager = mock {
            on { activeAccount } doAnswer {
                AccountEntity(
                    id = 1,
                    domain = mockWebServer.hostName,
                    accessToken = "fakeToken",
                    clientId = "fakeId",
                    clientSecret = "fakeSecret",
                    isActive = true
                )
            }
        }

        val retrofit = NetworkModule.providesRetrofit(okHttpClient, moshi, mockWebServer.port, "http://")
        val api: MastodonApi = NetworkModule.provideApi(okHttpClient, retrofit, accountManager, mockWebServer.port, "http://")

        val instanceResponse = api.getInstance()

        assertTrue(instanceResponse.isSuccess)
        assertEquals("Bearer fakeToken", mockWebServer.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `should make request to instance requested in special header when account active`() = runTest {
        mockInstanceResponse()

        val accountManager: AccountManager = mock {
            on { activeAccount } doAnswer {
                AccountEntity(
                    id = 1,
                    domain = "test.domain",
                    accessToken = "fakeToken",
                    clientId = "fakeId",
                    clientSecret = "fakeSecret",
                    isActive = true
                )
            }
        }

        val retrofit = NetworkModule.providesRetrofit(okHttpClient, moshi, mockWebServer.port, "http://")
        val api: MastodonApi = NetworkModule.provideApi(okHttpClient, retrofit, accountManager, mockWebServer.port, "http://")

        val instanceResponse = api.getInstance(domain = mockWebServer.hostName)

        assertTrue(instanceResponse.isSuccess)
        assertNull(mockWebServer.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `should make request to instance requested in special header when no account active`() = runTest {
        mockInstanceResponse()
        val accountManager: AccountManager = mock {
            on { activeAccount } doAnswer { null }
        }

        val retrofit = NetworkModule.providesRetrofit(okHttpClient, moshi, mockWebServer.port, "http://")
        val api: MastodonApi = NetworkModule.provideApi(okHttpClient, retrofit, accountManager, mockWebServer.port, "http://")

        val instanceResponse = api.getInstance(domain = mockWebServer.hostName)

        assertTrue(instanceResponse.isSuccess)
        assertNull(mockWebServer.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `should fail when current instance is requested but no user is logged in`() = runTest {
        mockInstanceResponse()
        val accountManager: AccountManager = mock {
            on { activeAccount } doAnswer { null }
        }

        val retrofit = NetworkModule.providesRetrofit(okHttpClient, moshi, mockWebServer.port, "http://")
        val api: MastodonApi = NetworkModule.provideApi(okHttpClient, retrofit, accountManager, mockWebServer.port, "http://")

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
