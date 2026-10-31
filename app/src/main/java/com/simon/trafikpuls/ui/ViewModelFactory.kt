package com.simon.trafikpuls.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.simon.trafikpuls.AppContainer
import com.simon.trafikpuls.TrafikpulsApplication

/** Builds a ViewModel from the app's dependency container. */
inline fun <reified VM : ViewModel> appViewModelFactory(
    crossinline create: (AppContainer) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TrafikpulsApplication
        create(app.container)
    }
}
