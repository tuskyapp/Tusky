package com.keylesspalace.tusky.network

import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock

class InstanceSwitchAuthInterceptorTest {

    private val mockWebServer = MockWebServer()

    @Before
    fun setup() {
        mockWebServer.start()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `should make regular request when requested`() {

        mockWebServer.enqueue(MockResponse())

        val accountManager: AccountManager = mock {
            on { activeAccount } doAnswer { null }
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(InstanceSwitchAuthInterceptor(accountManager))
            .build()

        val request = Request.Builder()
            .get()
            .url(mockWebServer.url("/test"))
            .build()

        val response = okHttpClient.newCall(request).execute()

        assertEquals(200, response.code)
    }

    @Test
    fun `should make request to instance requested in special header`() {
        mockWebServer.enqueue(MockResponse())

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

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(InstanceSwitchAuthInterceptor(accountManager))
            .build()

        val request = Request.Builder()
            .get()
            .url("http://" + MastodonApi.PLACEHOLDER_DOMAIN + ":" + mockWebServer.port + "/test")
            .header(MastodonApi.DOMAIN_HEADER, mockWebServer.hostName)
            .build()

        val response = okHttpClient.newCall(request).execute()

        assertEquals(200, response.code)

        assertNull(mockWebServer.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `should make request to current instance when requested and user is logged in`() {
        mockWebServer.enqueue(MockResponse())

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

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(InstanceSwitchAuthInterceptor(accountManager))
            .build()

        val request = Request.Builder()
            .get()
            .url("http://" + MastodonApi.PLACEHOLDER_DOMAIN + ":" + mockWebServer.port + "/test")
            .build()

        val response = okHttpClient.newCall(request).execute()

        assertEquals(200, response.code)

        assertEquals("Bearer fakeToken", mockWebServer.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `should fail to make request when request to current instance is requested but no user is logged in`() {
        mockWebServer.enqueue(MockResponse())

        val accountManager: AccountManager = mock {
            on { activeAccount } doAnswer { null }
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(InstanceSwitchAuthInterceptor(accountManager))
            .build()

        val request = Request.Builder()
            .get()
            .url("http://" + MastodonApi.PLACEHOLDER_DOMAIN + "/test")
            .build()

        val response = okHttpClient.newCall(request).execute()

        assertEquals(400, response.code)
        assertEquals(0, mockWebServer.requestCount)
    }
}
