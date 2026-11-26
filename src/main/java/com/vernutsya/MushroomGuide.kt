package com.vernutsya

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GuideGreen = Color(0xFF2E7D32)
private val GuideRed = Color(0xFFE53935)
private val GuideOrange = Color(0xFFF57C00)
private val GuideGray = Color(0xFF607D8B)
private val GuideStar = Color(0xFFFFB300)

private fun catInfo(c: Int): Pair<String, Color> = when (c) {
    0 -> "Съедобный" to GuideGreen
    1 -> "Условно-съедобный" to GuideOrange
    2 -> "ЯДОВИТЫЙ" to GuideRed
    else -> "Несъедобный" to GuideGray
}

@Composable
fun GuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    GuideStore.init(context)

    val cached by GuideStore.cached.collectAsStateWithLifecycle()
    var showCatalog by remember { mutableStateOf(false) }
    var openSpec by remember { mutableStateOf<MushroomSpec?>(null) }
    var photoView by remember { mutableStateOf<Pair<String, String>?>(null) }

    BackHandler(enabled = openSpec != null) { openSpec = null }
    BackHandler(enabled = openSpec == null && showCatalog) { showCatalog = false }
    BackHandler(enabled = openSpec == null && !showCatalog, onBack = onBack)

    val spec = openSpec
    when {
        spec != null -> ArticleScreen(
            spec = spec,
            article = cached[spec.id],
            onBack = { openSpec = null },
            onDelete = { GuideStore.delete(context, spec.id); openSpec = null },
            onPhoto = { path -> photoView = path to spec.name }
        )
        showCatalog -> CatalogPickerScreen(onBack = { showCatalog = false })
        else -> DownloadedListScreen(
            onBack = onBack,
            onOpenCatalog = { showCatalog = true },
            onOpenSpec = { openSpec = it }
        )
    }

    photoView?.let { (path, title) ->
        PhotoViewerDialog(path, title, "") { photoView = null }
    }
}

/** Основной список — только загруженные грибы. Избранные (★) всегда сверху. */
@Composable
private fun DownloadedListScreen(
    onBack: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenSpec: (MushroomSpec) -> Unit
) {
    val context = LocalContext.current
    val cached by GuideStore.cached.collectAsStateWithLifecycle()
    val favorites by GuideStore.favorites.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(-1) }
    var onlyFav by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<MushroomSpec?>(null) }

    val downloaded = GuideStore.catalog.filter { cached.containsKey(it.id) }
    val favCount = downloaded.count { favorites.contains(it.id) }
    val list = downloaded
        .filter {
            (filter < 0 || it.category == filter) &&
                    (!onlyFav || favorites.contains(it.id)) &&
                    (query.isBlank() || it.name.contains(query, true) || it.latin.contains(query, true))
        }
        .sortedWith(
            compareByDescending<MushroomSpec> { favorites.contains(it.id) }.thenBy { it.name }
        )

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { BackIcon() }
            Text("Справочник грибов", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = GuideGreen, modifier = Modifier.weight(1f))
            SosButton()
        }

        Button(onClick = onOpenCatalog, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            DownloadIcon(Modifier.size(18.dp), Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Загрузить грибы", fontSize = 16.sp)
        }

        if (downloaded.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Canvas(Modifier.size(96.dp)) { drawPlaceholder(GuideGreen) }
                    Text("Справочник пока пуст", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        "Нажмите «Загрузить грибы» — откроется каталог видов. " +
                                "Отметьте нужные и загрузите: фото и полное описание сохранятся в телефоне " +
                                "и будут доступны без интернета.",
                        fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center
                    )
                }
            }
            Text("Источник: Википедия / Wikimedia Commons, лицензия CC BY-SA 4.0.",
                fontSize = 11.sp, color = Color.Gray)
            return@Column
        }

        Text("Загружено: ${downloaded.size} из ${GuideStore.catalog.size} · избранных: $favCount. Доступно офлайн.",
            fontSize = 12.sp, color = Color.Gray)

        OutlinedTextField(value = query, onValueChange = { query = it },
            label = { Text("Поиск по загруженным") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            FilterChipSmall("Все", filter == -1, Modifier.weight(1f)) { filter = -1 }
            FilterChipSmall("Съед.", filter == 0, Modifier.weight(1f)) { filter = 0 }
            FilterChipSmall("Усл.", filter == 1, Modifier.weight(1f)) { filter = 1 }
            FilterChipSmall("Ядов.", filter == 2, Modifier.weight(1f)) { filter = 2 }
            FilterChipSmall("★", onlyFav, Modifier.weight(0.8f)) { onlyFav = !onlyFav }
        }

        Text("★ — отметить избранное (такие грибы всегда сверху). ✕ — удалить из памяти телефона.",
            fontSize = 11.sp, color = Color.Gray)
        Text("⚠️ Справочник ознакомительный. Не ешьте гриб, если не уверены на 100%.",
            fontSize = 12.sp, color = GuideRed)

        if (list.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text("Ничего не найдено. Измените поиск или фильтры.",
                    Modifier.padding(16.dp), fontSize = 13.sp, color = Color.Gray)
            }
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { m ->
                val art = cached[m.id]
                val isFav = favorites.contains(m.id)
                val (badge, bc) = catInfo(m.category)
                Card(
                    Modifier.fillMaxWidth().clickable { onOpenSpec(m) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isFav) Color(0xFFFFF8E1) else Color.White
                    )
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val bmp = rememberThumb(art?.image ?: "", 160)
                        if (bmp != null) {
                            Image(bmp, null, Modifier.size(62.dp).clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop)
                        } else {
                            Canvas(Modifier.size(62.dp)) { drawPlaceholder(bc) }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(m.latin, fontSize = 11.sp, color = Color.Gray)
                            Text(badge, color = bc, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { GuideStore.toggleFavorite(m.id) },
                            modifier = Modifier.size(38.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(if (isFav) "★" else "☆", fontSize = 24.sp,
                                color = if (isFav) GuideStar else GuideGray)
                        }
                        TextButton(
                            onClick = { toDelete = m },
                            modifier = Modifier.size(34.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("✕", fontSize = 16.sp, color = GuideRed) }
                    }
                }
            }
            item {
                Text("Источник описаний и фото: Википедия / Wikimedia Commons, CC BY-SA 4.0. " +
                        "Категории съедобности и предупреждения о двойниках заданы в приложении.",
                    fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
            }
        }
    }

    toDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить из справочника?") },
            text = {
                Text("«${m.name}» будет удалён из основного списка: фото и описание сотрутся из памяти телефона. " +
                        "Вид останется в каталоге — его можно загрузить снова.")
            },
            confirmButton = {
                TextButton(onClick = {
                    GuideStore.delete(context, m.id)
                    toDelete = null
                }) { Text("Удалить", color = GuideRed) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } }
        )
    }
}

/** Предварительный каталог: выбор видов для загрузки. */
@Composable
private fun CatalogPickerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cached by GuideStore.cached.collectAsStateWithLifecycle()
    val busy by GuideStore.busy.collectAsStateWithLifecycle()
    val status by GuideStore.status.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(-1) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    val list = GuideStore.catalog.filter {
        (filter < 0 || it.category == filter) &&
                (query.isBlank() || it.name.contains(query, true) || it.latin.contains(query, true))
    }
    val selectable = list.filter { !cached.containsKey(it.id) }
    val toDownload = selected.filter { !cached.containsKey(it) }.toSet()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { BackIcon() }
            Text("Каталог грибов", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = GuideGreen, modifier = Modifier.weight(1f))
        }

        Text("Отметьте виды и нажмите «Загрузить». Загруженные появятся в основном списке и будут доступны офлайн.",
            fontSize = 12.sp, color = Color.Gray)

        OutlinedTextField(value = query, onValueChange = { query = it },
            label = { Text("Поиск по каталогу") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChipSmall("Все", filter == -1, Modifier.weight(1f)) { filter = -1 }
            FilterChipSmall("Съед.", filter == 0, Modifier.weight(1f)) { filter = 0 }
            FilterChipSmall("Усл.", filter == 1, Modifier.weight(1f)) { filter = 1 }
            FilterChipSmall("Ядов.", filter == 2, Modifier.weight(1f)) { filter = 2 }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { selected = selected + selectable.map { it.id } },
                enabled = busy.isEmpty() && selectable.isNotEmpty(),
                modifier = Modifier.weight(1f).height(44.dp)
            ) { Text("Выбрать все", fontSize = 12.sp, maxLines = 1) }
            OutlinedButton(
                onClick = { selected = emptySet() },
                enabled = busy.isEmpty() && selected.isNotEmpty(),
                modifier = Modifier.weight(1f).height(44.dp)
            ) { Text("Снять выбор", fontSize = 12.sp, maxLines = 1) }
        }

        Button(
            onClick = {
                val ids: Set<String> = toDownload
                val job = scope.launch {
                    GuideStore.downloadSelected(context, ids)
                    selected = emptySet()
                }
                GuideStore.setBulkJob(job)
            },
            enabled = busy.isEmpty() && toDownload.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            DownloadIcon(Modifier.size(18.dp), Color.White)
            Spacer(Modifier.width(8.dp))
            Text(if (busy.isEmpty()) "Загрузить выбранные (${toDownload.size})" else "Загрузка…", fontSize = 15.sp)
        }

        status?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(it, fontSize = 12.sp, color = GuideGreen, modifier = Modifier.weight(1f))
                TextButton(onClick = { GuideStore.clearStatus() }) { Text("Скрыть") }
            }
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list, key = { it.id }) { m ->
                val isCached = cached.containsKey(m.id)
                val isBusy = busy.contains(m.id)
                val checked = selected.contains(m.id)
                val (badge, bc) = catInfo(m.category)
                Card(
                    Modifier.fillMaxWidth().clickable(enabled = !isCached && busy.isEmpty()) {
                        selected = if (checked) selected - m.id else selected + m.id
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (checked && !isCached) Color(0xFFE8F5E9) else Color.White
                    )
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isCached) {
                            Text("✓", color = GuideGreen, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp))
                        } else {
                            Checkbox(checked = checked, enabled = busy.isEmpty(),
                                onCheckedChange = { selected = if (it) selected + m.id else selected - m.id })
                        }
                        val bmp = rememberThumb(cached[m.id]?.image ?: "", 120)
                        if (bmp != null) {
                            Image(bmp, null, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop)
                        } else {
                            Canvas(Modifier.size(48.dp)) { drawPlaceholder(bc) }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(m.latin, fontSize = 10.sp, color = Color.Gray)
                            Text(badge, color = bc, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        if (isBusy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else if (isCached) Text("загружен", fontSize = 10.sp, color = GuideGreen)
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun FilterChipSmall(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick, modifier.height(40.dp), contentPadding = PaddingValues(2.dp)) {
            Text(label, fontSize = 12.sp, maxLines = 1)
        }
    } else {
        OutlinedButton(onClick, modifier.height(40.dp), contentPadding = PaddingValues(2.dp)) {
            Text(label, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ArticleScreen(
    spec: MushroomSpec,
    article: CachedArticle?,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onPhoto: (String) -> Unit
) {
    val context = LocalContext.current
    val favorites by GuideStore.favorites.collectAsStateWithLifecycle()
    val isFav = favorites.contains(spec.id)
    val (badge, bc) = catInfo(spec.category)
    val lines = remember(article?.text) { article?.text?.split("\n") ?: emptyList() }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { BackIcon() }
                Text(spec.name, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = GuideGreen, modifier = Modifier.weight(1f), maxLines = 2)
                TextButton(
                    onClick = { GuideStore.toggleFavorite(spec.id) },
                    modifier = Modifier.size(42.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(if (isFav) "★" else "☆", fontSize = 24.sp,
                        color = if (isFav) GuideStar else GuideGray)
                }
                SosButton()
            }
        }

        if (article == null) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Canvas(Modifier.size(120.dp)) { drawPlaceholder(bc) }
                Text(spec.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(badge, color = bc, fontWeight = FontWeight.Bold)
                Text("Статья не загружена. Откройте каталог и загрузите этот вид.",
                    fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center)
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Назад") }
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {

            item {
                val bmp = rememberThumb(article.image, 900)
                if (bmp != null) {
                    Image(bmp, null,
                        Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 300.dp)
                            .clip(RoundedCornerShape(12.dp)).clickable { onPhoto(article.image) },
                        contentScale = ContentScale.Crop)
                    Text("Нажмите фото, чтобы открыть целиком", fontSize = 11.sp, color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                } else {
                    Canvas(Modifier.fillMaxWidth().height(160.dp)) { drawPlaceholder(bc) }
                }
            }

            item {
                Text(spec.latin, fontSize = 13.sp, color = Color.Gray)
                Text(badge, color = bc, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
            }

            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = if (spec.category == 2) Color(0xFFFFEBEE) else Color(0xFFFFF8E1))) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Опасные двойники / примечание", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(spec.warning, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            items(lines) { raw ->
                val t = raw.trim()
                when {
                    t.isEmpty() -> Spacer(Modifier.height(6.dp))
                    t.startsWith("=") -> {
                        val level = t.takeWhile { it == '=' }.length
                        Text(t.trim('=', ' '),
                            fontWeight = FontWeight.Bold,
                            fontSize = if (level <= 2) 18.sp else 15.sp,
                            color = GuideGreen,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                    }
                    else -> Text(t, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }

            item {
                Spacer(Modifier.height(14.dp))
                Text("Загружено: " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(article.loadedAt)),
                    fontSize = 11.sp, color = Color.Gray)
                Text("Источник: Википедия, CC BY-SA 4.0. Фото: Wikimedia Commons.",
                    fontSize = 11.sp, color = Color.Gray)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.sourceUrl))) }
                    }, modifier = Modifier.weight(1f)) { Text("Источник", fontSize = 12.sp) }
                    OutlinedButton(onClick = { GuideStore.toggleFavorite(spec.id) },
                        modifier = Modifier.weight(1f)) {
                        Text(if (isFav) "★ В избранном" else "☆ В избранное", fontSize = 12.sp, maxLines = 1)
                    }
                }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GuideRed)
                ) { Text("Удалить из справочника") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить из справочника?") },
            text = { Text("«${spec.name}» будет удалён из основного списка: фото и описание сотрутся из памяти телефона. Вид останется в каталоге.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Удалить", color = GuideRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } }
        )
    }
}

private fun DrawScope.drawPlaceholder(accent: Color) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val h = size.minDimension / 2f * 0.72f
    drawRoundRect(Color(0xFFECEFF1), topLeft = Offset.Zero, size = Size(size.width, size.height),
        cornerRadius = CornerRadius(size.minDimension * 0.12f))
    drawRoundRect(Color(0xFFCFD8DC), topLeft = Offset(c.x - h * 0.22f, c.y - h * 0.05f),
        size = Size(h * 0.44f, h * 0.9f), cornerRadius = CornerRadius(h * 0.2f))
    drawPath(Path().apply {
        moveTo(c.x - h, c.y)
        quadraticBezierTo(c.x - h * 0.7f, c.y - h, c.x, c.y - h)
        quadraticBezierTo(c.x + h * 0.7f, c.y - h, c.x + h, c.y)
        close()
    }, accent.copy(alpha = 0.55f))
}