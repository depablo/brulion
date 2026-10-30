package pl.brulion.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.brulion.data.db.EventEntity
import pl.brulion.ui.common.NewBadge
import pl.brulion.ui.common.formatRelative

/** A calendar entry ("Sprawdzian · Matematyka" + topic); [showDate] for lists spanning several days. */
@Composable
fun EventRow(event: EventEntity, modifier: Modifier = Modifier, showDate: Boolean = false, isNew: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(10.dp)
                .background(Color(event.color), CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val title = listOfNotNull(event.typeName, event.subjectName).joinToString(" · ")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isNew) {
                    Spacer(Modifier.width(8.dp))
                    NewBadge()
                }
            }
            if (event.topic.isNotBlank()) {
                Text(
                    text = event.topic,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val meta = listOfNotNull(
                event.date.formatRelative().takeIf { showDate },
                // Teachers' lesson numbers are often wrong (and the start time follows from them);
                // only an explicitly given time is shown.
                event.startTime.takeIf { event.lessonNo == null },
                event.teacherName,
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
