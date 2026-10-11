package com.zahxrun.ttslive.tiktok

import android.os.Handler
import android.os.Looper
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

enum class ConnState { IDLE, CONNECTING, CONNECTED, RECONNECTING, ENDED, ERROR }

/**
 * Klien WebSocket EulerStream (pengganti tiktok-live-connector di versi Node).
 * Callback dipanggil dari thread OkHttp, bukan main thread.
 */
class EulerClient(private val listener: Listener) {

    interface Listener {
        fun onState(state: ConnState, detail: String)
        fun onChat(player: String, text: String, profileUrl: String?)
    }

    private val http = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val main = Handler(Looper.getMainLooper())

    private var socket: WebSocket? = null
    private var generation = 0
    private var intentional = false
    private var retries = 0
    private var uniqueId = ""
    private var apiKey = ""

    private var frames = 0
    private var chats = 0
    private var warnedBinary = false

    fun connect(uniqueId: String, apiKey: String) {
        this.uniqueId = uniqueId.trim().removePrefix("@")
        this.apiKey = apiKey.trim()
        intentional = false
        retries = 0
        open()
    }

    fun disconnect() {
        intentional = true
        generation++
        main.removeCallbacksAndMessages(null)
        socket?.close(1000, "bye")
        socket = null
        listener.onState(ConnState.IDLE, "Terputus")
    }

    private fun open() {
        if (uniqueId.isEmpty()) {
            listener.onState(ConnState.ERROR, "Username TikTok kosong")
            return
        }
        val myGen = ++generation
        frames = 0
        chats = 0
        warnedBinary = false
        listener.onState(
            if (retries == 0) ConnState.CONNECTING else ConnState.RECONNECTING,
            if (retries == 0) "Menyambungkan ke @$uniqueId..." else "Menyambung ulang (percobaan $retries/$MAX_RETRIES)...",
        )

        val url = HttpUrl.Builder()
            .scheme("https")
            .host("ws.eulerstream.com")
            .addQueryParameter("uniqueId", uniqueId)
            .apply { if (apiKey.isNotEmpty()) addQueryParameter("apiKey", apiKey) }
            .build()

        socket = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (myGen != generation) return
                retries = 0
                listener.onState(ConnState.CONNECTED, "Terhubung ke @$uniqueId")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (myGen != generation) return
                frames++
                val events = EventParser.extractChats(text)
                chats += events.size
                for (e in events) listener.onChat(e.player, e.text, e.profileUrl)
                if (frames == 1 || frames % 25 == 0) {
                    listener.onState(ConnState.CONNECTED, "Terhubung • frame $frames • komentar terbaca $chats")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (myGen != generation) return
                frames++
                if (!warnedBinary) {
                    warnedBinary = true
                    listener.onState(
                        ConnState.CONNECTED,
                        "Server mengirim frame biner (protobuf); format ini belum didukung, hanya JSON",
                    )
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (myGen != generation) return
                handleDown(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (myGen != generation) return
                val httpCode = response?.code
                if (httpCode == 401 || httpCode == 403) {
                    listener.onState(ConnState.ERROR, "Ditolak server (HTTP $httpCode): cek API key")
                    return
                }
                handleDown(-1, t.message ?: "koneksi gagal")
            }
        })
    }

    private fun handleDown(code: Int, reason: String) {
        if (intentional) return
        val msg = when (code) {
            STREAM_END -> "Live sudah berakhir"
            INVALID_OPTIONS -> "Opsi koneksi tidak valid (cek username)"
            INVALID_AUTH -> "API key tidak valid"
            NO_PERMISSION -> "API key tidak punya izin"
            NOT_LIVE -> "@$uniqueId sedang tidak live"
            TOO_MANY_CONNECTIONS -> "Terlalu banyak koneksi / kuota habis"
            else -> null
        }
        if (msg != null) {
            listener.onState(if (code == STREAM_END) ConnState.ENDED else ConnState.ERROR, msg)
            return
        }
        if (retries >= MAX_RETRIES) {
            listener.onState(ConnState.ERROR, "Gagal tersambung setelah $MAX_RETRIES percobaan ($reason)")
            return
        }
        retries++
        val delayMs = minOf(30_000L, 3_000L shl (retries - 1))
        listener.onState(ConnState.RECONNECTING, "Terputus ($reason), coba lagi ${delayMs / 1000} detik")
        val gen = generation
        main.postDelayed({ if (!intentional && gen == generation) open() }, delayMs)
    }

    companion object {
        const val MAX_RETRIES = 5
        // Kode close kustom EulerStream (Euler-WebSocket-SDK, ClientCloseCode)
        const val STREAM_END = 4005
        const val INVALID_OPTIONS = 4400
        const val INVALID_AUTH = 4401
        const val NO_PERMISSION = 4403
        const val NOT_LIVE = 4404
        const val TOO_MANY_CONNECTIONS = 4429
    }
}
