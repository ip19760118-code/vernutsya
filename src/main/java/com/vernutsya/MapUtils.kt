package com.vernutsya

import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView

const val MAP_MIN_ZOOM = 3.0
const val MAP_MAX_ZOOM = 20.0
const val MAP_FIT_MAX_ZOOM = 19.0

fun simplifyTrack(points: List<GeoPoint>, maxPoints: Int = 400): List<GeoPoint> {
    if (points.size <= maxPoints) return points
    val out = ArrayList<GeoPoint>(maxPoints + 2)
    val step = points.size.toDouble() / maxPoints
    var i = 0.0
    while (i < points.size) { out.add(points[i.toInt()]); i += step }
    val last = points.last()
    if (out.isEmpty() || out.last() != last) out.add(last)
    return out
}

fun applyZoomLimits(map: MapView) {
    runCatching {
        map.setMinZoomLevel(MAP_MIN_ZOOM)
        map.setMaxZoomLevel(MAP_MAX_ZOOM)
    }
}

fun safeFit(map: MapView, pts: List<OsmGeoPoint>, padding: Int = 60) {
    if (pts.isEmpty()) return
    map.post {
        runCatching {
            if (pts.size == 1) {
                map.controller.setCenter(pts[0])
                map.controller.setZoom(17.0)
                map.invalidate()
                return@runCatching
            }
            val box = BoundingBox.fromGeoPoints(pts)
            val spanLat = Math.abs(box.latNorth - box.latSouth)
            val spanLon = Math.abs(box.lonEast - box.lonWest)
            if (spanLat < 0.0008 && spanLon < 0.0008) {
                map.controller.setCenter(OsmGeoPoint((box.latNorth + box.latSouth) / 2.0, (box.lonEast + box.lonWest) / 2.0))
                map.controller.setZoom(17.0)
            } else {
                map.zoomToBoundingBox(box, false, padding)
                val z = map.zoomLevelDouble
                if (z > MAP_FIT_MAX_ZOOM) map.controller.setZoom(MAP_FIT_MAX_ZOOM)
                if (z < MAP_MIN_ZOOM) map.controller.setZoom(MAP_MIN_ZOOM)
            }
            map.invalidate()
        }
    }
}

fun zoomBy(map: MapView, delta: Double) {
    val z = (map.zoomLevelDouble + delta).coerceIn(MAP_MIN_ZOOM, MAP_MAX_ZOOM)
    runCatching { map.controller.setZoom(z) }
}