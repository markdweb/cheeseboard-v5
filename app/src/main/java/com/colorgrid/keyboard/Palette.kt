package com.colorgrid.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

class Palette(
    val dark: Boolean,
    val bg: Int,
    val key: Int,
    val special: Int,
    val text: Int,
    val subtext: Int,
    val accent: Int,
    val accentText: Int,
    val chip: Int,
    val selected: Int,
    val stroke: Int
) {
    companion object {
        private fun c(s: String) = Color.parseColor(s)

        fun isDark(context: Context, mode: Int): Boolean = when (mode) {
            1 -> false
            2 -> true
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }

        fun resolve(context: Context, mode: Int): Palette =
            if (isDark(context, mode)) {
                Palette(
                    dark = true, bg = c("#1B1D23"), key = c("#343842"), special = c("#252830"),
                    text = c("#EEF1F6"), subtext = c("#9AA1AE"), accent = c("#6F8FFF"),
                    accentText = c("#FFFFFF"), chip = c("#2B2E37"), selected = c("#3A4260"),
                    stroke = c("#6A7080")
                )
            } else {
                Palette(
                    dark = false, bg = c("#E9ECF2"), key = c("#FFFFFF"), special = c("#CDD3DE"),
                    text = c("#1C2029"), subtext = c("#6B7280"), accent = c("#3F6BFF"),
                    accentText = c("#FFFFFF"), chip = c("#FFFFFF"), selected = c("#D3DDFF"),
                    stroke = c("#B6BDCB")
                )
            }
    }
}
