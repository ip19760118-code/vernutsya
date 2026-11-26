package com.vernutsya

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.net.Uri
import android.util.Xml
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

const val RETURN_POINT_NAME = "Точка возврата"

data class GeoPoint(val lat: Double, val lon: Double, val time: Long = System.currentTimeMillis())

data class Waypoint(
    val id: Long, val name: String, val lat: Double, val lon: Double,
    val time: Long = System.currentTimeMillis(), val isCar: Boolean = false,
    val note: String = "", val photo: String = ""
)

data class TrackerState(
    val tracking: Boolean = false,
    val waypoints: List<Waypoint> = emptyList(),
    val targetId: Long? = null,
    val location: Location? = null,
    val moving: Boolean = false,
    val walkedMeters: Float = 0f,
    val startedAt: Long = 0L,
    val track: List<GeoPoint> = emptyList(),
    val guideTrack: List<GeoPoint> = emptyList(),
    val guideName: String = "",
    val lastFixAt: Long = 0L,
    val ecoMode: Boolean = false
) {
    val car: Waypoint? get() = waypoints.firstOrNull { it.isCar }
    val target: Waypoint? get() = waypoints.firstOrNull { it.id == targetId } ?: car
    val navTrack: List<GeoPoint> get() = if (guideTrack.size >= 2) guideTrack else track
}

data class SavedWalk(
    val file: File, val name: String, val startedAt: Long,
    val distanceMeters: Float, val durationMs: Long,
    val track: List<GeoPoint>, val waypoints: List<Waypoint>
)

object TrackStore {
    private const val PREFS = "vernutsya"
    private const val KEY_TRACKING = "tracking"
    private const val KEY_WALKED = "walked"
    private const val KEY_STARTED = "started"
    private const val KEY_TARGET = "target"
    private const val KEY_ECO = "eco"

    private val _state = MutableStateFlow(TrackerState())
    val state: StateFlow<TrackerState> = _state.asStateFlow()
    private val _walks = MutableStateFlow<List<SavedWalk>>(emptyList())
    val walks: StateFlow<List<SavedWalk>> = _walks.asStateFlow()

    private lateinit var prefs: SharedPreferences
    private lateinit var walksDir: File
    private lateinit var photosDir: File
    private var trackFile: File? = null
    private var waypointsFile: File? = null

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        val app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        trackFile = File(app.filesDir, "track.csv")
        waypointsFile = File(app.filesDir, "waypoints.json")
        walksDir = File(app.filesDir, "walks").apply { mkdirs() }
        photosDir = File(app.filesDir, "photos").apply { mkdirs() }
        _state.value = _state.value.copy(ecoMode = prefs.getBoolean(KEY_ECO, false))
        if (prefs.getBoolean(KEY_TRACKING, false)) {
            val wps = readWaypoints()
            if (wps.any { it.isCar }) {
                _state.value = _state.value.copy(
                    tracking = false, waypoints = wps,
                    targetId = prefs.getLong(KEY_TARGET, -1).takeIf { it >= 0 },
                    walkedMeters = prefs.getFloat(KEY_WALKED, 0f),
                    startedAt = prefs.getLong(KEY_STARTED, System.currentTimeMillis()),
                    track = readTrack()
                )
            }
        }
        refreshWalks()
    }

    /** Гарантированно уникальный id: больше и текущего времени, и всех существующих. */
    private fun nextId(): Long {
        val maxExisting = _state.value.waypoints.maxOfOrNull { it.id } ?: 0L
        return maxOf(maxExisting, System.currentTimeMillis()) + 1L
    }

    fun beginTracking(carLat: Double, carLon: Double) {
        trackFile?.writeText("")
        LocationFilter.reset()
        val now = System.currentTimeMillis()
        val car = Waypoint(nextId(), RETURN_POINT_NAME, carLat, carLon, now, true)
        writeWaypoints(listOf(car))
        prefs.edit().putBoolean(KEY_TRACKING, true).putFloat(KEY_WALKED, 0f)
            .putLong(KEY_STARTED, now).putLong(KEY_TARGET, car.id).apply()
        _state.value = _state.value.copy(
            tracking = true, waypoints = listOf(car), targetId = car.id,
            walkedMeters = 0f, startedAt = now, track = emptyList(),
            guideTrack = emptyList(), guideName = "", moving = false
        )
    }

    fun restore(): Boolean {
        if (!prefs.getBoolean(KEY_TRACKING, false)) return false
        val wps = readWaypoints()
        val car = wps.firstOrNull { it.isCar } ?: return false
        LocationFilter.reset()
        _state.value = _state.value.copy(
            tracking = true, waypoints = wps,
            targetId = prefs.getLong(KEY_TARGET, -1).takeIf { it >= 0 } ?: car.id,
            walkedMeters = prefs.getFloat(KEY_WALKED, 0f),
            startedAt = prefs.getLong(KEY_STARTED, System.currentTimeMillis()),
            track = readTrack()
        )
        return true
    }

    fun addWaypoint(loc: Location, name: String = "", note: String = "", photo: String = ""): Waypoint {
        val s = _state.value
        val n = s.waypoints.count { !it.isCar } + 1
        val wp = Waypoint(nextId(), name.ifBlank { "Точка $n" },
            loc.latitude, loc.longitude, note = note, photo = photo)
        val wps = s.waypoints + wp
        writeWaypoints(wps)
        _state.value = s.copy(waypoints = wps)
        return wp
    }

    /** Копирует файл фото, чтобы импорт не делил оригинал с исходным треком. */
    private fun copyPhotoForImport(path: String): String {
        if (path.isEmpty()) return ""
        return runCatching {
            val src = File(path)
            if (!src.exists()) return ""
            val dst = File(photosDir, "imp_${System.nanoTime()}.jpg")
            src.copyTo(dst, overwrite = false)
            dst.absolutePath
        }.getOrDefault("")
    }

    /** Импорт точек из сохранённого/присланного трека. Фото копируются, id уникальны. */
    fun importWaypoints(source: List<Waypoint>, prefix: String = ""): List<Waypoint> {
        val s = _state.value
        val fresh = source.filter { w ->
            s.waypoints.none { abs(it.lat - w.lat) < 0.000002 && abs(it.lon - w.lon) < 0.000002 }
        }
        if (fresh.isEmpty()) return emptyList()
        var id = nextId()
        val stamp = System.currentTimeMillis()
        val added = fresh.map { w ->
            val label = if (w.isCar) "Точка возврата (из трека)" else w.name
            Waypoint(
                id = id++,
                name = (prefix + label).take(60),
                lat = w.lat, lon = w.lon, time = stamp,
                isCar = false, note = w.note,
                photo = copyPhotoForImport(w.photo)
            )
        }
        val wps = s.waypoints + added
        writeWaypoints(wps)
        _state.value = s.copy(waypoints = wps)
        return added
    }

    /** Следование по чужому треку. Службу запускает вызывающая сторона. */
    fun followWalk(walk: SavedWalk, myLoc: Location?): Boolean {
        if (walk.track.size < 2) return false
        if (!_state.value.tracking) {
            if (myLoc == null) return false
            beginTracking(myLoc.latitude, myLoc.longitude)
        }
        val added = importWaypoints(walk.waypoints, "★ ")
        val pick = added.firstOrNull() ?: _state.value.waypoints.lastOrNull()
        if (pick != null) setTarget(pick.id)
        _state.value = _state.value.copy(guideTrack = walk.track, guideName = walk.name)
        return true
    }

    fun stopFollowing() {
        _state.value = _state.value.copy(guideTrack = emptyList(), guideName = "")
    }

    fun updateWaypoint(id: Long, name: String, note: String) {
        val s = _state.value
        val wps = s.waypoints.map { if (it.id == id) it.copy(name = name.ifBlank { it.name }, note = note) else it }
        writeWaypoints(wps)
        _state.value = s.copy(waypoints = wps)
    }

    fun moveReturnPoint(loc: Location) {
        val s = _state.value
        val wps = s.waypoints.map { if (it.isCar) it.copy(lat = loc.latitude, lon = loc.longitude) else it }
        writeWaypoints(wps)
        _state.value = s.copy(waypoints = wps)
    }

    private fun photoInUse(path: String, remaining: List<Waypoint>): Boolean {
        if (remaining.any { it.photo == path }) return true
        return _walks.value.any { w -> w.waypoints.any { it.photo == path } }
    }

    fun deleteWaypoint(id: Long) {
        val s = _state.value
        val wp = s.waypoints.firstOrNull { it.id == id } ?: return
        if (wp.isCar) return
        val wps = s.waypoints.filterNot { it.id == id }
        if (wp.photo.isNotEmpty() && !photoInUse(wp.photo, wps)) {
            runCatching { File(wp.photo).delete() }
        }
        val newTarget = if (s.targetId == id) s.car?.id else s.targetId
        writeWaypoints(wps)
        prefs.edit().putLong(KEY_TARGET, newTarget ?: -1L).apply()
        _state.value = s.copy(waypoints = wps, targetId = newTarget)
    }

    fun setTarget(id: Long) { prefs.edit().putLong(KEY_TARGET, id).apply(); _state.value = _state.value.copy(targetId = id) }

    fun setEco(eco: Boolean) { prefs.edit().putBoolean(KEY_ECO, eco).apply(); _state.value = _state.value.copy(ecoMode = eco) }

    fun onNewLocation(raw: Location) {
        val s = _state.value
        val f = LocationFilter.process(raw, s.track.lastOrNull(), s.tracking)
        var walked = s.walkedMeters
        var track = s.track
        if (s.tracking && f.accept && raw.accuracy <= 40f) {
            val p = GeoPoint(f.smoothed.latitude, f.smoothed.longitude, f.smoothed.time)
            track = track + p
            appendTrack(p)
            if (track.size > 1) {
                walked += f.step
                prefs.edit().putFloat(KEY_WALKED, walked).apply()
            }
        }
        _state.value = s.copy(
            location = f.smoothed, moving = f.moving,
            walkedMeters = walked, track = track, lastFixAt = raw.time
        )
    }

    fun saveCurrentWalk(): SavedWalk? {
        val s = _state.value
        if (s.track.size < 2) return null
        val name = "Прогулка " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date(s.startedAt))
        val file = File(walksDir, "walk_${s.startedAt}.gpx")
        return runCatching {
            writeGpx(file, name, s.track, s.waypoints); refreshWalks()
            _walks.value.firstOrNull { it.file.name == file.name }
        }.getOrNull()
    }

    fun refreshWalks() {
        _walks.value = walksDir.listFiles { f -> f.extension.equals("gpx", true) }
            ?.mapNotNull { readGpx(it) }?.sortedByDescending { it.startedAt } ?: emptyList()
    }

    fun deleteWalk(walk: SavedWalk) { walk.file.delete(); refreshWalks() }

    fun importGpx(context: Context, uri: Uri): SavedWalk? = runCatching {
        val file = File(walksDir, "walk_import_${System.currentTimeMillis()}.gpx")
        context.contentResolver.openInputStream(uri)?.use { i -> file.outputStream().use { i.copyTo(it) } } ?: return null
        val walk = readGpx(file)
        if (walk == null) { file.delete(); null } else { refreshWalks(); walk }
    }.getOrNull()

    fun clearAll() {
        val eco = prefs.getBoolean(KEY_ECO, false)
        prefs.edit().clear().putBoolean(KEY_ECO, eco).apply()
        trackFile?.writeText(""); waypointsFile?.writeText("[]")
        LocationFilter.reset()
        val s = _state.value
        _state.value = TrackerState(location = s.location, lastFixAt = s.lastFixAt, ecoMode = eco)
    }

    private fun appendTrack(p: GeoPoint) { runCatching { trackFile?.appendText("${p.lat},${p.lon},${p.time}\n") } }

    private fun readTrack(): List<GeoPoint> = runCatching {
        trackFile?.takeIf { it.exists() }?.readLines()?.mapNotNull { line ->
            val p = line.split(",")
            if (p.size >= 3) GeoPoint(p[0].toDouble(), p[1].toDouble(), p[2].toLong()) else null
        } ?: emptyList()
    }.getOrDefault(emptyList())

    private fun writeWaypoints(wps: List<Waypoint>) {
        runCatching {
            val arr = JSONArray()
            wps.forEach { w ->
                arr.put(JSONObject().apply {
                    put("id", w.id); put("name", w.name); put("lat", w.lat); put("lon", w.lon)
                    put("time", w.time); put("car", w.isCar); put("note", w.note); put("photo", w.photo)
                })
            }
            waypointsFile?.writeText(arr.toString())
        }
    }

    private fun readWaypoints(): List<Waypoint> = runCatching {
        val arr = JSONArray(waypointsFile?.takeIf { it.exists() }?.readText() ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val isCar = o.optBoolean("car", false)
            val raw = o.getString("name")
            Waypoint(o.getLong("id"), if (isCar && raw == "Машина") RETURN_POINT_NAME else raw,
                o.getDouble("lat"), o.getDouble("lon"), o.getLong("time"), isCar,
                o.optString("note", ""), o.optString("photo", ""))
        }
    }.getOrDefault(emptyList())

    private fun xmlEscape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun gpxTime(t: Long): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US); f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date(t))
    }

    fun writeGpx(file: File, name: String, track: List<GeoPoint>, waypoints: List<Waypoint>) {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Vernutsya\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        waypoints.forEach { w ->
            sb.append("  <wpt lat=\"${w.lat}\" lon=\"${w.lon}\">\n")
            sb.append("    <name>${xmlEscape(w.name)}</name>\n")
            if (w.note.isNotEmpty()) sb.append("    <desc>${xmlEscape(w.note)}</desc>\n")
            sb.append("    <type>${if (w.isCar) "car" else "spot"}</type>\n")
            if (w.photo.isNotEmpty()) sb.append("    <link href=\"${xmlEscape(w.photo)}\"></link>\n")
            sb.append("    <time>${gpxTime(w.time)}</time>\n  </wpt>\n")
        }
        sb.append("  <trk><name>${xmlEscape(name)}</name><trkseg>\n")
        track.forEach { p -> sb.append("    <trkpt lat=\"${p.lat}\" lon=\"${p.lon}\"><time>${gpxTime(p.time)}</time></trkpt>\n") }
        sb.append("  </trkseg></trk>\n</gpx>\n")
        file.writeText(sb.toString())
    }

    private fun parseGpxTime(s: String): Long {
        if (s.isBlank()) return 0
        for (p in arrayOf("yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")) {
            runCatching {
                val f = SimpleDateFormat(p, Locale.US); f.timeZone = TimeZone.getTimeZone("UTC")
                return f.parse(s)?.time ?: 0
            }
        }
        return 0
    }

    fun readGpx(file: File): SavedWalk? = runCatching {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        file.inputStream().use { input ->
            parser.setInput(input, null)
            val track = ArrayList<GeoPoint>(); val wps = ArrayList<Waypoint>()
            var walkName = ""; var inWpt = false; var inTrk = false
            var lat = 0.0; var lon = 0.0
            var wName = ""; var wType = ""; var wTime = 0L; var wDesc = ""; var wLink = ""; var pTime = 0L
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "wpt" -> {
                            inWpt = true
                            lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                            wName = ""; wType = ""; wTime = 0L; wDesc = ""; wLink = ""
                        }
                        "trk" -> inTrk = true
                        "trkpt" -> {
                            lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                            pTime = 0L
                        }
                        "name" -> { val t = parser.nextText(); if (inWpt) wName = t else if (inTrk && walkName.isEmpty()) walkName = t }
                        "desc" -> if (inWpt) wDesc = parser.nextText()
                        "type" -> if (inWpt) wType = parser.nextText()
                        "link" -> if (inWpt) wLink = parser.getAttributeValue(null, "href") ?: ""
                        "time" -> { val t = parseGpxTime(parser.nextText()); if (inWpt) wTime = t else pTime = t }
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "wpt" -> {
                            val isCar = wType.equals("car", true)
                            val nm = wName.ifEmpty { if (isCar) RETURN_POINT_NAME else "Точка ${wps.size + 1}" }
                            wps.add(Waypoint(if (wTime > 0) wTime else wps.size + 1L,
                                if (isCar && nm == "Машина") RETURN_POINT_NAME else nm,
                                lat, lon, wTime, isCar, wDesc, wLink))
                            inWpt = false
                        }
                        "trk" -> inTrk = false
                        "trkpt" -> track.add(GeoPoint(lat, lon, pTime))
                    }
                }
                event = parser.next()
            }
            if (track.isEmpty() && wps.isEmpty()) return null
            var dist = 0f
            for (i in 1 until track.size) {
                val a = track[i - 1]; val b = track[i]; val r = FloatArray(1)
                Location.distanceBetween(a.lat, a.lon, b.lat, b.lon, r); dist += r[0]
            }
            val start = track.firstOrNull()?.time?.takeIf { it > 0 } ?: wps.firstOrNull()?.time?.takeIf { it > 0 } ?: file.lastModified()
            val end = track.lastOrNull()?.time?.takeIf { it > 0 } ?: start
            SavedWalk(file, walkName.ifEmpty { file.nameWithoutExtension }, start, dist, (end - start).coerceAtLeast(0), track, wps)
        }
    }.getOrNull()
}