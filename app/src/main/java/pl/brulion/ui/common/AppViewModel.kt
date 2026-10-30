package pl.brulion.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.brulion.AppContainer
import pl.brulion.BrulionApp

/** Creates a ViewModel scoped to the current navigation entry, with access to the [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as BrulionApp).container
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}
