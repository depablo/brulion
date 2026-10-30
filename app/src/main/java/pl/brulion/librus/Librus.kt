/*
 * Part of Brulion, GNU GPL v3. Based on Librus support in Szkolny.eu
 * (https://github.com/szkolny-eu/szkolny-android), (c) Kuba Szczodrzyński and contributors, GPL v3.
 */
package pl.brulion.librus

/** Endpoints and client credentials of the official Librus mobile app. */
object Librus {
    const val CLIENT_ID = "VaItV6oRutdo8fnjJwysnTjVlvaswf52ZqmXsJGP"
    const val REDIRECT_URL = "app://librus"
    const val AUTHORIZE_URL = "https://portal.librus.pl/konto-librus/redirect/dru"
    const val PORTAL_TOKEN_URL = "https://portal.librus.pl/oauth2/access_token"
    const val PORTAL_API_URL = "https://portal.librus.pl/api"
    const val API_URL = "https://api.librus.pl/2.0"
    const val API_TOKEN_URL = "https://api.librus.pl/OAuth/Token"
    const val API_AUTHORIZATION = "Mjg6ODRmZGQzYTg3YjAzZDNlYTZmZmU3NzdiNThiMzMyYjE="
    const val REQUESTED_WITH = "pl.librus.synergiaDru2"
    const val USER_AGENT_SUFFIX = "LibrusMobileApp"
    const val SYNERGIA_URL = "https://synergia.librus.pl"
    const val MESSAGES_URL = "https://wiadomosci.librus.pl"
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    /**
     * An assumption, not a documented value: portal-issued Synergia tokens seem to last a few
     * hours. A wrong guess only costs a retry, as an expired token is refreshed on TOKEN_EXPIRED.
     */
    const val PORTAL_SYNERGIA_TOKEN_TTL = 6 * 60 * 60L

    val USER_AGENT = (System.getProperty("http.agent") ?: "Dalvik/2.1.0 (Linux; U; Android)") + USER_AGENT_SUFFIX
}
