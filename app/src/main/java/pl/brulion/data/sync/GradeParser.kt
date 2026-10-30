/*
 * Part of Brulion, GNU GPL v3. Based on Librus support in Szkolny.eu
 * (https://github.com/szkolny-eu/szkolny-android), (c) Kuba Szczodrzyński and contributors, GPL v3.
 */
package pl.brulion.data.sync

import pl.brulion.data.db.GradeEntity
import pl.brulion.data.db.GradeKind
import pl.brulion.data.db.GradeType
import pl.brulion.librus.array
import pl.brulion.librus.bool
import pl.brulion.librus.double
import pl.brulion.librus.flag
import pl.brulion.librus.int
import pl.brulion.librus.librusColor
import pl.brulion.librus.long
import pl.brulion.librus.normalizeSpaces
import pl.brulion.librus.obj
import pl.brulion.librus.objects
import pl.brulion.librus.parseLibrusDateTime
import pl.brulion.librus.refId
import pl.brulion.librus.string

// "0" (e.g. missing work) is shown but has no value for the average, same as in Szkolny.
private val GRADE_REGEX = Regex("^([1-6])([+-]?)$")
private val NOT_COUNTED = setOf("-", "+", "np", "bz")

/** "5" -> 5.0, "5+" -> 5.5, "5-" -> 4.75; null for "np", "+", etc. */
internal fun gradeNumericValue(value: String): Double? {
    val match = GRADE_REGEX.find(value.trim()) ?: return null
    val base = match.groupValues[1].toDouble()
    return when (match.groupValues[2]) {
        "+" -> base + 0.5
        "-" -> base - 0.25
        else -> base
    }
}

internal class GradeParser(private val session: SyncSession, private val profileId: Long) {

    suspend fun normal(): List<GradeEntity> {
        val subjects = session.subjects()
        val teachers = session.teachers()
        val categories = session.byId("Grades/Categories", "Categories")
        val comments = session.byId("Grades/Comments", "Comments")
            .mapValues { it.value.string("Text").orEmpty().normalizeSpaces() }

        val grades = session.get("Grades").array("Grades")?.objects().orEmpty()
        // A grade with Improvement.Id corrects an older one; remember the link on the older grade.
        val improvedBy = grades.mapNotNull { grade ->
            val id = grade.long("Id") ?: return@mapNotNull null
            grade.refId("Improvement")?.let { it to id }
        }.toMap()

        return grades.mapNotNull { grade ->
            val id = grade.long("Id") ?: return@mapNotNull null
            val semester = grade.int("Semester") ?: return@mapNotNull null
            val subjectId = grade.refId("Subject") ?: return@mapNotNull null
            val value = grade.string("Grade").orEmpty().trim()
            val category = grade.refId("Category")?.let(categories::get)
            val countsToAverage = category?.bool("CountToTheAverage") == true
            val weight = when {
                !countsToAverage || value.lowercase() in NOT_COUNTED -> 0.0
                else -> category?.double("Weight") ?: 0.0
            }
            val type = when {
                grade.flag("IsConstituent") -> GradeType.REGULAR
                grade.flag("IsSemester") -> GradeType.SEMESTER_FINAL
                grade.flag("IsSemesterProposition") -> GradeType.SEMESTER_PROPOSED
                grade.flag("IsFinal") -> GradeType.YEAR_FINAL
                grade.flag("IsFinalProposition") -> GradeType.YEAR_PROPOSED
                else -> GradeType.REGULAR
            }
            GradeEntity(
                profileId = profileId,
                kind = GradeKind.NORMAL,
                id = id,
                type = type,
                semester = semester,
                subjectId = subjectId,
                subjectName = subjects[subjectId].orEmpty(),
                value = value,
                numericValue = gradeNumericValue(value),
                maxValue = null,
                weight = weight,
                categoryName = category?.string("Name")?.normalizeSpaces().orEmpty(),
                color = librusColor(category?.obj("Color")?.int("Id")),
                comment = grade.array("Comments")?.objects()?.firstOrNull()?.long("Id")?.let(comments::get),
                teacherName = grade.refId("AddedBy")?.let(teachers::get),
                addedAt = parseLibrusDateTime(grade.string("AddDate")) ?: 0,
                improvedById = improvedBy[id],
                firstSeenAt = 0,
            )
        }
    }

    suspend fun point(): List<GradeEntity> {
        val subjects = session.subjects()
        val teachers = session.teachers()
        val categories = session.byId("PointGrades/Categories", "Categories")

        return session.get("PointGrades").array("Grades")?.objects().orEmpty().mapNotNull { grade ->
            val id = grade.long("Id") ?: return@mapNotNull null
            val semester = grade.int("Semester") ?: return@mapNotNull null
            val subjectId = grade.refId("Subject") ?: return@mapNotNull null
            val category = grade.refId("Category")?.let(categories::get)
            // Unlike normal grades, point categories count by default.
            val countsToAverage = category?.bool("CountToTheAverage") ?: true
            GradeEntity(
                profileId = profileId,
                kind = GradeKind.POINT,
                id = id,
                type = GradeType.REGULAR,
                semester = semester,
                subjectId = subjectId,
                subjectName = subjects[subjectId].orEmpty(),
                value = grade.string("Grade").orEmpty().trim(),
                numericValue = grade.double("GradeValue"),
                maxValue = category?.double("ValueTo"),
                weight = if (countsToAverage) category?.double("Weight") ?: 0.0 else 0.0,
                categoryName = category?.string("Name")?.normalizeSpaces().orEmpty(),
                color = librusColor(category?.obj("Color")?.int("Id")),
                comment = null,
                teacherName = grade.refId("AddedBy")?.let(teachers::get),
                addedAt = parseLibrusDateTime(grade.string("AddDate")) ?: 0,
                improvedById = null,
                firstSeenAt = 0,
            )
        }
    }

    suspend fun descriptive(): List<GradeEntity> {
        val subjects = session.subjects()
        val teachers = session.teachers()
        val skills = session.byId("DescriptiveTextGrades/Skills", "Skills")

        return session.get("DescriptiveGrades").array("Grades")?.objects().orEmpty().mapNotNull { grade ->
            val id = grade.long("Id") ?: return@mapNotNull null
            val semester = grade.int("Semester") ?: return@mapNotNull null
            val subjectId = grade.refId("Subject") ?: return@mapNotNull null
            val skill = grade.refId("Skill")?.let(skills::get)
            val realValue = grade.string("RealGradeValue")
            val mapped = grade.string("Map")
            GradeEntity(
                profileId = profileId,
                kind = GradeKind.DESCRIPTIVE,
                id = id,
                type = GradeType.REGULAR,
                semester = semester,
                subjectId = subjectId,
                subjectName = subjects[subjectId].orEmpty(),
                value = (mapped ?: realValue).orEmpty().trim(),
                numericValue = null,
                maxValue = null,
                weight = 0.0,
                categoryName = skill?.string("Name")?.normalizeSpaces().orEmpty(),
                color = librusColor(skill?.obj("Color")?.int("Id")),
                comment = listOfNotNull(
                    realValue?.takeIf { mapped != null && it != mapped },
                    grade.string("Phrase"),
                ).joinToString("\n").ifEmpty { null },
                teacherName = grade.refId("AddedBy")?.let(teachers::get),
                addedAt = parseLibrusDateTime(grade.string("AddDate")) ?: 0,
                improvedById = null,
                firstSeenAt = 0,
            )
        }
    }
}
