package com.whatsticker.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.decode.ImageDecoderDecoder
import coil.decode.GifDecoder
import com.whatsticker.app.util.inspectAnimatedWebp
import com.whatsticker.app.util.isAcceptableAnimatedWebp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private enum class FileCheck { Pending, Valid, Invalid }

/**
 * Preview of an animated sticker or capture inside an options dialog. Read-only: never
 * touches the file or the record. Plays the animation only on API 28+ with a valid file,
 * using a dedicated ImageLoader that lives (and is shut down) with this composable.
 */
@Composable
fun AnimatedStickerPreview(
    filePath: String,
    accessibilityDescription: String,
    onStopReady: (stop: (() -> Unit)?) -> Unit = {}
) {
    val context = LocalContext.current
    val check by produceState(FileCheck.Pending, filePath) {
        value = withContext(Dispatchers.IO) {
            try {
                if (isAcceptableAnimatedWebp(inspectAnimatedWebp(File(filePath).readBytes()))) {
                    FileCheck.Valid
                } else FileCheck.Invalid
            } catch (e: Exception) {
                FileCheck.Invalid
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .semantics { contentDescription = accessibilityDescription },
            contentAlignment = Alignment.Center
        ) {
            when {
                check == FileCheck.Invalid -> PreviewUnavailable()
                check == FileCheck.Valid && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                    val loader = remember {
                        ImageLoader.Builder(context)
                            .components {
                                add(ImageDecoderDecoder.Factory())
                                add(GifDecoder.Factory())
                            }
                            .build()
                    }
                    DisposableEffect(loader) {
                        // Expose a synchronous stop so the owner can release playback before closing
                        onStopReady { loader.shutdown() }
                        onDispose {
                            loader.shutdown()
                            onStopReady(null)
                        }
                    }
                    SubcomposeAsyncImage(
                        model = File(filePath),
                        imageLoader = loader,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        error = { PreviewUnavailable() }
                    )
                }
                check == FileCheck.Valid -> StillFrame(filePath)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "GIF animado",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun PreviewUnavailable() {
    Text(
        "Vista previa no disponible",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(12.dp)
    )
}

@Composable
private fun StillFrame(filePath: String) {
    // Pair of (decode finished, decoded bitmap); bitmap stays null until the IO pass completes.
    val result by produceState<Pair<Boolean, Bitmap?>>(false to null, filePath) {
        value = true to withContext(Dispatchers.IO) {
            try { BitmapFactory.decodeFile(filePath) } catch (e: Exception) { null }
        }
    }
    val (finished, bitmap) = result
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(12.dp)
        )
    } else if (finished) {
        PreviewUnavailable()
    }
}
