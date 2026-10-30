package pl.brulion.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import pl.brulion.librus.SCHOOL_ZONE
import pl.brulion.librus.schoolToday
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Today's date that moves on at midnight and when the app comes back, so a screen left open
 * overnight doesn't keep showing yesterday as today.
 */
@Composable
fun rememberToday(): LocalDate {
    var today by remember { mutableStateOf(schoolToday()) }
    LifecycleResumeEffect(Unit) {
        today = schoolToday()
        onPauseOrDispose {}
    }
    LaunchedEffect(today) {
        val midnight = today.plusDays(1).atStartOfDay(SCHOOL_ZONE)
        delay(Duration.between(ZonedDateTime.now(SCHOOL_ZONE), midnight).toMillis().coerceAtLeast(0) + 1_000)
        today = schoolToday()
    }
    return today
}
