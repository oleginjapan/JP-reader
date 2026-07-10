package com.example.jpreader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.jpreader.settings.ApiKeySettingsScreen
import com.example.jpreader.settings.ApiKeyStore
import com.example.jpreader.settings.SettingsViewModel

sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Reading")
    object Settings : Screen("settings", "Settings")
}

@Composable
fun JpReaderApp() {
    val navController = rememberNavController()

    Scaffold(
        topBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            TopAppBar(
                title = { Text(screenTitleFor(currentRoute)) },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Home.route) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val keyStore = remember(context) { ApiKeyStore(context) }
                val readingViewModel = androidx.lifecycle.viewmodel.compose.viewModel<ReadingViewModel> {
                    ReadingViewModel(context.contentResolver, keyStore)
                }
                ReadingScreen(readingViewModel)
            }
            composable(Screen.Settings.route) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val keyStore = remember(context) { ApiKeyStore(context) }
                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<SettingsViewModel> {
                    SettingsViewModel(keyStore)
                }
                ApiKeySettingsScreen(viewModel)
            }
        }
    }
}

private fun screenTitleFor(route: String?): String = when (route) {
    Screen.Settings.route -> Screen.Settings.title
    else -> Screen.Home.title
}

