package com.zahxrun.ttslive.stream

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.NoAudioSource
import com.pedro.library.rtmp.RtmpStream
import com.zahxrun.ttslive.LiveService
import com.zahxrun.ttslive.SecretStore

enum class StreamState { IDLE, STARTING, LIVE, ERROR }

/**
 * Mengurus pengiriman kanvas game ke server RTMP (YouTube dulu). Singleton seperti LiveController.
 * Semua perubahan state terjadi di main thread. Encoder-nya hidup di [LiveService].
 */
class StreamEngine private constructor(private val app: Application) {

    var state by mutableStateOf(StreamState.IDLE)
        private set
    var detail by mutableStateOf(IDLE_DETAIL)
        private set
    var streamKey by mutableStateOf(SecretStore.get(app, KEY_YT))

    /** Dipasang [LiveService] supaya notifikasi ikut berubah. */
    var onStatus: (() -> Unit)? = null

    val isActive: Boolean get() = state == StreamState.STARTING || state == StreamState.LIVE

    private var stream: RtmpStream? = null
    private val main = Handler(Looper.getMainLooper())

    private val checker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) {
            main.post { if (stream != null) set(StreamState.STARTING, "Menyambung ke server...") }
        }

        override fun onConnectionSuccess() {
            main.post { if (stream != null) set(StreamState.LIVE, "Video terkirim ke YouTube") }
        }

        override fun onConnectionFailed(reason: String) {
            main.post {
                val s = stream ?: return@post
                if (s.getStreamClient().reTry(RETRY_DELAY_MS, reason)) {
                    set(StreamState.STARTING, "Menyambung ulang...")
                } else {
                    fail("Gagal tersambung: $reason")
                }
            }
        }

        override fun onDisconnect() {}

        override fun onAuthError() {
            main.post { fail("Autentikasi ditolak, cek stream key") }
        }

        override fun onAuthSuccess() {}

        override fun onNewBitrate(bitrate: Long) {
            main.post {
                if (state == StreamState.LIVE) set(StreamState.LIVE, "Live \u2022 ${bitrate / 1000} kbps")
            }
        }
    }

    fun saveKey() {
        SecretStore.put(app, KEY_YT, streamKey.trim())
    }

    /** Dipanggil dari UI. Encoder baru dibuat di service lewat [begin]. */
    fun requestStart() {
        saveKey()
        if (isActive) return
        if (streamKey.isBlank()) {
            set(StreamState.ERROR, "Stream key kosong")
            return
        }
        set(StreamState.STARTING, "Menyiapkan encoder...")
        LiveService.startStream(app)
    }

    /** Dipanggil [LiveService] (main thread) dengan context service. */
    fun begin(ctx: Context) {
        if (stream != null) return
        val key = streamKey.trim()
        if (key.isEmpty()) {
            fail("Stream key kosong")
            return
        }
        var s: RtmpStream? = null
        try {
            s = RtmpStream(ctx, checker, CanvasVideoSource(ctx) { StreamCanvasRoot() }, NoAudioSource())
            val ok = s.prepareVideo(WIDTH, HEIGHT, VIDEO_BITRATE, FPS, 2, 0) &&
                s.prepareAudio(44100, true, 64 * 1000)
            if (!ok) throw IllegalStateException("Encoder menolak konfigurasi video/audio")
            s.getStreamClient().setReTries(MAX_RETRIES)
            stream = s
            s.startStream("$YOUTUBE_URL/$key")
        } catch (e: Exception) {
            if (stream == null) runCatching { s?.release() }
            fail("Gagal memulai stream: ${e.message}")
        }
    }

    fun stop() {
        cleanup()
        set(StreamState.IDLE, IDLE_DETAIL)
        LiveService.stopIfIdle(app)
    }

    /** Dipanggil saat service dihancurkan: bersihkan tanpa memicu stop service lagi. */
    fun shutdown() {
        cleanup()
        state = StreamState.IDLE
        detail = IDLE_DETAIL
    }

    private fun fail(msg: String) {
        cleanup()
        set(StreamState.ERROR, msg)
        LiveService.stopIfIdle(app)
    }

    private fun cleanup() {
        val s = stream
        stream = null
        if (s != null) {
            runCatching { if (s.isStreaming) s.stopStream() }
            runCatching { s.release() }
        }
    }

    private fun set(newState: StreamState, newDetail: String) {
        state = newState
        detail = newDetail
        onStatus?.invoke()
    }

    companion object {
        private const val KEY_YT = "youtube_stream_key"
        private const val IDLE_DETAIL = "Stream belum jalan"
        private const val YOUTUBE_URL = "rtmps://a.rtmps.youtube.com:443/live2"
        private const val WIDTH = 720
        private const val HEIGHT = 1280
        private const val FPS = 30
        private const val VIDEO_BITRATE = 2_500_000
        private const val MAX_RETRIES = 10
        private const val RETRY_DELAY_MS = 5000L

        @Volatile private var instance: StreamEngine? = null

        fun get(context: Context): StreamEngine =
            instance ?: synchronized(this) {
                instance ?: StreamEngine(context.applicationContext as Application)
                    .also { instance = it }
            }
    }
}
