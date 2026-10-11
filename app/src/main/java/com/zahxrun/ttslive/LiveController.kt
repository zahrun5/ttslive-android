package com.zahxrun.ttslive

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zahxrun.ttslive.engine.CariKataGame
import com.zahxrun.ttslive.engine.Family100Game
import com.zahxrun.ttslive.engine.HitungCepatGame
import com.zahxrun.ttslive.engine.Room
import com.zahxrun.ttslive.engine.RoomSnapshot
import com.zahxrun.ttslive.engine.SambungKataGame
import com.zahxrun.ttslive.engine.SusunKataGame
import com.zahxrun.ttslive.engine.TtsGame
import com.zahxrun.ttslive.tiktok.ConnState
import com.zahxrun.ttslive.tiktok.EulerClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Otak sesi live. Satu instance per proses (singleton), BUKAN per Activity, jadi tetap hidup
 * walau Activity ditutup selama [LiveService] (foreground service) masih jalan.
 *
 * Semua perubahan state dilakukan di main thread (scope Main.immediate), sesuai asumsi [Room]
 * bahwa semua pemanggilan datang dari satu thread.
 */
class LiveController private constructor(private val app: Application) {

    private val prefs = app.getSharedPreferences("ttslive", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var username by mutableStateOf(prefs.getString(KEY_USER, "") ?: "")
    var apiKey by mutableStateOf(prefs.getString(KEY_API, "") ?: "")
    var selectedGame by mutableStateOf(GameMode.FAMILY_100)
        private set

    private var room = buildRoom()

    var snapshot: RoomSnapshot by mutableStateOf(room.snapshot())
        private set
    var connState by mutableStateOf(ConnState.IDLE)
        private set
    var connDetail by mutableStateOf(IDLE_DETAIL)
        private set

    /** Dipasang oleh [LiveService] supaya notifikasi ikut berubah. Dipanggil di main thread. */
    var onStatus: ((ConnState, String) -> Unit)? = null

    private var client: EulerClient? = null
    private var advanceJob: Job? = null

    /** True kalau ada sesi live (koneksi TikTok) yang sedang dibuka. */
    val isLive: Boolean get() = client != null

    init {
        loadScores()
        snapshot = room.snapshot()
    }

    private fun buildRoom(): Room {
        val game = when (selectedGame) {
            GameMode.FAMILY_100 -> Family100Game(app)
            GameMode.HITUNG_CEPAT -> HitungCepatGame()
            GameMode.CARI_KATA -> CariKataGame(app)
            GameMode.SUSUN_KATA -> SusunKataGame(app)
            GameMode.SAMBUNG_KATA -> SambungKataGame(app)
            GameMode.TTS -> TtsGame(app)
        }
        return Room(game)
    }

    fun switchGame(mode: GameMode) {
        stopLive()
        advanceJob?.cancel()
        selectedGame = mode
        room = buildRoom()
        loadScores()
        snapshot = room.snapshot()
    }

    fun saveSettings() {
        prefs.edit().putString(KEY_USER, username.trim()).putString(KEY_API, apiKey.trim()).apply()
    }

    /** Idempotent: kalau sesi sudah aktif/menyambung, tidak melakukan apa-apa. */
    fun startLive() {
        saveSettings()
        val active = client != null &&
            (connState == ConnState.CONNECTING ||
                connState == ConnState.CONNECTED ||
                connState == ConnState.RECONNECTING)
        if (active) return

        client?.disconnect()
        val c = EulerClient(object : EulerClient.Listener {
            override fun onState(state: ConnState, detail: String) {
                scope.launch { setConn(state, detail) }
            }
            override fun onChat(player: String, text: String, profileUrl: String?) {
                scope.launch { onComment(player, text) }
            }
        })
        client = c
        LiveService.start(app)
        c.connect(username, apiKey)
    }

    fun stopLive() {
        val c = client
        client = null
        c?.disconnect()
        setConn(ConnState.IDLE, IDLE_DETAIL)
        LiveService.stopIfIdle(app)
    }

    private fun setConn(state: ConnState, detail: String) {
        connState = state
        connDetail = detail
        onStatus?.invoke(state, detail)
    }

    fun onComment(player: String, text: String) {
        val outcome = room.handleChat(player, text)
        snapshot = room.snapshot()
        if (outcome.result?.ok == true) saveScores()
        if (outcome.roundCompleted) scheduleAdvance()
    }

    fun resetGame() {
        advanceJob?.cancel()
        room.resetAll()
        saveScores()
        snapshot = room.snapshot()
    }

    private fun scheduleAdvance() {
        advanceJob?.cancel()
        advanceJob = scope.launch {
            delay(room.summaryDurationMs())
            room.advance()
            snapshot = room.snapshot()
        }
    }

    private fun saveScores() {
        val json = JSONObject()
        room.exportScores().forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(KEY_SCORES, json.toString()).apply()
    }

    private fun loadScores() {
        val raw = prefs.getString(KEY_SCORES, null) ?: return
        try {
            val json = JSONObject(raw)
            val map = LinkedHashMap<String, Int>()
            val keys = json.keys()
            while (keys.hasNext()) { val k = keys.next(); map[k] = json.optInt(k, 0) }
            room.importScores(map)
        } catch (_: Exception) {}
    }

    companion object {
        private const val KEY_USER = "username"
        private const val KEY_API = "apiKey"
        private const val KEY_SCORES = "scores"
        private const val IDLE_DETAIL = "Belum tersambung (mode tes)"

        @Volatile private var instance: LiveController? = null

        fun get(context: Context): LiveController =
            instance ?: synchronized(this) {
                instance ?: LiveController(context.applicationContext as Application)
                    .also { instance = it }
            }
    }
}
