package com.zahxrun.ttslive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.zahxrun.ttslive.engine.RoomSnapshot
import com.zahxrun.ttslive.stream.StreamEngine
import com.zahxrun.ttslive.stream.StreamState
import com.zahxrun.ttslive.tiktok.ConnState

enum class GameMode(val label: String) {
    FAMILY_100("Family 100"),
    HITUNG_CEPAT("Hitung Cepat"),
    CARI_KATA("Cari Kata"),
    SUSUN_KATA("Susun Kata Acak"),
    SAMBUNG_KATA("Sambung Kata"),
    TTS("TTS"),
}

/**
 * Lapisan tipis untuk UI. Semua state dan logika live ada di [LiveController] (hidup di luar
 * Activity), jadi ViewModel ini boleh dibuang-buat ulang tanpa memutus sesi live.
 */
class LiveViewModel(app: Application) : AndroidViewModel(app) {

    private val c = LiveController.get(app)
    private val e = StreamEngine.get(app)

    var username: String
        get() = c.username
        set(v) { c.username = v }

    var apiKey: String
        get() = c.apiKey
        set(v) { c.apiKey = v }

    val selectedGame: GameMode get() = c.selectedGame
    val snapshot: RoomSnapshot get() = c.snapshot
    val connState: ConnState get() = c.connState
    val connDetail: String get() = c.connDetail
    val isLive: Boolean get() = c.isLive

    var streamKey: String
        get() = e.streamKey
        set(v) { e.streamKey = v }
    val streamState: StreamState get() = e.state
    val streamDetail: String get() = e.detail
    fun startStream() = e.requestStart()
    fun stopStream() = e.stop()

    fun switchGame(mode: GameMode) = c.switchGame(mode)
    fun saveSettings() = c.saveSettings()
    fun startLive() = c.startLive()
    fun stopLive() = c.stopLive()
    fun onComment(player: String, text: String) = c.onComment(player, text)
    fun resetGame() = c.resetGame()
}
