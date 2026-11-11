package com.whatsticker.app.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whatsticker.app.data.local.ColorAccent
import com.whatsticker.app.data.local.PreferencesRepository
import com.whatsticker.app.data.local.ThemeMode
import com.whatsticker.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesRepository = remember { PreferencesRepository(context) }
    val userSettings by preferencesRepository.userSettingsFlow.collectAsState(
        initial = com.whatsticker.app.data.local.UserSettings()
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. MODO CLARO / OSCURO
            SettingsCard(
                title = "Modo Claro / Oscuro",
                icon = Icons.Default.Brightness4
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Selecciona el tema de la aplicación:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeOptionChip(
                            title = "Sistema",
                            isSelected = userSettings.themeMode == ThemeMode.SYSTEM,
                            onClick = {
                                scope.launch { preferencesRepository.setThemeMode(ThemeMode.SYSTEM) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionChip(
                            title = "Claro",
                            isSelected = userSettings.themeMode == ThemeMode.LIGHT,
                            onClick = {
                                scope.launch { preferencesRepository.setThemeMode(ThemeMode.LIGHT) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionChip(
                            title = "Oscuro",
                            isSelected = userSettings.themeMode == ThemeMode.DARK,
                            onClick = {
                                scope.launch { preferencesRepository.setThemeMode(ThemeMode.DARK) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 2. APARIENCIA / ÉNFASIS
            SettingsCard(
                title = "Apariencia y Énfasis",
                icon = Icons.Default.Palette
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Personaliza el color de acento de la interfaz:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    ColorAccent.entries.forEach { accent ->
                        val isSelected = userSettings.colorAccent == accent
                        val sampleColor = when (accent) {
                            ColorAccent.DYNAMIC -> MaterialTheme.colorScheme.primary
                            ColorAccent.WHATSAPP_GREEN -> WhatsAppGreen
                            ColorAccent.OCEAN_BLUE -> AccentOceanBlue
                            ColorAccent.DEEP_PURPLE -> AccentDeepPurple
                            ColorAccent.CORAL -> AccentCoral
                            ColorAccent.AMBER -> AccentAmber
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    scope.launch { preferencesRepository.setColorAccent(accent) }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(sampleColor)
                                    .border(2.dp, Color.White, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = accent.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    scope.launch { preferencesRepository.setColorAccent(accent) }
                                }
                            )
                        }
                    }
                }
            }

            // PRIVACIDAD (SIN REGISTRO / SIN CUENTAS)
            SettingsCard(
                title = "Privacidad y Datos",
                icon = Icons.Default.Security
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "100% Offline y sin cuentas",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "WhatSticker no requiere registro, no recopila correos electrónicos ni contraseñas. Todas tus capturas de pantalla y stickers se procesan y almacenan únicamente dentro de tu dispositivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 3. ACERCA DE LA APP
            SettingsCard(
                title = "Acerca de la App",
                icon = Icons.Default.Info
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "WhatSticker",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Versión 1.0.0 (Build 1)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "Creador de stickers para WhatsApp con capturador flotante en tiempo real.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "Cumple con las especificaciones técnicas de WhatsApp Stickers (WebP 512x512, peso < 100KB, ContentProvider integrado).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 4. DONACIONES PARA EL DESARROLLADOR
            SettingsCard(
                title = "Donaciones para el Desarrollador",
                icon = Icons.Default.Favorite
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Si esta aplicación te ha sido útil y deseas apoyar su desarrollo continuo e independiente, puedes hacer una donación voluntaria:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    DonationButton(
                        label = "Invítame un café (Ko-fi)",
                        icon = Icons.Default.LocalCafe,
                        onClick = {
                            openUrl(context, "https://ko-fi.com")
                        }
                    )

                    DonationButton(
                        label = "Donar por PayPal",
                        icon = Icons.Default.Payment,
                        onClick = {
                            openUrl(context, "https://paypal.me")
                        }
                    )

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Donación WhatSticker", "gracias_por_apoyar_whatsticker")
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "¡Información copiada al portapapeles!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copiar enlace de donación")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            content()
        }
    }
}

@Composable
fun ThemeOptionChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(title, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
        modifier = modifier
    )
}

@Composable
fun DonationButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No se pudo abrir el navegador", Toast.LENGTH_SHORT).show()
    }
}
