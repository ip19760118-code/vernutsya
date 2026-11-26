package com.vernutsya

import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

private val SosRed = Color(0xFFD32F2F)

fun buildSosMessage(loc: Location?): String {
    if (loc == null) return "SOS! Мне нужна помощь. GPS-координаты пока недоступны."
    val lat = String.format(Locale.US, "%.6f", loc.latitude)
    val lon = String.format(Locale.US, "%.6f", loc.longitude)
    return "SOS! Мне нужна помощь. Координаты: $lat, $lon\n" +
            "Открыть в приложении Вернутся: vernutsya://sos?lat=$lat&lon=$lon\n" +
            "Карта: https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=16/$lat/$lon"
}

fun parseSosText(text: String): Pair<Double, Double>? {
    val patterns = listOf(
        Regex("""vernutsya://sos\?lat=(-?\d+(?:\.\d+)?)&lon=(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
        Regex("""mlat=(-?\d+(?:\.\d+)?)&mlon=(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
        Regex("""(-?\d{1,2}\.\d{3,})\s*[,;]\s*(-?\d{1,3}\.\d{3,})""")
    )
    for (p in patterns) {
        val m = p.find(text) ?: continue
        val a = m.groupValues[1].toDoubleOrNull() ?: continue
        val b = m.groupValues[2].toDoubleOrNull() ?: continue
        if (a in -90.0..90.0 && b in -180.0..180.0) return a to b
    }
    return null
}

fun acceptSosPoint(context: Context, lat: Double, lon: Double): Boolean {
    val st = TrackStore.state.value
    var me = st.location
    if (me == null) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        me = runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
            ?: runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
    }
    if (me == null) {
        Toast.makeText(context, "Нет координат вашего устройства — дождитесь GPS", Toast.LENGTH_LONG).show()
        return false
    }
    val sosLoc = Location("sos").apply {
        latitude = lat; longitude = lon; accuracy = 1f; time = System.currentTimeMillis()
    }
    val wasTracking = st.tracking
    if (!wasTracking) TrackStore.beginTracking(me.latitude, me.longitude)
    val wp = TrackStore.addWaypoint(sosLoc, "SOS-сигнал", "Координаты человека из SMS")
    TrackStore.setTarget(wp.id)
    if (!wasTracking) TrackerService.resume(context) else TrackerService.refresh(context)
    Toast.makeText(context, "Точка SOS добавлена. Цель — человек.", Toast.LENGTH_LONG).show()
    return true
}

/** Открывает системное SMS-приложение с готовым текстом. Разрешение SEND_SMS не требуется. */
fun sendSos(context: Context, phone: String, loc: Location?) {
    val msg = buildSosMessage(loc)
    val sent = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
                putExtra("sms_body", msg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }.isSuccess
    if (!sent) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, msg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }.onFailure {
            Toast.makeText(context, "Не удалось открыть приложение для сообщений", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun SosButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings by AppSettings.state.collectAsStateWithLifecycle()
    val state by TrackStore.state.collectAsStateWithLifecycle()
    OutlinedButton(
        onClick = {
            if (settings.sosPhone.isBlank())
                Toast.makeText(context, "Укажите номер SOS в настройках", Toast.LENGTH_LONG).show()
            else sendSos(context, settings.sosPhone, state.location)
        },
        modifier = modifier.height(40.dp),
        border = BorderStroke(2.dp, SosRed),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
    ) { Text("SOS", color = SosRed, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1) }
}