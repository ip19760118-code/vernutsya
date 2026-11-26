package com.vernutsya

import android.content.Context
import android.content.SharedPreferences
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class HeadingState {
    var deg by mutableFloatStateOf(Float.NaN)
}

/**
 * Две автоматические поправки, ручных настроек нет:
 * 1) магнитное склонение — считается по координатам (для каждой местности своё);
 * 2) остаточная поправка устройства — самообучается по курсу GPS при ходьбе.
 */
object CompassCorrection {
    @Volatile var declination: Float = 0f
    @Volatile var residual: Float = 0f
    @Volatile var learned: Int = 0

    private var prefs: SharedPreferences? = null
    private var lastLat = 0.0
    private var lastLon = 0.0
    private var lastAt = 0L
    private var sinceSave = 0

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("vernutsya_compass", Context.MODE_PRIVATE)
        residual = prefs?.getFloat("residual", 0f) ?: 0f
        learned = prefs?.getInt("learned", 0) ?: 0
    }

    /** Склонение для текущей местности. Пересчёт при смещении >~50 км или раз в сутки. */
    fun update(loc: Location) {
        val now = System.currentTimeMillis()
        if (lastAt != 0L && now - lastAt < 86_400_000L &&
            abs(loc.latitude - lastLat) < 0.5 && abs(loc.longitude - lastLon) < 0.5) return
        runCatching {
            val g = GeomagneticField(
                loc.latitude.toFloat(), loc.longitude.toFloat(),
                (if (loc.hasAltitude()) loc.altitude else 0.0).toFloat(), now
            )
            declination = g.declination
            lastLat = loc.latitude; lastLon = loc.longitude; lastAt = now
        }
    }

    /**
     * Самообучение: при уверенном движении курс GPS — это истинное направление.
     * Медленно подтягиваем остаточную поправку к разнице. Боковые удержания
     * телефона усредняются, устойчивое смещение сохраняется.
     */
    fun learn(loc: Location, headingTrue: Float) {
        if (!loc.hasSpeed() || loc.speed < 2.0f) return
        if (!loc.hasBearing()) return
        if (loc.hasAccuracy() && loc.accuracy > 15f) return
        var d = loc.bearing - headingTrue
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        if (abs(d) > 25f) return
        residual = (residual + 0.01f * (d - residual)).coerceIn(-30f, 30f)
        learned++
        sinceSave++
        if (sinceSave >= 100) {
            sinceSave = 0
            prefs?.edit()?.putFloat("residual", residual)?.putInt("learned", learned)?.apply()
        }
    }

    fun resetLearning() {
        residual = 0f; learned = 0; sinceSave = 0
        prefs?.edit()?.putFloat("residual", 0f)?.putInt("learned", 0)?.apply()
    }
}

@Composable
fun rememberHeading(): HeadingState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val holder = remember { HeadingState() }

    DisposableEffect(lifecycleOwner) {
        CompassCorrection.init(context)
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val rot = FloatArray(9)
        val ori = FloatArray(3)
        var sSin = 0f
        var sCos = 0f
        var started = false
        var registered = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, event.values)
                SensorManager.getOrientation(rot, ori)
                val az = ori[0]
                val sn = sin(az); val cs = cos(az)
                if (!started) { sSin = sn; sCos = cs; started = true }
                else {
                    val a = 0.35f
                    sSin = a * sn + (1 - a) * sSin
                    sCos = a * cs + (1 - a) * sCos
                }
                val magnetic = (Math.toDegrees(atan2(sSin.toDouble(), sCos.toDouble())).toFloat() + 360f) % 360f

                val loc = TrackStore.state.value.location
                if (loc != null) CompassCorrection.update(loc)

                val base = (magnetic + CompassCorrection.declination + 720f) % 360f
                if (loc != null) CompassCorrection.learn(loc, (base + CompassCorrection.residual + 720f) % 360f)

                holder.deg = (base + CompassCorrection.residual + 720f) % 360f
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }

        fun reg() {
            if (!registered && sensor != null) {
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                registered = true
            }
        }
        fun unreg() { if (registered) { sm.unregisterListener(listener); registered = false } }

        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> reg()
                Lifecycle.Event.ON_PAUSE -> unreg()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) reg()
        onDispose { unreg(); lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    return holder
}