package pl.brulion.data.school

import pl.brulion.data.db.GradeEntity
import pl.brulion.data.db.GradeKind
import pl.brulion.data.db.GradeType

/** Grades of one subject in one semester, as shown on the grades screen. */
data class SubjectGrades(
    val subjectName: String,
    /** Regular grades in the order they were given. */
    val grades: List<GradeEntity>,
    /** Proposed and final grades (semester or year), in [GradeType] order. */
    val summaryGrades: List<GradeEntity>,
    val average: Double?,
)

/** Weighted average of regular normal grades; improved grades still count, like in Librus. */
fun weightedAverage(grades: List<GradeEntity>): Double? {
    val counted = grades.filter {
        it.kind == GradeKind.NORMAL && it.type == GradeType.REGULAR && it.weight > 0 && it.numericValue != null
    }
    val weights = counted.sumOf { it.weight }
    if (weights == 0.0) return null
    return counted.sumOf { it.numericValue!! * it.weight } / weights
}

/** The weighted average after one more grade of [value] with [weight]. */
fun averageWith(grades: List<GradeEntity>, value: Double, weight: Double): Double? {
    val counted = grades.filter {
        it.kind == GradeKind.NORMAL && it.type == GradeType.REGULAR && it.weight > 0 && it.numericValue != null
    }
    val weights = counted.sumOf { it.weight } + weight
    if (weights == 0.0) return null
    return (counted.sumOf { it.numericValue!! * it.weight } + value * weight) / weights
}

/** Weights the subject's counted grades use, e.g. [1, 2, 3], to try a next grade with. */
fun usedWeights(grades: List<GradeEntity>): List<Double> =
    grades.filter { it.kind == GradeKind.NORMAL && it.type == GradeType.REGULAR && it.weight > 0 && it.numericValue != null }
        .map { it.weight }.distinct().sorted()

fun subjectGrades(grades: List<GradeEntity>, semester: Int): List<SubjectGrades> =
    grades.filter { it.semester == semester }
        .groupBy { it.subjectName }
        .map { (subject, list) ->
            val regular = list.filter { it.type == GradeType.REGULAR }.sortedBy { it.addedAt }
            SubjectGrades(
                subjectName = subject,
                grades = regular,
                summaryGrades = list.filter { it.type != GradeType.REGULAR }.sortedBy { it.type.ordinal },
                average = weightedAverage(regular),
            )
        }
        .sortedBy { it.subjectName.lowercase() }

/** Mean of subject averages, which is what Librus shows as the overall average. */
fun overallAverage(subjects: List<SubjectGrades>): Double? =
    subjects.mapNotNull { it.average }.takeIf { it.isNotEmpty() }?.average()

/** The semester to open by default: the second one once it has any grades. */
fun currentSemester(grades: List<GradeEntity>): Int = if (grades.any { it.semester >= 2 }) 2 else 1
