/*
 * Part of Brulion, GNU GPL v3. Based on Librus support in Szkolny.eu
 * (https://github.com/szkolny-eu/szkolny-android), (c) Kuba Szczodrzyński and contributors, GPL v3.
 */
package pl.brulion.librus

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

/** [onRequest] receives one line per request (method, URL without query, status) for diagnostics. */
class LibrusHttp(debug: Boolean, onRequest: (String) -> Unit = {}) {

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            // Web requests may set a browser User-Agent; everything else looks like the official app.
            val request = chain.request()
            if (request.header("User-Agent") != null) {
                chain.proceed(request)
            } else {
                chain.proceed(request.newBuilder().header("User-Agent", Librus.USER_AGENT).build())
            }
        }
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.newBuilder().query(null).build()
            val started = System.currentTimeMillis()
            try {
                chain.proceed(request).also { onRequest("${request.method} $url -> ${it.code} (${System.currentTimeMillis() - started} ms)") }
            } catch (e: IOException) {
                onRequest("${request.method} $url -> ${e.javaClass.simpleName}: ${e.message}")
                throw e
            }
        }
        .apply {
            // BASIC logs only method, URL and status - no bodies; auto-login tokens in URLs are masked.
            if (debug) {
                val logger = HttpLoggingInterceptor { Log.d("OkHttp", it.replace(TOKEN_IN_URL, "/token/***")) }
                addInterceptor(logger.setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
        }
        .build()

    /** A client with its own cookie store, for Synergia/messages web sessions. */
    fun webClient(cookies: CookieJar = MemoryCookieJar()): OkHttpClient = client.newBuilder().cookieJar(cookies).build()

    suspend fun execute(request: Request, client: OkHttpClient = this.client): HttpResult = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                HttpResult(response.code, response.body?.string().orEmpty(), response.request.url.toString())
            }
        } catch (e: IOException) {
            throw LibrusException(LibrusException.Reason.NETWORK, e.message, e)
        }
    }

    private companion object {
        val TOKEN_IN_URL = Regex("/token/[^/\\s]+")
    }
}

/** [finalUrl] is the URL after following redirects. */
class HttpResult(val code: Int, val body: String, val finalUrl: String) {
    fun json(): JsonObject? = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
}

fun nowSeconds(): Long = System.currentTimeMillis() / 1000
