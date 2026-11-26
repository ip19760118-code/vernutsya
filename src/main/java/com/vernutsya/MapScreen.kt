package com.vernutsya

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline

@Composable
fun MapScreen(walk: SavedWalk?, sosPoint: GeoPoint?, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val state by TrackStore.state.collectAsStateWithLifecycle()
    val settings by AppSettings.state.collectAsStateWithLifecycle()
    val maps by OfflineMaps.maps.collectAsStateWithLifecycle()
    val active by OfflineMaps.active.collectAsStateWithLifecycle()
    val regions by OfflineMaps.regions.collectAsStateWithLifecycle()

    val rawTrack = walk?.track ?: state.track
    val drawTrack = remember(rawTrack) { simplifyTrack(rawTrack, 600) }
    val waypoints = walk?.waypoints ?: state.waypoints
    val current = if (walk == null) state.location else null
    val car = waypoints.firstOrNull { it.isCar }
    val isRegion = active?.startsWith("region:") == true
    val fitPoints = remember(drawTrack, waypoints, sosPoint) {
        drawTrack.map { OsmGeoPoint(it.lat, it.lon) } +
                waypoints.map { OsmGeoPoint(it.lat, it.lon) } +
                listOfNotNull(sosPoint?.let { OsmGeoPoint(it.lat, it.lon) })
    }

    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var mpp by remember { mutableStateOf(0.0) }
    val appliedKey = remember { arrayOfNulls<String>(1) }
    val overlaySig = remember { arrayOfNulls<String>(1) }
    val myOverlays = remember { mutableListOf<Overlay>() }

    var showPicker by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var dlBox by remember { mutableStateOf<BoundingBox?>(null) }
    var dlName by remember { mutableStateOf("") }
    var dlRunning by remember { mutableStateOf(false) }
    var dlProgress by remember { mutableStateOf<MapDownloader.Progress?>(null) }
    var dlJob by remember { mutableStateOf<Job?>(null) }
    var extraZoom by remember { mutableStateOf(2) }
    var renameReg by remember { mutableStateOf<Region?>(null) }

    val mapListener = remember {
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

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val file = withContext(Dispatchers.IO) { OfflineMaps.importMap(context, uri) }
            if (file != null) {
                OfflineMaps.setActive(file.name)
                Toast.makeText(context, "Карта загружена: ${file.name}", Toast.LENGTH_LONG).show()
            } else Toast.makeText(context, "Нужен файл .mbtiles", Toast.LENGTH_LONG).show()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> mapRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapRef?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); mapRef?.onDetach() }
    }

    LaunchedEffect(active, mapRef) {
        val r = OfflineMaps.regionByActive()
        if (r != null && sosPoint == null) {
            mapRef?.let {
                safeFit(it, listOf(OsmGeoPoint(r.latNorth, r.lonWest), OsmGeoPoint(r.latSouth, r.lonEast)), 40)
            }
        }
    }

    BackHandler(onBack = onBack)

    val sourceLabel = when {
        active == null -> "Онлайн OSM (нужен интернет)"
        isRegion -> OfflineMaps.regionByActive()?.name ?: "Скачанная область"
        else -> active ?: ""
    }

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { BackIcon() }
                    Text("Карта", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    SosButton()
                    IconButton(onClick = { showPicker = true }, modifier = Modifier.size(48.dp)) { LayersIcon() }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MapActionButton({ ShareIcon() }, "Импорт", Modifier.weight(1f)) { importLauncher.launch(arrayOf("*/*")) }
                    MapActionButton({ LayersIcon() }, "Карты", Modifier.weight(1f)) { showPicker = true }
                    MapActionButton({ DownloadIcon() }, "Скачать", Modifier.weight(1f), enabled = active == null || isRegion) {
                        dlBox = mapRef?.boundingBox; dlProgress = null; dlName = ""; showDownloadDialog = true
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            AndroidView(
                factory = { ctx ->
                    OfflineMaps.init(ctx)
                    MapView(ctx).apply {
                        setMultiTouchControls(true)
                        isHorizontalMapRepetitionEnabled = false
                        isVerticalMapRepetitionEnabled = false
                        applyZoomLimits(this)
                        setTileSource(TileSourceFactory.MAPNIK)
                        controller.setZoom(16.0)
                        val center = sosPoint?.let { OsmGeoPoint(it.lat, it.lon) }
                            ?: current?.let { OsmGeoPoint(it.latitude, it.longitude) }
                            ?: car?.let { OsmGeoPoint(it.lat, it.lon) }
                            ?: drawTrack.firstOrNull()?.let { OsmGeoPoint(it.lat, it.lon) }
                            ?: OsmGeoPoint(55.751244, 37.618423)
                        controller.setCenter(center)
                        addMapListener(mapListener)
                    }.also {
                        mapRef = it
                        mpp = metersPerPixel(it.mapCenter.latitude, it.zoomLevelDouble)
                    }
                },
                update = { map ->
                    val key = active ?: "@online"
                    if (appliedKey[0] != key) {
                        appliedKey[0] = key
                        val file = if (active != null && !isRegion) maps.firstOrNull { it.name == active } else null
                        if (file != null) {
                            runCatching { map.setTileProvider(OfflineMaps.tileProvider(context, file)); map.setUseDataConnection(false) }
                                .onFailure { map.setTileSource(TileSourceFactory.MAPNIK); map.setUseDataConnection(true) }
                        } else {
                            map.setTileSource(TileSourceFactory.MAPNIK)
                            map.setUseDataConnection(true)
                        }
                    }

                    val sig = buildString {
                        append(drawTrack.size).append('|').append(waypoints.size).append('|')
                        append(current?.latitude ?: 0.0).append('|').append(current?.longitude ?: 0.0).append('|')
                        append(sosPoint?.lat ?: 0.0).append('|').append(sosPoint?.lon ?: 0.0)
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
                            val m = Marker(map).apply {
                                position = OsmGeoPoint(wp.lat, wp.lon)
                                icon = mapDot(if (wp.isCar) AndroidColor.parseColor("#E53935") else AndroidColor.parseColor("#F57C00"), 34)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); title = wp.name
                            }
                            map.overlays.add(m); myOverlays.add(m)
                        }
                        current?.let {
                            val m = Marker(map).apply {
                                position = OsmGeoPoint(it.latitude, it.longitude)
                                icon = mapDot(AndroidColor.parseColor("#2E7D32"), 34)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); title = "Вы здесь"
                            }
                            map.overlays.add(m); myOverlays.add(m)
                        }
                        sosPoint?.let {
                            val m = Marker(map).apply {
                                position = OsmGeoPoint(it.lat, it.lon)
                                icon = mapDot(AndroidColor.parseColor("#D500F9"), 52)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); title = "SOS-сигнал"
                            }
                            map.overlays.add(m); myOverlays.add(m)
                        }
                        map.invalidate()
                    }
                },
                modifier = Modifier.fillMaxSize().clipToBounds()
            )

            MapDecorations(mpp)

            Surface(Modifier.align(Alignment.TopStart).padding(8.dp), shape = MaterialTheme.shapes.small,
                color = Color.White.copy(alpha = 0.92f), shadowElevation = 2.dp) {
                Text(sourceLabel, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MapActionButton({ CrosshairIcon() }, "", Modifier.weight(1f), enabled = current != null) {
                        current?.let { mapRef?.controller?.animateTo(OsmGeoPoint(it.latitude, it.longitude)) }
                    }
                    MapActionButton({
                        Canvas(Modifier.size(24.dp)) {
                            drawTargetBadge(settings.icon, Offset(size.width / 2f, size.height / 2f),
                                size.minDimension / 2f - 1.dp.toPx(), Color(0xFF2E7D32))
                        }
                    }, "", Modifier.weight(1f), enabled = car != null) {
                        car?.let { mapRef?.controller?.animateTo(OsmGeoPoint(it.lat, it.lon)) }
                    }
                    MapActionButton({ DiagonalArrowsIcon() }, "", Modifier.weight(1f), enabled = fitPoints.size >= 2) {
                        mapRef?.let { m -> safeFit(m, fitPoints, 60) }
                    }
                }
                Text("Линейка масштаба — слева внизу, стрелка севера — справа вверху.",
                    fontSize = 11.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Мои карты") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(Modifier.fillMaxWidth().clickable { OfflineMaps.setActive(null); showPicker = false },
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = active == null, onClick = { OfflineMaps.setActive(null); showPicker = false })
                        Text("Онлайн OSM (нужен интернет)")
                    }
                    if (regions.isNotEmpty()) {
                        Text("Скачанные области (офлайн)", fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        regions.forEach { r ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = active == "region:${r.id}",
                                    onClick = { OfflineMaps.setActive("region:${r.id}"); showPicker = false })
                                Column(Modifier.weight(1f)) {
                                    Text(r.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${r.tiles} тайлов · масштаб ${r.zMin}–${r.zMax}", fontSize = 11.sp, color = Color.Gray)
                                }
                                TextButton(onClick = { renameReg = r }) { EditIcon() }
                                TextButton(onClick = { OfflineMaps.deleteRegion(r.id) }) { Text("✕", color = Color(0xFFE53935)) }
                            }
                        }
                    }
                    if (maps.isNotEmpty()) {
                        Text("Файлы .mbtiles", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        maps.forEach { file ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = active == file.name, onClick = { OfflineMaps.setActive(file.name); showPicker = false })
                                Column(Modifier.weight(1f)) {
                                    Text(file.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${file.length() / 1024 / 1024} МБ", fontSize = 11.sp, color = Color.Gray)
                                }
                                TextButton(onClick = { OfflineMaps.delete(file) }) { Text("✕", color = Color(0xFFE53935)) }
                            }
                        }
                    }
                    if (regions.isEmpty() && maps.isEmpty())
                        Text("Пока нет сохранённых карт. Нажмите «Скачать» или «Импорт».", color = Color.Gray)
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Закрыть") } }
        )
    }

    renameReg?.let { r ->
        var nm by remember(r.id) { mutableStateOf(r.name) }
        AlertDialog(
            onDismissRequest = { renameReg = null },
            title = { Text("Название области") },
            text = { OutlinedTextField(value = nm, onValueChange = { nm = it }, label = { Text("Название") },
                singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { OfflineMaps.renameRegion(r.id, nm); renameReg = null }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { renameReg = null }) { Text("Отмена") } }
        )
    }

    if (showDownloadDialog) {
        val box = dlBox
        val zMin = (mapRef?.zoomLevel ?: 15).coerceIn(3, 16)
        val zMax = (zMin + extraZoom).coerceAtMost(18)
        val estimate = if (box != null) MapDownloader.countTiles(box, zMin, zMax) else 0
        AlertDialog(
            onDismissRequest = { if (!dlRunning) showDownloadDialog = false },
            title = { Text("Скачать карту для офлайн") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (dlRunning || dlProgress != null) {
                        val p = dlProgress
                        val frac = if (p != null && p.total > 0) p.done.toFloat() / p.total else 0f
                        LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                        Text("Обработано ${p?.done ?: 0} из ${p?.total ?: estimate} тайлов")
                        Text("В кэше: ${p?.cached ?: 0} · Запрошено: ${p?.queued ?: 0}", fontSize = 12.sp, color = Color.Gray)
                        if (!dlRunning && p != null) Text("Готово. Область сохранена в «Мои карты».",
                            fontSize = 12.sp, color = Color(0xFF2E7D32))
                    } else {
                        OutlinedTextField(value = dlName, onValueChange = { dlName = it },
                            label = { Text("Название области (напр. Лес за деревней)") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text("Скачается видимая область: текущий масштаб и ещё +$extraZoom уровней.")
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Детализация:")
                            IconButton(onClick = { extraZoom = (extraZoom - 1).coerceAtLeast(0) }) { Text("−") }
                            Text("+$extraZoom", fontWeight = FontWeight.Bold)
                            IconButton(onClick = { extraZoom = (extraZoom + 1).coerceAtMost(3) }) { Text("+") }
                        }
                        Text("Примерно тайлов: $estimate", fontWeight = FontWeight.Bold)
                        if (estimate > 2500) Text("Слишком большая область. Приблизьте карту.",
                            color = Color(0xFFE53935), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                if (dlRunning) TextButton(onClick = { dlJob?.cancel(); dlRunning = false }) { Text("Отменить") }
                else TextButton(enabled = box != null && estimate in 1..2500, onClick = {
                    val provider = mapRef?.tileProvider ?: return@TextButton
                    val b = box ?: return@TextButton
                    val nm = dlName
                    dlRunning = true
                    dlProgress = MapDownloader.Progress(0, estimate, 0, 0)
                    dlJob = scope.launch(Dispatchers.IO) {
                        val res = MapDownloader.download(provider, b, zMin, zMax) { p -> dlProgress = p }
                        val region = OfflineMaps.addRegion(b, zMin, zMax, res.cached + res.queued, nm)
                        withContext(Dispatchers.Main) {
                            dlRunning = false; dlProgress = res
                            OfflineMaps.setActive("region:${region.id}")
                            Toast.makeText(context, "Сохранено: ${region.name}", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Скачать") }
            },
            dismissButton = { TextButton(onClick = { if (!dlRunning) showDownloadDialog = false }) { Text("Закрыть") } }
        )
    }
}

@Composable
fun MapActionButton(icon: @Composable () -> Unit, label: String, modifier: Modifier = Modifier,
                    enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(48.dp), enabled = enabled,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            icon()
            if (label.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun mapDot(color: Int, sizePx: Int): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL; setColor(color); setSize(sizePx, sizePx); setStroke(4, AndroidColor.WHITE)
}