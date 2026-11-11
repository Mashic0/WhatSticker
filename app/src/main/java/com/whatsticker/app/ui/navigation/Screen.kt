package com.whatsticker.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    object Captures : Screen("captures", "Capturas", Icons.Default.Collections)
    object Packs : Screen("packs", "Mis Paquetes", Icons.Default.FolderSpecial)
    object Settings : Screen("settings", "Configuración")
    object PackDetail : Screen("pack_detail/{packId}", "Detalle del Paquete") {
        fun createRoute(packId: String) = "pack_detail/$packId"
    }
}
