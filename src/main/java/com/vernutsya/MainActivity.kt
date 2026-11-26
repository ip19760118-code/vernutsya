package com.vernutsya

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val Green = Color(0xFF2E7D32)
private val Red = Color(0xFFE53935)
private val Orange = Color(0xFFF57C00)

sealed class Screen {
    object Main : Screen()
    object Walks : Screen()
    object Settings : Screen()
    object Guide : Screen()
    data class View(val walk: SavedWalk) : Screen()
    data class Map(val walk: SavedWalk?, val sos: GeoPoint? = null) : Screen()
    data class Sos(val lat: Double, val lon: Double) : Screen()
}

private data class NavInfo(
    val heading: Float?,
    val meters: Float?,
    val along: Boolean,
    val arrived: Boolean,
    val off: Float?,
    val onRoute: Boolean
)

private fun buildNav(state: TrackerState, along: Boolean, target: Waypoint?): NavInfo {
    val loc = state.location
    if (loc == null || target == null) return NavInfo(null, null, false, false, null, true)
    if (along) {
        val g = TrackNavigator.compute(loc, state.navTrack, target)
        if (g != null) return NavInfo(g.heading, g.alongMeters, true, g.arrived, g.offMeters, g.onRoute)
    }
    val r = FloatArray(2)
    Location.distanceBetween(loc.latitude, loc.longitude, target.lat, target.lon, r)
    return NavInfo((r[1] + 360f) % 360f, r[0], false, r[0] < 20f, null, true)
}

class MainActivity : ComponentActivity() {
    private var incomingUri by mutableStateOf<Uri?>(null)
    private var incomingSos by mutableStateOf<Pair<Double, Double>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TrackStore.init(this); AppSettings.init(this); OfflineMaps.init(this); GuideStore.init(this)
        UpdateManager.init(this)
        Analytics.init(this)
        Analytics.log("app_open", mapOf("app_version" to UpdateManager.currentVersion(this)))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true; isAppearanceLightNavigationBars = true
        }
        handleIncoming(intent)
        setContent {
            val settings by AppSettings.state.collectAsStateWithLifecycle()
            val scheme = if (settings.nightMode) darkColorScheme(
                primary = Color(0xFFFF5252), background = Color(0xFF0A0000), surface = Color(0xFF1A0505),
                onPrimary = Color.Black, onBackground = Color(0xFFFF8A80), onSurface = Color(0xFFFF8A80)
            ) else lightColorScheme(primary = Green, background = Color(0xFFF1F8E9), surface = Color.White)
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                        AppRoot(incomingUri, incomingSos) { incomingUri = null; incomingSos = null }
                        if (settings.nightMode) Box(Modifier.fillMaxSize().background(Color(0x33FF0000)))
                        UpdateDialog()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIncoming(intent) }

    private fun handleIncoming(intent: Intent?) {
        val data = intent?.data
        if (intent?.action == Intent.ACTION_VIEW && data?.scheme == "vernutsya") {
            val lat = data.getQueryParameter("lat")?.toDoubleOrNull()
            val lon = data.getQueryParameter("lon")?.toDoubleOrNull()
            if (lat != null && lon != null) { incomingSos = lat to lon; return }
        }
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val p = intent.getStringExtra(Intent.EXTRA_TEXT)?.let { parseSosText(it) }
            if (p != null) { incomingSos = p; return }
        }
        val uri = when (intent?.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> data
            else -> null
        }
        if (uri != null) incomingUri = uri
    }
}

/**
 * Подпитка позицией. Работает, когда служба не пишет трек,
 * а также как страховка: если при активном треке фиксов нет 25 с —
 * включаем собственный слушатель, чтобы навигация не «слепла».
 */
@Composable
fun LocationFeeder() {
    val context = LocalContext.current
    val state by TrackStore.state.collectAsStateWithLifecycle()
    var stale by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            val s = TrackStore.state.value
            stale = s.tracking &&
                    (s.lastFixAt == 0L || System.currentTimeMillis() - s.lastFixAt > 25000L)
        }
    }

    val needOwn = !state.tracking || stale
    DisposableEffect(needOwn) {
        if (needOwn && hasLocPermission(context)) {
            val lm = context.getSystemService(LocationManager::class.java)
            val l = LocationListener { TrackStore.onNewLocation(it) }
            runCatching {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, l, Looper.getMainLooper())
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { TrackStore.onNewLocation(it) }
            }
            onDispose { lm.removeUpdates(l) }
        } else onDispose {}
    }
}

@Composable
fun AppRoot(incomingUri: Uri?, incomingSos: Pair<Double, Double>?, onHandled: () -> Unit) {
    val context = LocalContext.current
    val settings by AppSettings.state.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }

    LaunchedEffect(Unit) { if (settings.autoCheckUpdate) UpdateManager.check(context, false) }

    LaunchedEffect(screen) {
        val n = when (screen) {
            Screen.Main -> "main"; Screen.Walks -> "walks"; Screen.Settings -> "settings"
            Screen.Guide -> "guide"; is Screen.View -> "walk_view"
            is Screen.Map -> "map"; is Screen.Sos -> "sos"
        }
        Analytics.screen(n)
    }

    LaunchedEffect(incomingUri, incomingSos) {
        if (incomingSos != null) {
            Analytics.log("sos_received")
            screen = Screen.Sos(incomingSos.first, incomingSos.second); onHandled()
        } else if (incomingUri != null) {
            val walk = withContext(Dispatchers.IO) { TrackStore.importGpx(context, incomingUri) }
            if (walk != null) { Analytics.log("gpx_import"); screen = Screen.View(walk) }
            else Toast.makeText(context, "Не удалось прочитать GPX", Toast.LENGTH_LONG).show()
            onHandled()
        }
    }

    when (val s = screen) {
        Screen.Main -> MainScreen({ screen = Screen.Walks }, { screen = Screen.Map(null) },
            { screen = Screen.Settings }, { screen = Screen.Guide })
        Screen.Walks -> WalksScreen({ screen = Screen.Main }) { screen = Screen.View(it) }
        Screen.Settings -> SettingsScreen({ screen = Screen.Main }) { la, lo ->
            if (acceptSosPoint(context, la, lo)) screen = Screen.Main else screen = Screen.Sos(la, lo)
        }
        Screen.Guide -> GuideScreen { screen = Screen.Main }
        is Screen.View -> WalkViewScreen(s.walk, { screen = Screen.Walks }, { screen = Screen.Main })
        is Screen.Map -> MapScreen(s.walk, s.sos) { screen = if (s.walk != null) Screen.View(s.walk) else Screen.Main }
        is Screen.Sos -> SosScreen(s.lat, s.lon, { screen = Screen.Main },
            { screen = Screen.Map(null, GeoPoint(s.lat, s.lon)) },
            { if (acceptSosPoint(context, s.lat, s.lon)) screen = Screen.Main })
    }
}

@Composable
fun MainScreen(onOpenWalks: () -> Unit, onOpenMap: () -> Unit, onOpenSettings: () -> Unit, onOpenGuide: () -> Unit) {
    val context = LocalContext.current
    val state by TrackStore.state.collectAsStateWithLifecycle()
    val settings by AppSettings.state.collectAsStateWithLifecycle()
    LocationFeeder()

    var hasPermission by remember { mutableStateOf(hasLocPermission(context)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        hasPermission = res[Manifest.permission.ACCESS_FINE_LOCATION] == true || res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    LaunchedEffect(Unit) { if (!hasPermission) permLauncher.launch(requiredPermissions()) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    val window = (context as? Activity)?.window
    DisposableEffect(settings.keepScreenOn) {
        if (settings.keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    var showStopDialog by remember { mutableStateOf(false) }
    var showGpsDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var saveWalk by remember { mutableStateOf(true) }

    // Галочка «Сохранить маршрут» включается только если есть что сохранять:
    // записанный трек (>=2 точек) либо добавленные пользователем точки.
    val openStopDialog: () -> Unit = {
        val s = TrackStore.state.value
        saveWalk = s.track.size >= 2 || s.waypoints.any { !it.isCar }
        showStopDialog = true
    }

    val startTrack: () -> Unit = {
        val lm = context.getSystemService(LocationManager::class.java)
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) showGpsDialog = true
        else {
            val l = state.location
            if (l == null) Toast.makeText(context, "Ждём сигнал GPS…", Toast.LENGTH_SHORT).show()
            else { Analytics.log("track_started"); TrackerService.start(context, l.latitude, l.longitude) }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("🍄 Вернутся", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Green, modifier = Modifier.weight(1f))
            SosButton()
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp, 40.dp),
                border = BorderStroke(1.5.dp, Green), contentPadding = PaddingValues(0.dp)) {
                GearIcon(Modifier.size(22.dp), Green)
            }
        }
        if (!hasPermission) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Приложению нужен доступ к геолокации, чтобы отмечать точку возврата и писать трек.")
                    Button(onClick = { permLauncher.launch(requiredPermissions()) }) { Text("Разрешить геолокацию") }
                }
            }
            return@Column
        }
        if (settings.mode == UiMode.SIMPLE) {
            SimpleContent(state, settings, now,
                {
                    when {
                        state.tracking -> showMoveDialog = true
                        state.car != null -> TrackerService.resume(context)
                        else -> startTrack()
                    }
                }, openStopDialog)
        } else {
            AdvancedContent(state, settings, now, startTrack, openStopDialog,
                onOpenWalks, onOpenMap, onOpenGuide)
        }
    }

    if (showStopDialog) {
        val nothingToSave = state.track.size < 2 && state.waypoints.none { !it.isCar }
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("Завершить прогулку?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Пройдено: ${TrackerService.formatDistance(state.walkedMeters)}\nВремя: ${formatDuration(now - state.startedAt)}")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = saveWalk, onCheckedChange = { saveWalk = it })
                        Text("Сохранить маршрут")
                    }
                    if (nothingToSave) {
                        Text("Трек не записан и точек нет — сохранять нечего.",
                            fontSize = 12.sp, color = Color.Gray)
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                showStopDialog = false
                Analytics.log("track_finished", mapOf(
                    "meters" to state.walkedMeters.roundToInt(),
                    "minutes" to ((now - state.startedAt) / 60000L).toInt(),
                    "saved" to saveWalk))
                TrackerService.stop(context, saveWalk)
            }) { Text("Завершить", color = Red) } },
            dismissButton = { TextButton(onClick = { showStopDialog = false }) { Text("Отмена") } }
        )
    }
    if (showGpsDialog) AlertDialog(
        onDismissRequest = { showGpsDialog = false },
        title = { Text("Геолокация выключена") },
        text = { Text("Включите GPS в настройках телефона.") },
        confirmButton = { TextButton(onClick = { showGpsDialog = false; context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("Открыть настройки") } },
        dismissButton = { TextButton(onClick = { showGpsDialog = false }) { Text("Отмена") } }
    )
    if (showMoveDialog) AlertDialog(
        onDismissRequest = { showMoveDialog = false },
        title = { Text("Переставить точку возврата?") },
        text = { Text("Точка возврата будет перенесена в ваше текущее место.") },
        confirmButton = { TextButton(onClick = {
            showMoveDialog = false
            state.location?.let { l ->
                val car = state.car
                if (car != null) {
                    TrackStore.moveReturnPoint(l); TrackStore.setTarget(car.id)
                    if (state.tracking) TrackerService.refresh(context)
                }
            }
        }) { Text("Переставить", color = Red) } },
        dismissButton = { TextButton(onClick = { showMoveDialog = false }) { Text("Отмена") } }
    )
}

@Composable
fun NavModeCard(state: TrackerState, settings: SettingsState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Как вести к цели", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!settings.alongTrack) {
                    Button(onClick = {}, modifier = Modifier.weight(1f).height(46.dp),
                        contentPadding = PaddingValues(2.dp)) { Text("По прямой", fontSize = 13.sp, maxLines = 1) }
                } else {
                    OutlinedButton(onClick = { AppSettings.setAlongTrack(false) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        contentPadding = PaddingValues(2.dp)) { Text("По прямой", fontSize = 13.sp, maxLines = 1) }
                }
                if (settings.alongTrack) {
                    Button(onClick = {}, modifier = Modifier.weight(1f).height(46.dp),
                        contentPadding = PaddingValues(2.dp)) { Text("По треку", fontSize = 13.sp, maxLines = 1) }
                } else {
                    OutlinedButton(onClick = { AppSettings.setAlongTrack(true) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        contentPadding = PaddingValues(2.dp)) { Text("По треку", fontSize = 13.sp, maxLines = 1) }
                }
            }
            Text(
                if (settings.alongTrack) "Ведёт по пройденной тропе обратно — удобно в густом лесу, где напрямик не пройти."
                else "Ведёт кратчайшим путём по прямой.",
                fontSize = 12.sp, color = Color.Gray
            )
            if (state.guideTrack.size >= 2) {
                Text("Идёте по маршруту «${state.guideName}»", fontSize = 12.sp, color = Green, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { TrackStore.stopFollowing() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Перестать идти по чужому маршруту", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun SimpleContent(state: TrackerState, settings: SettingsState, now: Long,
                  onSetReturn: () -> Unit, onEnd: () -> Unit) {
    GpsStatus(state, now)
    val car = state.car
    val nav = buildNav(state, settings.alongTrack, car)
    val near = (nav.meters ?: 999f) < 25f

    if (nav.arrived) Text("🎉 Точка возврата уже рядом!", fontSize = 20.sp, fontWeight = FontWeight.Bold,
        color = Green, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

    NavArrow(state.location, car?.lat, car?.lon, nav.arrived, settings.icon,
        moving = state.moving, nearTarget = near, overrideHeading = nav.heading)

    Text(nav.meters?.let { TrackerService.formatDistance(it) } ?: "—",
        fontSize = 54.sp, fontWeight = FontWeight.Bold, color = Green)
    Text(if (nav.along) "по треку до точки возврата" else "по прямой до точки возврата",
        fontSize = 15.sp, textAlign = TextAlign.Center)
    if (!state.moving && state.tracking) Text("Стоите — трек не пишется, стрелка не дрожит",
        fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Пройдено", fontSize = 13.sp, color = Color.Gray)
            Text(TrackerService.formatDistance(state.walkedMeters), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
    DaylightCard(state, now)
    val label = when {
        state.tracking -> "📍 Переставить точку возврата"
        state.car != null -> "▶ Продолжить прогулку"
        else -> "📍 Отметить точку возврата"
    }
    Button(onClick = onSetReturn, enabled = state.location != null || state.car != null,
        modifier = Modifier.fillMaxWidth().height(60.dp)) { Text(label, fontSize = 17.sp) }
    OutlinedButton(onClick = onEnd, enabled = state.tracking || state.car != null,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
        modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("⏹ Окончание трека", fontSize = 16.sp) }
    Text("Экран можно заблокировать — запись продолжится, расстояние видно в уведомлении.",
        fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
}

@Composable
fun AdvancedContent(
    state: TrackerState, settings: SettingsState, now: Long,
    onStart: () -> Unit, onEnd: () -> Unit,
    onOpenWalks: () -> Unit, onOpenMap: () -> Unit, onOpenGuide: () -> Unit
) {
    val context = LocalContext.current
    val target = state.target; val loc = state.location
    val walks by TrackStore.walks.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var addName by remember { mutableStateOf("") }
    var addNote by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var showImport by remember { mutableStateOf(false) }

    GpsStatus(state, now)

    when {
        state.tracking && target != null -> {
            val nav = buildNav(state, settings.alongTrack, target)
            val near = (nav.meters ?: 999f) < 25f

            if (nav.arrived) Text(
                if (target.isCar) "🎉 Точка возврата уже рядом!" else "🎉 Вы почти у цели!",
                fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Green,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            NavArrow(loc, target.lat, target.lon, nav.arrived, settings.icon,
                moving = state.moving, nearTarget = near, overrideHeading = nav.heading)

            Text(nav.meters?.let { TrackerService.formatDistance(it) } ?: "—",
                fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Green)
            Text((if (nav.along) "по треку до «" else "по прямой до «") + target.name + "»",
                fontSize = 16.sp, textAlign = TextAlign.Center)
            if (nav.heading != null) Text("Азимут: ${nav.heading.roundToInt()}° (${cardinal(nav.heading)})", fontSize = 14.sp)
            if (nav.along && !nav.onRoute && nav.off != null) {
                Text("Вы в ${TrackerService.formatDistance(nav.off)} от маршрута — вернитесь к тропе",
                    fontSize = 13.sp, color = Orange, textAlign = TextAlign.Center)
            }
            if (!state.moving) Text("Стоите — трек не пишется", fontSize = 12.sp, color = Color.Gray)

            NavModeCard(state, settings)
            StatsWindow(state, now)
            DaylightCard(state, now)

            Button(onClick = {
                if (loc == null) Toast.makeText(context, "Ждём GPS…", Toast.LENGTH_SHORT).show()
                else { addName = ""; addNote = ""; showAddDialog = true }
            }, enabled = loc != null, modifier = Modifier.fillMaxWidth()) {
                PinIcon(Modifier.size(18.dp), Color.White); Spacer(Modifier.width(8.dp)); Text("Поставить точку здесь")
            }
            PhotoPointButton(loc)
            OutlinedButton(onClick = { showImport = true }, modifier = Modifier.fillMaxWidth()) {
                FolderIcon(Modifier.size(18.dp), Green); Spacer(Modifier.width(8.dp))
                Text("Добавить точки из другого трека")
            }

            WaypointsCard(state, loc, selectedId) { selectedId = it }

            if (state.track.isNotEmpty() || state.waypoints.isNotEmpty()) {
                TrackMapView(state.track, state.waypoints, loc, selectedId, Modifier.fillMaxWidth().height(260.dp))
                Text("Карта: двигайте пальцем, масштаб — двумя пальцами или «+» / «−». «⤢» — вписать трек.",
                    fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
            }
            OutlinedButton(onClick = onEnd, colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)) { Text("Завершить прогулку") }
        }
        !state.tracking && state.car != null -> {
            DaylightCard(state, now)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Прогулка была прервана", fontWeight = FontWeight.Bold)
                    Text("Пройдено: ${TrackerService.formatDistance(state.walkedMeters)}. Точка возврата сохранена.")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { TrackerService.resume(context) }) { Text("Продолжить") }
                        OutlinedButton(onClick = { TrackStore.clearAll() }) { Text("Сбросить") }
                    }
                }
            }
        }
        else -> {
            DaylightCard(state, now)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Как пользоваться:", fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp))
                    Text("1. Встаньте там, куда будете возвращаться, дождитесь GPS ±10–15 м.\n" +
                            "2. Нажмите «Отметить точку возврата».\n" +
                            "3. По пути ставьте точки и фото-точки.\n" +
                            "4. Можно вести по прямой или по пройденной тропе.\n" +
                            "5. Точки и маршруты друзей можно загрузить и идти по ним.\n" +
                            "6. Сохраняйте маршрут в GPX.")
                }
            }
            Button(onClick = onStart, enabled = state.location != null,
                modifier = Modifier.fillMaxWidth().height(56.dp)) {
                PinIcon(Modifier.size(18.dp), Color.White); Spacer(Modifier.width(8.dp))
                Text("Отметить точку возврата", fontSize = 17.sp)
            }
            OutlinedButton(onClick = { showImport = true }, modifier = Modifier.fillMaxWidth()) {
                FolderIcon(Modifier.size(18.dp), Green); Spacer(Modifier.width(8.dp))
                Text("Взять точки и начать прогулку")
            }
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NavButton({ FolderIcon() }, "Маршруты", Modifier.weight(1f), onOpenWalks)
        NavButton({ MapIcon() }, "Карта", Modifier.weight(1f), onOpenMap)
        NavButton({ BookIcon() }, "Грибы", Modifier.weight(1f), onOpenGuide)
    }

    if (showAddDialog) AlertDialog(
        onDismissRequest = { showAddDialog = false },
        title = { Text("Новая точка") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = addName, onValueChange = { addName = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = addNote, onValueChange = { addNote = it }, label = { Text("Заметка") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = {
            loc?.let { TrackStore.addWaypoint(it, addName, addNote); Analytics.log("waypoint_added") }
            showAddDialog = false
        }) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Отмена") } }
    )

    if (showImport) AlertDialog(
        onDismissRequest = { showImport = false },
        title = { Text("Точки из трека") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!state.tracking) {
                    Text("Прогулка ещё не начата — она запустится от вашего текущего места, " +
                            "а выбранная точка станет целью.",
                        fontSize = 12.sp, color = Color.Gray)
                }
                if (walks.isEmpty()) Text("Нет сохранённых маршрутов. Сначала сохраните прогулку или откройте GPX-файл в «Маршруты».", color = Color.Gray)
                walks.forEach { w ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(w.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("точек: ${w.waypoints.size} · ${TrackerService.formatDistance(w.distanceMeters)}",
                                fontSize = 12.sp, color = Color.Gray)
                            Button(onClick = {
                                val wasTracking = TrackStore.state.value.tracking
                                if (!wasTracking) {
                                    val l = TrackStore.state.value.location
                                    if (l == null) {
                                        Toast.makeText(context, "Нужен GPS — выйдите на открытое место", Toast.LENGTH_LONG).show()
                                        return@Button
                                    }
                                    TrackStore.beginTracking(l.latitude, l.longitude)
                                }
                                val added = TrackStore.importWaypoints(w.waypoints, "★ ")
                                added.firstOrNull()?.let { TrackStore.setTarget(it.id) }
                                if (!wasTracking) TrackerService.resume(context) else TrackerService.refresh(context)
                                Analytics.log("waypoints_imported")
                                Toast.makeText(context,
                                    if (added.isEmpty()) "Все точки уже есть в списке"
                                    else if (!wasTracking) "Прогулка начата, точек добавлено: ${added.size}"
                                    else "Добавлено точек: ${added.size}",
                                    Toast.LENGTH_LONG).show()
                                showImport = false
                            }, enabled = w.waypoints.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                                Text(if (state.tracking) "Добавить точки" else "Начать прогулку с этими точками", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showImport = false }) { Text("Закрыть") } }
    )
}

@Composable
fun NavButton(icon: @Composable () -> Unit, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(58.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            icon(); Spacer(Modifier.height(3.dp)); Text(label, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
fun SosScreen(lat: Double, lon: Double, onBack: () -> Unit, onOpenMap: () -> Unit, onAccept: () -> Unit) {
    val state by TrackStore.state.collectAsStateWithLifecycle()
    val settings by AppSettings.state.collectAsStateWithLifecycle()
    LocationFeeder()
    BackHandler(onBack = onBack)
    val loc = state.location
    var dist: Float? = null
    if (loc != null) {
        val r = FloatArray(1); Location.distanceBetween(loc.latitude, loc.longitude, lat, lon, r); dist = r[0]
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { BackIcon() }
            Text("SOS-сигнал", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F), modifier = Modifier.weight(1f))
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Координаты человека", fontWeight = FontWeight.Bold)
                Text(String.format(Locale.US, "%.6f, %.6f", lat, lon), fontSize = 15.sp)
                GpsStatus(state, System.currentTimeMillis())
                if (!state.tracking) Text("Трек запустится автоматически от вашего текущего места.",
                    fontSize = 12.sp, color = Color.Gray)
            }
        }
        NavArrow(loc, lat, lon, dist != null && dist < 20f, settings.icon,
            moving = state.moving, nearTarget = (dist ?: 999f) < 25f)
        Text(dist?.let { TrackerService.formatDistance(it) } ?: "—", fontSize = 52.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
        Text("до точки SOS", fontSize = 15.sp)
        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            PinIcon(Modifier.size(18.dp), Color.White); Spacer(Modifier.width(8.dp))
            Text("Открыть точку и идти", fontSize = 16.sp)
        }
        OutlinedButton(onClick = onOpenMap, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            MapIcon(Modifier.size(18.dp), Green); Spacer(Modifier.width(8.dp)); Text("Показать на карте")
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenSos: (Double, Double) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by AppSettings.state.collectAsStateWithLifecycle()
    val state by TrackStore.state.collectAsStateWithLifecycle()
    val checking by UpdateManager.checking.collectAsStateWithLifecycle()
    val updMessage by UpdateManager.message.collectAsStateWithLifecycle()
    val updAvailable by UpdateManager.available.collectAsStateWithLifecycle()
    var sosText by remember { mutableStateOf("") }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { BackIcon() }
            Text("Настройки", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Green, modifier = Modifier.weight(1f))
            SosButton()
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Режим экрана", fontWeight = FontWeight.Bold)
                Text("Простой — стрелка и две кнопки. Продвинутый — точки, карта, справочник.", fontSize = 13.sp, color = Color.Gray)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeButton("Простой", settings.mode == UiMode.SIMPLE, Modifier.weight(1f)) { AppSettings.setMode(UiMode.SIMPLE) }
                    ModeButton("Продвинутый", settings.mode == UiMode.ADVANCED, Modifier.weight(1f)) { AppSettings.setMode(UiMode.ADVANCED) }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Картинка цели на круге", fontWeight = FontWeight.Bold)
                IconPicker(settings.icon) { AppSettings.setIcon(it) }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Навигация, GPS и экран", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = settings.alongTrack, onCheckedChange = { AppSettings.setAlongTrack(it) })
                    Text("Вести по пройденному треку")
                }
                Text("Полезно в густом лесу: возврат той же тропой, а не напрямик.", fontSize = 11.sp, color = Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = state.ecoMode, onCheckedChange = { TrackStore.setEco(it); if (state.tracking) TrackerService.setEco(context, it) })
                    Text("Экономный режим GPS")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = settings.autoEco, onCheckedChange = { AppSettings.setAutoEco(it) })
                    Text("Авто-ЭКО при низком заряде")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = settings.keepScreenOn, onCheckedChange = { AppSettings.setKeepScreenOn(it) })
                    Text("Не гасить экран")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = settings.nightMode, onCheckedChange = { AppSettings.setNightMode(it) })
                    Text("Ночной режим (красный)")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Обновление приложения", fontWeight = FontWeight.Bold)
                Text("Текущая версия: ${UpdateManager.currentVersion(context)}", fontSize = 13.sp, color = Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = settings.autoCheckUpdate, onCheckedChange = { AppSettings.setAutoCheckUpdate(it) })
                    Text("Проверять автоматически при запуске")
                }
                Button(onClick = {
                    scope.launch { Analytics.log("update_check"); UpdateManager.check(context, true) }
                }, enabled = !checking, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    DownloadIcon(Modifier.size(18.dp), Color.White); Spacer(Modifier.width(8.dp))
                    Text(if (checking) "Проверяем…" else "Проверить обновления")
                }
                updMessage?.let { Text(it, fontSize = 13.sp, color = Color.Gray) }
                updAvailable?.let { i ->
                    Text("Доступна версия ${i.version}", color = Green, fontWeight = FontWeight.Bold)
                    Button(onClick = { Analytics.log("update_open_store"); UpdateManager.openStore(context) },
                        modifier = Modifier.fillMaxWidth()) { Text("Обновить через RuStore") }
                }
                Text("Обновления устанавливает RuStore.", fontSize = 11.sp, color = Color.Gray)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SOS-контакт", fontWeight = FontWeight.Bold)
                Text("Номер, на который уйдёт SMS с координатами. Отправку подтверждаете вы в приложении сообщений.",
                    fontSize = 13.sp, color = Color.Gray)
                OutlinedTextField(value = settings.sosPhone, onValueChange = { AppSettings.setSosPhone(it) },
                    label = { Text("Номер телефона") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Принять SOS-сигнал", fontWeight = FontWeight.Bold)
                Text("Вставьте текст SMS. Точка человека станет целью; если трек не запущен — он запустится от вашего места.",
                    fontSize = 13.sp, color = Color.Gray)
                OutlinedTextField(value = sosText, onValueChange = { sosText = it },
                    label = { Text("Текст SMS") }, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val t = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                        if (t != null) sosText = t else Toast.makeText(context, "Буфер пуст", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.weight(1f)) { Text("Вставить") }
                    val parsed = parseSosText(sosText)
                    Button(onClick = { parsed?.let { onOpenSos(it.first, it.second) } },
                        enabled = parsed != null, modifier = Modifier.weight(1f)) { Text("Открыть точку") }
                }
            }
        }

        CalibrationCard()
        ShareAppCard()
    }
}

@Composable
fun ModeButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick, modifier.height(48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text(label, fontSize = 14.sp, maxLines = 1) }
    else OutlinedButton(onClick, modifier.height(48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text(label, fontSize = 14.sp, maxLines = 1) }
}

@Composable
fun IconPicker(selected: TargetIcon, onSelect: (TargetIcon) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TargetIcon.values().toList().chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { ic ->
                    val sel = ic == selected
                    Card(Modifier.weight(1f).clickable { onSelect(ic) },
                        colors = CardDefaults.cardColors(containerColor = if (sel) Color(0xFFE8F5E9) else Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Canvas(Modifier.size(42.dp)) {
                                drawTargetBadge(ic, Offset(size.width / 2f, size.height / 2f),
                                    size.minDimension / 2f - 2.dp.toPx(), if (sel) Green else Color(0xFF607D8B))
                            }
                            Text(ic.label, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WaypointsCard(state: TrackerState, loc: Location?, selectedId: Long?, onSelect: (Long) -> Unit) {
    val context = LocalContext.current
    var editWp by remember { mutableStateOf<Waypoint?>(null) }
    var viewWp by remember { mutableStateOf<Waypoint?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Точки маршрута", fontWeight = FontWeight.Bold)
            Text("Нажмите строку — подсветится на карте. «Идти» — сделать целью.", fontSize = 11.sp, color = Color.Gray)
            state.waypoints.forEach { wp ->
                val isTarget = state.target?.id == wp.id
                val isSel = selectedId == wp.id
                val isSos = wp.name.startsWith("SOS")
                var d: Float? = null
                if (loc != null) { val r = FloatArray(1); Location.distanceBetween(loc.latitude, loc.longitude, wp.lat, wp.lon, r); d = r[0] }
                Row(Modifier.fillMaxWidth()
                    .background(if (isSel) Color(0xFFFFF3C4) else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onSelect(wp.id) }.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (wp.photo.isNotEmpty()) {
                        val bmp = rememberThumb(wp.photo, 120)
                        if (bmp != null) {
                            Image(bmp, null, Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).clickable { viewWp = wp })
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text((if (isSos) "🆘 " else if (wp.isCar) "🏠 " else "📍 ") + wp.name,
                            fontWeight = if (isTarget || isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSos) Color(0xFFD32F2F) else if (isTarget) Green else Color.Unspecified)
                        Text(d?.let { TrackerService.formatDistance(it) } ?: "—", fontSize = 12.sp, color = Color.Gray)
                        if (wp.note.isNotEmpty()) Text(wp.note, fontSize = 12.sp, color = Color.Gray)
                    }
                    if (isTarget) Text("цель ✓", color = Green, fontSize = 13.sp)
                    else TextButton(onClick = {
                        TrackStore.setTarget(wp.id); Analytics.log("target_changed")
                        if (state.tracking) TrackerService.refresh(context)
                    }) { Text("Идти") }
                    TextButton(onClick = { editWp = wp }, contentPadding = PaddingValues(4.dp)) { EditIcon() }
                    if (!wp.isCar) TextButton(onClick = { TrackStore.deleteWaypoint(wp.id) }, contentPadding = PaddingValues(4.dp)) { Text("✕", color = Red) }
                }
            }
        }
    }
    editWp?.let { w ->
        var en by remember(w.id) { mutableStateOf(w.name) }
        var enote by remember(w.id) { mutableStateOf(w.note) }
        AlertDialog(onDismissRequest = { editWp = null }, title = { Text("Изменить точку") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = en, onValueChange = { en = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = enote, onValueChange = { enote = it }, label = { Text("Заметка") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { TrackStore.updateWaypoint(w.id, en, enote); editWp = null }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { editWp = null }) { Text("Отмена") } })
    }
    viewWp?.let { w -> PhotoViewerDialog(w.photo, w.name, w.note) { viewWp = null } }
}

@Composable
fun WalksScreen(onBack: () -> Unit, onOpen: (SavedWalk) -> Unit) {
    val context = LocalContext.current
    val walks by TrackStore.walks.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<SavedWalk?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val w = TrackStore.importGpx(context, uri)
            if (w != null) { Analytics.log("gpx_import"); onOpen(w) }
            else Toast.makeText(context, "Не удалось прочитать GPX", Toast.LENGTH_LONG).show()
        }
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { BackIcon() }
            Text("Мои маршруты", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Green, modifier = Modifier.weight(1f))
            SosButton()
        }
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            ShareIcon(Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Открыть GPX-файл")
        }
        if (walks.isEmpty()) Text("Пока нет сохранённых маршрутов.", color = Color.Gray)
        walks.forEach { walk ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(walk.name, fontWeight = FontWeight.Bold)
                    Text("${TrackerService.formatDistance(walk.distanceMeters)} • ${formatDuration(walk.durationMs)} • точек: ${walk.waypoints.size}",
                        fontSize = 13.sp, color = Color.Gray)
                    Row {
                        TextButton(onClick = { onOpen(walk) }) { Text("Открыть") }
                        TextButton(onClick = { shareGpx(context, walk) }) { Text("Отправить") }
                        TextButton(onClick = { toDelete = walk }) { Text("Удалить", color = Red) }
                    }
                }
            }
        }
    }
    toDelete?.let { w ->
        AlertDialog(onDismissRequest = { toDelete = null }, title = { Text("Удалить маршрут?") }, text = { Text(w.name) },
            confirmButton = { TextButton(onClick = { TrackStore.deleteWalk(w); toDelete = null }) { Text("Удалить", color = Red) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } })
    }
}

@Composable
fun WalkViewScreen(walk: SavedWalk, onBack: () -> Unit, onGoMain: () -> Unit) {
    val context = LocalContext.current
    val state by TrackStore.state.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var viewWp by remember { mutableStateOf<Waypoint?>(null) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { BackIcon() }
            Text(walk.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Green, modifier = Modifier.weight(1f))
            SosButton()
        }
        Text(SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(walk.startedAt)), fontSize = 13.sp, color = Color.Gray)
        TrackMapView(walk.track, walk.waypoints, null, selectedId, Modifier.fillMaxWidth().height(300.dp))
        Text("Двигайте карту пальцем, масштаб — двумя пальцами или «+» / «−». «⤢» — вписать трек.",
            fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Длина", TrackerService.formatDistance(walk.distanceMeters), Modifier.weight(1f))
            StatCard("Время", formatDuration(walk.durationMs), Modifier.weight(1f))
            StatCard("Точек", walk.track.size.toString(), Modifier.weight(1f))
        }

        Button(onClick = {
            val wasTracking = state.tracking
            val ok = TrackStore.followWalk(walk, state.location)
            if (ok) {
                if (!wasTracking) TrackerService.resume(context) else TrackerService.refresh(context)
                AppSettings.setAlongTrack(true)
                Analytics.log("follow_walk")
                Toast.makeText(context, "Идёте по маршруту «${walk.name}»", Toast.LENGTH_LONG).show()
                onGoMain()
            } else {
                Toast.makeText(context, "Нужен GPS и минимум 2 точки трека", Toast.LENGTH_LONG).show()
            }
        }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            MapIcon(Modifier.size(18.dp), Color.White); Spacer(Modifier.width(8.dp))
            Text("Пройти по этому треку")
        }
        OutlinedButton(onClick = {
            val wasTracking = state.tracking
            if (!wasTracking) {
                val l = state.location
                if (l == null) {
                    Toast.makeText(context, "Нужен GPS — выйдите на открытое место", Toast.LENGTH_LONG).show()
                    return@OutlinedButton
                }
                TrackStore.beginTracking(l.latitude, l.longitude)
            }
            val added = TrackStore.importWaypoints(walk.waypoints, "★ ")
            added.firstOrNull()?.let { TrackStore.setTarget(it.id) }
            if (!wasTracking) TrackerService.resume(context) else TrackerService.refresh(context)
            Analytics.log("waypoints_imported")
            Toast.makeText(context,
                if (added.isEmpty()) "Все точки уже есть в списке"
                else if (!wasTracking) "Прогулка начата, точек добавлено: ${added.size}"
                else "Добавлено точек: ${added.size}",
                Toast.LENGTH_LONG).show()
            if (added.isNotEmpty()) onGoMain()
        }, enabled = walk.waypoints.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
            PinIcon(Modifier.size(18.dp), Green); Spacer(Modifier.width(8.dp))
            Text("Добавить точки в текущий трек")
        }

        if (walk.waypoints.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Точки маршрута", fontWeight = FontWeight.Bold)
                Text("Нажмите строку — подсветится на карте. Нажмите фото — откроется снимок.", fontSize = 11.sp, color = Color.Gray)
                walk.waypoints.forEach { wp ->
                    val isSel = selectedId == wp.id
                    Row(Modifier.fillMaxWidth()
                        .background(if (isSel) Color(0xFFFFF3C4) else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { selectedId = wp.id }.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (wp.photo.isNotEmpty()) {
                            val bmp = rememberThumb(wp.photo, 120)
                            if (bmp != null) {
                                Image(bmp, null, Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).clickable { viewWp = wp })
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(wp.name, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                            if (wp.note.isNotEmpty()) Text(wp.note, fontSize = 12.sp, color = Color.Gray)
                        }
                        if (wp.photo.isNotEmpty()) TextButton(onClick = { viewWp = wp }) { Text("Фото") }
                    }
                }
            }
        }
        Button(onClick = { shareGpx(context, walk) }, modifier = Modifier.fillMaxWidth()) {
            ShareIcon(Modifier.size(18.dp), Color.White); Spacer(Modifier.width(8.dp)); Text("Отправить GPX")
        }
    }
    viewWp?.let { w -> PhotoViewerDialog(w.photo, w.name, w.note) { viewWp = null } }
}

@Composable
fun GpsStatus(state: TrackerState, now: Long) {
    val loc = state.location; val age = (now - state.lastFixAt) / 1000
    val (text, color) = when {
        loc == null -> "GPS: поиск спутников…" to Orange
        age > 20 -> "GPS: нет сигнала ${age}с" to Red
        loc.accuracy <= 10 -> "GPS: точность ±${loc.accuracy.roundToInt()} м ✓" to Green
        loc.accuracy <= 25 -> "GPS: точность ±${loc.accuracy.roundToInt()} м" to Orange
        else -> "GPS: слабая точность ±${loc.accuracy.roundToInt()} м" to Red
    }
    Text(text, color = color, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
}

@Composable
fun StatsWindow(state: TrackerState, now: Long) {
    val speed = if (now > state.startedAt) state.walkedMeters / ((now - state.startedAt) / 1000f) * 3.6f else 0f
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Трек", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatItem("Пройдено", TrackerService.formatDistance(state.walkedMeters), Modifier.weight(1f))
                StatItem("Время", formatDuration(now - state.startedAt), Modifier.weight(1f))
                StatItem("Ср. скорость", String.format(Locale.US, "%.1f км/ч", speed), Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StatItem(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 12.sp, color = Color.Gray)
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

fun shareGpx(context: Context, walk: SavedWalk) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", walk.file)
        Analytics.log("gpx_share")
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"; putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, walk.name); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Отправить маршрут"))
    }.onFailure { Toast.makeText(context, "Не удалось открыть меню отправки", Toast.LENGTH_SHORT).show() }
}

fun formatDuration(ms: Long): String { val s = ms / 1000; return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60) }
fun cardinal(deg: Float): String { val d = listOf("С","СВ","В","ЮВ","Ю","ЮЗ","З","СЗ"); return d[(((deg + 22.5f) % 360f) / 45f).toInt()] }
fun hasLocPermission(c: Context) =
    ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()