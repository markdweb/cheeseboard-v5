package com.colorgrid.keyboard

/** What the keyboard view needs from the InputMethodService. */
interface KeyboardHost {
    fun typeText(text: String)
    fun backspace()
    fun enter()
    /** Label for the enter key (e.g. "Go", "Search"), or null for the plain return icon. */
    fun enterLabel(): String?
    /** True when the cursor is at a position where the next letter should be capitalized. */
    fun cursorCaps(): Boolean
    fun switchKeyboard()
    fun hideKeyboard()
    fun openApp(tab: String?)
    /** Copies the color's text to the clipboard and inserts it. Returns true on successful insertion. */
    fun insertColor(item: ColorItem): Boolean
    fun insertClip(text: String): Boolean
    /** Copies a calculator result, inserts it into the focused field, and records it. */
    fun insertCalculatorResult(text: String): Boolean
}
