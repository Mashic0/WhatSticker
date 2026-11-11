package com.whatsticker.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.whatsticker.app.data.local.PreferencesRepository
import com.whatsticker.app.data.local.UserSettings
import com.whatsticker.app.service.FloatingCaptureService
import com.whatsticker.app.ui.navigation.Screen
import com.whatsticker.app.ui.screens.captures.CapturesScreen
import com.whatsticker.app.ui.screens.packs.PackDetailScreen
import com.whatsticker.app.ui.screens.packs.PacksScreen
import com.whatsticker.app.ui.screens.settings.SettingsScreen
import com.whatsticker.app.ui.theme.WhatStickerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current
            val preferencesRepository = remember { PreferencesRepository(context) }
            val userSettings by preferencesRepository.userSettingsFlow.collectAsState(initial = UserSettings())

            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val currentRoute = currentDestination?.route

            // Launcher para autorización de Screen Capture (MediaProjection)
            // Launcher para permiso de superposición (Overlay)
            val overlayPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                if (Settings.canDrawOverlays(context)) {
                    val serviceIntent = Intent(context, FloatingCaptureService::class.java).apply {
                        action = FloatingCaptureService.ACTION_START
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Toast.makeText(context, "¡Botón flotante activado!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Se requiere permiso para mostrar sobre otras aplicaciones", Toast.LENGTH_LONG).show()
                }
            }

            // Launcher para permiso de notificaciones (Android 13+)
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                // Proceder con overlay check
                if (Settings.canDrawOverlays(context)) {
                    val serviceIntent = Intent(context, FloatingCaptureService::class.java).apply {
                        action = FloatingCaptureService.ACTION_START
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Toast.makeText(context, "¡Botón flotante activado!", Toast.LENGTH_SHORT).show()
                } else {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    overlayPermissionLauncher.launch(intent)
                }
            }

            val toggleFloatingService: () -> Unit = {
                if (FloatingCaptureService.isRunning.value) {
                    val stopIntent = Intent(context, FloatingCaptureService::class.java).apply {
                        action = FloatingCaptureService.ACTION_STOP
                    }
                    context.startService(stopIntent)
                    Toast.makeText(context, "Botón flotante detenido", Toast.LENGTH_SHORT).show()
                } else {
                    // Verificar permisos
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else if (!Settings.canDrawOverlays(context)) {
                        Toast.makeText(context, "Por favor autoriza 'Mostrar sobre otras apps'", Toast.LENGTH_LONG).show()
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        overlayPermissionLauncher.launch(intent)
                    } else {
                        val serviceIntent = Intent(context, FloatingCaptureService::class.java).apply {
                            action = FloatingCaptureService.ACTION_START
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                        Toast.makeText(context, "¡Botón flotante activado!", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            WhatStickerTheme(
                themeMode = userSettings.themeMode,
                colorAccent = userSettings.colorAccent
            ) {
                val showBottomBar = currentRoute in listOf(Screen.Captures.route, Screen.Packs.route)

                Scaffold(
                    topBar = {
                        if (showBottomBar) {
                            @OptIn(ExperimentalMaterial3Api::class)
                            TopAppBar(
                                title = {
                                    Text(
                                        text = "WhatSticker",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                },
                                actions = {
                                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Configuración"
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    },
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                val bottomScreens = listOf(Screen.Captures, Screen.Packs)
                                bottomScreens.forEach { screen ->
                                    val selected = currentRoute == screen.route
                                    NavigationBarItem(
                                        icon = {
                                            screen.icon?.let { Icon(it, contentDescription = screen.title) }
                                        },
                                        label = { Text(screen.title) },
                                        selected = selected,
                                        onClick = {
                                            if (currentRoute != screen.route) {
                                                navController.navigate(screen.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Captures.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Captures.route) {
                            CapturesScreen(
                                onToggleFloatingService = toggleFloatingService,
                                onNavigateToPack = { packId ->
                                    navController.navigate(Screen.PackDetail.createRoute(packId))
                                }
                            )
                        }

                        composable(Screen.Packs.route) {
                            PacksScreen(
                                onNavigateToPackDetail = { packId ->
                                    navController.navigate(Screen.PackDetail.createRoute(packId))
                                }
                            )
                        }

                        composable(
                            route = Screen.PackDetail.route,
                            arguments = listOf(navArgument("packId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val packId = backStackEntry.arguments?.getString("packId") ?: ""
                            PackDetailScreen(
                                packId = packId,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
