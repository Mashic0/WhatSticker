package com.whatsticker.app.ui.screens.packs

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.whatsticker.app.data.local.WhatStickerDatabase
import com.whatsticker.app.data.model.CapturedSticker
import com.whatsticker.app.ui.components.AnimatedStickerPreview
import com.whatsticker.app.ui.components.AnimatedStickerThumbnail
import com.whatsticker.app.ui.theme.WhatsAppGreen
import com.whatsticker.app.util.ImageUtils
import com.whatsticker.app.util.WhatsAppStickerUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackDetailScreen(
    packId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { WhatStickerDatabase.getInstance(context) }

    val packWithStickers by db.stickerDao().getPackWithStickers(packId).collectAsState(initial = null)
    val unassignedStickers by db.stickerDao().getUnassignedStickers().collectAsState(initial = emptyList())

    var showAddFromUnassignedDialog by remember { mutableStateOf(false) }
    var showDeletePackConfirm by remember { mutableStateOf(false) }
    var selectedStickerForAction by remember { mutableStateOf<CapturedSticker?>(null) }
    var previewStop by remember { mutableStateOf<(() -> Unit)?>(null) }
    var previewGone by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }

    // Single exit path of the sticker dialog: stops the decoder, then completes once the dialog content is disposed
    fun closeStickerDialog(): CompletableDeferred<Unit> {
        val gone = CompletableDeferred<Unit>()
        previewGone = gone
        previewStop?.invoke()
        previewStop = null
        selectedStickerForAction = null
        return gone
    }

    // Importar imagen directamente al paquete
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        val processedBytes = ImageUtils.processStickerBitmap(bitmap)
                        val savedFile = ImageUtils.saveStickerToFile(context, processedBytes, "pack_$packId")
                        val newSticker = CapturedSticker(
                            filePath = savedFile.absolutePath,
                            originalWidth = bitmap.width,
                            originalHeight = bitmap.height,
                            packId = packId
                        )
                        db.stickerDao().insertSticker(newSticker)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Sticker añadido al paquete", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(packWithStickers?.pack?.name ?: "Detalle del Paquete") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeletePackConfirm = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar Paquete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        val currentPack = packWithStickers
        if (currentPack == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val stickers = currentPack.stickers
            val isValid = currentPack.isValidForWhatsApp

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = paddingValues.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Header del paquete
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .padding(2.dp)
                                ) {
                                    val tray = File(currentPack.pack.trayImagePath)
                                    if (tray.exists()) {
                                        AsyncImage(
                                            model = tray,
                                            contentDescription = "Icono",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentPack.pack.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Creado por: ${currentPack.pack.publisher}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${stickers.size} de 30 stickers (mínimo 3 para WhatsApp)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Botón para transferir a WhatsApp
                            Button(
                                onClick = {
                                    val (success, message) = WhatsAppStickerUtil.addPackToWhatsApp(context, currentPack)
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isValid) WhatsAppGreen else MaterialTheme.colorScheme.outlineVariant,
                                    contentColor = if (isValid) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isValid) "Transferir a WhatsApp" else "Añade al menos ${3 - stickers.size} sticker(s) más",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { showAddFromUnassignedDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("De Capturas", style = MaterialTheme.typography.bodySmall)
                                }

                                OutlinedButton(
                                    onClick = { galleryLauncher.launch("image/*") },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("De Galería", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                // Grid de stickers
                if (stickers.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Este paquete aún no contiene stickers.\nUsa los botones superiores para añadir stickers.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(stickers, key = { it.id }) { sticker ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { selectedStickerForAction = sticker }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (sticker.isAnimated) {
                                    AnimatedStickerThumbnail(
                                        filePath = sticker.filePath,
                                        accessibilityDescription = "Sticker animado",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp)
                                    )
                                } else {
                                    AsyncImage(
                                        model = File(sticker.filePath),
                                        contentDescription = "Sticker",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { selectedStickerForAction = sticker },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(30.dp)
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "Opciones",
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal para añadir desde capturas sin asignar
    if (showAddFromUnassignedDialog) {
        AlertDialog(
            onDismissRequest = { showAddFromUnassignedDialog = false },
            title = { Text("Añadir desde Capturas") },
            text = {
                if (unassignedStickers.isEmpty()) {
                    Text("No hay capturas sin asignar disponibles. Usa el botón flotante o importa desde la galería.")
                } else {
                    Column(modifier = Modifier.heightIn(max = 350.dp)) {
                        Text(
                            "Selecciona una captura para incluirla en este paquete:",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(unassignedStickers) { sticker ->
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clickable {
                                            scope.launch(Dispatchers.IO) {
                                                db.stickerDao().assignStickerToPack(sticker.id, packId)
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Sticker añadido", Toast.LENGTH_SHORT).show()
                                                    showAddFromUnassignedDialog = false
                                                }
                                            }
                                        }
                                ) {
                                    if (sticker.isAnimated) {
                                        AnimatedStickerThumbnail(
                                            filePath = sticker.filePath,
                                            accessibilityDescription = "Sticker animado",
                                            modifier = Modifier.fillMaxSize().padding(4.dp)
                                        )
                                    } else {
                                        AsyncImage(
                                            model = File(sticker.filePath),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize().padding(4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddFromUnassignedDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // Modal de acción sobre un sticker del paquete
    selectedStickerForAction?.let { sticker ->
        AlertDialog(
            onDismissRequest = { closeStickerDialog() },
            title = { Text("Opciones del Sticker") },
            text = {
                DisposableEffect(Unit) {
                    onDispose { previewGone?.complete(Unit) }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (sticker.isAnimated) {
                        // The component draws its own 200 dp container plus the "GIF animado" badge
                        key(sticker.id) {
                            AnimatedStickerPreview(
                                filePath = sticker.filePath,
                                accessibilityDescription = "Sticker animado",
                                onStopReady = { previewStop = it }
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = File(sticker.filePath),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val gone = closeStickerDialog()
                        scope.launch(Dispatchers.IO) {
                            gone.await()
                            // Devolver a capturas sin asignar
                            db.stickerDao().unassignSticker(sticker.id)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Sticker devuelto a capturas sin asignar", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Mover a Sin Asignar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val gone = closeStickerDialog()
                        scope.launch(Dispatchers.IO) {
                            gone.await()
                            File(sticker.filePath).delete()
                            db.stickerDao().deleteSticker(sticker)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Sticker eliminado", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar definitivamente")
                }
            }
        )
    }

    // Confirmar eliminación de paquete
    if (showDeletePackConfirm) {
        AlertDialog(
            onDismissRequest = { showDeletePackConfirm = false },
            title = { Text("¿Eliminar este paquete?") },
            text = {
                Text("Los stickers asignados volverán a estar disponibles en la sección 'Capturas sin asignar'.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            // Desasignar stickers
                            packWithStickers?.stickers?.forEach {
                                db.stickerDao().unassignSticker(it.id)
                            }
                            // Eliminar tray icon y paquete
                            packWithStickers?.pack?.let { pack ->
                                File(pack.trayImagePath).delete()
                                db.stickerDao().deletePack(pack)
                            }
                            withContext(Dispatchers.Main) {
                                showDeletePackConfirm = false
                                Toast.makeText(context, "Paquete eliminado", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePackConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
