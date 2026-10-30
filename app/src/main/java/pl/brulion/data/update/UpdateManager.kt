package pl.brulion.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import pl.brulion.BuildConfig
import pl.brulion.data.Diagnostics
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Self-updates outside any store: reads update.json from [BuildConfig.UPDATE_URL], downloads
 * the APK, checks its checksum and signing certificate, and hands it to PackageInstaller.
 */
class UpdateManager(private val context: Context, private val diagnostics: Diagnostics) {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val info: UpdateInfo) : State
        /** [progress] is 0..1, or null when the size is unknown. */
        data class Downloading(val info: UpdateInfo, val progress: Float?) : State
        data class Installing(val info: UpdateInfo) : State
        data class Failed(val info: UpdateInfo?, val message: String) : State
    }

    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean get() = BuildConfig.UPDATE_URL.isNotBlank()

    private val _state = MutableStateFlow(knownUpdate()?.let { State.Available(it) } ?: State.Idle)
    val state: StateFlow<State> = _state

    /**
     * The system's "update this app?" screen while it waits for the user. If it gets covered
     * (another app, the notification shade) the user can bring it back from the dialog.
     */
    private val _pendingConfirmation = MutableStateFlow<Intent?>(null)
    val pendingConfirmation: StateFlow<Intent?> = _pendingConfirmation

    /** Asks the user to look at an update, e.g. after tapping its notification. */
    val promptRequest = MutableStateFlow(false)

    /** The newer version found by the last check, kept across restarts. */
    private fun knownUpdate(): UpdateInfo? = prefs.getString(KEY_KNOWN, null)
        ?.let { runCatching { UpdateInfo.parse(it) }.getOrNull() }
        ?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }

    /** Returns the newer version, or null when up to date. Network errors are thrown. */
    suspend fun check(): UpdateInfo? {
        if (!isConfigured) return null
        _state.value = State.Checking
        return try {
            val info = withContext(Dispatchers.IO) {
                val request = Request.Builder().url(BuildConfig.UPDATE_URL).header("Cache-Control", "no-cache").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    UpdateInfo.parse(response.body!!.string())
                }
            }
            prefs.edit().putLong(KEY_CHECKED_AT, System.currentTimeMillis()).apply()
            val newer = info.takeIf { it.versionCode > BuildConfig.VERSION_CODE && it.minSdk <= Build.VERSION.SDK_INT }
            prefs.edit().putString(KEY_KNOWN, newer?.toJson()).apply()
            _state.value = newer?.let { State.Available(it) } ?: State.UpToDate
            diagnostics.log("Update check: ${info.versionName} (${info.versionCode}), installed ${BuildConfig.VERSION_CODE}")
            newer
        } catch (e: Exception) {
            diagnostics.log("Update check failed", e)
            _state.value = State.Failed(null, "Nie udało się sprawdzić aktualizacji (${e.message})")
            throw e
        }
    }

    /** When the last successful check happened (epoch millis), 0 if never. */
    val lastCheckedAt: Long get() = prefs.getLong(KEY_CHECKED_AT, 0)

    /** The version the notification was shown for, so it's shown once per version. */
    var notifiedVersion: Int
        get() = prefs.getInt(KEY_NOTIFIED, 0)
        set(value) = prefs.edit().putInt(KEY_NOTIFIED, value).apply()

    /** Android asks once per installer app; without it the session can't be committed. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    suspend fun install(info: UpdateInfo) {
        try {
            _pendingConfirmation.value = null
            _state.value = State.Downloading(info, 0f)
            val apk = download(info)
            verify(apk, info)
            _state.value = State.Installing(info)
            withContext(Dispatchers.IO) { commit(apk) }
        } catch (e: Exception) {
            diagnostics.log("Update ${info.versionName} failed", e)
            _state.value = State.Failed(info, e.message ?: e.javaClass.simpleName)
        }
    }

    /** Called by [UpdateInstallReceiver] with the installer's result. */
    fun onInstallResult(status: Int, message: String?) {
        _pendingConfirmation.value = null
        val info = (state.value as? State.Installing)?.info ?: knownUpdate()
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> Unit // The process is replaced right away.
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = info?.let { State.Available(it) } ?: State.Idle
            else -> {
                diagnostics.log("Update install failed: status $status, $message")
                _state.value = State.Failed(info, "Instalacja nie powiodła się (${message ?: status})")
            }
        }
    }

    /** Shows the system confirmation; called by [UpdateInstallReceiver] and from the dialog. */
    fun confirm(intent: Intent? = pendingConfirmation.value) {
        intent ?: return
        _pendingConfirmation.value = intent
        context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private suspend fun download(info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "brulion-${info.versionCode}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        val request = Request.Builder().url(info.apkUrl(BuildConfig.UPDATE_URL)).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Pobieranie nie powiodło się (HTTP ${response.code})")
            val body = response.body!!
            val total = body.contentLength()
            var done = 0L
            var shown = -1
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        val percent = if (total > 0) (done * 100 / total).toInt() else -1
                        if (percent != shown) {
                            shown = percent
                            _state.value = State.Downloading(info, if (total > 0) done.toFloat() / total else null)
                        }
                    }
                }
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (!sha.equals(info.sha256, ignoreCase = true)) {
            target.delete()
            throw IOException("Plik aktualizacji jest uszkodzony (zła suma kontrolna)")
        }
        target
    }

    /** Same package, newer version and the same signing key as the installed app. */
    private fun verify(apk: File, info: UpdateInfo) {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apk.path, signatureFlags())
            ?: throw IOException("Pobrany plik nie jest aplikacją")
        if (archive.packageName != context.packageName) throw IOException("Plik jest dla innej aplikacji (${archive.packageName})")
        if (archive.longVersionCodeCompat() < info.versionCode) throw IOException("Plik ma starszą wersję niż zapowiedziana")
        val installed = pm.getPackageInfo(context.packageName, signatureFlags())
        if (certificates(archive) != certificates(installed)) {
            throw IOException("Aktualizacja jest podpisana innym kluczem. Odinstaluj aplikację i zainstaluj nową wersję ręcznie.")
        }
    }

    private fun commit(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // Lets later updates go through without a prompt once this app installed itself.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("brulion.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, UpdateInstallReceiver::class.java),
                // Mutable: the installer adds the status extras.
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
            )
            session.commit(callback.intentSender)
        }
    }

    @Suppress("DEPRECATION")
    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun certificates(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        val sha = MessageDigest.getInstance("SHA-256")
        return signatures.orEmpty().map { sig -> sha.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
            .ifEmpty { throw IOException("Nie udało się odczytać podpisu aplikacji") }
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    private companion object {
        const val KEY_KNOWN = "known_update"
        const val KEY_CHECKED_AT = "checked_at"
        const val KEY_NOTIFIED = "notified_version"
    }
}
