package com.vernutsya

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {
    data class Info(val version: String, val pageUrl: String, val notes: String)

    private const val API = "https://api.github.com/repos/ip19760118-code/vernutsya/releases/latest"

    const val REPO_URL = "https://github.com/ip19760118-code/vernutsya"
    const val RUSTORE_URL = "https://apps.rustore.ru/app/com.vernutsya"

    private val _available = MutableStateFlow<Info?>(null)
    val available: StateFlow<Info?> = _available.asStateFlow()
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _latestVersion = MutableStateFlow<String?>(null)
    val latestVersion: StateFlow<String?> = _latestVersion.asStateFlow()
    private val _latestUrl = MutableStateFlow<String?>(null)
    val latestUrl: StateFlow<String?> = _latestUrl.asStateFlow()

    fun init(context: Context) { /* не требуется */ }

    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    }.getOrDefault("0")

    suspend fun check(context: Context, manual: Boolean) {
        if (_checking.value) return
        _checking.value = true
        _message.value = null
        try {
            if (!hasInternet(context)) { if (manual) _message.value = "Нет интернета"; return }
            val json = withContext(Dispatchers.IO) { httpGet(API) }
            val obj = JSONObject(json)
            val tag = obj.optString("tag_name", "")
            val page = obj.optString("html_url", "")
            val notes = obj.optString("body", "")
            if (tag.isNotEmpty()) {
                _latestVersion.value = tag
                _latestUrl.value = page.ifEmpty { REPO_URL }
            }
            val cur = currentVersion(context)
            if (tag.isNotEmpty() && compareVersions(tag, cur) > 0) {
                _available.value = Info(tag, page, notes)
                if (manual) _message.value = "Доступна новая версия $tag"
            } else {
                _available.value = null
                if (manual) _message.value = "У вас последняя версия ($cur)"
            }
        } catch (_: Exception) {
            if (manual) _message.value = "Не удалось проверить обновление"
        } finally { _checking.value = false }
    }

    fun dismiss() { _available.value = null }

    /** Открывает карточку приложения в RuStore — обновление устанавливает сам магазин. */
    fun openStore(context: Context) {
        val opened = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RUSTORE_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!opened) {
            Toast.makeText(context, "Откройте RuStore и обновите приложение вручную", Toast.LENGTH_LONG).show()
        }
    }

    fun openReleasePage(context: Context) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(_latestUrl.value ?: REPO_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun hasInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000; conn.readTimeout = 10000
        conn.setRequestProperty("User-Agent", "Vernutsya-App")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    }

    fun compareVersions(a: String, b: String): Int {
        fun parts(s: String) = s.trim().removePrefix("v").removePrefix("V").split(".").map { it.toIntOrNull() ?: 0 }
        val pa = parts(a); val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }; val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}

@Composable
fun UpdateDialog() {
    val info by UpdateManager.available.collectAsStateWithLifecycle()
    val context = LocalContext.current
    info?.let { i ->
        AlertDialog(
            onDismissRequest = { UpdateManager.dismiss() },
            title = { Text("Доступна версия ${i.version}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Обновите приложение через RuStore — магазин установит новую версию автоматически.")
                }
            },
            confirmButton = {
                TextButton(onClick = { UpdateManager.openStore(context); UpdateManager.dismiss() }) { Text("Открыть RuStore") }
            },
            dismissButton = { TextButton(onClick = { UpdateManager.dismiss() }) { Text("Позже") } }
        )
    }
}