package com.vernutsya

import android.location.Location
import kotlin.math.max

/**
 * Сглаживание позиции (фильтр Калмана) + детектор «стою / иду».
 * Убирает дрожание GPS на стоянке: ложные точки трека, лишние метры
 * и вращение стрелки возле цели.
 */
object LocationFilter {

    private var lat = 0.0
    private var lon = 0.0
    private var variance = -1f
    private var lastMs = 0L

    private var anchorLat = 0.0
    private var anchorLon = 0.0
    private var anchorMs = 0L

    private var movingState = false
    private var lastAcceptMs = 0L
    private var initialized = false

    data class Result(
        val smoothed: Location,
        val moving: Boolean,
        val accept: Boolean,
        val step: Float
    )

    fun reset() {
        initialized = false
        variance = -1f
        movingState = false
        lastAcceptMs = 0L
    }

    fun process(raw: Location, lastTrack: GeoPoint?, tracking: Boolean): Result {
        val acc = if (raw.hasAccuracy() && raw.accuracy > 1f) raw.accuracy else 15f
        val t = if (raw.time > 0) raw.time else System.currentTimeMillis()

        if (!initialized) {
            lat = raw.latitude; lon = raw.longitude
            variance = acc * acc
            anchorLat = lat; anchorLon = lon; anchorMs = t
            lastMs = t
            initialized = true
        } else {
            val dt = (t - lastMs).coerceAtLeast(0L) / 1000f
            if (dt > 0f) variance += dt * dt * 4f      // ожидаемое движение ~2 м/с
            val measVar = acc * acc
            val k = variance / (variance + measVar)
            lat += k * (raw.latitude - lat)
            lon += k * (raw.longitude - lon)
            variance *= (1f - k)
            lastMs = t
        }

        // --- стою или иду: сравниваем сглаженную позицию с якорем ---
        val dAnchor = dist(lat, lon, anchorLat, anchorLon)
        val stillRadius = max(10f, acc * 1.5f)
        val moveRadius = max(18f, acc * 2.5f)
        when {
            dAnchor > moveRadius -> {
                anchorLat = lat; anchorLon = lon; anchorMs = t
                movingState = true
            }
            dAnchor <= stillRadius && t - anchorMs > 8000L -> movingState = false
        }
        if (raw.hasSpeed() && raw.speed > 1.2f) movingState = true

        val sm = Location(raw).apply {
            latitude = lat; longitude = lon; time = t
        }

        // --- запись точки трека ---
        var accept = false
        var step = 0f
        if (tracking) {
            if (lastTrack == null) {
                accept = true
                lastAcceptMs = t
            } else if (movingState) {
                step = dist(lat, lon, lastTrack.lat, lastTrack.lon)
                val minStep = max(8f, acc)                       // не меньше точности
                val speedOk = !raw.hasSpeed() || raw.speed >= 0.4f
                if (step >= minStep && t - lastAcceptMs >= 3000L && speedOk) {
                    accept = true
                    lastAcceptMs = t
                }
            }
        }
        return Result(sm, movingState, accept, step)
    }

    private fun dist(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Float {
        val r = FloatArray(1)
        Location.distanceBetween(aLat, aLon, bLat, bLon, r)
        return r[0]
    }
}