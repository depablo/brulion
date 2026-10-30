package pl.brulion.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import pl.brulion.AppContainer
import pl.brulion.BrulionApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as BrulionApp).container
