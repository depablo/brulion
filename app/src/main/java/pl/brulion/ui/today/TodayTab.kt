package pl.brulion.ui.today

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import pl.brulion.R
import pl.brulion.data.childKey
import pl.brulion.data.db.AnnouncementEntity
import pl.brulion.data.db.EventEntity
import pl.brulion.data.db.GradeEntity
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.MessageEntity
import pl.brulion.data.db.ProfileEntity
import pl.brulion.data.db.TimetableLessonEntity
import pl.brulion.data.school.RECENT_DAYS
import pl.brulion.data.school.TIMETABLE_WEEKS
import pl.brulion.data.school.eventsByLesson
import pl.brulion.data.school.nextLessonDay
import pl.brulion.data.school.schoolHours
import pl.brulion.data.school.todayContent
import pl.brulion.data.summary.syncProblem
import pl.brulion.data.sync.SyncController
import pl.brulion.librus.schoolTimeNow
import pl.brulion.ui.common.GradeChip
import pl.brulion.ui.common.NewBadge
import pl.brulion.ui.common.SectionCard
import pl.brulion.ui.common.appContainer
import pl.brulion.ui.common.formatLong
import pl.brulion.ui.common.formatRelative
import pl.brulion.ui.common.formatTimestamp
import pl.brulion.ui.common.isFresh
import pl.brulion.ui.common.rememberToday
import pl.brulion.ui.events.EventRow
import pl.brulion.ui.timetable.LessonRow
import java.text.Collator
import java.time.LocalDate
import java.util.Locale

private const val DAY = 24 * 60 * 60L

@Composable
fun TodayTab(
    profile: ProfileEntity,
    onShowTimetable: () -> Unit,
    onShowGrades: () -> Unit,
    onShowMessages: () -> Unit,
    onOpenMessage: (messageId: Long) -> Unit,
    onShowEvents: () -> Unit,
    onShowAnnouncements: () -> Unit,
) {
    val container = appContainer()
    val db = container.database
    val profileId = profile.id
    val syncStates by container.sync.states.collectAsStateWithLifecycle()
    val today = rememberToday()
    val lessons by remember(profileId, today) {
        db.timetableDao().observeRange(profileId, today, today.plusWeeks(TIMETABLE_WEEKS))
    }.collectAsStateWithLifecycle(emptyList())
    val profiles by container.accounts.allProfiles.collectAsStateWithLifecycle(emptyList())
    val familyLessons by remember(today) {
        db.timetableDao().observeRangeAllProfiles(today, today.plusWeeks(TIMETABLE_WEEKS))
    }.collectAsStateWithLifecycle(emptyList())
    val lucky by remember(profileId, today) { db.luckyNumberDao().observeFrom(profileId, today) }.collectAsStateWithLifecycle(null)
    // Only what the screen can show: unread messages, this week's planner, recent grades.
    val messages by remember(profileId) { db.messageDao().observeUnread(profileId) }.collectAsStateWithLifecycle(emptyList())
    val grades by remember(profileId, today) {
        val now = System.currentTimeMillis() / 1000
        db.gradeDao().observeRecent(profileId, addedSince = now - RECENT_DAYS * DAY, seenSince = now - DAY)
    }.collectAsStateWithLifecycle(emptyList())
    val events by remember(profileId, today) { db.eventDao().observeRange(profileId, today, today.plusDays(RECENT_DAYS)) }
        .collectAsStateWithLifecycle(emptyList())
    val announcements by remember(profileId) { container.announcements.observe(profileId).map { it.visible } }
        .collectAsStateWithLifecycle(emptyList())

    val content = todayContent(
        lessons, messages, grades, events, announcements,
        today = today,
        now = System.currentTimeMillis() / 1000,
        nowTime = schoolTimeNow(),
    )
    val lessonDay = content.lessonDay
    val unread = content.unread
    val recentGrades = content.recentGrades
    val upcoming = content.upcoming
    val recentAnnouncements = content.announcements
    val changes = content.changes

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(today.formatLong(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (profile.lastSyncAt == 0L) {
                        stringResource(R.string.home_sync_never)
                    } else {
                        stringResource(
                            R.string.today_updated,
                            DateUtils.getRelativeTimeSpanString(profile.lastSyncAt * 1000).toString(),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                lucky?.let {
                    Spacer(Modifier.height(8.dp))
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                if (it.date == today) stringResource(R.string.today_lucky, it.number)
                                else stringResource(R.string.today_lucky_on, it.number, it.date.formatLong())
                            )
                        },
                    )
                }
            }
        }

        familyHoursSection(profiles, familyLessons, today)

        // A parent wants to know what happened, a student what's coming.
        if (profile.isParent) {
            unreadSection(unread, onShowMessages, onOpenMessage)
            gradesSection(recentGrades, onShowGrades)
            changesSection(changes, today, onShowTimetable)
            upcomingSection(upcoming, onShowEvents)
            announcementsSection(recentAnnouncements, onShowAnnouncements)
        } else {
            lessonsSection(lessonDay, events, today, onShowTimetable)
            upcomingSection(upcoming, onShowEvents)
            unreadSection(unread, onShowMessages, onOpenMessage)
            announcementsSection(recentAnnouncements, onShowAnnouncements)
        }

        if (content.isQuiet(profile.isParent)) {
            // "Nothing new" is only true when the data could be fetched.
            val problem = when (val state = syncStates[profileId]) {
                null, SyncController.State.Running -> null
                else -> syncProblem(listOf(state))
            }
            item {
                if (problem == null && profile.lastSyncAt != 0L) {
                    SectionCard(stringResource(R.string.today_quiet), icon = Icons.Outlined.CheckCircle) {
                        Text(stringResource(R.string.today_quiet_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    SectionCard(stringResource(R.string.today_stale), icon = Icons.Outlined.CloudOff) {
                        Text(
                            text = problem ?: stringResource(R.string.home_sync_never),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * When each child starts and finishes on the next school day, for picking them up. Only with
 * more than one child; for one, the lessons section says it already.
 */
private fun LazyListScope.familyHoursSection(profiles: List<ProfileEntity>, lessons: List<TimetableLessonEntity>, today: LocalDate) {
    val children = profiles.groupBy { childKey(it) }
    if (children.size < 2) return
    val (day, _) = nextLessonDay(lessons, today) ?: return
    val profileChild = profiles.associate { it.id to childKey(it) }
    val dayByChild = lessons.filter { it.date == day }.groupBy { profileChild[it.profileId] }
    item {
        val title = when (day) {
            today -> stringResource(R.string.today_family_today)
            today.plusDays(1) -> stringResource(R.string.today_family_tomorrow)
            else -> stringResource(R.string.today_family_on, day.formatLong())
        }
        SectionCard(title, icon = Icons.Outlined.Groups) {
            children.entries.sortedWith(compareBy(Collator.getInstance(Locale.forLanguageTag("pl"))) { it.value.first().studentName }).forEach { (key, childProfiles) ->
                // Parent and student profiles show the same timetable; whichever has it.
                val hours = schoolHours(dayByChild[key].orEmpty().distinctBy { it.lessonNo to it.position })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        childProfiles.first().studentName.substringBefore(' '),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = hours?.let { (start, end) -> "$start–$end" } ?: stringResource(R.string.today_family_free),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (hours == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.lessonsSection(
    lessonDay: Pair<LocalDate, List<TimetableLessonEntity>>?,
    events: List<EventEntity>,
    today: LocalDate,
    onShowTimetable: () -> Unit,
) {
    val (day, dayLessons) = lessonDay ?: return
    val byLesson = eventsByLesson(dayLessons, events.filter { it.date == day })
    item {
        val title = when (day) {
            today -> stringResource(R.string.today_lessons_today)
            today.plusDays(1) -> stringResource(R.string.today_lessons_tomorrow)
            else -> stringResource(R.string.today_lessons_on, day.formatLong())
        }
        SectionCard(title, icon = Icons.Outlined.Schedule, action = stringResource(R.string.today_more), onAction = onShowTimetable) {
            dayLessons.forEach { LessonRow(it, Modifier.fillMaxWidth(), events = byLesson[it].orEmpty()) }
        }
    }
}

private fun LazyListScope.changesSection(changes: List<TimetableLessonEntity>, today: LocalDate, onShowTimetable: () -> Unit) {
    if (changes.isEmpty()) return
    item {
        SectionCard(
            title = stringResource(R.string.today_changes),
            icon = Icons.Outlined.EventBusy,
            action = stringResource(R.string.today_more),
            onAction = onShowTimetable,
        ) {
            changes.groupBy { it.date }.forEach { (day, lessons) ->
                Text(
                    text = day.formatRelative(today),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                lessons.forEach { LessonRow(it, Modifier.fillMaxWidth()) }
            }
        }
    }
}

private fun LazyListScope.unreadSection(
    unread: List<MessageEntity>,
    onShowMessages: () -> Unit,
    onOpenMessage: (Long) -> Unit,
) {
    if (unread.isEmpty()) return
    item {
        SectionCard(
            title = stringResource(R.string.today_unread_messages, unread.size),
            icon = Icons.Outlined.Mail,
            action = stringResource(R.string.today_more),
            onAction = onShowMessages,
        ) {
            unread.take(3).forEach { message -> CompactMessage(message, Modifier.clickable { onOpenMessage(message.id) }) }
        }
    }
}

private fun LazyListScope.gradesSection(grades: List<GradeEntity>, onShowGrades: () -> Unit) {
    if (grades.isEmpty()) return
    item {
        SectionCard(
            title = stringResource(R.string.today_recent_grades),
            icon = Icons.Outlined.School,
            action = stringResource(R.string.today_more),
            onAction = onShowGrades,
        ) {
            grades.forEach { RecentGrade(it) }
        }
    }
}

private fun LazyListScope.upcomingSection(events: List<EventEntity>, onShowEvents: () -> Unit) {
    if (events.isEmpty()) return
    item {
        SectionCard(
            title = stringResource(R.string.today_upcoming),
            icon = Icons.Outlined.Event,
            action = stringResource(R.string.today_more),
            onAction = onShowEvents,
        ) {
            events.forEach { EventRow(it, showDate = true, isNew = isFresh(it.firstSeenAt)) }
        }
    }
}

private fun LazyListScope.announcementsSection(announcements: List<AnnouncementEntity>, onShowAnnouncements: () -> Unit) {
    if (announcements.isEmpty()) return
    item {
        SectionCard(
            title = stringResource(R.string.section_announcements),
            icon = Icons.Outlined.Campaign,
            action = stringResource(R.string.today_more),
            onAction = onShowAnnouncements,
        ) {
            announcements.forEach { CompactAnnouncement(it, Modifier.clickable(onClick = onShowAnnouncements)) }
        }
    }
}

@Composable
private fun CompactMessage(message: MessageEntity, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message.senderName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(formatTimestamp(message.sentAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(message.subject, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RecentGrade(grade: GradeEntity) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GradeChip(grade)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(grade.subjectName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOf(grade.categoryName, formatTimestamp(grade.addedAt)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isFresh(grade.firstSeenAt)) NewBadge()
    }
}

@Composable
private fun CompactAnnouncement(announcement: AnnouncementEntity, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = announcement.subject,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOfNotNull(announcement.authorName, formatTimestamp(announcement.addedAt)).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
