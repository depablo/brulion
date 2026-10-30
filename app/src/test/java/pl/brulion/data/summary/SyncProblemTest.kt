package pl.brulion.data.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.brulion.data.sync.SectionResult
import pl.brulion.data.sync.SyncController.State
import pl.brulion.data.sync.SyncReport
import pl.brulion.data.sync.SyncSection
import pl.brulion.librus.LibrusException
import pl.brulion.librus.LibrusException.Reason

class SyncProblemTest {
    private fun done(vararg results: Pair<SyncSection, SectionResult>) =
        State.Done(SyncReport(SyncSection.entries.associateWith { SectionResult.Ok(0, 0) } + results))

    private val failed = SectionResult.Failed(RuntimeException("500"))

    @Test
    fun freshDataHasNoWarning() {
        assertNull(syncProblem(listOf(done())))
        // A skipped module or a missing lucky number isn't a problem.
        assertNull(syncProblem(listOf(done(SyncSection.NOTES to SectionResult.Skipped(Reason.FEATURE_DISABLED), SyncSection.LUCKY_NUMBER to failed))))
    }

    @Test
    fun failedSyncAsksToLogInAgain() {
        val warning = syncProblem(listOf(State.Failed(LibrusException(Reason.REAUTH_REQUIRED, "x"))))!!
        assertTrue(warning, warning.contains("zaloguj się ponownie"))
        assertTrue(syncProblem(listOf(State.Failed(LibrusException(Reason.NETWORK, "x"))))!!.startsWith("Brak połączenia"))
        // Every section failing is as bad as the whole sync failing.
        val allFailed = State.Done(SyncReport(SyncSection.entries.associateWith { failed }))
        assertEquals("Nie udało się pobrać nowych danych z Librusa.", syncProblem(listOf(allFailed)))
    }

    @Test
    fun listsMissingSections() {
        assertEquals("Nie udało się pobrać: oceny, wiadomości.", syncProblem(listOf(done(SyncSection.GRADES to failed, SyncSection.MESSAGES to failed))))
    }

    @Test
    fun otherProfileOfTheChildCoversASection() {
        // The parent profile failed grades, the student profile got them.
        assertNull(syncProblem(listOf(done(SyncSection.GRADES to failed), done())))
        assertNull(syncProblem(listOf(State.Failed(RuntimeException()), done())))
    }

    @Test
    fun warningReplacesQuietText() {
        val summary = DailySummary("Jan: podsumowanie dnia", emptyList(), warning = "Brak połączenia z Librusem.")
        assertEquals("Brak połączenia z Librusem.", summary.text)
        assertEquals("Brak połączenia z Librusem.", summary.headline)
        assertEquals(DailySummary.QUIET_TEXT, summary.copy(warning = null).text)
    }

    @Test
    fun offlineOnlyWhenEverythingFailedOnNetwork() {
        val network = LibrusException(Reason.NETWORK, "x")
        assertTrue(isOffline(listOf(State.Failed(network), State.Done(SyncReport(SyncSection.entries.associateWith { SectionResult.Failed(network) })))))
        assertFalse(isOffline(listOf(State.Failed(network), done())))
        assertFalse(isOffline(listOf(State.Failed(LibrusException(Reason.MAINTENANCE, "x")))))
        assertFalse(isOffline(listOf(State.Failed(network), State.Failed(LibrusException(Reason.REAUTH_REQUIRED, "x")))))
    }
}
