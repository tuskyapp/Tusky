import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.network.UnicodeSafeMastodonApi
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import java.net.IDN

private const val UNICODE_DOMAIN = "これはペンです"

class UnicodeSafeMastodonApiTest {
    private val asciiDomain = IDN.toASCII(UNICODE_DOMAIN)
    private lateinit var mockApi: MastodonApi
    private lateinit var api: UnicodeSafeMastodonApi

    @Before
    fun setup() {
        mockApi = mock()
        api = UnicodeSafeMastodonApi(mockApi)
    }

    @Test
    fun `should call getInstance with ASCII domain instead of Unicode`() {
        runBlocking { api.getInstance(UNICODE_DOMAIN) }
        verifyBlocking(mockApi) { getInstance(asciiDomain) }
    }

    @Test
    fun `should call markersWithAuth with ASCII domain`() {
        runBlocking { api.markersWithAuth("", UNICODE_DOMAIN, emptyList()) }
        verifyBlocking(mockApi) { markersWithAuth("", asciiDomain, emptyList()) }
    }

    @Test
    fun `should call notificationsWithAuth with ASCII domain`() {
        runBlocking { api.notificationsWithAuth("", UNICODE_DOMAIN, "") }
        verifyBlocking(mockApi) { notificationsWithAuth("", asciiDomain, "") }
    }

    @Test
    fun `calls createStatus with ASCII domain`() {
        runBlocking { api.createStatus("", UNICODE_DOMAIN, "", mock()) }
        verifyBlocking(mockApi) { createStatus(anyString(), eq(asciiDomain), anyString(), any()) }
    }

    @Test
    fun `calls accountVerifyCredentials with ASCII domain`() {
        runBlocking { api.accountVerifyCredentials(UNICODE_DOMAIN, "") }
        verifyBlocking(mockApi) { accountVerifyCredentials(asciiDomain, "") }
    }

    @Test
    fun `calls authenticateApp with ASCII domain`() {
        runBlocking { api.authenticateApp(UNICODE_DOMAIN, "", "", "", "") }
        verifyBlocking(mockApi) { authenticateApp(asciiDomain, "", "", "", "") }
    }

    @Test
    fun `calls fetchOAuthToken with ASCII domain`() {
        runBlocking { api.fetchOAuthToken(UNICODE_DOMAIN, "", "", "", "", "") }
        verifyBlocking(mockApi) { fetchOAuthToken(asciiDomain, "", "", "", "", "") }
    }

    @Test
    fun `calls subscribePushNotifications with ASCII domain`() {
        runBlocking { api.subscribePushNotifications("", UNICODE_DOMAIN, "", "", "", emptyMap()) }
        verifyBlocking(mockApi) { subscribePushNotifications("", asciiDomain, "", "", "", emptyMap()) }
    }

    @Test
    fun `calls updatePushNotificationSubscription with ASCII domain`() {
        runBlocking { api.updatePushNotificationSubscription("", UNICODE_DOMAIN, emptyMap()) }
        verifyBlocking(mockApi) { updatePushNotificationSubscription("", asciiDomain, emptyMap()) }
    }

    @Test
    fun `calls unsubscribePushNotifications with ASCII domain`() {
        runBlocking { api.unsubscribePushNotifications("", UNICODE_DOMAIN) }
        verifyBlocking(mockApi) { unsubscribePushNotifications("", asciiDomain) }
    }
}
