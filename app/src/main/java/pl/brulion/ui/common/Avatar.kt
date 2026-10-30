package pl.brulion.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

private val AvatarColors = listOf(
    Color(0xFF2B4C7E), Color(0xFF3E6837), Color(0xFF8A5A00),
    Color(0xFF7A3E8E), Color(0xFF00696E), Color(0xFFA23D33),
)

/** The color a person keeps everywhere in the app (avatar, feed marker). */
fun avatarColor(name: String): Color = AvatarColors[name.hashCode().absoluteValue % AvatarColors.size]

/** Initials on a color picked deterministically from the name, so each child keeps "their" color. */
@Composable
fun NameAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    val color = avatarColor(name)
    Box(
        modifier = modifier.size(size).background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials.ifEmpty { "?" },
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
