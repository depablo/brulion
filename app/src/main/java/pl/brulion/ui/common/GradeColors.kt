package pl.brulion.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.brulion.data.db.GradeEntity

// Grades are colored by value rather than by Librus category, so a glance tells how it went.
private val GradeScale = listOf(
    Color(0xFFE5534B), // 1
    Color(0xFFF08A3E), // 2
    Color(0xFFE8B730), // 3
    Color(0xFF8DBF4A), // 4
    Color(0xFF3FA66B), // 5
    Color(0xFF2B8C8C), // 6
)

fun gradeColor(numericValue: Double?): Color? =
    numericValue?.takeIf { it >= 1.0 }?.let { GradeScale[(it.toInt() - 1).coerceIn(0, GradeScale.lastIndex)] }

@Composable
fun GradeChip(grade: GradeEntity, modifier: Modifier = Modifier) {
    val color = gradeColor(grade.numericValue)
    Box(
        modifier = modifier
            .alpha(if (grade.improvedById != null) 0.45f else 1f)
            .background(color ?: MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
            .defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
            .widthIn(max = 160.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = grade.value.ifEmpty { "?" },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (color != null) Color.White else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
