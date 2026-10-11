package com.zahxrun.ttslive.stream

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class StreamState { IDLE, STARTING, LIVE, ERROR }

/**
 * No-op stub. RTMP/canvas streaming dinonaktifkan karena nambah delay 5-30 detik
 * lewat platform pihak ketiga (YouTube, TikTok Live Studio). Versi ini fokus ke
 * jalur EulerStream websocket — komentar penonton masuk real-time, dan layar
 * cukup ditampilkan via screen share / OBS di PC kalau mau disiarkan.
 */
class StreamEngine private constructor(private val app: Application) {

    var state by mutableStateOf(StreamState.IDLE)
        private set
    var detail by mutableStateOf(IDLE_DETAIL)
        private set
    var streamUrl: String = ""
    var streamKey: String = ""

    fun requestStart() { /* no-op: RTMP disabled */ }
    fun stop() { /* no-op: RTMP disabled */ }

    companion object {
        private const val IDLE_DETAIL = "Streaming video dinonaktifkan (mode rendah-latency)."

        @Volatile private var instance: StreamEngine? = null

        fun get(context: Context): StreamEngine =
            instance ?: synchronized(this) {
                instance ?: StreamEngine(context.applicationContext as Application)
                    .also { instance = it }
            }
    }
}
