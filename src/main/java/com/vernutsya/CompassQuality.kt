package com.vernutsya

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

object CompassQuality {
    private val _acc = MutableStateFlow<Int?>(null)
    val accuracy: StateFlow<Int?> = _acc.asStateFlow()
    fun set(a: Int) { _acc.value = a }
}

@Composable
fun rememberCompassAccuracy(): Int? {
    val context = LocalContext.current
    val acc by CompassQuality.accuracy.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {}
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) { CompassQuality.set(accuracy) }
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm.unregisterListener(listener) }
    }
    return acc
}

@Composable
fun CalibrationCard() {
    val context = LocalContext.current
    val acc = rememberCompassAccuracy()
    val state by TrackStore.state.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(3000); tick++ }
    }

    val (label, color) = when (acc) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "Высокая ✓" to Color(0xFF2E7D32)
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Средняя" to Color(0xFFF57C00)
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Низкая" to Color(0xFFE53935)
        SensorManager.SENSOR_STATUS_UNRELIABLE -> "Ненадёжная" to Color(0xFFE53935)
        else -> "Нет данных" to Color.Gray
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Компас", fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Точность: ", fontSize = 14.sp)
                Text(label, color = color, fontWeight = FontWeight.Bold)
            }
            Text(
                "Калибровка датчика: рисуйте телефоном «восьмёрку» в воздухе, поворачивая " +
                        "во все стороны, пока точность не станет «Высокая». Держите подальше " +
                        "от металла и магнитов.",
                fontSize = 13.sp, color = Color.Gray
            )

            HorizontalDivider()

            Text("Автопоправка направления", fontWeight = FontWeight.Bold)
            Text(
                "Приложение само учитывает магнитное склонение вашей местности и " +
                        "подстраивает остаточную погрешность датчика: пока вы идёте с хорошим GPS, " +
                        "курс сравнивается с показанием компаса. Ручная настройка не нужна.",
                fontSize = 12.sp, color = Color.Gray
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Склонение местности: ", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(
                    if (state.location != null) "${CompassCorrection.declination.roundToInt()}°" else "нет GPS",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Автоподстройка датчика: ", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(
                    "${CompassCorrection.residual.roundToInt()}°",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
            }
            Text(
                if (CompassCorrection.learned > 0) "Обучение шло ${CompassCorrection.learned} раз — точность растёт по мере ходьбы."
                else "Пройдитесь 100–200 метров с телефоном верхом вперёд — поправка уточнится сама.",
                fontSize = 11.sp, color = Color.Gray
            )
            OutlinedButton(
                onClick = {
                    CompassCorrection.resetLearning()
                    tick++
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Сбросить автоподстройку", fontSize = 13.sp) }
        }
    }
}