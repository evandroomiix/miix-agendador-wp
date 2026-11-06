package com.miix.agendadorwp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.miix.agendadorwp.data.AppDatabase
import com.miix.agendadorwp.data.WhatsAppType
import com.miix.agendadorwp.ui.screens.*
import com.miix.agendadorwp.ui.theme.*

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Início", Icons.Default.Home)
    object New : Screen("new", "Novo", Icons.Default.AddCircle)
    object History : Screen("history", "Histórico", Icons.Default.History)
    object Settings : Screen("settings", "Ajustes", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pedirPermissoesIniciais()

        val database = AppDatabase.getInstance(applicationContext)

        setContent {
            MiixAgendadorWPTheme {
                MainScreen(database = database)
            }
        }
    }

    private fun pedirPermissoesIniciais() {
        // Permissão de Notificações no Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Permissão de Alarmes Exatos no Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    startActivity(intent)
                } catch (e: Exception) {
                    // Fallback
                }
            }
        }
    }
}

@Composable
fun MainScreen(database: AppDatabase) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var selectedPlatformForNew by remember { mutableStateOf(WhatsAppType.WHATSAPP_NORMAL) }
    var editingMessageId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                contentColor = TextPrimary
            ) {
                val items = listOf(Screen.Home, Screen.New, Screen.History, Screen.Settings)
                items.forEach { screen ->
                    val selected = currentScreen == screen
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (screen == Screen.New) {
                                editingMessageId = null
                            }
                            currentScreen = screen
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DarkBackground,
                            selectedTextColor = GreenPrimary,
                            indicatorColor = GreenPrimary,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                is Screen.Home -> HomeScreen(
                    database = database,
                    onNavigateToNew = { chosenPlatform ->
                        selectedPlatformForNew = chosenPlatform
                        editingMessageId = null
                        currentScreen = Screen.New
                    },
                    onEditMessage = { messageId ->
                        editingMessageId = messageId
                        currentScreen = Screen.New
                    }
                )
                is Screen.New -> NewScheduleScreen(
                    database = database,
                    initialPlatform = selectedPlatformForNew,
                    editingMessageId = editingMessageId,
                    onSuccess = {
                        editingMessageId = null
                        currentScreen = Screen.Home
                    }
                )
                is Screen.History -> HistoryScreen(database = database)
                is Screen.Settings -> SettingsScreen()
            }
        }
    }
}
