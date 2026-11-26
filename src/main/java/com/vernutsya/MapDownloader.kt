package com.vernutsya

import android.graphics.drawable.Drawable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.MapTileIndex
import kotlin.coroutines.coroutineContext
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

object MapDownloader {

    data class Progress(
        val done: Int,
        val total: Int,
        val cached: Int,
        val queued: Int
    )

    private fun tileX(lon: Double, z: Int): Int {
        val n = 1 shl z
        return floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    }

    private fun tileY(lat: Double, z: Int): Int {
        val n = 1 shl z
        val rad = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
        return floor((1.0 - ln(tan(rad) + 1.0 / Math.cos(rad)) / Math.PI) / 2.0 * n)
            .toInt().coerceIn(0, n - 1)
    }

    fun countTiles(box: BoundingBox, zMin: Int, zMax: Int): Int {
        var total = 0
        for (z in zMin..zMax) {
            val x0 = tileX(box.lonWest, z); val x1 = tileX(box.lonEast, z)
            val y0 = tileY(box.latNorth, z); val y1 = tileY(box.latSouth, z)
            total += (x1 - x0 + 1) * (y1 - y0 + 1)
        }
        return total
    }

    suspend fun download(
        provider: MapTileProviderBase,
        box: BoundingBox,
        zMin: Int,
        zMax: Int,
        onProgress: (Progress) -> Unit
    ): Progress {
        val total = countTiles(box, zMin, zMax)
        var done = 0
        var cached = 0
        var queued = 0

        for (z in zMin..zMax) {
            val x0 = tileX(box.lonWest, z); val x1 = tileX(box.lonEast, z)
            val y0 = tileY(box.latNorth, z); val y1 = tileY(box.latSouth, z)
            for (x in x0..x1) {
                for (y in y0..y1) {
                    if (!coroutineContext.isActive) return Progress(done, total, cached, queued)
                    val d: Drawable? = try {
                        provider.getMapTile(MapTileIndex.getTileIndex(z, x, y))
                    } catch (e: Exception) { null }
                    if (d != null) cached++ else { queued++; delay(25) }
                    done++
                    if (done % 5 == 0 || done == total) {
                        onProgress(Progress(done, total, cached, queued))
                    }
                }
            }
        }
        delay(1200)
        onProgress(Progress(done, total, cached, queued))
        return Progress(done, total, cached, queued)
    }
}