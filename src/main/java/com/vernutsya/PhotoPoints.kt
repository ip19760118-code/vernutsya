package com.vernutsya

import android.graphics.BitmapFactory
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun loadThumb(path: String, req: Int): ImageBitmap? = try {
    val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, b)
    var sample = 1
    while (b.outWidth / sample > req * 2 || b.outHeight / sample > req * 2) sample *= 2
    val o = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeFile(path, o)?.asImageBitmap()
} catch (_: Exception) { null }

/** Асинхронная загрузка миниатюры — не блокирует UI-поток. */
@Composable
fun rememberThumb(path: String, req: Int = 120): ImageBitmap? {
    var bmp by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        if (path.isNotEmpty()) bmp = withContext(Dispatchers.IO) { loadThumb(path, req) }
    }
    return bmp
}

@Composable
fun PhotoPointButton(loc: Location?) {
    val context = LocalContext.current
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && pendingFile != null) showDialog = true
        else { pendingFile?.delete(); pendingFile = null }
    }

    Button(
        onClick = {
            if (loc == null) { Toast.makeText(context, "Ждём GPS…", Toast.LENGTH_SHORT).show(); return@Button }
            val dir = File(context.filesDir, "photos").apply { mkdirs() }
            val f = File(dir, "photo_${System.currentTimeMillis()}.jpg")
            pendingFile = f
            launcher.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f))
        },
        enabled = loc != null,
        modifier = Modifier.fillMaxWidth()
    ) {
        CameraIcon(Modifier.size(18.dp), androidx.compose.ui.graphics.Color.White)
        Spacer(Modifier.width(8.dp))
        Text("Фото-точка")
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { pendingFile?.delete(); pendingFile = null; showDialog = false },
            title = { Text("Фото-точка") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Заметка (напр. белые под берёзой)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val l = loc
                    if (l != null && pendingFile != null) {
                        TrackStore.addWaypoint(l, name, note, pendingFile!!.absolutePath)
                        Toast.makeText(context, "Фото-точка добавлена", Toast.LENGTH_SHORT).show()
                    }
                    name = ""; note = ""; pendingFile = null; showDialog = false
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingFile?.delete(); pendingFile = null; showDialog = false }) { Text("Отмена") }
            }
        )
    }
}