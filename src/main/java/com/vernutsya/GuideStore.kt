package com.vernutsya

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Категория: 0 съедобный, 1 условно-съедобный, 2 ядовитый, 3 несъедобный. */
data class MushroomSpec(
    val id: String,
    val name: String,
    val wiki: String,
    val latin: String,
    val category: Int,
    val warning: String
)

data class CachedArticle(
    val id: String,
    val text: String,
    val image: String,
    val sourceUrl: String,
    val loadedAt: Long
)

object GuideStore {

    private const val UA = "Vernutsya/2.6 (Android; mushroom field guide; +https://github.com/ip19760118-code/vernutsya)"
    private const val API = "https://ru.wikipedia.org/w/api.php"
    private const val KEY_FAV = "favorites"

    val catalog: List<MushroomSpec> = listOf(
        MushroomSpec("boletus_edulis", "Белый гриб", "Белый гриб", "Boletus edulis", 0, "Двойник: жёлчный гриб (горький, розовеющий трубчатый слой)."),
        MushroomSpec("leccinum_scabrum", "Подберёзовик", "Подберёзовик", "Leccinum scabrum", 0, "Двойник: жёлчный гриб."),
        MushroomSpec("leccinum_aurantiacum", "Подосиновик", "Подосиновик", "Leccinum aurantiacum", 0, "Двойник: сатанинский гриб (ядовит)."),
        MushroomSpec("cantharellus_cibarius", "Лисичка обыкновенная", "Лисичка обыкновенная", "Cantharellus cibarius", 0, "Двойник: лисичка ложная (ровные частые пластинки)."),
        MushroomSpec("armillaria_mellea", "Опёнок осенний", "Опёнок осенний", "Armillaria mellea", 0, "Двойники: ложноопята; на пнях — галерина окаймлённая (смертельно ядовита)."),
        MushroomSpec("lactarius_deliciosus", "Рыжик настоящий", "Рыжик настоящий", "Lactarius deliciosus", 0, "Двойник: волнушка (белый едкий сок)."),
        MushroomSpec("suillus_luteus", "Маслёнок обыкновенный", "Маслёнок обыкновенный", "Suillus luteus", 0, "Слизистую кожицу шляпки снимают."),
        MushroomSpec("xerocomus_subtomentosus", "Моховик зелёный", "Моховик зелёный", "Xerocomus subtomentosus", 0, "Двойник: перечный гриб (острый, несъедобен)."),
        MushroomSpec("agaricus_campestris", "Шампиньон луговой", "Шампиньон луговой", "Agaricus campestris", 0, "Опасные двойники: бледная поганка, шампиньон желтокожий."),
        MushroomSpec("russula_vesca", "Сыроежка пищевая", "Сыроежка пищевая", "Russula vesca", 0, "Двойник: сыроежка рвотная (едкая)."),
        MushroomSpec("pleurotus_ostreatus", "Вёшенка обыкновенная", "Вёшенка обыкновенная", "Pleurotus ostreatus", 0, "Растёт на древесине; проверяйте пластинки и запах."),
        MushroomSpec("lycoperdon_perlatum", "Дождевик шиповатый", "Дождевик шиповатый", "Lycoperdon perlatum", 0, "Собирать только с белой мякотью; разрезайте — внутри не должно быть зачатка шляпки мухомора."),
        MushroomSpec("hydnum_repandum", "Ежовик жёлтый", "Ежовик жёлтый", "Hydnum repandum", 0, "Снизу шипики, а не пластинки."),
        MushroomSpec("kuehneromyces_mutabilis", "Опёнок летний", "Опёнок летний", "Kuehneromyces mutabilis", 0, "Смертельный двойник: галерина окаймлённая. Новичкам лучше не собирать."),
        MushroomSpec("macrolepiota_procera", "Гриб-зонтик пёстрый", "Гриб-зонтик пёстрый", "Macrolepiota procera", 0, "Крупная шляпка с чешуйками, подвижное кольцо, ножка с «змеиным» рисунком. Двойники: мелкие ядовитые лепиоты и хлорофиллум свинцовошлаковый."),
        MushroomSpec("lactarius_resimus", "Груздь настоящий", "Груздь настоящий", "Lactarius resimus", 1, "Только солёным после длительного вымачивания."),
        MushroomSpec("lactarius_torminosus", "Волнушка розовая", "Волнушка розовая", "Lactarius torminosus", 1, "Отварить 15–20 минут, отвар слить, затем солить."),
        MushroomSpec("russula_foetens", "Валуй", "Валуй", "Russula foetens", 1, "Вымачивание и отваривание обязательны."),
        MushroomSpec("tricholoma_flavovirens", "Зелёнушка", "Зелёнушка", "Tricholoma flavovirens", 1, "Двойники: ядовитые рядовки и паутинники."),
        MushroomSpec("tricholoma_portentosum", "Рядовка серая", "Рядовка серая", "Tricholoma portentosum", 1, "Двойники: паутинники (смертельно ядовиты)."),
        MushroomSpec("morchella_esculenta", "Сморчок съедобный", "Сморчок съедобный", "Morchella esculenta", 1, "Только после отваривания со сливом отвара; сырой ядовит."),
        MushroomSpec("russula_delica", "Подгруздок белый", "Подгруздок белый", "Russula delica", 1, "Сухой, без млечного сока; для засола."),
        MushroomSpec("chlorophyllum_rhacodes", "Зонтик краснеющий", "Зонтик краснеющий", "Chlorophyllum rhacodes", 1, "Мякоть краснеет на срезе. Только после отваривания. Двойник: хлорофиллум свинцовошлаковый (ядовит)."),
        MushroomSpec("leucoagaricus_leucothites", "Зонтик белый", "Зонтик белый", "Leucoagaricus leucothites", 1, "Опасные двойники: бледная поганка и мухомор вонючий — всегда проверяйте вольву у основания ножки."),
        MushroomSpec("gyromitra_esculenta", "Строчок обыкновенный", "Строчок обыкновенный", "Gyromitra esculenta", 2, "Содержит гиромитрин. Во многих странах запрещён к продаже."),
        MushroomSpec("paxillus_involutus", "Свинушка тонкая", "Свинушка тонкая", "Paxillus involutus", 2, "Накапливает токсины, разрушает кровь. С 1981 г. считается ядовитой."),
        MushroomSpec("amanita_phalloides", "Бледная поганка", "Бледная поганка", "Amanita phalloides", 2, "СМЕРТЕЛЬНО ЯДОВИТА. Клубень-вольва у основания. Двойники: шампиньон, сыроежка, зонтик белый."),
        MushroomSpec("amanita_muscaria", "Мухомор красный", "Мухомор красный", "Amanita muscaria", 2, "Ядовит: тяжёлое отравление, галлюцинации."),
        MushroomSpec("amanita_virosa", "Мухомор вонючий", "Мухомор вонючий", "Amanita virosa", 2, "СМЕРТЕЛЬНО ЯДОВИТ. Белый, с кольцом и вольвой."),
        MushroomSpec("amanita_pantherina", "Мухомор пантерный", "Мухомор пантерный", "Amanita pantherina", 2, "Сильно ядовит, опаснее красного мухомора."),
        MushroomSpec("hypholoma_fasciculare", "Ложноопёнок серопластинчатый", "Ложноопёнок серопластинчатый", "Hypholoma fasciculare", 2, "Ядовит. Пластинки серо-зелёные, без кольца, горький."),
        MushroomSpec("hypholoma_sublateritium", "Ложноопёнок кирпично-красный", "Ложноопёнок кирпично-красный", "Hypholoma sublateritium", 2, "Ядовит/несъедобен. Без кольца на ножке."),
        MushroomSpec("rubroboletus_satanas", "Сатанинский гриб", "Сатанинский гриб", "Rubroboletus satanas", 2, "Ядовит. Красная сетка на ножке, синеет на срезе."),
        MushroomSpec("cortinarius_rubellus", "Паутинник красивейший", "Паутинник красивейший", "Cortinarius rubellus", 2, "СМЕРТЕЛЬНО ЯДОВИТ. Поражение почек проявляется через недели."),
        MushroomSpec("inocybe_erubescens", "Волоконница Патуйяра", "Волоконница Патуйяра", "Inocybe erubescens", 2, "СМЕРТЕЛЬНО ЯДОВИТА. Много мускарина, краснеет на воздухе."),
        MushroomSpec("galerina_marginata", "Галерина окаймлённая", "Галерина окаймлённая", "Galerina marginata", 2, "СМЕРТЕЛЬНО ЯДОВИТА. Двойник летнего опёнка, растёт на пнях."),
        MushroomSpec("clitocybe_dealbata", "Говорушка беловатая", "Говорушка беловатая", "Clitocybe dealbata", 2, "Ядовита. Мелкая белая, растёт на газонах рядом со шампиньонами."),
        MushroomSpec("entoloma_sinuatum", "Энтолома ядовитая", "Энтолома ядовитая", "Entoloma sinuatum", 2, "Ядовита. Розовые пластинки, мучнистый запах."),
        MushroomSpec("hygrophoropsis_aurantiaca", "Лисичка ложная", "Лисичка ложная", "Hygrophoropsis aurantiaca", 2, "Несъедобна/слабо ядовита. Пластинки частые, ровные, оранжевые."),
        MushroomSpec("chlorophyllum_molybdites", "Хлорофиллум свинцовошлаковый", "Хлорофиллум свинцовошлаковый", "Chlorophyllum molybdites", 2, "Ядовит. Пластинки с возрастом зеленоватые. Частый двойник съедобных зонтиков."),
        MushroomSpec("lepiota_cristata", "Лепиота острочешуйчатая", "Лепиота острочешуйчатая", "Lepiota cristata", 2, "Ядовита. Мелкая (шляпка до 5 см) — не путать с крупными зонтиками."),
        MushroomSpec("tylopilus_felleus", "Жёлчный гриб", "Жёлчный гриб", "Tylopilus felleus", 3, "Несъедобен из-за горечи. Двойник белого гриба."),
        MushroomSpec("chalciporus_piperatus", "Перечный гриб", "Перечный гриб", "Chalciporus piperatus", 3, "Несъедобен: очень острая мякоть.")
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var imgDir: File
    private var indexFile: File? = null
    private var initialized = false

    private val _cached = MutableStateFlow<Map<String, CachedArticle>>(emptyMap())
    val cached: StateFlow<Map<String, CachedArticle>> = _cached.asStateFlow()

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private var bulkJob: Job? = null

    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        prefs = app.getSharedPreferences("vernutsya_guide", Context.MODE_PRIVATE)
        imgDir = File(app.filesDir, "guide_img").apply { mkdirs() }
        indexFile = File(app.filesDir, "guide_index.json")
        _cached.value = readIndex()
        _favorites.value = prefs.getStringSet(KEY_FAV, emptySet())?.toSet() ?: emptySet()
        initialized = true
    }

    fun article(id: String): CachedArticle? = _cached.value[id]
    fun isCached(id: String): Boolean = _cached.value.containsKey(id)
    fun cachedCount(): Int = _cached.value.size
    fun specById(id: String): MushroomSpec? = catalog.firstOrNull { it.id == id }

    // ---------- избранное ----------

    fun isFavorite(id: String): Boolean = _favorites.value.contains(id)

    fun toggleFavorite(id: String) {
        if (!initialized) return
        val next = if (_favorites.value.contains(id)) _favorites.value - id else _favorites.value + id
        _favorites.value = next
        prefs.edit().putStringSet(KEY_FAV, next).apply()
    }

    private fun dropFavorite(id: String) {
        if (!initialized || !_favorites.value.contains(id)) return
        val next = _favorites.value - id
        _favorites.value = next
        prefs.edit().putStringSet(KEY_FAV, next).apply()
    }

    // ---------- загрузка ----------

    suspend fun download(context: Context, spec: MushroomSpec): Boolean {
        init(context)
        if (_busy.value.contains(spec.id)) return false
        _busy.value = _busy.value + spec.id
        return try {
            withContext(Dispatchers.IO) {
                val fetched = fetchArticle(spec)
                if (fetched == null) false
                else {
                    val (title, text, imgUrl) = fetched
                    val local = if (imgUrl.isNotEmpty()) downloadImage(spec.id, imgUrl) else ""
                    val art = CachedArticle(spec.id, text, local, pageUrl(title), System.currentTimeMillis())
                    _cached.value = _cached.value + (spec.id to art)
                    writeIndex()
                    true
                }
            }
        } catch (_: Exception) { false } finally {
            _busy.value = _busy.value - spec.id
        }
    }

    suspend fun downloadSelected(context: Context, ids: Set<String>) {
        init(context)
        val targets = catalog.filter { ids.contains(it.id) && !isCached(it.id) }
        if (targets.isEmpty()) { _status.value = "Выбранные грибы уже загружены"; return }
        var done = 0
        var failed = 0
        for (spec in targets) {
            _status.value = "Загрузка ${done + failed + 1} из ${targets.size}: ${spec.name}"
            if (download(context, spec)) done++ else failed++
            delay(250)
        }
        _status.value = if (failed == 0) "Загружено статей: $done"
                        else "Загружено: $done, не удалось: $failed"
    }

    suspend fun downloadAll(context: Context) {
        downloadSelected(context, catalog.map { it.id }.toSet())
    }

    fun cancelBulk() { bulkJob?.cancel(); bulkJob = null; _status.value = "Загрузка остановлена" }
    fun setBulkJob(job: Job) { bulkJob = job }
    fun clearStatus() { _status.value = null }

    /** Удаляет статью и фото из памяти телефона; вид остаётся в каталоге. */
    fun delete(context: Context, id: String) {
        init(context)
        _cached.value[id]?.image?.takeIf { it.isNotEmpty() }?.let { runCatching { File(it).delete() } }
        _cached.value = _cached.value - id
        writeIndex()
        dropFavorite(id)
    }

    // ---------- сеть ----------

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun pageUrl(title: String): String = "https://ru.wikipedia.org/wiki/" + enc(title)

    private fun fetchArticle(spec: MushroomSpec): Triple<String, String, String>? {
        var title = spec.wiki
        var page = queryPage(title)
        if (page == null) {
            val found = searchTitle(spec.name)
            if (found != null) { title = found; page = queryPage(title) }
        }
        val p = page ?: return null
        return Triple(title, cleanText(p.first), p.second)
    }

    private fun queryPage(title: String): Pair<String, String>? {
        val url = API + "?action=query&format=json&formatversion=2" +
                "&prop=extracts%7Cpageimages&explaintext=1&piprop=thumbnail&pithumbsize=900" +
                "&redirects=1&titles=" + enc(title)
        val root = JSONObject(httpGet(url))
        val pages = root.optJSONObject("query")?.optJSONArray("pages") ?: return null
        if (pages.length() == 0) return null
        val p = pages.getJSONObject(0)
        if (p.optBoolean("missing", false)) return null
        val text = p.optString("extract", "")
        if (text.isBlank()) return null
        val img = p.optJSONObject("thumbnail")?.optString("source", "") ?: ""
        return text to img
    }

    private fun searchTitle(name: String): String? {
        val url = API + "?action=query&format=json&formatversion=2&list=search&srlimit=1&srsearch=" + enc("$name гриб")
        val root = JSONObject(httpGet(url))
        val arr = root.optJSONObject("query")?.optJSONArray("search") ?: return null
        if (arr.length() == 0) return null
        return arr.getJSONObject(0).optString("title", "").takeIf { it.isNotBlank() }
    }

    private fun cleanText(raw: String): String {
        var t = raw
        for (marker in listOf("\n== Литература", "\n== Примечания", "\n== Ссылки", "\n== Таксономия", "\n== Источники")) {
            val i = t.indexOf(marker)
            if (i > 400) t = t.substring(0, i)
        }
        return t.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun downloadImage(id: String, url: String): String {
        return try {
            val f = File(imgDir, "$id.jpg")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("User-Agent", UA)
            try {
                if (conn.responseCode !in 200..299) return ""
                conn.inputStream.use { input -> f.outputStream().use { input.copyTo(it) } }
                if (f.length() < 500L) { f.delete(); "" } else f.absolutePath
            } finally { conn.disconnect() }
        } catch (_: Exception) { "" }
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept", "application/json")
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    }

    // ---------- кэш ----------

    private fun writeIndex() {
        runCatching {
            val root = JSONObject()
            _cached.value.values.forEach { a ->
                root.put(a.id, JSONObject().apply {
                    put("text", a.text); put("image", a.image)
                    put("url", a.sourceUrl); put("at", a.loadedAt)
                })
            }
            indexFile?.writeText(root.toString())
        }
    }

    private fun readIndex(): Map<String, CachedArticle> = runCatching {
        val f = indexFile ?: return emptyMap()
        if (!f.exists()) return emptyMap()
        val root = JSONObject(f.readText())
        val out = HashMap<String, CachedArticle>()
        root.keys().forEach { key ->
            val o = root.getJSONObject(key)
            val img = o.optString("image", "")
            if (img.isEmpty() || File(img).exists()) {
                out[key] = CachedArticle(key, o.optString("text", ""), img, o.optString("url", ""), o.optLong("at", 0L))
            }
        }
        out as Map<String, CachedArticle>
    }.getOrDefault(emptyMap())
}