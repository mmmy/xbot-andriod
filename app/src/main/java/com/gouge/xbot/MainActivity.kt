package com.gouge.xbot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gouge.xbot.data.ServerConfigStore
import com.gouge.xbot.data.SessionStore
import com.gouge.xbot.data.AlertVisibilityStore
import com.gouge.xbot.data.XbotRepository
import com.gouge.xbot.ui.MainScreen
import com.gouge.xbot.ui.MainViewModel
import com.gouge.xbot.ui.theme.XbotTheme
import com.gouge.xbot.widget.SignalWidgetScheduler

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val serverConfigStore = ServerConfigStore(applicationContext)
        val sessionStore = SessionStore(applicationContext)
        val repository = XbotRepository(serverConfigStore, sessionStore)
        val alertVisibilityStore = AlertVisibilityStore(applicationContext)
        val factory = MainViewModelFactory(
            repository = repository,
            serverConfigStore = serverConfigStore,
            sessionStore = sessionStore,
            alertVisibilityStore = alertVisibilityStore,
            onSignalsChanged = {
                SignalWidgetScheduler.enqueueImmediate(applicationContext)
            },
        )

        setContent {
            XbotTheme {
                val mainViewModel: MainViewModel = viewModel(factory = factory)
                MainScreen(mainViewModel)
            }
        }
    }
}

private class MainViewModelFactory(
    private val repository: XbotRepository,
    private val serverConfigStore: ServerConfigStore,
    private val sessionStore: SessionStore,
    private val alertVisibilityStore: AlertVisibilityStore,
    private val onSignalsChanged: () -> Unit,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(
                repository = repository,
                serverConfigStore = serverConfigStore,
                sessionStore = sessionStore,
                alertVisibilityStore = alertVisibilityStore,
                onSignalsChanged = onSignalsChanged,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
