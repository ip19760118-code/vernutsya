package com.vernutsya

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun PhotoViewerDialog(photo: String, title: String, note: String, onDismiss: () -> Unit) {
    val bmp = remember(photo) { loadThumb(photo, 1200) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xF2000000)).padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = onDismiss,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(38.dp)
                ) { Text("Закрыть", color = Color.White, fontSize = 13.sp) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Text("Фото не найдено", color = Color.White)
            }
            if (note.isNotEmpty()) {
                Surface(color = Color(0x33FFFFFF), shape = MaterialTheme.shapes.medium) {
                    Text(note, color = Color.White, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(12.dp))
                }
            }
        }
    }
}