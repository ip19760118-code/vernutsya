package com.vernutsya

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.location.Location
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline

/**
 * Карта-подложка под треком. Одинаково работает для живого и сохранённого трека:
 * панорама пальцем, pinch-zoom двумя пальцами, кнопки «+», «−», «⤢».
 */
@Composable
fun TrackMapView(
    track: List<GeoPoint>,
    waypoints: List<Waypoint>,
    current: Location?,
    selectedId: Long?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val active by OfflineMaps.active.collectAsStateWithLifecycle()
    val maps by OfflineMaps.maps.collectAsStateWithLifecycle()

    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var mpp by remember { mutableStateOf(0.0) }

    val appliedKey = remember { arrayOfNulls<String>(1) }
    val overlaySig = remember { arrayOfNulls<String>(1) }
    val didFit = remember { booleanArrayOf(false) }
    val myOverlays = remember { mutableListOf<Overlay>() }
    val meMarker = remember { arrayOfNulls<Marker>(1) }

    val drawTrack = remember(track.size) { simplifyTrack(track, 400) }
    val fitPoints = remember(drawTrack, waypoints) {
        drawTrack.map { OsmGeoPoint(it.lat, it.lon) } + waypoints.map { OsmGeoPoint(it.lat, it.lon) }
    }

    val listener = remember {
        object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                mapRef?.let { mpp = metersPerPixel(it.mapCenter.latitude, it.zoomLevelDouble) }
                return false
            }
            override fun onZoom(event: ZoomEvent?): Boolean {
                mapRef?.let { mpp = metersPerPixel(it.mapCenter.latitude, it.zoomLevelDouble) }
                return false
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> mapRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapRef?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs); mapRef?.onDetach() }
    }

    Box(modifier.clipToBounds()) {
        AndroidView(
            factory = { ctx ->
                OfflineMaps.init(ctx)
                MapView(ctx).apply {
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(false)
                    isHorizontalMapRepetitionEnabled = false
                    isVerticalMapRepetitionEnabled = false
                    applyZoomLimits(this)
                    setTileSource(TileSourceFactory.MAPNIK)
                    controller.setZoom(17.0)
                    addMapListener(listener)
                }.also {
                    mapRef = it
                    mpp = metersPerPixel(it.mapCenter.latitude, it.zoomLevelDouble)
                }
            },
            update = { map ->
                val key = active ?: "@online"
                if (appliedKey[0] != key) {
                    appliedKey[0] = key
                    val isRegion = active?.startsWith("region:") == true
                    val file = if (active != null && !isRegion) maps.firstOrNull { it.name == active } else null
                    if (file != null) {
                        runCatching { map.setTileProvider(OfflineMaps.tileProvider(context, file)); map.setUseDataConnection(false) }
                            .onFailure { map.setTileSource(TileSourceFactory.MAPNIK); map.setUseDataConnection(true) }
                    } else {
                        map.setTileSource(TileSourceFactory.MAPNIK)
                        map.setUseDataConnection(true)
                    }
                }

                // Пересборка только при изменении точек/выбора — НЕ при каждом фиксе GPS
                val sig = buildString {
                    append(drawTrack.size).append('|').append(waypoints.size).append('|').append(selectedId)
                    waypoints.forEach { append('|').append(it.id).append(':').append(it.lat).append(':').append(it.lon) }
                }
                if (overlaySig[0] != sig) {
                    overlaySig[0] = sig
                    myOverlays.forEach { map.overlays.remove(it) }
                    myOverlays.clear()

                    if (drawTrack.size >= 2) {
                        val line = Polyline().apply {
                            setPoints(drawTrack.map { OsmGeoPoint(it.lat, it.lon) })
                            outlinePaint.color = AndroidColor.parseColor("#1976D2")
                            outlinePaint.strokeWidth = 9f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                        }
                        map.overlays.add(line); myOverlays.add(line)
                    }
                    waypoints.forEach { wp ->
                        val sel = wp.id == selectedId
                        val m = Marker(map).apply {
                            position = OsmGeoPoint(wp.lat, wp.lon)
                            icon = dot(if (sel) AndroidColor.parseColor("#FFC107")
                                     else if (wp.isCar) AndroidColor.parseColor("#E53935")
                                     else AndroidColor.parseColor("#F57C00"), if (sel) 46 else 32)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            title = wp.name
                        }
                        map.overlays.add(m); myOverlays.add(m)
                    }
                    map.invalidate()
                }

                // Маркер «я» создаётся один раз и просто двигается
                if (meMarker[0] == null) {
                    val m = Marker(map).apply {
                        icon = dot(AndroidColor.parseColor("#2E7D32"), 32)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "Вы здесь"
                    }
                    map.overlays.add(m)
                    meMarker[0] = m
                }
                meMarker[0]?.let { m ->
                    if (current != null) {
                        m.isEnabled = true
                        m.position = OsmGeoPoint(current.latitude, current.longitude)
                    } else m.isEnabled = false
                }

                if (!didFit[0] && fitPoints.isNotEmpty()) {
                    didFit[0] = true
                    safeFit(map, fitPoints, 50)
                }
                map.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        MapDecorations(mpp)

        Column(
            Modifier.align(Alignment.BottomEnd).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ZoomBtn("+") { mapRef?.let { zoomBy(it, 1.0) } }
            ZoomBtn("−") { mapRef?.let { zoomBy(it, -1.0) } }
            ZoomBtn("⤢") { mapRef?.let { safeFit(it, fitPoints, 50) } }
        }
    }
}

@Composable
private fun ZoomBtn(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = Color.White.copy(alpha = 0.94f),
        shadowElevation = 2.dp,
        modifier = Modifier.size(38.dp)
    ) {
        Box(Modifier.fillMaxSize().clickable { onClick() }, contentAlignment = Alignment.Center) {
            Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF263238))
        }
    }
}

private fun dot(color: Int, sizePx: Int): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL; setColor(color); setSize(sizePx, sizePx); setStroke(4, AndroidColor.WHITE)
}