package pl.brulion.data.summary

import pl.brulion.data.sync.SectionResult
import pl.brulion.data.sync.SyncController
import pl.brulion.data.sync.SyncSection
import pl.brulion.librus.LibrusException
import pl.brulion.librus.LibrusException.Reason

/**
 * What the summary of one child must admit about the sync before it: null when the data is
 * fresh. A child can have a parent and a student profile; a section counts as fetched when
 * either got it.
 */
fun syncProblem(states: List<SyncController.State?>): String? {
    val reports = states.filterIsInstance<SyncController.State.Done>().map { it.report }
    val fetched = SyncSection.entries.filter { section -> reports.any { it.results[section] is SectionResult.Ok } }
    if (fetched.isEmpty()) {
        val errors = states.mapNotNull { (it as? SyncController.State.Failed)?.error } +
            reports.flatMap { report -> report.results.values.mapNotNull { (it as? SectionResult.Failed)?.error } }
        val reasons = errors.mapNotNull { (it as? LibrusException)?.reason }.toSet()
        return when {
            Reason.REAUTH_REQUIRED in reasons || Reason.ACCOUNT_DISCONNECTED in reasons ->
                "Brulion nie może zalogować się do Librusa. Otwórz aplikację i zaloguj się ponownie; do tego czasu podsumowania będą puste."
            Reason.MAINTENANCE in reasons -> "Librus ma przerwę techniczną, nie udało się pobrać nowych danych."
            Reason.NETWORK in reasons -> "Brak połączenia z Librusem, nie udało się pobrać nowych danych."
            else -> "Nie udało się pobrać nowych danych z Librusa."
        }
    }
    val missing = SyncSection.entries.filter { section ->
        section in SECTION_LABELS && section !in fetched && reports.any { it.results[section] is SectionResult.Failed }
    }
    if (missing.isEmpty()) return null
    return "Nie udało się pobrać: " + missing.joinToString(", ") { SECTION_LABELS.getValue(it) } + "."
}

/** True when nothing was fetched only because the network was down, so trying later may help. */
fun isOffline(states: List<SyncController.State?>): Boolean {
    val reports = states.filterIsInstance<SyncController.State.Done>().map { it.report }
    if (reports.any { report -> report.results.values.any { it is SectionResult.Ok } }) return false
    val errors = states.mapNotNull { (it as? SyncController.State.Failed)?.error } +
        reports.flatMap { report -> report.results.values.mapNotNull { (it as? SectionResult.Failed)?.error } }
    return errors.isNotEmpty() && errors.all { (it as? LibrusException)?.reason == Reason.NETWORK }
}

/** Sections whose absence makes the summary incomplete; the lucky number doesn't. */
private val SECTION_LABELS = mapOf(
    SyncSection.GRADES to "oceny",
    SyncSection.MESSAGES to "wiadomości",
    SyncSection.NOTES to "uwagi",
    SyncSection.ATTENDANCE to "frekwencja",
    SyncSection.ANNOUNCEMENTS to "ogłoszenia",
    SyncSection.EVENTS to "terminarz",
    SyncSection.TIMETABLE to "plan lekcji",
)
