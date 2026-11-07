package com.colorgrid.keyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

data class TabRequest(val id: Int, val tab: String?)

class MainActivity : ComponentActivity() {

    private var tick by mutableIntStateOf(0)
    private var tabRequest by mutableStateOf(TabRequest(0, null))
    private lateinit var store: Store

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = Store(this)
        tabRequest = TabRequest(1, intent?.getStringExtra("tab"))
        setContent {
            ColorGridTheme {
                AppRoot(store, tick, tabRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        tabRequest = TabRequest(tabRequest.id + 1, intent.getStringExtra("tab"))
    }

    override fun onResume() {
        super.onResume()
        tick++
    }
}

@Composable
fun ColorGridTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF8FA8FF), onPrimary = Color(0xFF0B1330))
    } else {
        lightColorScheme(primary = Color(0xFF3F6BFF), onPrimary = Color.White)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun AppRoot(store: Store, tick: Int, request: TabRequest) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(request.id) {
        when (request.tab) {
            "colors" -> tab = 1
            "settings" -> tab = 2
        }
    }
    val items = listOf(
        "Setup" to Icons.Default.Home,
        "Colors" to Icons.Default.Edit,
        "Settings" to Icons.Default.Settings,
        "About" to Icons.Default.Info
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> SetupScreen(tick)
                1 -> ColorsScreen(store, tick)
                2 -> SettingsScreen(store) { tab = 1 }
                else -> AboutScreen()
            }
        }
    }
}

// ------------------------------------------------------------------ Setup

@Composable
fun SetupScreen(tick: Int) {
    val ctx = LocalContext.current
    val imm = remember { ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }
    var refresh by remember { mutableIntStateOf(0) }
    val enabled = remember(tick, refresh) {
        imm.enabledInputMethodList.any { it.packageName == ctx.packageName }
    }
    val selected = remember(tick, refresh) {
        android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith(ctx.packageName + "/") == true
    }
    var test by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Color Grid Keyboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Tap a color on the keyboard and its name is copied and typed into any text field - then the keyboard gets out of your way.",
            style = MaterialTheme.typography.bodyMedium
        )
        StepCard(
            1, "Enable the keyboard",
            "Turn on \"Color Grid Keyboard\" in the list of on-screen keyboards.",
            enabled, "Open keyboard settings"
        ) { ctx.startActivity(Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        StepCard(
            2, "Switch to Color Grid",
            "Pick Color Grid Keyboard as your active keyboard. You can switch back any time with the globe key.",
            selected, "Choose keyboard"
        ) { imm.showInputMethodPicker() }
        TextButton(onClick = { refresh++ }) { Text("Refresh status") }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Try it here", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Tap the field, open the Color Grid (the 4-square icon above the keys) and tap a color.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = test,
                    onValueChange = { test = it },
                    label = { Text("Test field") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun StepCard(n: Int, title: String, body: String, done: Boolean, button: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp).background(
                        if (done) Color(0xFF2E9D57) else MaterialTheme.colorScheme.primary, CircleShape
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    if (done) {
                        Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(18.dp))
                    } else {
                        Text("$n", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onClick) { Text(button) }
        }
    }
}

// ------------------------------------------------------------------ Colors

@Composable
fun ColorsScreen(store: Store, tick: Int) {
    var colors by remember(tick) { mutableStateOf(store.loadColors()) }
    var editing by remember { mutableStateOf<ColorItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ColorItem?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    fun commit(list: List<ColorItem>) {
        colors = list
        store.saveColors(list)
    }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target < 0 || target > colors.lastIndex) return
        val m = colors.toMutableList()
        val item = m.removeAt(index)
        m.add(target, item)
        commit(m)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Your colors", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${colors.size} colors - use the arrows to reorder",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = { adding = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(colors, key = { _, c -> c.id }) { index, c ->
                ColorRow(
                    item = c,
                    canUp = index > 0,
                    canDown = index < colors.lastIndex,
                    onFavorite = {
                        commit(colors.map { if (it.id == c.id) it.copy(favorite = !it.favorite) else it })
                    },
                    onUp = { move(index, -1) },
                    onDown = { move(index, 1) },
                    onEdit = { editing = c },
                    onDelete = { deleting = c }
                )
            }
            item {
                OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Reset to default colors")
                }
            }
        }
    }

    if (adding) {
        ColorEditDialog(null, { adding = false }) { n, h, t ->
            commit(colors + ColorItem.create(n, h, t))
            adding = false
        }
    }
    editing?.let { e ->
        ColorEditDialog(e, { editing = null }) { n, h, t ->
            commit(colors.map { if (it.id == e.id) it.copy(name = n, hex = h, text = t) else it })
            editing = null
        }
    }
    deleting?.let { d ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete color?") },
            text = { Text("\"${d.name}\" will be removed from the keyboard.") },
            confirmButton = {
                TextButton(onClick = {
                    commit(colors.filter { it.id != d.id })
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset colors?") },
            text = { Text("This replaces your list with the 11 default colors. Custom colors will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    commit(Defaults.colors)
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ColorRow(
    item: ColorItem,
    canUp: Boolean,
    canDown: Boolean,
    onFavorite: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp)
                    .background(Color(item.argb), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    if (item.text.isBlank()) item.hex else "${item.hex} - inserts \"${item.text}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Column {
                IconButton(onClick = onUp, enabled = canUp, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = onDown, enabled = canDown, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                }
            }
            IconButton(onClick = onFavorite, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Favorite",
                    tint = if (item.favorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.outline
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
fun ColorEditDialog(initial: ColorItem?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var hex by remember { mutableStateOf(initial?.hex ?: "#") }
    var text by remember { mutableStateOf(initial?.text ?: "") }
    val norm = Hex.normalize(hex)
    val valid = name.isNotBlank() && norm != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add color" else "Edit color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. Coffee Brown)") },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { hex = it },
                        label = { Text("HEX (e.g. #6F4E37)") },
                        singleLine = true,
                        isError = norm == null,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier.size(40.dp)
                            .background(
                                if (norm != null) Color(android.graphics.Color.parseColor(norm)) else Color.Transparent,
                                CircleShape
                            )
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text to insert (optional)") },
                    supportingText = { Text("Empty = insert the name. Type the HEX code here to insert that instead.") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(name.trim(), norm ?: hex, text.trim()) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ------------------------------------------------------------------ Settings

@Composable
fun SettingsScreen(store: Store, onManageColors: () -> Unit) {
    val ctx = LocalContext.current
    var gridEnabled by remember { mutableStateOf(store.colorGridEnabled) }
    var autoHide by remember { mutableStateOf(store.autoHide) }
    var theme by remember { mutableIntStateOf(store.themeMode) }
    var height by remember { mutableFloatStateOf(store.heightScale) }
    var keySize by remember { mutableFloatStateOf(store.keyScale) }
    var sound by remember { mutableStateOf(store.sound) }
    var soundProfile by remember { mutableStateOf(store.soundProfile) }
    var soundVolume by remember { mutableFloatStateOf(store.soundVolume.toFloat()) }
    var vibrate by remember { mutableStateOf(store.vibrate) }
    var history by remember { mutableStateOf(store.historyEnabled) }
    var capture by remember { mutableStateOf(store.captureExternal) }
    var limit by remember { mutableIntStateOf(store.historyLimit) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SectionTitle("Color Grid")
        SwitchRow("Enable Color Grid", "Show the Color Grid button on the keyboard", gridEnabled) {
            gridEnabled = it; store.colorGridEnabled = it
        }
        SwitchRow("Hide keyboard after inserting", "Close the panel and dismiss the keyboard once a color is inserted", autoHide) {
            autoHide = it; store.autoHide = it
        }
        OutlinedButton(onClick = onManageColors, modifier = Modifier.fillMaxWidth()) { Text("Manage custom colors") }

        SectionTitle("Appearance")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("System", "Light", "Dark").forEachIndexed { i, label ->
                FilterChip(
                    selected = theme == i,
                    onClick = { theme = i; store.themeMode = i },
                    label = { Text(label) }
                )
            }
        }
        SliderRow("Keyboard height", "${(height * 100).roundToInt()}%", height, 0.8f..1.4f) {
            height = it; store.heightScale = it
        }
        SliderRow("Key size", "${(keySize * 100).roundToInt()}%", keySize, 0.8f..1.3f) {
            keySize = it; store.keyScale = it
        }

        SectionTitle("Sound Studio")
        SwitchRow("Keyboard sounds", "Play the selected sound profile when a key is pressed", sound) {
            sound = it; store.sound = it
        }
        Text("Choose a sound profile", style = MaterialTheme.typography.bodyMedium)
        KeyboardSounds.profiles.forEach { profile ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = soundProfile == profile.id,
                    onClick = { soundProfile = profile.id; store.soundProfile = profile.id }
                )
                Column(Modifier.weight(1f)) {
                    Text(profile.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(profile.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { KeyboardSounds.play(ctx, profile.id, soundVolume.roundToInt()) }) {
                    Text("Preview")
                }
            }
        }
        Text("Sound volume · ${soundVolume.roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = soundVolume,
            onValueChange = { soundVolume = it; store.soundVolume = it.roundToInt() },
            valueRange = 0f..100f
        )
        Text("Uses the supplied Mechvibes sound-pack samples. Audio plays locally on your device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionTitle("Haptics")
        SwitchRow("Vibration on keypress", null, vibrate) { vibrate = it; store.vibrate = it }

        SectionTitle("Clipboard")
        SwitchRow("Keep clipboard history", "Remember colors you insert", history) {
            history = it; store.historyEnabled = it
        }
        SwitchRow("Record text copied elsewhere", "Also add text copied in other apps while this keyboard is active", capture) {
            capture = it; store.captureExternal = it
        }
        Text("History size", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 25, 50).forEach { n ->
                FilterChip(
                    selected = limit == n,
                    onClick = { limit = n; store.historyLimit = n },
                    label = { Text("$n") }
                )
            }
        }
        OutlinedButton(
            onClick = {
                store.clearHistory(false)
                Toast.makeText(ctx, "Clipboard history cleared", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text("Clear clipboard history") }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SliderRow(title: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

// ------------------------------------------------------------------ About

@Composable
fun AboutScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("About", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Color Grid Keyboard 1.0.0", style = MaterialTheme.typography.titleMedium)
        Text(
            "A full QWERTY keyboard with a built-in Color Grid. Tap a color: its text is copied to the clipboard, " +
                "typed straight into the focused field, and the keyboard hides so you can keep working.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Tips\n" +
                "- Long-press a color in the grid to favorite it.\n" +
                "- Open the clipboard panel to re-insert or pin recent items.\n" +
                "- Add your own colors (name + HEX) in the Colors tab.\n" +
                "- Use the globe key to switch to another keyboard.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Privacy: the app has no internet permission. Colors, settings and clipboard history stay on your device. " +
                "Text marked as sensitive (such as passwords copied from a password manager) is never recorded.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
