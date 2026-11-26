package com.vernutsya

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderArray
import org.osmdroid.tileprovider.modules.IArchiveFile
import org.osmdroid.tileprovider.modules.MapTileFileArchiveProvider
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.modules.MBTilesFileArchive
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.util.BoundingBox
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Region(
    val id: String, val name: String,
    val latNorth: Double, val lonEast: Double, val latSouth: Double, val lonWest: Double,
    val zMin: Int, val zMax: Int, val tiles: Int, val createdAt: Long
)

object OfflineMaps {
    private lateinit var prefs: SharedPreferences
    private lateinit var dir: File
    private var regionsFile: File? = null

    private val _maps = MutableStateFlow<List<File>>(emptyList())
    val maps: StateFlow<List<File>> = _maps.asStateFlow()
    private val _active = MutableStateFlow<String?>(null)
    val active: StateFlow<String?> = _active.asStateFlow()
    private val _regions = MutableStateFlow<List<Region>>(emptyList())
    val regions: StateFlow<List<Region>> = _regions.asStateFlow()

    fun init(context: Context) {
        if (::dir.isInitialized) return
        val app = context.applicationContext
        prefs = app.getSharedPreferences("vernutsya_maps", Context.MODE_PRIVATE)
        dir = File(app.filesDir, "maps").apply { mkdirs() }
        regionsFile = File(app.filesDir, "regions.json")
        val config = Configuration.getInstance()
        config.load(app, app.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        config.userAgentValue = app.packageName
        config.osmdroidBasePath = File(app.filesDir, "osmdroid").apply { mkdirs() }
        config.osmdroidTileCache = File(config.osmdroidBasePath, "tiles").apply { mkdirs() }
        _active.value = prefs.getString("active", null)
        _regions.value = readRegions()
        refresh()
    }

    fun refresh() {
        _maps.value = dir.listFiles { f -> f.isFile && f.name.lowercase().endsWith(".mbtiles") }?.sortedBy { it.name } ?: emptyList()
    }

    fun importMap(context: Context, uri: Uri): File? {
        return try {
            val name = displayName(context, uri) ?: "map_${System.currentTimeMillis()}.mbtiles"
            if (!name.lowercase().endsWith(".mbtiles")) return null
            val file = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { ins -> file.outputStream().use { ins.copyTo(it) } }
            refresh()
            file
        } catch (_: Exception) { null }
    }

    fun setActive(name: String?) { prefs.edit().putString("active", name).apply(); _active.value = name }

    fun delete(file: File) { file.delete(); if (_active.value == file.name) setActive(null); refresh() }

    fun addRegion(box: BoundingBox, zMin: Int, zMax: Int, tiles: Int, name: String = ""): Region {
        val now = System.currentTimeMillis()
        val r = Region(
            now.toString(),
            name.ifBlank { "Область " + SimpleDateFormat("dd.MM HH:mm", Locale.US).format(Date(now)) },
            box.latNorth, box.lonEast, box.latSouth, box.lonWest, zMin, zMax, tiles, now
        )
        _regions.value = _regions.value + r
        writeRegions(_regions.value)
        return r
    }

    fun renameRegion(id: String, name: String) {
        _regions.value = _regions.value.map { if (it.id == id) it.copy(name = name.ifBlank { it.name }) else it }
        writeRegions(_regions.value)
    }

    fun deleteRegion(id: String) {
        _regions.value = _regions.value.filterNot { it.id == id }
        writeRegions(_regions.value)
        if (_active.value == "region:$id") setActive(null)
    }

    fun regionByActive(): Region? {
        val a = _active.value ?: return null
        if (!a.startsWith("region:")) return null
        return _regions.value.firstOrNull { it.id == a.removePrefix("region:") }
    }

    private fun writeRegions(list: List<Region>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { r ->
                arr.put(JSONObject().apply {
                    put("id", r.id); put("name", r.name)
                    put("n", r.latNorth); put("e", r.lonEast); put("s", r.latSouth); put("w", r.lonWest)
                    put("zmin", r.zMin); put("zmax", r.zMax); put("tiles", r.tiles); put("created", r.createdAt)
                })
            }
            regionsFile?.writeText(arr.toString())
        }
    }

    private fun readRegions(): List<Region> = runCatching {
        val arr = JSONArray(regionsFile?.takeIf { it.exists() }?.readText() ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Region(o.getString("id"), o.getString("name"), o.getDouble("n"), o.getDouble("e"),
                o.getDouble("s"), o.getDouble("w"), o.getInt("zmin"), o.getInt("zmax"),
                o.getInt("tiles"), o.getLong("created"))
        }
    }.getOrDefault(emptyList())

    fun tileProvider(context: Context, file: File): MapTileProviderArray {
        val source: ITileSource = XYTileSource("VernutsyaOffline", 0, 20, 256, ".png", arrayOf<String>())
        val receiver = SimpleRegisterReceiver(context)
        val archive = MBTilesFileArchive.getDatabaseFileArchive(file)
        val provider = MapTileFileArchiveProvider(receiver, source, arrayOf<IArchiveFile>(archive))
        return MapTileProviderArray(source, receiver, arrayOf<MapTileModuleProviderBase>(provider))
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}