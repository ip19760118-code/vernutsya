package com.vernutsya

import android.location.Location
import kotlin.math.max

/**
 * Ведение по маршруту (своему пройденному или присланному треку), а не по прямой.
 * Находит ближайшую вершину, выбирает направление обхода к цели,
 * даёт азимут на точку впереди и остаток пути вдоль маршрута.
 */
object TrackNavigator {

    data class Guidance(
        val heading: Float,       // азимут, куда идти сейчас
        val alongMeters: Float,   // остаток по маршруту
        val offMeters: Float,     // отклонение от маршрута
        val onRoute: Boolean,
        val arrived: Boolean,
        val directMeters: Float
    )

    private fun dist(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Float {
        val r = FloatArray(1)
        Location.distanceBetween(aLat, aLon, bLat, bLon, r)
        return r[0]
    }

    private fun bear(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Float {
        val r = FloatArray(2)
        Location.distanceBetween(aLat, aLon, bLat, bLon, r)
        return (r[1] + 360f) % 360f
    }

    fun compute(loc: Location, track: List<GeoPoint>, target: Waypoint?): Guidance? {
        if (track.size < 2 || target == null) return null
        val tLat = target.lat; val tLon = target.lon

        val dStart = dist(track.first().lat, track.first().lon, tLat, tLon)
        val dEnd = dist(track.last().lat, track.last().lon, tLat, tLon)
        // цель далеко от обоих концов маршрута — вести по нему бессмысленно
        if (minOf(dStart, dEnd) > 150f) return null

        val forward = dEnd <= dStart
        val step = if (forward) 1 else -1

        var idx = 0
        var nearest = Float.MAX_VALUE
        for (i in track.indices) {
            val dd = dist(loc.latitude, loc.longitude, track[i].lat, track[i].lon)
            if (dd < nearest) { nearest = dd; idx = i }
        }

        // точка «впереди» примерно в 15 м по маршруту — сглаживает повороты
        var lookIdx = idx
        var lookAcc = 0f
        var i = idx
        while (i + step in track.indices) {
            val n = i + step
            lookAcc += dist(track[i].lat, track[i].lon, track[n].lat, track[n].lon)
            i = n; lookIdx = n
            if (lookAcc >= 15f) break
        }

        var remain = 0f
        var j = idx
        while (j + step in track.indices) {
            val n = j + step
            remain += dist(track[j].lat, track[j].lon, track[n].lat, track[n].lon)
            j = n
        }

        val endPt = if (forward) track.last() else track.first()
        val tail = dist(endPt.lat, endPt.lon, tLat, tLon)
        val direct = dist(loc.latitude, loc.longitude, tLat, tLon)
        val along = nearest + remain + tail

        val acc = if (loc.hasAccuracy()) loc.accuracy else 15f
        val onRoute = nearest <= max(25f, acc * 2f)
        val arrived = direct < 20f || (tail < 20f && remain < 20f)

        // у самой цели ведём прямо на неё, иначе — по маршруту
        val heading = if (tail < 25f || direct < 25f)
            bear(loc.latitude, loc.longitude, tLat, tLon)
        else
            bear(loc.latitude, loc.longitude, track[lookIdx].lat, track[lookIdx].lon)

        return Guidance(heading, along, nearest, onRoute, arrived, direct)
    }
}