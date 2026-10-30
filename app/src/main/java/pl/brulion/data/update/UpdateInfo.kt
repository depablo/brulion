package pl.brulion.data.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Contents of `update.json`, written by scripts/release.sh:
 * ```
 * {"versionCode": 2, "versionName": "0.2.0", "apkUrl": "brulion-0.2.0.apk",
 *  "sha256": "…", "changelog": "…", "minSdk": 26}
 * ```
 * [apkUrl] may be relative to the address of update.json.
 */
@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val changelog: String = "",
    val minSdk: Int = 1,
) {
    fun apkUrl(manifestUrl: String): HttpUrl =
        manifestUrl.toHttpUrl().resolve(apkUrl) ?: throw IllegalArgumentException("bad apkUrl: $apkUrl")

    fun toJson(): String = UpdateJson.encodeToString(serializer(), this)

    companion object {
        private val UpdateJson = Json { ignoreUnknownKeys = true }

        fun parse(json: String): UpdateInfo {
            val info = UpdateJson.decodeFromString(serializer(), json)
            require(info.versionCode > 0 && info.apkUrl.isNotBlank()) { "incomplete update.json" }
            require(info.sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "bad sha256 in update.json" }
            return info
        }
    }
}
