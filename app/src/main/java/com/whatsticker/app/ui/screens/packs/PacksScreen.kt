package com.whatsticker.app.ui.screens.packs

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.whatsticker.app.data.local.WhatStickerDatabase
import com.whatsticker.app.data.model.PackWithStickers
import com.whatsticker.app.ui.theme.WhatsAppGreen
import com.whatsticker.app.util.WhatsAppStickerUtil
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacksScreen(
    onNavigateToPackDetail: (packId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember { WhatStickerDatabase.getInstance(context) }
    val packsWithStickers by db.stickerDao().getAllPacksWithStickers().collectAsState(initial = emptyList())

    Scaffold(
        modifier = modifier
    ) { paddingValues ->
        if (packsWithStickers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Aún no tienes paquetes de stickers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ve a la sección 'Capturas' y presiona el botón 'Nuevo Paquete' para crear tu primer conjunto de stickers para WhatsApp.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = paddingValues.calculateBottomPadding() + 80.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(packsWithStickers, key = { it.pack.id }) { packWithStickers ->
                    PackItemCard(
                        packWithStickers = packWithStickers,
                        onCardClick = { onNavigateToPackDetail(packWithStickers.pack.id) },
                        onAddToWhatsApp = {
                            val (success, message) = WhatsAppStickerUtil.addPackToWhatsApp(context, packWithStickers)
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PackItemCard(
    packWithStickers: PackWithStickers,
    onCardClick: () -> Unit,
    onAddToWhatsApp: () -> Unit
) {
    val pack = packWithStickers.pack
    val count = packWithStickers.stickerCount
    val isValid = packWithStickers.isValidForWhatsApp

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Tray Icon
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val trayFile = File(pack.trayImagePath)
                    if (trayFile.exists()) {
                        AsyncImage(
                            model = trayFile,
                            contentDescription = "Icono",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pack.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Por ${pack.publisher}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = "$count/30 stickers",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (isValid)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            ),
                            border = null,
                            modifier = Modifier.height(26.dp)
                        )

                        if (!isValid) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (count < 3) "Mínimo 3 req." else "Máx 30",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                IconButton(onClick = onCardClick) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Editar",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Vista previa de los primeros 4 stickers en fila
            if (packWithStickers.stickers.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    packWithStickers.stickers.take(5).forEach { sticker ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = File(sticker.filePath),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Botón de acción para añadir a WhatsApp
            Button(
                onClick = onAddToWhatsApp,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isValid) WhatsAppGreen else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isValid) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isValid) "Añadir a WhatsApp" else "Requiere mínimo 3 stickers",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
