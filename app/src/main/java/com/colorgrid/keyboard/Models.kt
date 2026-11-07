package com.colorgrid.keyboard

import android.graphics.Color
import org.json.JSONObject
import java.util.UUID

/** A color shown in the Color Grid. [text] is what gets inserted; empty means "insert the name". */
data class ColorItem(
    val id: String,
    val name: String,
    val hex: String,
    val text: String = "",
    val favorite: Boolean = false
) {
    val insertText: String get() = if (text.isBlank()) name else text

    val argb: Int
        get() = try {
            Color.parseColor(hex)
        } catch (e: IllegalArgumentException) {
            Color.GRAY
        }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("hex", hex)
        .put("text", text)
        .put("fav", favorite)

    companion object {
        fun create(name: String, hex: String, text: String = ""): ColorItem =
            ColorItem(UUID.randomUUID().toString(), name, hex, text)

        fun fromJson(o: JSONObject): ColorItem = ColorItem(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.getString("name"),
            hex = o.getString("hex"),
            text = o.optString("text", ""),
            favorite = o.optBoolean("fav", false)
        )
    }
}

/** One entry of the keyboard's clipboard history. */
data class ClipEntry(val text: String, val pinned: Boolean, val time: Long) {
    fun toJson(): JSONObject = JSONObject()
        .put("text", text)
        .put("pinned", pinned)
        .put("time", time)

    companion object {
        fun fromJson(o: JSONObject): ClipEntry = ClipEntry(
            text = o.getString("text"),
            pinned = o.optBoolean("pinned", false),
            time = o.optLong("time", 0L)
        )
    }
}

object Hex {
    /** Accepts "#abc", "abc", "#aabbcc", "aabbcc" and returns "#AABBCC", or null when invalid. */
    fun normalize(raw: String): String? {
        var s = raw.trim().removePrefix("#")
        if (s.length == 3) s = s.map { "$it$it" }.joinToString("")
        if (s.length != 6) return null
        val ok = s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        return if (ok) "#" + s.uppercase() else null
    }
}

object Defaults {
    val colors: List<ColorItem> = listOf(
        ColorItem("default_red", "Red", "#E53935"),
        ColorItem("default_blue", "Blue", "#1E88E5"),
        ColorItem("default_green", "Green", "#43A047"),
        ColorItem("default_yellow", "Yellow", "#FDD835"),
        ColorItem("default_orange", "Orange", "#FB8C00"),
        ColorItem("default_purple", "Purple", "#8E24AA"),
        ColorItem("default_pink", "Pink", "#F06292"),
        ColorItem("default_brown", "Brown", "#6D4C41"),
        ColorItem("default_black", "Black", "#000000"),
        ColorItem("default_white", "White", "#FFFFFF"),
        ColorItem("default_gray", "Gray", "#9E9E9E"),
        ColorItem("default_cyan", "Cyan", "#00BCD4"),
        ColorItem("default_magenta", "Magenta", "#D81B60"),
        ColorItem("default_gold", "Gold", "#D4AF37"),
        ColorItem("default_silver", "Silver", "#BFC5CC"),
        ColorItem("default_navy", "Navy", "#1A237E"),
        ColorItem("default_teal", "Teal", "#00897B"),
        ColorItem("default_violet", "Violet", "#7E57C2"),
        ColorItem("default_maroon", "Maroon", "#800000"),
        ColorItem("default_lime", "Lime", "#AEEA00")
    )
}
