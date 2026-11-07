package com.colorgrid.keyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper

/** Short, per-key audio samples extracted from the sound-pack archive supplied by the user. */
object KeyboardSounds {
    data class Profile(val id: String, val title: String, val description: String, val folder: String)

    val profiles = listOf(
        Profile("cherry_blue_abs", "Cherry MX Blue · ABS", "Bright, crisp mechanical click", "cherry_blue_abs"),
        Profile("cherry_blue_pbt", "Cherry MX Blue · PBT", "Crisp click with a fuller body", "cherry_blue_pbt"),
        Profile("cherry_brown_abs", "Cherry MX Brown · ABS", "Soft tactile mechanical tap", "cherry_brown_abs"),
        Profile("cherry_brown_pbt", "Cherry MX Brown · PBT", "Muted tactile tap", "cherry_brown_pbt"),
        Profile("cherry_red_abs", "Cherry MX Red · ABS", "Smooth linear keypress", "cherry_red_abs"),
        Profile("cherry_black_abs", "Cherry MX Black · ABS", "Deeper linear keystroke", "cherry_black_abs"),
        Profile("cherry_black_pbt", "Cherry MX Black · PBT", "Low, muted linear keystroke", "cherry_black_pbt"),
        Profile("crystal_purple", "EG Crystal Purple", "Bright crystal-style switch sound", "crystal_purple"),
        Profile("oreo", "EG Oreo", "Rounded, soft mechanical sound", "oreo"),
        Profile("topre_purple", "Topre Purple Hybrid PBT", "Soft, cushioned thock", "topre_purple"),
        Profile("silent", "Silent Switch", "No keyboard sound", "")
    )

    private var pool: SoundPool? = null
    private val sampleIds = HashMap<String, Int>()
    private val loaded = HashSet<Int>()
    private val pending = HashMap<Int, Pair<Int, Float>>()
    private val main = Handler(Looper.getMainLooper())

    @Synchronized private fun ensurePool(context: Context) {
        if (pool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()
        p.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                synchronized(this) {
                    loaded.add(sampleId)
                    pending.remove(sampleId)?.let { (volume, rate) ->
                        p.play(sampleId, volume / 100f, volume / 100f, 1, 0, rate)
                    }
                }
            }
        }
        pool = p
        val app = context.applicationContext
        profiles.filter { it.id != "silent" }.forEach { profile ->
            listOf("letter", "space", "enter", "delete").forEach { category ->
                val key = "${profile.id}_$category"
                try {
                    val fd = app.assets.openFd("key_sounds/$key.wav")
                    sampleIds[key] = p.load(fd, 1)
                    fd.close()
                } catch (_: Exception) { }
            }
        }
    }

    fun play(context: Context, profileId: String, volume: Int, category: String = "letter") {
        if (volume <= 0 || profileId == "silent") return
        ensurePool(context)
        val key = "${profileId}_${category}"
        val id = sampleIds[key] ?: sampleIds["${profileId}_letter"] ?: return
        synchronized(this) {
            val p = pool ?: return
            if (loaded.contains(id)) {
                val v = volume.coerceIn(0, 100) / 100f
                p.play(id, v, v, 1, 0, 1f)
            } else {
                pending[id] = volume.coerceIn(0, 100) to 1f
            }
        }
    }

    fun release() {
        synchronized(this) {
            pool?.release()
            pool = null
            sampleIds.clear()
            loaded.clear()
            pending.clear()
        }
    }
}
