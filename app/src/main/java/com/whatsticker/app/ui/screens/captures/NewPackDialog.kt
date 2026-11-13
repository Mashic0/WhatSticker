package com.whatsticker.app.ui.screens.captures

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NewPackDialog(
    initialStickerCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (name: String, publisher: String) -> Unit
) {
    var packName by remember { mutableStateOf("") }
    var publisherName by remember { mutableStateOf("WhatSticker User") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Crear Nuevo Paquete", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (initialStickerCount > 0)
                        "Se asignarán las $initialStickerCount captura(s) seleccionada(s) a este nuevo paquete."
                    else
                        "Ingresa los datos del nuevo paquete para comenzar a añadirle stickers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = packName,
                    onValueChange = {
                        packName = it
                        if (errorMessage != null) errorMessage = null
                    },
                    label = { Text("Nombre del paquete *") },
                    placeholder = { Text("Ej: Memes Divertidos") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = publisherName,
                    onValueChange = { publisherName = it },
                    label = { Text("Autor / Creador *") },
                    placeholder = { Text("Tu nombre o apodo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (packName.isBlank()) {
                        errorMessage = "El nombre del paquete es obligatorio"
                    } else if (publisherName.isBlank()) {
                        errorMessage = "El nombre del creador es obligatorio"
                    } else {
                        onConfirm(packName.trim(), publisherName.trim())
                    }
                }
            ) {
                Text("Crear Paquete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
