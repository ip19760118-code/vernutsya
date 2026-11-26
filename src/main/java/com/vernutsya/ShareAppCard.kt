package com.vernutsya

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

private const val APP_LINK = "https://apps.rustore.ru/app/com.vernutsya"

private const val SHARE_TEXT =
    "🍄 Вернутся — навигатор для грибников.\n" +
    "Отмечаешь точку возврата, идёшь за грибами, а стрелка всегда показывает дорогу обратно. " +
    "Работает без интернета и с заблокированным экраном.\n\n" +
    "Скачать в RuStore: "

private const val AUTHOR = "Ряшенцев Е. В."
private const val PRIVACY_URL =
    "https://github.com/ip19760118-code/vernutsya/blob/main/PRIVACY.md"
private const val YANDEX_POLICY_URL = "https://yandex.ru/legal/confidential/"

@Composable
fun ShareAppCard() {
    val context = LocalContext.current
    val settings by AppSettings.state.collectAsStateWithLifecycle()
    var showAbout by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Поделиться приложением", fontWeight = FontWeight.Bold)
            Text(
                "Ссылка скопируется в буфер обмена и откроется выбор — куда отправить: мессенджер, почта или SMS.",
                fontSize = 13.sp, color = Color.Gray
            )
            Button(
                onClick = { shareAppLink(context) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                ShareIcon(color = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Копировать ссылку на приложение", fontSize = 15.sp, textAlign = TextAlign.Center)
            }
            Text(APP_LINK, fontSize = 11.sp, color = Color.Gray)
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Анонимная статистика", fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = settings.analyticsEnabled,
                    onCheckedChange = {
                        AppSettings.setAnalyticsEnabled(it)
                        Analytics.applyToggle()
                    }
                )
                Text("Помогать улучшать приложение")
            }
            Text(
                "Используется Яндекс AppMetrica (серверы в России). Передаются только " +
                        "названия действий, версия приложения, названия экранов и округлённые " +
                        "длина и длительность прогулки. Координаты, трек, названия точек, " +
                        "фотографии, заметки и номер SOS-контакта НЕ передаются. " +
                        "Отслеживание геолокации в аналитике выключено. Рекламы нет. " +
                        "Можно выключить в любой момент.",
                fontSize = 12.sp, color = Color.Gray
            )
            OutlinedButton(
                onClick = { openUrl(context, PRIVACY_URL) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Политика приложения", fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
            OutlinedButton(
                onClick = { openUrl(context, YANDEX_POLICY_URL) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Политика Яндекса", fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("О приложении", fontWeight = FontWeight.Bold)
            Text("Версия, автор, описание, источники данных и лицензии.", fontSize = 13.sp, color = Color.Gray)
            OutlinedButton(
                onClick = { showAbout = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                BookIcon(Modifier.size(18.dp), Color(0xFF2E7D32))
                Spacer(Modifier.width(8.dp))
                Text("О приложении", fontSize = 15.sp)
            }
        }
    }

    if (showAbout) AboutDialog { showAbout = false }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by AppSettings.state.collectAsStateWithLifecycle()
    val latest by UpdateManager.latestVersion.collectAsStateWithLifecycle()
    val latestUrl by UpdateManager.latestUrl.collectAsStateWithLifecycle()
    val checking by UpdateManager.checking.collectAsStateWithLifecycle()
    val installed = remember { UpdateManager.currentVersion(context) }

    LaunchedEffect(Unit) { UpdateManager.check(context, false) }

    val newer = latest != null && UpdateManager.compareVersions(latest!!, installed) > 0

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) { BackIcon() }
                    Text("О приложении", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                }

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🍄", fontSize = 56.sp)
                        Text("Вернутся", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        Text("Навигатор для грибников", fontSize = 14.sp, color = Color.Gray)
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            InfoRow("Установленная версия", installed)
                            InfoRow("Последняя опубликованная",
                                when {
                                    checking -> "проверяем…"
                                    latest != null -> latest!!
                                    else -> "нет данных (нужен интернет)"
                                })
                            InfoRow("Создатель приложения", AUTHOR)
                            InfoRow("Платформа", "Android 8.0 и выше")
                            if (newer) {
                                Text("Доступна версия $latest", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                Button(onClick = {
                                    Analytics.log("update_open_store")
                                    UpdateManager.openStore(context)
                                }, modifier = Modifier.fillMaxWidth()) {
                                    DownloadIcon(Modifier.size(18.dp), Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Обновить через RuStore")
                                }
                            } else if (latest != null) {
                                Text("У вас актуальная версия", color = Color(0xFF2E7D32), fontSize = 13.sp)
                            }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    Analytics.log("update_check")
                                    UpdateManager.check(context, true)
                                }
                            }, enabled = !checking, modifier = Modifier.fillMaxWidth()) {
                                Text(if (checking) "Проверяем…" else "Проверить обновления")
                            }
                            Text("Обновления устанавливает RuStore.", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Краткое описание", fontWeight = FontWeight.Bold)
                            Text(
                                "«Вернутся» помогает не потеряться в лесу. Отметьте точку возврата у машины, " +
                                        "идите за грибами — приложение пишет трек, а объёмная 3D-стрелка всегда показывает " +
                                        "направление обратно и расстояние по прямой. Работает без интернета, без Google-сервисов " +
                                        "и с заблокированным экраном: расстояние видно прямо в уведомлении.",
                                fontSize = 13.sp
                            )
                        }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Возможности", fontWeight = FontWeight.Bold)
                            listOf(
                                "3D-стрелка к точке возврата и картинка цели на круге",
                                "Автопоправка компаса по местности и датчику",
                                "Возврат по пройденному треку, а не только по прямой",
                                "Промежуточные точки, фото-точки, свои названия и заметки",
                                "Точки из сохранённых и присланных треков",
                                "Следование по чужому треку",
                                "Офлайн-карты: скачивание областей и импорт .mbtiles",
                                "Карта-подложка под треком, масштаб и стрелка севера",
                                "Запись трека в фоне, уведомление с расстоянием",
                                "Сохранение маршрутов в GPX и отправка друзьям",
                                "SOS: SMS с координатами и приём сигнала у друга",
                                "Таймер до заката, точность GPS, калибровка компаса",
                                "Ночной режим, ЭКО-режим, авто-ЭКО при низком заряде",
                                "Справочник грибов с фото и полными описаниями офлайн"
                            ).forEach { Text("• $it", fontSize = 13.sp) }
                        }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Технологии и данные", fontWeight = FontWeight.Bold)
                            InfoRow("Язык и UI", "Kotlin, Jetpack Compose, Material 3")
                            InfoRow("Карты", "osmdroid, OpenStreetMap")
                            InfoRow("Справочник грибов", "Википедия / Wikimedia Commons")
                            InfoRow("Лицензия материалов", "CC BY-SA 4.0")
                            InfoRow("Лицензия приложения", "MIT")
                            InfoRow("Аналитика",
                                if (settings.analyticsEnabled) "Яндекс AppMetrica" else "выключена")
                            InfoRow("Реклама", "отсутствует")
                        }
                    }

                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Важное предупреждение", fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                            Text(
                                "Справочник грибов носит ознакомительный характер. Категории съедобности и предупреждения " +
                                        "о двойниках заданы в приложении, описания и фото загружаются из Википедии. " +
                                        "Не употребляйте гриб, если не уверены в его определении на 100%. " +
                                        "Приложение не заменяет консультацию специалиста.",
                                fontSize = 13.sp
                            )
                        }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Ссылки", fontWeight = FontWeight.Bold)
                            OutlinedButton(onClick = {
                                Analytics.log("open_store")
                                UpdateManager.openStore(context)
                            }, modifier = Modifier.fillMaxWidth()) { Text("Страница в RuStore", fontSize = 13.sp) }
                            OutlinedButton(onClick = { openUrl(context, latestUrl ?: UpdateManager.REPO_URL) },
                                modifier = Modifier.fillMaxWidth()) { Text("Страница последнего релиза", fontSize = 13.sp) }
                            OutlinedButton(onClick = { openUrl(context, PRIVACY_URL) },
                                modifier = Modifier.fillMaxWidth()) { Text("Политика приложения", fontSize = 13.sp) }
                            OutlinedButton(onClick = { openUrl(context, YANDEX_POLICY_URL) },
                                modifier = Modifier.fillMaxWidth()) { Text("Политика Яндекса", fontSize = 13.sp) }
                            OutlinedButton(onClick = { shareAppLink(context) }, modifier = Modifier.fillMaxWidth()) {
                                ShareIcon(Modifier.size(18.dp), Color(0xFF2E7D32))
                                Spacer(Modifier.width(8.dp)); Text("Поделиться ссылкой", fontSize = 13.sp)
                            }
                        }
                    }

                    Text("© $AUTHOR. Сделано для грибников.",
                        fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, fontSize = 13.sp, color = Color.Gray, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

fun shareAppLink(context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Вернутся", APP_LINK))
    Toast.makeText(context, "Ссылка скопирована в буфер обмена", Toast.LENGTH_SHORT).show()
    Analytics.log("share_app")
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, SHARE_TEXT + APP_LINK)
        putExtra(Intent.EXTRA_SUBJECT, "Приложение «Вернутся» для грибников")
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться ссылкой"))
}