package pl.brulion.ui.grades

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.brulion.R
import pl.brulion.data.db.GradeEntity
import pl.brulion.data.db.GradeKind
import pl.brulion.data.db.GradeType
import pl.brulion.data.school.SubjectGrades
import pl.brulion.data.school.averageWith
import pl.brulion.data.school.currentSemester
import pl.brulion.data.school.overallAverage
import pl.brulion.data.school.subjectGrades
import pl.brulion.data.school.usedWeights
import pl.brulion.ui.common.EmptyState
import pl.brulion.ui.common.GradeChip
import pl.brulion.ui.common.NewBadge
import pl.brulion.ui.common.appContainer
import pl.brulion.ui.common.epochToLocal
import pl.brulion.ui.common.formatAverage
import pl.brulion.ui.common.formatShort
import pl.brulion.ui.common.formatWeight
import pl.brulion.ui.common.isFresh

@Composable
fun GradesTab(profileId: Long) {
    val db = appContainer().database
    val grades by remember(profileId) { db.gradeDao().observeAll(profileId) }.collectAsStateWithLifecycle(null)
    val all = grades ?: return
    if (all.isEmpty()) {
        EmptyState(Icons.Outlined.School, stringResource(R.string.grades_empty))
        return
    }

    var semester by rememberSaveable(profileId) { mutableStateOf(currentSemester(all)) }
    val subjects = remember(all, semester) { subjectGrades(all, semester) }
    var expanded by rememberSaveable(profileId, semester) { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                for (number in 1..2) {
                    SegmentedButton(
                        selected = semester == number,
                        onClick = { semester = number },
                        shape = SegmentedButtonDefaults.itemShape(number - 1, 2),
                    ) { Text(stringResource(R.string.grades_semester, number)) }
                }
            }
        }
        item {
            overallAverage(subjects)?.let {
                Text(
                    text = stringResource(R.string.grades_overall_average, it.formatAverage()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (subjects.isEmpty()) {
            item { Text(stringResource(R.string.grades_semester_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(subjects, key = { it.subjectName }) { subject ->
            SubjectCard(
                subject = subject,
                expanded = expanded == subject.subjectName,
                onClick = { expanded = if (expanded == subject.subjectName) null else subject.subjectName },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectCard(subject: SubjectGrades, expanded: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = subject.subjectName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                subject.average?.let {
                    Text(
                        text = it.formatAverage(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (subject.grades.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    subject.grades.forEach { GradeChip(it) }
                }
            }
            if (subject.summaryGrades.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    subject.summaryGrades.forEach { grade ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(grade.type.label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(6.dp))
                            GradeChip(grade)
                        }
                    }
                }
            }
            if (expanded) {
                // Only where there's a numeric average to move; not for e.g. letter grades in year one.
                if (subject.average != null) NextGradeCalculator(subject.grades)
                HorizontalDivider()
                (subject.grades + subject.summaryGrades).sortedByDescending { it.addedAt }.forEach { GradeDetails(it) }
            }
        }
    }
}

@Composable
private fun GradeDetails(grade: GradeEntity) {
    Row(verticalAlignment = Alignment.Top) {
        GradeChip(grade)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = grade.categoryName.ifBlank { stringResource(grade.type.label) },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isFresh(grade.firstSeenAt)) {
                    Spacer(Modifier.width(8.dp))
                    NewBadge()
                }
            }
            val meta = listOfNotNull(
                epochToLocal(grade.addedAt).toLocalDate().formatShort(),
                grade.maxValue?.takeIf { grade.kind == GradeKind.POINT }?.let { stringResource(R.string.grades_max_points, it.formatWeight()) },
                stringResource(R.string.grades_weight, grade.weight.formatWeight()).takeIf { grade.kind == GradeKind.NORMAL },
                grade.teacherName,
            ).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (grade.improvedById != null) {
                Text(
                    text = stringResource(R.string.grades_improved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            grade.comment?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private val GradeType.label: Int
    get() = when (this) {
        GradeType.REGULAR -> R.string.grade_type_regular
        GradeType.SEMESTER_PROPOSED -> R.string.grade_type_semester_proposed
        GradeType.SEMESTER_FINAL -> R.string.grade_type_semester_final
        GradeType.YEAR_PROPOSED -> R.string.grade_type_year_proposed
        GradeType.YEAR_FINAL -> R.string.grade_type_year_final
    }

/** "If the next grade is a 5 (weight 3), the average becomes 4,21", for every grade at once. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NextGradeCalculator(grades: List<GradeEntity>) {
    val weights = remember(grades) { usedWeights(grades) }
    if (weights.isEmpty()) return
    var weight by rememberSaveable(weights) { mutableStateOf(weights.last()) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.grades_next_grade),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            weights.forEach { w ->
                FilterChip(
                    selected = w == weight,
                    onClick = { weight = w },
                    label = { Text(stringResource(R.string.grades_weight_short, w.formatWeight())) },
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (value in 6 downTo 1) {
                val average = averageWith(grades, value.toDouble(), weight) ?: continue
                Text(
                    text = "$value → ${average.formatAverage()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
