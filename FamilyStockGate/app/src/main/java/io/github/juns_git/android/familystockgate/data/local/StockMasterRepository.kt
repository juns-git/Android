package io.github.juns_git.android.familystockgate.data.local

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.juns_git.android.familystockgate.data.model.StockItem
import java.io.File

object StockMasterRepository {

    private const val PREFS_NAME  = "stock_prefs"
    private const val KEY_VERSION = "stock_master_version"
    private const val KEY_RECENT  = "recent_searches"
    private const val FILE_NAME   = "stock_master.json"
    private const val MAX_RECENT  = 5

    private val gson = Gson()

    // ── 버전 ──────────────────────────────────────────────────────────────────

    fun getLocalVersion(context: Context): Long =
        prefs(context).getLong(KEY_VERSION, 0L)

    private fun saveVersion(context: Context, version: Long) =
        prefs(context).edit().putLong(KEY_VERSION, version).apply()

    // ── 마스터 파일 ───────────────────────────────────────────────────────────

    fun hasLocalData(context: Context): Boolean {
        val f = masterFile(context)
        return f.exists() && f.length() > 0L
    }

    fun saveStockMaster(context: Context, stocks: List<StockItem>, version: Long) {
        runCatching {
            masterFile(context).writeText(gson.toJson(stocks))
            saveVersion(context, version)
        }.onFailure { Log.e("StockMasterRepo", "저장 실패", it) }
    }

    fun loadStockMaster(context: Context): List<StockItem> = runCatching {
        val file = masterFile(context)
        if (!file.exists()) return emptyList()
        val type = object : TypeToken<List<StockItem>>() {}.type
        gson.fromJson<List<StockItem>>(file.readText(), type) ?: emptyList()
    }.getOrElse { e ->
        Log.e("StockMasterRepo", "로드 실패", e)
        emptyList()
    }

    // ── 최근 검색 ─────────────────────────────────────────────────────────────

    fun loadRecentSearches(context: Context): List<StockItem> = runCatching {
        val json = prefs(context).getString(KEY_RECENT, null) ?: return emptyList()
        val type = object : TypeToken<List<StockItem>>() {}.type
        gson.fromJson<List<StockItem>>(json, type) ?: emptyList()
    }.getOrElse { emptyList() }

    /** 중복 시 맨 앞으로 이동 (move-to-front), 최대 5개 유지 */
    fun addRecentSearch(context: Context, stock: StockItem) {
        val list = loadRecentSearches(context).toMutableList()
        list.removeAll { it.ticker == stock.ticker }
        list.add(0, stock)
        prefs(context).edit()
            .putString(KEY_RECENT, gson.toJson(list.take(MAX_RECENT)))
            .apply()
    }

    fun removeRecentSearch(context: Context, ticker: String) {
        val list = loadRecentSearches(context).filter { it.ticker != ticker }
        prefs(context).edit().putString(KEY_RECENT, gson.toJson(list)).apply()
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun masterFile(context: Context) = File(context.filesDir, FILE_NAME)
}
