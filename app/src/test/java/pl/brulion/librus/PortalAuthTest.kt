package pl.brulion.librus

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.brulion.librus.LibrusException.Reason

class PortalAuthTest {
    private lateinit var server: MockWebServer
    private lateinit var auth: PortalAuth

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        auth = PortalAuth(LibrusHttp(debug = false), tokenUrl = "$base/oauth2/access_token", apiUrl = "$base/api")
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setBody(body)

    @Test
    fun exchangeCodeReadsTokens() = runBlocking {
        server.enqueue(json("""{"access_token":"a1","refresh_token":"r1","expires_in":3600}"""))
        val tokens = auth.exchangeCode("code")
        assertEquals("a1", tokens.accessToken)
        assertEquals("r1", tokens.refreshToken)
        assertTrue(server.takeRequest().body.readUtf8().contains("grant_type=authorization_code"))
    }

    @Test
    fun expiredCodeNeedsReauthentication() {
        server.enqueue(json("""{"hint":"Authorization code has expired"}""", code = 400))
        val error = assertThrows(LibrusException::class.java) { runBlocking { auth.exchangeCode("code") } }
        assertEquals(Reason.REAUTH_REQUIRED, error.reason)
    }

    @Test
    fun accountsNeedingActionAreSkippedNotFatal() = runBlocking {
        server.enqueue(
            json(
                """{"accounts":[
                    {"id":1,"login":"111","accessToken":"t1","studentName":"Ola Nowak","state":"ok","group":"student"},
                    {"id":2,"login":"222","accessToken":"t2","studentName":"Jan Nowak","state":"requiring_an_action","group":"parent"}
                ]}""",
            ),
        )
        val result = auth.synergiaAccounts("portal")
        assertEquals(listOf("111"), result.usable.map { it.login })
        assertEquals(listOf("Jan Nowak"), result.needingAction)
        assertEquals("Bearer portal", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun onlyAccountsNeedingActionIsAccountDisconnected() {
        server.enqueue(json("""{"accounts":[{"id":2,"login":"222","studentName":"Jan","state":"need-activation"}]}"""))
        val error = assertThrows(LibrusException::class.java) { runBlocking { auth.synergiaAccounts("portal") } }
        assertEquals(Reason.ACCOUNT_DISCONNECTED, error.reason)
    }

    @Test
    fun freshTokenIsReadForTheLogin() = runBlocking {
        server.enqueue(json("""{"accessToken":"fresh"}"""))
        assertEquals("fresh", auth.freshSynergiaToken("portal", "111"))
        assertEquals("/api/v3/SynergiaAccounts/fresh/111", server.takeRequest().path)
    }
}
