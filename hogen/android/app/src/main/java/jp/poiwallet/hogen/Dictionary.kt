package jp.poiwallet.hogen

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 1語分。r=読み, w=表記, g=地域, m=標準語の意味, mr=意味の読み */
data class Entry(
    val r: String,
    val w: String,
    val g: String,
    val m: String = "",
    val mr: String = "",
    val mine: Boolean = false,
) {
    val id: String get() = "$r|$w|$g"
    val regions: List<String> get() = g.split("・")

    fun toJson(): JSONObject = JSONObject()
        .put("r", r).put("w", w).put("g", g).put("m", m).put("mr", mr)

    companion object {
        fun fromJson(o: JSONObject, mine: Boolean) = Entry(
            r = o.optString("r"), w = o.optString("w"), g = o.optString("g"),
            m = o.optString("m"), mr = o.optString("mr"), mine = mine,
        )
    }
}

data class Candidate(val entry: Entry, val viaStandard: Boolean)

/**
 * 辞書。同梱の dict.json（Web版と共通）と、ユーザーが追加した言葉・使った回数を持つ。
 * 保存は SharedPreferences（端末内のみ）。
 */
class Dictionary private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences("hogen", Context.MODE_PRIVATE)
    private val base: List<Entry> = loadBase()
    private val mine = mutableListOf<Entry>()
    private val uses = mutableMapOf<String, Int>()
    var preferredRegions: Set<String> = emptySet()
        set(value) {
            field = value
            prefs.edit().putString("regions", JSONArray(value.toList()).toString()).apply()
        }

    init { loadUser() }

    private fun loadBase(): List<Entry> = try {
        val text = context.assets.open("dict.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val arr = JSONArray(text)
        List(arr.length()) { Entry.fromJson(arr.getJSONObject(it), mine = false) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun loadUser() {
        try {
            val arr = JSONArray(prefs.getString("mine", "[]"))
            for (i in 0 until arr.length()) mine += Entry.fromJson(arr.getJSONObject(i), mine = true)
            val u = JSONObject(prefs.getString("uses", "{}") ?: "{}")
            for (k in u.keys()) uses[k] = u.optInt(k)
            val rg = JSONArray(prefs.getString("regions", "[]"))
            preferredRegions = List(rg.length()) { rg.getString(it) }.toSet()
        } catch (_: Exception) { }
    }

    private fun saveUser() {
        prefs.edit()
            .putString("mine", JSONArray(mine.map { it.toJson() }).toString())
            .putString("uses", JSONObject(uses as Map<*, *>).toString())
            .apply()
    }

    val all: List<Entry> get() = base + mine
    val myWords: List<Entry> get() = mine.toList()
    val baseCount: Int get() = base.size
    val totalUses: Int get() = uses.values.sum()
    fun allRegions(): List<String> = all.flatMap { it.regions }.distinct()

    /** 読みの前方一致、または標準語の読みの前方一致で候補を出す */
    fun suggest(input: String, limit: Int = 20): List<Candidate> {
        val q = toHiragana(input)
        if (q.isEmpty()) return emptyList()
        val out = mutableListOf<Pair<Candidate, Int>>()
        val seen = HashSet<String>()
        for (e in all) {
            val byReading = toHiragana(e.r).startsWith(q)
            val byStd = e.mr.isNotEmpty() && toHiragana(e.mr).startsWith(q)
            if (!byReading && !byStd) continue
            if (!seen.add(e.id)) continue
            var s = (uses[e.id] ?: 0) * 10
            if (e.mine) s += 5
            if (preferredRegions.isNotEmpty() && e.regions.any { it in preferredRegions }) s += 20
            if ((if (byReading) e.r else e.mr) == q) s += 8
            if (!byReading) s -= 2
            out += Candidate(e, viaStandard = !byReading) to s
        }
        return out.sortedWith(compareByDescending<Pair<Candidate, Int>> { it.second }
            .thenBy { it.first.entry.r.length })
            .take(limit).map { it.first }
    }

    fun recordUse(e: Entry) {
        uses[e.id] = (uses[e.id] ?: 0) + 1
        saveUser()
    }

    /** 追加できたら true。同じ言葉が既にあれば false */
    fun add(e: Entry): Boolean {
        val entry = e.copy(r = toHiragana(e.r), mr = toHiragana(e.mr), mine = true)
        if (all.any { it.id == entry.id }) return false
        mine += entry
        saveUser()
        return true
    }

    fun remove(e: Entry) {
        mine.removeAll { it.id == e.id }
        saveUser()
    }

    /** Web版の「書き出す」と同じ形式 */
    fun exportJson(): String = JSONObject()
        .put("version", 1)
        .put("mine", JSONArray(mine.map { it.toJson() }))
        .put("uses", JSONObject(uses as Map<*, *>))
        .toString()

    /** 読み込んだ語数を返す。形式が違えば例外 */
    fun importJson(text: String): Int {
        val o = JSONObject(text)
        val arr = o.getJSONArray("mine")
        var n = 0
        for (i in 0 until arr.length()) {
            val e = Entry.fromJson(arr.getJSONObject(i), mine = true)
            if (e.r.isNotEmpty() && e.w.isNotEmpty() && e.g.isNotEmpty() && add(e)) n++
        }
        o.optJSONObject("uses")?.let { u ->
            for (k in u.keys()) uses[k] = maxOf(uses[k] ?: 0, u.optInt(k))
        }
        saveUser()
        return n
    }

    companion object {
        @Volatile private var instance: Dictionary? = null
        fun get(context: Context): Dictionary =
            instance ?: synchronized(this) {
                instance ?: Dictionary(context.applicationContext).also { instance = it }
            }

        fun toHiragana(s: String): String = buildString(s.length) {
            for (c in s) append(if (c in 'ァ'..'ヶ') c - 0x60 else c)
        }

        fun toKatakana(s: String): String = buildString(s.length) {
            for (c in s) append(if (c in 'ぁ'..'ゖ') c + 0x60 else c)
        }
    }
}
