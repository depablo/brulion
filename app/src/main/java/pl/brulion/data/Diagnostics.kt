package pl.brulion.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import pl.brulion.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Collects what's needed to understand a problem: recent requests and errors, plus the last
 * crash. Lines are masked before they're stored (tokens, keys, query strings), and nothing
 * leaves the phone unless the user sends the report from their mail app.
 */
class Diagnostics(private val context: Context) {
    private val crashFile get() = File(context.filesDir, "last_crash.txt")
    /** A crash already offered for sending; still attached to reports for a while. */
    private val sentCrashFile get() = File(context.filesDir, "last_crash_sent.txt")
    /** The log also goes to a file, so a report covers background work of earlier processes. */
    private val logFile = File(context.filesDir, "diagnostics.log")
    private val lines = ArrayDeque(runCatching { logFile.readLines().takeLast(MAX_LINES) }.getOrDefault(emptyList()))

    fun log(message: String) {
        val line = "${LocalDateTime.now().format(Stamp)} ${mask(message)}"
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
            runCatching {
                // Appending is cheap; the file is cut back to the in-memory window now and then.
                if (logFile.length() > MAX_FILE_BYTES) logFile.writeText(lines.joinToString("\n", postfix = "\n") { it.replace("\n", "\n  ") })
                else logFile.appendText(line.replace("\n", "\n  ") + "\n")
            }
        }
    }

    fun log(message: String, error: Throwable) = log("$message: ${error.javaClass.simpleName}: ${error.message}\n${stackTrace(error)}")

    /** Keeps the default crash dialog but saves the stack trace first, to offer a report on next start. */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { crashFile.writeText(report("Awaria w wątku ${thread.name}:\n${mask(stackTrace(error))}")) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun hasCrashReport(): Boolean = crashFile.isFile

    fun dismissCrashReport() {
        crashFile.delete()
    }

    /** Packages that handle `mailto:`; mail apps, not messengers. */
    private fun mailApps(): List<String> =
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), 0)
            .map { it.activityInfo.packageName }
            .distinct()

    /**
     * Opens the mail app with the report attached, or the share sheet when there's no mail app.
     * Each mail app is targeted by package: a `mailto:` selector on ACTION_SEND fell through
     * to the full share sheet on a Pixel with Gmail.
     */
    fun send(reason: String) {
        val crash = crashFile.takeIf { it.isFile }
            ?: sentCrashFile.takeIf { it.isFile && System.currentTimeMillis() - it.lastModified() < SENT_CRASH_KEPT_MS }
        val text = crash?.let { it.readText() + "\n\n" + report(reason) } ?: report(reason)
        val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "brulion-diagnostyka-${LocalDateTime.now().format(FileStamp)}.txt").apply { writeText(text) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, "Brulion ${BuildConfig.VERSION_NAME}: diagnostyka")
            .putExtra(Intent.EXTRA_TEXT, "Opis problemu:\n\n")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        send.clipData = ClipData.newRawUri(file.name, uri)
        val mailIntents = mailApps().map { Intent(send).setPackage(it) }
            .filter { context.packageManager.queryIntentActivities(it, 0).isNotEmpty() }
        when {
            mailIntents.size == 1 -> context.startActivity(mailIntents.single())
            mailIntents.size > 1 -> context.startActivity(
                // A chooser limited to mail apps: the first one plus the rest as extra targets.
                Intent.createChooser(mailIntents.first(), "Brulion: diagnostyka")
                    .putExtra(Intent.EXTRA_INITIAL_INTENTS, mailIntents.drop(1).toTypedArray())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            else -> {
                Toast.makeText(context, "Brak aplikacji pocztowej. Wyślij plik na $EMAIL", Toast.LENGTH_LONG).show()
                context.startActivity(
                    Intent.createChooser(send, "Brulion: diagnostyka").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        // Whether the mail actually goes out is unknown; keep the crash for the next report too.
        if (crashFile.isFile) {
            sentCrashFile.delete()
            crashFile.renameTo(sentCrashFile)
        }
    }

    private fun report(reason: String): String = buildString {
        appendLine("Brulion ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}${if (BuildConfig.DEBUG) ", debug" else ""})")
        appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Czas: ${LocalDateTime.now().format(Stamp)}")
        appendLine("Powód: $reason")
        appendLine()
        appendLine("Ostatnie zdarzenia:")
        synchronized(lines) { lines.forEach { appendLine(it) } }
    }

    private fun stackTrace(error: Throwable): String =
        StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString().lines().take(40).joinToString("\n")

    companion object {
        const val EMAIL = "brulion1234@proton.me"
        private const val MAX_LINES = 300
        private const val MAX_FILE_BYTES = 256 * 1024L
        private const val SENT_CRASH_KEPT_MS = 7 * 24 * 60 * 60 * 1000L
        private val Stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val FileStamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

        /** Removes secrets that could end up in URLs or error messages. */
        fun mask(text: String): String = text
            .replace(Regex("/token/[^/\\s]+"), "/token/***")
            .replace(Regex("(?i)(singleUseKey|token|code|key|password|haslo)=[^&\\s\"]+"), "$1=***")
            .replace(Regex("(?i)(Bearer)\\s+[\\w.-]+"), "$1 ***")
    }
}
