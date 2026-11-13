package com.whatsticker.app.ui.screens.captures

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.whatsticker.app.data.model.PackWithStickers

@Composable
fun AssignToPackDialog(
    packs: List<PackWithStickers>,
    onDismiss: () -> Unit,
    onSelectPack: (packId: String) -> Unit,
    onCreateNewPack: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Asignar a un Paquete", style = MaterialTheme.typography.titleLarge) },
        text = {
            if (packs.isEmpty()) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "No tienes paquetes creados todavía.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onCreateNewPack) {
                        Text("Crear Nuevo Paquete")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(packs) { packWithStickers ->
                        ListItem(
                            headlineContent = { Text(packWithStickers.pack.name) },
                            supportingContent = {
                                Text("${packWithStickers.stickers.size} stickers • Por ${packWithStickers.pack.publisher}")
                            },
                            leadingContent = {
                                Icon(Icons.Default.Folder, contentDescription = null)
                            },
                            modifier = Modifier
                                .clickable {
                                    onSelectPack(packWithStickers.pack.id)
                                }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            if (packs.isNotEmpty()) {
                TextButton(onClick = onCreateNewPack) {
                    Text("+ Crear Nuevo")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
