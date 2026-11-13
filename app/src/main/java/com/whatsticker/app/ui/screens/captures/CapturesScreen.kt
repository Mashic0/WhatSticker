package com.whatsticker.app.ui.screens.captures

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import com.whatsticker.app.data.model.CapturedSticker
import com.whatsticker.app.data.model.PackWithStickers
import com.whatsticker.app.data.model.StickerPack
import com.whatsticker.app.service.FloatingCaptureService
import com.whatsticker.app.util.ImageUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CapturesScreen(
    onToggleFloatingService: () -> Unit,
    onNavigateToPack: (packId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isFloatingRunning by FloatingCaptureService.isRunning.collectAsState()
    val scope = rememberCoroutineScope()
    val db = remember { WhatStickerDatabase.getInstance(context) }

    val unassignedStickers by db.stickerDao().getUnassignedStickers().collectAsState(initial = emptyList())
    val packs by db.stickerDao().getAllPacksWithStickers().collectAsState(initial = emptyList())

    var selectedStickerIds by remember { mutableStateOf(setOf<Long>()) }
    var showNewPackDialog by remember { mutableStateOf(false) }
    var showAssignDialog by remember { mutableStateOf(false) }
    var stickerToAssign by remember { mutableStateOf<CapturedSticker?>(null) }
    var previewSticker by remember { mutableStateOf<CapturedSticker?>(null) }
    var previewStop by remember { mutableStateOf<(() -> Unit)?>(null) }
    var previewGone by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }

    // Selector de imágenes/GIFs de la galería
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
                        val savedFile = ImageUtils.saveStickerToFile(context, processedBytes, "gallery")
                        val newSticker = CapturedSticker(
                            filePath = savedFile.absolutePath,
                            originalWidth = bitmap.width,
                            originalHeight = bitmap.height,
                            packId = null
                        )
                        db.stickerDao().insertSticker(newSticker)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Imagen importada con éxito", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error importando imagen: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewPackDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = {
                    Text(
                        if (selectedStickerIds.isNotEmpty())
                            "Crear Paquete (${selectedStickerIds.size})"
                        else
                            "Nuevo Paquete"
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = paddingValues.calculateTopPadding() + 8.dp,
                bottom = paddingValues.calculateBottomPadding() + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Header: Botón flotante y Acciones
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isFloatingRunning) Color(0xFF25D366)
                                            else MaterialTheme.colorScheme.outline
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isFloatingRunning)
                                        "Botón Flotante Activo"
                                    else
                                        "Botón Flotante Detenido",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            FilledTonalButton(onClick = onToggleFloatingService) {
                                Text(if (isFloatingRunning) "Detener" else "Activar")
                            }
                        }

                        Text(
                            text = "Pulsa el botón flotante en cualquier aplicación para detectar y recortar imágenes o GIFs de la pantalla.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Importar Imagen")
                            }

                            if (selectedStickerIds.isNotEmpty()) {
                                Button(
                                    onClick = { showAssignDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Asignar (${selectedStickerIds.size})")
                                }
                            }
                        }
                    }
                }
            }

            // Barra de estado de selección
            if (unassignedStickers.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Capturas sin asignar (${unassignedStickers.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (selectedStickerIds.isNotEmpty()) {
                            TextButton(onClick = { selectedStickerIds = emptySet() }) {
                                Text("Deseleccionar todo")
                            }
                        }
                    }
                }
            }

            // Grid de capturas
            if (unassignedStickers.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp, bottom = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Collections,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No hay capturas pendientes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Activa el botón flotante y pulsa sobre él en tus apps favoritas para capturar stickers al instante, o importa imágenes desde tu galería.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                val sections = groupUnassignedCaptures(unassignedStickers)
                sections.forEach { section ->
                item(
                    key = "header_${section.kind}",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    Text(
                        text = if (section.kind == CaptureSectionKind.ANIMATED)
                            "Capturas animadas" else "Capturas estáticas",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(section.captures, key = { it.id }) { sticker ->
                    val isSelected = selectedStickerIds.contains(sticker.id)

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .aspectRatio(1f)
                            .then(
                                if (isSelected)
                                    Modifier.border(
                                        3.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(12.dp)
                                    )
                                else Modifier
                            )
                            .combinedClickable(
                                onClick = {
                                    if (selectedStickerIds.isNotEmpty()) {
                                        selectedStickerIds = if (isSelected) {
                                            selectedStickerIds - sticker.id
                                        } else {
                                            selectedStickerIds + sticker.id
                                        }
                                    } else {
                                        previewSticker = sticker
                                    }
                                },
                                onLongClick = {
                                    selectedStickerIds = if (isSelected) {
                                        selectedStickerIds - sticker.id
                                    } else {
                                        selectedStickerIds + sticker.id
                                    }
                                }
                            )
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (sticker.isAnimated) {
                                AnimatedCaptureThumbnail(
                                    filePath = sticker.filePath,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                AsyncImage(
                                    model = File(sticker.filePath),
                                    contentDescription = "Captura",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                )
                            }

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                )
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Seleccionado",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(22.dp)
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }

    // Diálogo para crear nuevo paquete
    if (showNewPackDialog) {
        NewPackDialog(
            initialStickerCount = selectedStickerIds.size,
            onDismiss = { showNewPackDialog = false },
            onConfirm = { name, publisher ->
                scope.launch(Dispatchers.IO) {
                    val packId = "pack_${System.currentTimeMillis()}"

                    // Obtener o crear tray icon a partir del primer sticker
                    val firstStickerFile = if (selectedStickerIds.isNotEmpty()) {
                        unassignedStickers.firstOrNull { it.id in selectedStickerIds }?.filePath
                    } else {
                        unassignedStickers.firstOrNull()?.filePath
                    }

                    val trayFile = if (firstStickerFile != null) {
                        val bitmap = ImageUtils.loadBitmapFromFile(firstStickerFile)
                        if (bitmap != null) {
                            ImageUtils.createTrayIcon(context, bitmap, packId)
                        } else {
                            createDefaultTrayIcon(context, packId)
                        }
                    } else {
                        createDefaultTrayIcon(context, packId)
                    }

                    val newPack = StickerPack(
                        id = packId,
                        name = name,
                        publisher = publisher,
                        trayImagePath = trayFile.absolutePath
                    )
                    db.stickerDao().insertPack(newPack)

                    // Asignar los stickers seleccionados al nuevo paquete
                    selectedStickerIds.forEach { stickerId ->
                        db.stickerDao().assignStickerToPack(stickerId, packId)
                    }

                    selectedStickerIds = emptySet()
                    showNewPackDialog = false

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "¡Paquete '$name' creado!", Toast.LENGTH_SHORT).show()
                        onNavigateToPack(packId)
                    }
                }
            }
        )
    }

    // Diálogo para asignar a paquete existente
    if (showAssignDialog) {
        AssignToPackDialog(
            packs = packs,
            onDismiss = {
                showAssignDialog = false
                stickerToAssign = null
            },
            onSelectPack = { packId ->
                scope.launch(Dispatchers.IO) {
                    val targetIds = if (stickerToAssign != null) {
                        listOf(stickerToAssign!!.id)
                    } else {
                        selectedStickerIds.toList()
                    }

                    targetIds.forEach { id ->
                        db.stickerDao().assignStickerToPack(id, packId)
                    }

                    selectedStickerIds = emptySet()
                    stickerToAssign = null
                    showAssignDialog = false

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Stickers asignados al paquete", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onCreateNewPack = {
                showAssignDialog = false
                showNewPackDialog = true
            }
        )
    }

    // Modal de vista previa y acción individual
    // Single close path: stops playback synchronously, then removes the dialog from composition.
    // The returned deferred completes once the dialog has actually left composition.
    fun closePreview(): CompletableDeferred<Unit> {
        val gone = CompletableDeferred<Unit>()
        previewGone = gone
        previewStop?.invoke()
        previewStop = null
        previewSticker = null
        return gone
    }

    previewSticker?.let { sticker ->
        AlertDialog(
            onDismissRequest = { closePreview() },
            title = { Text("Opciones de Captura") },
            text = {
                DisposableEffect(Unit) {
                    onDispose { previewGone?.complete(Unit) }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (sticker.isAnimated) {
                        key(sticker.id) {
                            AnimatedCapturePreview(sticker.filePath) { previewStop = it }
                        }
                    } else Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = File(sticker.filePath),
                            contentDescription = "Detalle de Sticker",
                            modifier = Modifier.fillMaxSize().padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Capturado: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(sticker.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        closePreview()
                        stickerToAssign = sticker
                        showAssignDialog = true
                    }
                ) {
                    Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Asignar a Paquete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val gone = closePreview()
                        scope.launch {
                            // Wait until the preview left composition before touching the file
                            gone.await()
                            val message = withContext(Dispatchers.IO) {
                                val file = File(sticker.filePath)
                                val removed = try {
                                    !file.exists() || file.delete()
                                } catch (e: Exception) {
                                    false
                                }
                                if (removed) {
                                    db.stickerDao().deleteSticker(sticker)
                                    "Captura eliminada"
                                } else "No se pudo eliminar la captura"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Eliminar")
                }
            }
        )
    }
}

private fun createDefaultTrayIcon(context: Context, packId: String): File {
    val bitmap = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint().apply {
        color = 0xFF25D366.toInt()
        isAntiAlias = true
    }
    canvas.drawCircle(48f, 48f, 44f, paint)
    return ImageUtils.createTrayIcon(context, bitmap, packId)
}
