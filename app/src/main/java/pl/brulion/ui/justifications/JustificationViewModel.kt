package pl.brulion.ui.justifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import pl.brulion.data.db.AttendanceEntity
import pl.brulion.data.justifications.JustificationMode
import pl.brulion.data.justifications.JustificationsRepository
import pl.brulion.data.justifications.canNotifyTeachers
import pl.brulion.data.justifications.justifiableEntries
import pl.brulion.data.justifications.justificationRequest
import pl.brulion.librus.LibrusJustifications
import pl.brulion.librus.schoolToday
import pl.brulion.ui.common.UiError
import pl.brulion.ui.common.toUiError
import java.time.LocalDate

/**
 * Sending runs in [appScope], so leaving the screen doesn't cut the request off halfway.
 * [initialLessonNo] preselects one lesson when the form is opened from an attendance row.
 */
class JustificationViewModel(
    attendance: Flow<List<AttendanceEntity>>,
    private val repository: JustificationsRepository,
    private val appScope: CoroutineScope,
    private val profileId: Long,
    initialDate: LocalDate,
    initialLessonNo: Int?,
) : ViewModel() {

    var entries by mutableStateOf<List<AttendanceEntity>>(emptyList())
        private set
    var mode by mutableStateOf(JustificationMode.LESSONS)
        private set
    var date by mutableStateOf(initialDate)
        private set
    var from by mutableStateOf(initialDate)
        private set
    var to by mutableStateOf(initialDate)
        private set
    var lessons by mutableStateOf(setOfNotNull(initialLessonNo))
        private set
    var message by mutableStateOf(DEFAULT_MESSAGE)
    var notifyTeachers by mutableStateOf(false)
    var sending by mutableStateOf(false)
        private set
    var sent by mutableStateOf(false)
        private set
    var error by mutableStateOf<UiError?>(null)
        private set

    init {
        viewModelScope.launch { attendance.collect { entries = it } }
    }

    /** Lessons of [date] that can be justified; empty when nothing is recorded for that day. */
    val lessonCandidates: List<AttendanceEntity> get() = justifiableEntries(entries, date)

    private val today: LocalDate get() = schoolToday()

    /** Librus allows notifying teachers only when the absence has not ended yet. */
    val canNotify: Boolean
        get() = canNotifyTeachers(if (mode == JustificationMode.LESSONS) date else to, today)

    val request: LibrusJustifications.Request?
        get() = justificationRequest(mode, date, from, to, lessons, message, notifyTeachers, today)

    val canSend: Boolean get() = request != null && !sending

    fun selectMode(newMode: JustificationMode) {
        mode = newMode
    }

    fun pickDate(newDate: LocalDate) {
        if (newDate == date) return
        date = newDate
        lessons = emptySet()
    }

    fun pickFrom(newFrom: LocalDate) {
        from = newFrom
        if (to.isBefore(newFrom)) to = newFrom
    }

    fun pickTo(newTo: LocalDate) {
        to = newTo
    }

    fun toggleLesson(lessonNo: Int) {
        lessons = if (lessonNo in lessons) lessons - lessonNo else lessons + lessonNo
    }

    fun send() {
        val request = request ?: return
        sending = true
        error = null
        appScope.launch {
            try {
                repository.send(profileId, request)
                sent = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUiError()
            }
            sending = false
        }
    }

    private companion object {
        /** Gender-neutral and short; the parent can edit it before sending. */
        const val DEFAULT_MESSAGE = "Proszę o usprawiedliwienie nieobecności."
    }
}
