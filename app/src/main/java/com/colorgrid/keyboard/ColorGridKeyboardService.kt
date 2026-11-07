package com.colorgrid.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast

class ColorGridKeyboardService : InputMethodService(), KeyboardHost {

    private lateinit var store: Store
    private lateinit var clipboard: ClipboardManager
    private var keyboardView: KeyboardView? = null
    private var viewSignature = ""

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClipboard() }

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        clipboard.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val view = KeyboardView(this, store, this)
        keyboardView = view
        viewSignature = store.signature(this)
        return view
    }

    // The extract (fullscreen) UI would cover the website in landscape; never use it.
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (store.signature(this) != viewSignature) {
            setInputView(onCreateInputView())
        }
        val cls = (info?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
        val symbols = cls == InputType.TYPE_CLASS_NUMBER ||
            cls == InputType.TYPE_CLASS_PHONE ||
            cls == InputType.TYPE_CLASS_DATETIME
        keyboardView?.onInputStarted(symbols, cursorCaps(), restarting)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        keyboardView?.resetToLetters()
    }

    // ---------- KeyboardHost ----------

    override fun typeText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    override fun backspace() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    override fun enter() {
        val action = enterAction()
        if (action != 0) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    override fun enterLabel(): String? = when (enterAction()) {
        EditorInfo.IME_ACTION_GO -> "Go"
        EditorInfo.IME_ACTION_SEARCH -> "Search"
        EditorInfo.IME_ACTION_SEND -> "Send"
        EditorInfo.IME_ACTION_NEXT -> "Next"
        EditorInfo.IME_ACTION_DONE -> "Done"
        EditorInfo.IME_ACTION_PREVIOUS -> "Prev"
        else -> null
    }

    private fun enterAction(): Int {
        val ei = currentInputEditorInfo ?: return 0
        if ((ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return 0
        if ((ei.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) return 0
        val action = ei.imeOptions and EditorInfo.IME_MASK_ACTION
        return if (action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) 0 else action
    }

    override fun cursorCaps(): Boolean {
        val ei = currentInputEditorInfo ?: return false
        if ((ei.inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        val mode = currentInputConnection?.getCursorCapsMode(ei.inputType) ?: 0
        return mode != 0
    }

    override fun switchKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }

    override fun hideKeyboard() {
        requestHideSelf(0)
    }

    override fun openApp(tab: String?) {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (tab != null) intent.putExtra("tab", tab)
        startActivity(intent)
    }

    override fun insertColor(item: ColorItem): Boolean {
        val text = item.insertText
        // 1) always copy, so the fallback "paste manually" path works
        copyToClipboard(text)
        // 2) keep keyboard clipboard history up to date
        store.addHistory(text)
        // 3) insert straight into the focused field
        val ok = try {
            currentInputConnection?.commitText(text, 1) == true
        } catch (e: Exception) {
            false
        }
        if (!ok) {
            Toast.makeText(
                this,
                "Couldn't insert automatically. \"$text\" is copied - paste it manually.",
                Toast.LENGTH_LONG
            ).show()
        }
        return ok
    }

    override fun insertCalculatorResult(text: String): Boolean {
        copyToClipboard(text)
        store.addHistory(text)
        val ok = try {
            currentInputConnection?.commitText(text, 1) == true
        } catch (e: Exception) {
            false
        }
        if (!ok) {
            Toast.makeText(this, "Couldn't insert. Result copied - paste it manually.", Toast.LENGTH_LONG).show()
        }
        return ok
    }

    override fun insertClip(text: String): Boolean {
        val ok = try {
            currentInputConnection?.commitText(text, 1) == true
        } catch (e: Exception) {
            false
        }
        if (!ok) {
            copyToClipboard(text)
            Toast.makeText(this, "Couldn't insert. Copied - paste it manually.", Toast.LENGTH_LONG).show()
        }
        return ok
    }

    // ---------- clipboard ----------

    private fun copyToClipboard(text: String) {
        try {
            clipboard.setPrimaryClip(ClipData.newPlainText("Color Grid", text))
        } catch (e: Exception) {
            // Some devices refuse clipboard writes in rare states; insertion still works.
        }
    }

    private fun captureClipboard() {
        if (!store.historyEnabled || !store.captureExternal) return
        try {
            val clip = clipboard.primaryClip ?: return
            if (clip.itemCount == 0) return
            val sensitive = clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
            if (sensitive) return
            val text = clip.getItemAt(0).coerceToText(this)?.toString()?.trim() ?: return
            if (text.isNotEmpty() && text.length <= 300) store.addHistory(text)
        } catch (e: Exception) {
            // Clipboard not readable right now; ignore.
        }
    }
}
