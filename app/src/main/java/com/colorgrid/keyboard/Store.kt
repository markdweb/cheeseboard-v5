package com.colorgrid.keyboard

import android.content.Context
import android.content.res.Configuration
import org.json.JSONArray

/** All persistent data (settings, colors, clipboard history), shared by the keyboard and the app UI. */
class Store(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("colorgrid_prefs", Context.MODE_PRIVATE)

    // ---------- settings ----------

    var colorGridEnabled: Boolean
        get() = sp.getBoolean("color_grid_enabled", true)
        set(v) { sp.edit().putBoolean("color_grid_enabled", v).apply() }

    /** 0 = follow system, 1 = light, 2 = dark */
    var themeMode: Int
        get() = sp.getInt("theme_mode", 0)
        set(v) { sp.edit().putInt("theme_mode", v).apply() }

    var heightScale: Float
        get() = sp.getFloat("height_scale", 1.0f)
        set(v) { sp.edit().putFloat("height_scale", v).apply() }

    var keyScale: Float
        get() = sp.getFloat("key_scale", 1.0f)
        set(v) { sp.edit().putFloat("key_scale", v).apply() }

    var sound: Boolean
        get() = sp.getBoolean("sound", false)
        set(v) { sp.edit().putBoolean("sound", v).apply() }

    /** Built-in synthesized sound profile; no external audio assets or network access. */
    var soundProfile: String
        get() = when (sp.getString("sound_profile", "cherry_brown_abs")) {
            "deep_thock", "topre" -> "topre_purple"
            "crisp_clicky" -> "cherry_blue_abs"
            "typewriter" -> "cherry_brown_abs"
            "smooth_linear" -> "cherry_red_abs"
            else -> sp.getString("sound_profile", "cherry_brown_abs") ?: "cherry_brown_abs"
        }
        set(v) { sp.edit().putString("sound_profile", v).apply() }

    var soundVolume: Int
        get() = sp.getInt("sound_volume", 65).coerceIn(0, 100)
        set(v) { sp.edit().putInt("sound_volume", v.coerceIn(0, 100)).apply() }

    var vibrate: Boolean
        get() = sp.getBoolean("vibrate", true)
        set(v) { sp.edit().putBoolean("vibrate", v).apply() }

    var autoHide: Boolean
        get() = sp.getBoolean("auto_hide", true)
        set(v) { sp.edit().putBoolean("auto_hide", v).apply() }

    var historyEnabled: Boolean
        get() = sp.getBoolean("history_enabled", true)
        set(v) { sp.edit().putBoolean("history_enabled", v).apply() }

    /** Also record text copied in other apps (only possible while this keyboard is active). */
    var captureExternal: Boolean
        get() = sp.getBoolean("capture_external", true)
        set(v) { sp.edit().putBoolean("capture_external", v).apply() }

    var historyLimit: Int
        get() = sp.getInt("history_limit", 25)
        set(v) { sp.edit().putInt("history_limit", v).apply() }

    /** Changes whenever the keyboard view must be rebuilt. */
    fun signature(ctx: Context): String {
        val c = ctx.resources.configuration
        return listOf(
            themeMode, heightScale, keyScale, colorGridEnabled,
            c.orientation, c.uiMode and Configuration.UI_MODE_NIGHT_MASK, c.screenWidthDp
        ).joinToString("|")
    }

    // ---------- colors ----------

    fun loadColors(): List<ColorItem> {
        val raw = sp.getString("colors", null) ?: return Defaults.colors
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { ColorItem.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            Defaults.colors
        }
    }

    fun saveColors(list: List<ColorItem>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString("colors", arr.toString()).apply()
    }

    fun toggleFavorite(id: String) {
        saveColors(loadColors().map { if (it.id == id) it.copy(favorite = !it.favorite) else it })
    }

    // ---------- clipboard history (stored newest first) ----------

    private fun loadHistory(): MutableList<ClipEntry> {
        val raw = sp.getString("history", null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { ClipEntry.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun saveHistory(list: List<ClipEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString("history", arr.toString()).apply()
    }

    /** Pinned entries first, then newest first. */
    fun sortedHistory(): List<ClipEntry> = loadHistory().sortedByDescending { it.pinned }

    fun addHistory(text: String) {
        val clean = text.trim()
        if (!historyEnabled || clean.isEmpty()) return
        val list = loadHistory()
        val existing = list.firstOrNull { it.text == clean }
        list.removeAll { it.text == clean }
        list.add(0, ClipEntry(clean, existing?.pinned ?: false, System.currentTimeMillis()))
        var unpinned = list.count { !it.pinned }
        while (unpinned > historyLimit) {
            val idx = list.indexOfLast { !it.pinned }
            if (idx < 0) break
            list.removeAt(idx)
            unpinned--
        }
        saveHistory(list)
    }

    fun togglePin(text: String) {
        saveHistory(loadHistory().map { if (it.text == text) it.copy(pinned = !it.pinned) else it })
    }

    fun removeHistory(text: String) {
        saveHistory(loadHistory().filter { it.text != text })
    }

    fun clearHistory(keepPinned: Boolean) {
        saveHistory(if (keepPinned) loadHistory().filter { it.pinned } else emptyList())
    }
}
