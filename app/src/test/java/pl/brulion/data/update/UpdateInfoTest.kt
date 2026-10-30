package pl.brulion.data.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateInfoTest {
    private val sha = "a".repeat(64)

    @Test
    fun parsesManifestAndIgnoresUnknownKeys() {
        val info = UpdateInfo.parse(
            """{"versionCode":3,"versionName":"0.3.0","apkUrl":"brulion-0.3.0.apk","sha256":"$sha","extra":1}"""
        )
        assertEquals(3, info.versionCode)
        assertEquals("", info.changelog)
        assertEquals(info, UpdateInfo.parse(info.toJson()))
    }

    @Test
    fun resolvesApkUrlAgainstManifest() {
        val info = UpdateInfo(2, "0.2.0", "brulion-0.2.0.apk", sha)
        assertEquals(
            "https://github.com/u/r/releases/latest/download/brulion-0.2.0.apk",
            info.apkUrl("https://github.com/u/r/releases/latest/download/update.json").toString(),
        )
        assertEquals(
            "https://cdn.example.com/x.apk",
            info.copy(apkUrl = "https://cdn.example.com/x.apk").apkUrl("https://example.com/update.json").toString(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingChecksum() {
        UpdateInfo.parse("""{"versionCode":3,"versionName":"0.3.0","apkUrl":"a.apk","sha256":"x"}""")
    }
}
