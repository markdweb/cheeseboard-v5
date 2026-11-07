package com.colorgrid.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

private const val MP = ViewGroup.LayoutParams.MATCH_PARENT
private const val WC = ViewGroup.LayoutParams.WRAP_CONTENT

private val LETTER_ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
private val SYM1 = listOf("@#\$_&-+()/", "*\"':;!?~`|", "%=[]{}\\")
private val SYM2 = listOf("€£¥¢°©®™✓•", "^<>±÷×¶§√π", "…«»¿¡≠≈")

private val EMOJIS = (
    "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 " +
    "🤐 😐 😑 😶 😏 😒 🙄 😬 😮 😯 😲 😳 🥺 😢 😭 😤 😠 😡 🤬 😱 😨 😰 😥 😓 🤯 😴 😵 🤢 🤮 🥳 " +
    "😎 🤓 🧐 👍 👎 👌 ✌️ 🤞 🤟 🤘 👏 🙌 🙏 💪 👋 🤝 ☝️ 👈 👉 👆 👇 ✊ 👊 ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 " +
    "💔 💕 💖 💯 ✨ ⭐ 🌟 🔥 💥 🎉 🎊 🎁 🎨 🖌️ 🌈 ☀️ 🌙 ⚡ ❄️ 🌸 🌹 🌻 🍀 🍎 🍕 🍔 🍟 🍩 ☕ 🍺 " +
    "🐶 🐱 🐭 🐰 🦊 🐻 🐼 🐨 🦁 🐸 🐵 🐧 🦄 🐢 🐙 🚗 ✈️ 🚀 🏠 💡 📱 💻 📷 🎵 ✅ ❌ ⚠️ ❓ ❗ 💬"
    ).split(" ")

@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
class KeyboardView(
    context: Context,
    private val store: Store,
    private val host: KeyboardHost
) : LinearLayout(context) {

    private enum class Page { LETTERS, SYMBOLS1, SYMBOLS2, COLORS, CLIPBOARD, EMOJI, CALCULATOR }
    private enum class Shift { OFF, ON, LOCK }
    private enum class Style { LETTER, SPECIAL, ACCENT }

    private class Key(val cell: FrameLayout, val face: TextView)

    private val pal = Palette.resolve(context, store.themeMode)
    private val density = context.resources.displayMetrics.density
    private val cfg = context.resources.configuration
    private val landscape = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
    private val rowH = dp((if (landscape) 34f else 43f) * store.heightScale)
    private val keyScale = store.keyScale
    private val handler = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Suppress("DEPRECATION")
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private var page = Page.LETTERS
    private var shift = Shift.OFF
    private var lastShiftTap = 0L
    private var colorsFavTab = false
    private var calcExpression = StringBuilder()
    private var calcResult: String? = null
    private var repeatRunnable: Runnable? = null

    private val content = FrameLayout(context)
    private val toolButtons = HashMap<Page, View>()
    private val letterKeys = ArrayList<Pair<TextView, Char>>()
    private var shiftIcon: IconView? = null
    private var shiftFace: TextView? = null
    private var enterFace: TextView? = null
    private var enterIcon: IconView? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(pal.bg)
        setPadding(dp(4), 0, dp(4), 0)
        val toolbarH = dp(if (landscape) 36f else 44f)
        addView(buildToolbar(), LinearLayout.LayoutParams(MP, toolbarH))
        addView(content, LinearLayout.LayoutParams(MP, rowH * 5 + dp(8)))
        if (Build.VERSION.SDK_INT >= 35) {
            // Android 15+: the keyboard window extends behind the navigation bar.
            setOnApplyWindowInsetsListener { v, insets ->
                val bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                v.setPadding(dp(4), 0, dp(4), bottom)
                insets
            }
        }
        showPage(Page.LETTERS)
    }

    // ---------- public API used by the service ----------

    fun onInputStarted(startOnSymbols: Boolean, caps: Boolean, restarting: Boolean) {
        if (!restarting) {
            colorsFavTab = false
            val target = if (startOnSymbols) Page.SYMBOLS1 else Page.LETTERS
            if (page != target) showPage(target)
        }
        if (!restarting || shift != Shift.LOCK) {
            shift = if (caps) Shift.ON else Shift.OFF
        }
        refreshShift()
        refreshEnter()
    }

    fun resetToLetters() {
        if (page != Page.LETTERS) showPage(Page.LETTERS)
    }

    override fun onDetachedFromWindow() {
        stopRepeat()
        super.onDetachedFromWindow()
    }

    // ---------- helpers ----------

    private fun dp(v: Float): Int = (v * density).roundToInt()
    private fun dp(v: Int): Int = (v * density).roundToInt()

    private fun rounded(color: Int, radiusDp: Float, strokeColor: Int? = null): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = dp(radiusDp).toFloat()
        d.setColor(color)
        if (strokeColor != null) d.setStroke(dp(1f), strokeColor)
        return d
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt()
    )

    private fun keyBg(style: Style, pressed: Boolean): GradientDrawable {
        val base = when (style) {
            Style.LETTER -> pal.key
            Style.SPECIAL -> pal.special
            Style.ACCENT -> pal.accent
        }
        val c = if (pressed) blend(base, if (pal.dark) Color.WHITE else Color.BLACK, 0.16f) else base
        return rounded(c, 9f)
    }

    private fun feedback(fx: Int) {
        if (store.sound) {
            val category = when (fx) {
                AudioManager.FX_KEYPRESS_SPACEBAR -> "space"
                AudioManager.FX_KEYPRESS_RETURN -> "enter"
                AudioManager.FX_KEYPRESS_DELETE -> "delete"
                else -> "letter"
            }
            KeyboardSounds.play(context, store.soundProfile, store.soundVolume, category)
        }
        if (store.vibrate) {
            val v = vibrator
            if (v != null && v.hasVibrator()) {
                v.vibrate(VibrationEffect.createOneShot(14, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    private fun pressAnim(v: View) {
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(60).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
            }
            false
        }
    }

    private fun startRepeat(action: () -> Unit) {
        stopRepeat()
        val r = object : Runnable {
            override fun run() {
                action()
                handler.postDelayed(this, 45)
            }
        }
        repeatRunnable = r
        handler.postDelayed(r, 380)
    }

    private fun stopRepeat() {
        repeatRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
    }

    // ---------- toolbar ----------

    private fun buildToolbar(): View {
        val bar = LinearLayout(context)
        bar.orientation = HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL

        fun add(cell: View) = bar.addView(cell, LinearLayout.LayoutParams(0, MP, 1f))

        add(toolCell(toolbarLabel("ABC"), WC, Page.LETTERS) { showPage(Page.LETTERS) })
        add(toolCell(toolbarLabel("CALCU"), WC, Page.CALCULATOR) { showPage(Page.CALCULATOR) })
        if (store.colorGridEnabled) {
            add(toolCell(IconView(context, IconView.Kind.GRID, pal.text), dp(24f), Page.COLORS) {
                togglePage(Page.COLORS)
            })
        }
        add(toolCell(emojiText("📋"), WC, Page.CLIPBOARD) { togglePage(Page.CLIPBOARD) })
        add(toolCell(emojiText("😀"), WC, Page.EMOJI) { togglePage(Page.EMOJI) })
        add(toolCell(emojiText("⚙️"), WC, null) { host.openApp(null) })
        add(toolCell(IconView(context, IconView.Kind.CHEVRON_DOWN, pal.text), dp(24f), null) {
            host.hideKeyboard()
        })
        return bar
    }

    private fun toolbarLabel(s: String): TextView {
        val t = TextView(context)
        t.text = s
        t.textSize = 12f
        t.typeface = Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER
        t.includeFontPadding = false
        t.setTextColor(pal.text)
        return t
    }

    private fun emojiText(s: String): TextView {
        val t = TextView(context)
        t.text = s
        t.textSize = 19f
        t.gravity = Gravity.CENTER
        t.includeFontPadding = false
        return t
    }

    private fun toolCell(contentView: View, size: Int, page: Page?, onClick: () -> Unit): View {
        val cell = FrameLayout(context)
        cell.setPadding(dp(3f), dp(4f), dp(3f), dp(4f))
        val pill = FrameLayout(context)
        pill.addView(contentView, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        cell.addView(pill, FrameLayout.LayoutParams(MP, MP))
        cell.setOnClickListener { onClick() }
        pressAnim(cell)
        if (page != null) toolButtons[page] = pill
        return cell
    }

    private fun togglePage(p: Page) {
        showPage(if (page == p) Page.LETTERS else p)
    }

    // ---------- page switching ----------

    private fun showPage(p: Page) {
        page = p
        stopRepeat()
        content.removeAllViews()
        letterKeys.clear()
        shiftIcon = null
        shiftFace = null
        enterFace = null
        enterIcon = null

        val v: View = when (p) {
            Page.LETTERS, Page.SYMBOLS1, Page.SYMBOLS2 -> buildKeys(p)
            Page.COLORS -> buildColorPanel()
            Page.CLIPBOARD -> buildClipboardPanel()
            Page.EMOJI -> buildEmojiPanel()
            Page.CALCULATOR -> buildCalculatorPanel()
        }
        content.setPadding(0, dp(4f), 0, dp(4f))
        content.layoutParams = content.layoutParams.apply {
            height = if (p == Page.CALCULATOR) dp(330f) else rowH * 5 + dp(8f)
        }
        // Keep page rendering immediate. Animating the whole keyboard content can
        // look like a flash/flicker when Android restarts the input view.
        content.addView(v, FrameLayout.LayoutParams(MP, MP))

        for ((k, view) in toolButtons) {
            view.background = if (k == p) rounded(pal.selected, 10f) else null
        }
        refreshShift()
        refreshEnter()
    }

    // ---------- typing keyboard ----------

    private fun newKey(
        label: String,
        weight: Float,
        style: Style,
        sizeSp: Float = 21f,
        repeat: Boolean = false,
        fx: Int = AudioManager.FX_KEYPRESS_STANDARD,
        action: () -> Unit
    ): Key {
        val cell = FrameLayout(context)
        val face = TextView(context)
        face.text = label
        face.gravity = Gravity.CENTER
        face.includeFontPadding = false
        face.typeface = Typeface.DEFAULT
        face.textSize = sizeSp * keyScale
        face.setTextColor(if (style == Style.ACCENT) pal.accentText else pal.text)
        face.background = keyBg(style, false)

        val m = (dp(3f) + (1f - keyScale) * dp(6f)).roundToInt().coerceAtLeast(dp(1f))
        val lp = FrameLayout.LayoutParams(MP, MP)
        lp.setMargins(m, m, m, m)
        cell.addView(face, lp)
        cell.layoutParams = LinearLayout.LayoutParams(0, MP, weight)

        cell.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    face.background = keyBg(style, true)
                    feedback(fx)
                    if (repeat) {
                        action()
                        startRepeat(action)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    face.background = keyBg(style, false)
                    stopRepeat()
                    val inside = ev.x >= 0f && ev.x <= v.width && ev.y >= 0f && ev.y <= v.height
                    if (!repeat && inside) action()
                }
                MotionEvent.ACTION_CANCEL -> {
                    face.background = keyBg(style, false)
                    stopRepeat()
                }
            }
            true
        }
        return Key(cell, face)
    }

    private fun addIcon(key: Key, icon: IconView) {
        key.cell.addView(icon, FrameLayout.LayoutParams(dp(24f), dp(24f), Gravity.CENTER))
    }

    private fun charKey(s: String): Key = newKey(s, 1f, Style.LETTER) { typeChar(s) }

    private fun letterKey(c: Char): Key {
        val key = newKey(c.toString(), 1f, Style.LETTER) {
            typeChar(if (shift == Shift.OFF) c.toString() else c.uppercaseChar().toString())
        }
        letterKeys.add(Pair(key.face, c))
        return key
    }

    private fun backspaceKey(): Key {
        val key = newKey("", 1.5f, Style.SPECIAL, repeat = true, fx = AudioManager.FX_KEYPRESS_DELETE) {
            host.backspace()
            afterTyped()
        }
        addIcon(key, IconView(context, IconView.Kind.BACKSPACE, pal.text))
        return key
    }

    private fun typeChar(s: String) {
        host.typeText(s)
        afterTyped()
    }

    private fun afterTyped() {
        updateAutoShift(host.cursorCaps())
    }

    private fun updateAutoShift(caps: Boolean) {
        if (shift == Shift.LOCK) return
        val next = if (caps) Shift.ON else Shift.OFF
        if (next != shift) {
            shift = next
            refreshShift()
        }
    }

    private fun onShiftTap() {
        val now = System.currentTimeMillis()
        shift = when {
            shift == Shift.LOCK -> Shift.OFF
            shift == Shift.ON && now - lastShiftTap < 350 -> Shift.LOCK
            shift == Shift.ON -> Shift.OFF
            else -> Shift.ON
        }
        lastShiftTap = now
        refreshShift()
    }

    private fun refreshShift() {
        for ((tv, c) in letterKeys) {
            tv.text = if (shift == Shift.OFF) c.toString() else c.uppercaseChar().toString()
        }
        shiftIcon?.let {
            it.filled = shift != Shift.OFF
            it.locked = shift == Shift.LOCK
            it.tint = if (shift != Shift.OFF) pal.accentText else pal.text
            it.invalidate()
        }
        shiftFace?.background = keyBg(if (shift == Shift.OFF) Style.SPECIAL else Style.ACCENT, false)
    }

    private fun refreshEnter() {
        val face = enterFace ?: return
        val icon = enterIcon ?: return
        val label = host.enterLabel()
        if (label == null) {
            face.text = ""
            icon.visibility = View.VISIBLE
        } else {
            face.text = label
            face.textSize = 15f * keyScale
            icon.visibility = View.GONE
        }
    }

    private fun buildKeys(p: Page): View {
        val col = LinearLayout(context)
        col.orientation = VERTICAL
        val letters = p == Page.LETTERS

        fun addRow(keys: List<Key>, spacerWeight: Float = 0f) {
            val row = LinearLayout(context)
            row.orientation = HORIZONTAL
            if (spacerWeight > 0f) row.addView(View(context), LinearLayout.LayoutParams(0, MP, spacerWeight))
            keys.forEach { row.addView(it.cell) }
            if (spacerWeight > 0f) row.addView(View(context), LinearLayout.LayoutParams(0, MP, spacerWeight))
            col.addView(row, LinearLayout.LayoutParams(MP, rowH))
        }

        addRow("1234567890".map { charKey(it.toString()) })

        if (letters) {
            addRow(LETTER_ROWS[0].map { letterKey(it) })
            addRow(LETTER_ROWS[1].map { letterKey(it) }, 0.5f)
            val shiftKey = newKey("", 1.5f, Style.SPECIAL) { onShiftTap() }
            val sIcon = IconView(context, IconView.Kind.SHIFT, pal.text)
            addIcon(shiftKey, sIcon)
            shiftIcon = sIcon
            shiftFace = shiftKey.face
            addRow(listOf(shiftKey) + LETTER_ROWS[2].map { letterKey(it) } + listOf(backspaceKey()))
        } else {
            val sym = if (p == Page.SYMBOLS2) SYM2 else SYM1
            addRow(sym[0].map { charKey(it.toString()) })
            addRow(sym[1].map { charKey(it.toString()) })
            val toggle = newKey(
                if (p == Page.SYMBOLS1) "=\\<" else "?123", 1.5f, Style.SPECIAL, sizeSp = 15f
            ) { showPage(if (p == Page.SYMBOLS1) Page.SYMBOLS2 else Page.SYMBOLS1) }
            addRow(listOf(toggle) + sym[2].map { charKey(it.toString()) } + listOf(backspaceKey()))
        }

        val modeKey = newKey(if (letters) "?123" else "ABC", 1.5f, Style.SPECIAL, sizeSp = 15f) {
            showPage(if (letters) Page.SYMBOLS1 else Page.LETTERS)
        }
        val globe = newKey("🌐", 1f, Style.SPECIAL, sizeSp = 18f) { host.switchKeyboard() }
        val comma = charKey(",")
        val space = newKey("Color Grid", 4f, Style.LETTER, sizeSp = 13f, fx = AudioManager.FX_KEYPRESS_SPACEBAR) {
            typeChar(" ")
        }
        space.face.setTextColor(pal.subtext)
        val period = charKey(".")
        val enter = newKey("", 1.5f, Style.ACCENT, sizeSp = 15f, fx = AudioManager.FX_KEYPRESS_RETURN) {
            host.enter()
            afterTyped()
        }
        val eIcon = IconView(context, IconView.Kind.ENTER, pal.accentText)
        addIcon(enter, eIcon)
        enterFace = enter.face
        enterIcon = eIcon
        addRow(listOf(modeKey, globe, comma, space, period, enter))
        return col
    }

    // ---------- shared panel widgets ----------

    private fun styleTab(tv: TextView, selected: Boolean) {
        tv.background = rounded(if (selected) pal.accent else pal.chip, 16f)
        tv.setTextColor(if (selected) pal.accentText else pal.text)
    }

    private fun pill(label: String, selected: Boolean = false, onClick: () -> Unit): TextView {
        val tv = TextView(context)
        tv.text = label
        tv.textSize = 13.5f
        tv.gravity = Gravity.CENTER
        tv.includeFontPadding = false
        tv.setPadding(dp(14f), 0, dp(14f), 0)
        styleTab(tv, selected)
        tv.setOnClickListener { onClick() }
        pressAnim(tv)
        return tv
    }

    private fun headerLp(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(WC, MP)
        lp.setMargins(dp(3f), dp(5f), dp(3f), dp(5f))
        return lp
    }

    private fun headerRow(): LinearLayout {
        val h = LinearLayout(context)
        h.orientation = HORIZONTAL
        h.gravity = Gravity.CENTER_VERTICAL
        return h
    }

    private fun panelRoot(): LinearLayout {
        val r = LinearLayout(context)
        r.orientation = VERTICAL
        r.setPadding(dp(6f), 0, dp(6f), 0)
        return r
    }

    private fun emptyText(msg: String): TextView {
        val t = TextView(context)
        t.text = msg
        t.textSize = 14f
        t.gravity = Gravity.CENTER
        t.setTextColor(pal.subtext)
        t.setPadding(dp(24f), dp(28f), dp(24f), dp(28f))
        return t
    }

    // ---------- Color Grid panel ----------

    private fun colorChip(item: ColorItem, onClick: () -> Unit, onLong: () -> Unit): View {
        val chip = LinearLayout(context)
        chip.orientation = HORIZONTAL
        chip.gravity = Gravity.CENTER_VERTICAL
        chip.background = rounded(pal.chip, 12f)
        chip.setPadding(dp(10f), 0, dp(10f), 0)

        val swatch = View(context)
        val sd = GradientDrawable()
        sd.shape = GradientDrawable.OVAL
        sd.setColor(item.argb)
        sd.setStroke(dp(1.5f), pal.stroke)
        swatch.background = sd
        chip.addView(swatch, LinearLayout.LayoutParams(dp(24f), dp(24f)))

        val name = TextView(context)
        name.text = item.name
        name.textSize = 14.5f
        name.maxLines = 1
        name.ellipsize = android.text.TextUtils.TruncateAt.END
        name.setTextColor(pal.text)
        name.setPadding(dp(10f), 0, 0, 0)
        chip.addView(name, LinearLayout.LayoutParams(0, WC, 1f))

        if (item.favorite) {
            val star = TextView(context)
            star.text = "★"
            star.textSize = 13f
            star.setTextColor(pal.accent)
            chip.addView(star, LinearLayout.LayoutParams(WC, WC))
        }

        chip.setOnClickListener { onClick() }
        chip.setOnLongClickListener {
            onLong()
            true
        }
        pressAnim(chip)
        return chip
    }

    private fun buildColorPanel(): View {
        val root = panelRoot()
        val grid = LinearLayout(context)
        grid.orientation = VERTICAL
        val scroll = ScrollView(context)
        scroll.addView(grid)
        var colors = store.loadColors()
        val cols = (cfg.screenWidthDp / 125).coerceIn(3, 6)

        fun fill() {
            grid.removeAllViews()
            val shown = if (colorsFavTab) colors.filter { it.favorite } else colors
            if (shown.isEmpty()) {
                grid.addView(
                    emptyText(
                        if (colorsFavTab) "No favorites yet.\nLong-press any color to add it here."
                        else "No colors yet.\nTap Manage to add some."
                    )
                )
                return
            }
            for (rowItems in shown.chunked(cols)) {
                val row = LinearLayout(context)
                row.orientation = HORIZONTAL
                for (item in rowItems) {
                    val lp = LinearLayout.LayoutParams(0, dp(46f), 1f)
                    lp.setMargins(dp(2f), dp(2f), dp(2f), dp(2f))
                    row.addView(
                        colorChip(
                            item,
                            onClick = {
                                val ok = host.insertColor(item)
                                if (ok) {
                                    showPage(Page.LETTERS)
                                    if (store.autoHide) host.hideKeyboard()
                                }
                            },
                            onLong = {
                                store.toggleFavorite(item.id)
                                colors = store.loadColors()
                                fill()
                            }
                        ),
                        lp
                    )
                }
                repeat(cols - rowItems.size) {
                    val lp = LinearLayout.LayoutParams(0, dp(46f), 1f)
                    lp.setMargins(dp(2f), dp(2f), dp(2f), dp(2f))
                    row.addView(View(context), lp)
                }
                grid.addView(row, LinearLayout.LayoutParams(MP, WC))
            }
        }

        val header = headerRow()
        val tabAll = pill("All", !colorsFavTab) {}
        val tabFav = pill("★ Favorites", colorsFavTab) {}
        tabAll.setOnClickListener {
            colorsFavTab = false
            styleTab(tabAll, true)
            styleTab(tabFav, false)
            fill()
        }
        tabFav.setOnClickListener {
            colorsFavTab = true
            styleTab(tabAll, false)
            styleTab(tabFav, true)
            fill()
        }
        header.addView(pill("ABC") { showPage(Page.LETTERS) }, headerLp())
        header.addView(tabAll, headerLp())
        header.addView(tabFav, headerLp())
        header.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(pill("Manage") { host.openApp("colors") }, headerLp())

        root.addView(header, LinearLayout.LayoutParams(MP, dp(40f)))
        root.addView(scroll, LinearLayout.LayoutParams(MP, 0, 1f))
        fill()
        return root
    }

    // ---------- Calculator panel ----------

    private fun buildCalculatorPanel(): View {
        val root = panelRoot()
        root.setPadding(dp(6f), dp(2f), dp(6f), dp(4f))

        // Header: title only, with a close button on the right.
        val header = headerRow()
        val title = TextView(context).apply {
            text = "Calculator"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(pal.text)
        }
        val close = TextView(context).apply {
            text = "×"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(pal.subtext)
            background = rounded(pal.chip, 24f)
            setOnClickListener { showPage(Page.LETTERS) }
        }
        header.addView(View(context), LinearLayout.LayoutParams(dp(42f), MP))
        header.addView(title, LinearLayout.LayoutParams(0, MP, 1f))
        header.addView(close, LinearLayout.LayoutParams(dp(42f), dp(42f)))
        root.addView(header, LinearLayout.LayoutParams(MP, dp(36f)))

        // Compact two-line display: expression preview above, green total below.
        val displayBox = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(blend(pal.bg, Color.BLACK, if (pal.dark) 0.18f else 0.04f), 14f, pal.stroke)
            setPadding(dp(14f), dp(5f), dp(14f), dp(5f))
        }
        val preview = TextView(context).apply {
            text = calcExpression.toString().ifEmpty { " " }
            textSize = 15f
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.START
            setTextColor(pal.subtext)
        }
        val display = TextView(context).apply {
            text = calcResult ?: "0"
            textSize = 30f
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(Color.rgb(76, 175, 112))
            typeface = Typeface.DEFAULT_BOLD
        }
        displayBox.addView(preview, LinearLayout.LayoutParams(MP, 0, 0.8f))
        displayBox.addView(display, LinearLayout.LayoutParams(MP, 0, 1.2f))
        root.addView(displayBox, LinearLayout.LayoutParams(MP, dp(60f)).apply {
            setMargins(dp(2f), dp(2f), dp(2f), dp(3f))
        })

        fun refresh() {
            preview.text = calcExpression.toString().ifEmpty { " " }
            display.text = calcResult ?: evaluateCalculator(calcExpression.toString()) ?: "0"
            display.setTextColor(Color.rgb(76, 175, 112))
        }
        val rows = listOf(
            listOf("⌫", "AC", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("00", "0", ".", "=")
        )
        for (items in rows) {
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (label in items) {
                val utility = label in setOf("⌫", "AC")
                val op = label in setOf("%", "÷", "×", "−", "+", "=")
                val key = TextView(context).apply {
                    text = label
                    textSize = if (label == "AC") 17f else 19f
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setTextColor(when {
                        label == "AC" || label == "⌫" -> Color.rgb(242, 120, 130)
                        op -> pal.accentText
                        else -> pal.text
                    })
                    background = rounded(when {
                        utility -> blend(pal.key, Color.RED, 0.18f)
                        op -> pal.accent
                        else -> pal.key
                    }, 13f)
                    setOnClickListener {
                        when (label) {
                            "AC" -> { calcExpression.clear(); calcResult = null }
                            "⌫" -> {
                                if (calcResult != null) calcResult = null
                                else if (calcExpression.isNotEmpty()) calcExpression.deleteCharAt(calcExpression.lastIndex)
                            }
                            "=" -> {
                                val value = evaluateCalculator(calcExpression.toString())
                                calcResult = value ?: "Error"
                                if (value != null) calcExpression = StringBuilder(value)
                            }
                            else -> {
                                if (calcResult != null) {
                                    if (label !in setOf("+", "−", "×", "÷")) calcExpression.clear()
                                    calcResult = null
                                }
                                val token = when (label) {
                                    "÷" -> "/"; "×" -> "*"; "−" -> "-"; "00" -> "00"; else -> label
                                }
                                if (token == ".") {
                                    val start = calcExpression.indexOfLast { it in "+-*/" } + 1
                                    val current = calcExpression.substring(start)
                                    if ("." in current) return@setOnClickListener
                                    if (current.isEmpty()) calcExpression.append("0")
                                }
                                if (token == "%") {
                                    if (calcExpression.isNotEmpty() && calcExpression.last().isDigit()) calcExpression.append('%')
                                } else if (token in listOf("+", "-", "*", "/")) {
                                    if (calcExpression.isEmpty() && token != "-") return@setOnClickListener
                                    if (calcExpression.isNotEmpty() && calcExpression.last() in "+-*/") calcExpression.setCharAt(calcExpression.lastIndex, token[0])
                                    else calcExpression.append(token)
                                } else calcExpression.append(token)
                            }
                        }
                        refresh()
                    }
                    setOnTouchListener { v, ev ->
                        if (ev.actionMasked == MotionEvent.ACTION_DOWN) v.alpha = 0.72f
                        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) v.alpha = 1f
                        false
                    }
                }
                row.addView(key, LinearLayout.LayoutParams(0, MP, 1f).apply {
                    setMargins(dp(2f), dp(2f), dp(2f), dp(2f))
                })
            }
            root.addView(row, LinearLayout.LayoutParams(MP, 0, 1f))
        }

        val copy = TextView(context).apply {
            text = "Copy"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(pal.accentText)
            background = rounded(pal.accent, 16f)
            setOnClickListener { copyCalculatorResult() }
        }
        root.addView(copy, LinearLayout.LayoutParams(MP, dp(42f)).apply {
            setMargins(dp(2f), dp(4f), dp(2f), dp(1f))
        })
        return root
    }

    private fun copyCalculatorResult() {
        val value = calcResult?.takeIf { it != "Error" }
            ?: evaluateCalculator(calcExpression.toString())
            ?: calcExpression.toString().takeIf { it.matches(Regex("\\d+(\\.\\d+)?")) }
        if (value.isNullOrBlank()) {
            android.widget.Toast.makeText(context, "Enter a valid calculation first", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (host.insertCalculatorResult(value)) {
            calcExpression = StringBuilder()
            calcResult = null
            showPage(Page.LETTERS)
            host.hideKeyboard()
        }
    }

    private fun evaluateCalculator(source: String): String? {
        if (source.isBlank()) return null
        return try {
            class Parser(private val input: String) {
                var pos = 0
                fun expression(): Double {
                    var value = term()
                    while (true) {
                        skip(); if (pos >= input.length) return value
                        when (input[pos]) {
                            '+' -> { pos++; value += term() }
                            '-' -> { pos++; value -= term() }
                            else -> return value
                        }
                    }
                }
                fun term(): Double {
                    var value = factor()
                    while (true) {
                        skip(); if (pos >= input.length) return value
                        when (input[pos]) {
                            '*' -> { pos++; value *= factor() }
                            '/' -> { pos++; val d = factor(); if (d == 0.0) throw ArithmeticException(); value /= d }
                            else -> return value
                        }
                    }
                }
                fun factor(): Double {
                    skip()
                    if (pos < input.length && (input[pos] == '+' || input[pos] == '-')) {
                        val sign = input[pos++]; val n = factor(); return if (sign == '-') -n else n
                    }
                    val start = pos
                    while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
                    if (start == pos) throw IllegalArgumentException()
                    var value = input.substring(start, pos).toDouble()
                    if (pos < input.length && input[pos] == '%') { pos++; value /= 100.0 }
                    return value
                }
                fun skip() { while (pos < input.length && input[pos].isWhitespace()) pos++ }
            }
            val parser = Parser(source)
            val number = parser.expression()
            parser.skip()
            if (parser.pos != source.length || !number.isFinite()) null
            else if (number == number.toLong().toDouble()) number.toLong().toString()
            else number.toBigDecimal().stripTrailingZeros().toPlainString()
        } catch (_: Exception) { null }
    }

    // ---------- Clipboard panel ----------

    private fun buildClipboardPanel(): View {
        val root = panelRoot()
        val list = LinearLayout(context)
        list.orientation = VERTICAL
        val scroll = ScrollView(context)
        scroll.addView(list)

        fun smallButton(label: String, alpha: Float, onClick: () -> Unit): TextView {
            val b = TextView(context)
            b.text = label
            b.textSize = 15f
            b.gravity = Gravity.CENTER
            b.alpha = alpha
            b.setTextColor(pal.text)
            b.setOnClickListener { onClick() }
            return b
        }

        fun fill() {
            list.removeAllViews()
            val entries = store.sortedHistory()
            if (entries.isEmpty()) {
                list.addView(emptyText("Nothing here yet.\nColors you tap and text you copy will appear here."))
                return
            }
            for (e in entries) {
                val row = LinearLayout(context)
                row.orientation = HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.background = rounded(pal.chip, 12f)
                row.setPadding(dp(12f), 0, dp(4f), 0)

                val t = TextView(context)
                t.text = e.text
                t.textSize = 14f
                t.maxLines = 2
                t.ellipsize = android.text.TextUtils.TruncateAt.END
                t.setTextColor(pal.text)
                row.addView(t, LinearLayout.LayoutParams(0, WC, 1f))

                row.addView(
                    smallButton("📌", if (e.pinned) 1f else 0.3f) {
                        store.togglePin(e.text)
                        fill()
                    },
                    LinearLayout.LayoutParams(dp(40f), dp(40f))
                )
                row.addView(
                    smallButton("✕", 0.6f) {
                        store.removeHistory(e.text)
                        fill()
                    },
                    LinearLayout.LayoutParams(dp(40f), dp(40f))
                )

                row.setOnClickListener {
                    if (host.insertClip(e.text)) showPage(Page.LETTERS)
                }
                pressAnim(row)
                val lp = LinearLayout.LayoutParams(MP, dp(48f))
                lp.setMargins(dp(2f), dp(3f), dp(2f), dp(3f))
                list.addView(row, lp)
            }
        }

        val header = headerRow()
        header.addView(pill("ABC") { showPage(Page.LETTERS) }, headerLp())
        val title = TextView(context)
        title.text = "Clipboard"
        title.textSize = 15f
        title.typeface = Typeface.DEFAULT_BOLD
        title.gravity = Gravity.CENTER
        title.setTextColor(pal.text)
        header.addView(title, LinearLayout.LayoutParams(0, MP, 1f))
        header.addView(pill("Clear") {
            store.clearHistory(true)
            fill()
        }, headerLp())

        root.addView(header, LinearLayout.LayoutParams(MP, dp(40f)))
        root.addView(scroll, LinearLayout.LayoutParams(MP, 0, 1f))
        fill()
        return root
    }

    // ---------- Emoji panel ----------

    private fun buildEmojiPanel(): View {
        val root = panelRoot()
        val header = headerRow()
        header.addView(pill("ABC") { showPage(Page.LETTERS) }, headerLp())
        header.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        val bk = backspaceKey(); bk.cell.layoutParams = LinearLayout.LayoutParams(dp(48f), MP); header.addView(bk.cell)
        val search = android.widget.EditText(context).apply {
            hint = "Search emoji"; textSize = 14f; setSingleLine(true)
            setTextColor(pal.text); setHintTextColor(pal.subtext)
            background = rounded(pal.chip, 12f); setPadding(dp(12f), 0, dp(12f), 0)
        }
        val categories = listOf("Recent" to "", "Smileys" to "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😍 🥰 😘 🙂 😉 🤔 😎 😭 😡 🥳", "People" to "👍 👎 👌 ✌️ 🤞 👏 🙌 🙏 💪 👋 🤝 🫶", "Animals" to "🐶 🐱 🐭 🐰 🦊 🐻 🐼 🐨 🦁 🐸 🐵 🐧 🦄 🐢 🐙", "Food" to "🍎 🍊 🍋 🍉 🍇 🍓 🍒 🍑 🥑 🍕 🍔 🍟 🍩 🍪 ☕", "Travel" to "🚗 🚕 🚌 🚎 ✈️ 🚀 🚲 🚢 🏠 🏖️ 🌋", "Objects" to "📱 💻 ⌚ 📷 🎧 💡 📚 🎁 ⚽ 🎮", "Symbols" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 💔 ✨ ⭐ 🔥 💯", "Flags" to "🇵🇭 🇺🇸 🇬🇧 🇯🇵 🇰🇷 🇨🇦 🇦🇺")
        var selected = "Smileys"
        val chips = LinearLayout(context).apply { orientation = HORIZONTAL }
        val chipScroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(chips) }
        val list = LinearLayout(context).apply { orientation = VERTICAL }
        val scroll = ScrollView(context); scroll.addView(list)
        val recent = mutableListOf<String>()
        fun render(filter: String = "") {
            list.removeAllViews()
            val source = if (filter.isNotBlank()) categories.flatMap { it.second.split(" ") }.distinct().filter { it.contains(filter) }
                else if (selected == "Recent") recent else categories.firstOrNull { it.first == selected }?.second?.split(" ").orEmpty()
            val cols = (cfg.screenWidthDp / 44).coerceIn(6, 12)
            for (chunk in source.chunked(cols)) {
                val row = LinearLayout(context).apply { orientation = HORIZONTAL }
                chunk.forEach { e ->
                    val t = TextView(context).apply { text=e; textSize=24f; gravity=Gravity.CENTER; includeFontPadding=false
                        setOnClickListener { feedback(AudioManager.FX_KEYPRESS_STANDARD); host.typeText(e); recent.remove(e); recent.add(0,e); if (recent.size>30) recent.removeAt(recent.lastIndex) } }
                    row.addView(t, LinearLayout.LayoutParams(0, dp(43f), 1f))
                }
                repeat(cols-chunk.size) { row.addView(View(context), LinearLayout.LayoutParams(0, dp(43f), 1f)) }
                list.addView(row)
            }
        }
        fun rebuildCategoryChips() {
            chips.removeAllViews()
            categories.forEach { cat ->
                chips.addView(pill(cat.first, cat.first == selected) {
                    selected = cat.first
                    rebuildCategoryChips()
                    render()
                })
            }
        }
        rebuildCategoryChips()
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        root.addView(header, LinearLayout.LayoutParams(MP, dp(38f)))
        root.addView(search, LinearLayout.LayoutParams(MP, dp(38f)))
        root.addView(chipScroll, LinearLayout.LayoutParams(MP, dp(38f)))
        root.addView(scroll, LinearLayout.LayoutParams(MP, 0, 1f))
        render()
        return root
    }

}
