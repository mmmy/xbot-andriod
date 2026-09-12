package com.gouge.xbot

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.gouge.xbot.widget.AlertDataCoordinator
import com.gouge.xbot.widget.AlertWidgetTarget
import com.gouge.xbot.widget.AlertWidgetIntents

class MainActivity : ComponentActivity() {
    private val widgetTarget = mutableStateOf<AlertWidgetTarget?>(null)
    private val resumeGeneration = mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readWidgetTarget(intent)
        val serverConfigStore = ServerConfigStore(applicationContext)
        val sessionStore = SessionStore(applicationContext)
        val repository = XbotRepository(serverConfigStore, sessionStore)
        val alertVisibilityStore = AlertVisibilityStore(applicationContext)
        val factory = MainViewModelFactory(
            repository = repository,
            serverConfigStore = serverConfigStore,
            sessionStore = sessionStore,
            alertVisibilityStore = alertVisibilityStore,
            alertSync = AlertDataCoordinator(applicationContext),
            onSignalsChanged = {
                SignalWidgetScheduler.enqueueImmediate(applicationContext)
            },
        )

        setContent {
            XbotTheme {
                val mainViewModel: MainViewModel = viewModel(factory = factory)
                MainScreen(mainViewModel, widgetTarget.value, resumeGeneration.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readWidgetTarget(intent)
    }

    override fun onResume() {
        super.onResume()
        resumeGeneration.intValue++
    }

    private fun readWidgetTarget(intent: Intent) {
        widgetTarget.value = intent.getStringExtra(AlertWidgetIntents.ConfigId)?.let {
            AlertWidgetTarget(it, intent.getLongExtra(AlertWidgetIntents.AlertId, -1))
        }
    }
}

private class MainViewModelFactory(
    private val repository: XbotRepository,
    private val serverConfigStore: ServerConfigStore,
    private val sessionStore: SessionStore,
    private val alertVisibilityStore: AlertVisibilityStore,
    private val alertSync: AlertDataCoordinator,
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
                alertSync = alertSync,
                onSignalsChanged = onSignalsChanged,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
