package com.zahxrun.ttslive.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zahxrun.ttslive.LiveViewModel
import com.zahxrun.ttslive.engine.RoomSnapshot
import com.zahxrun.ttslive.engine.TtsState
import com.zahxrun.ttslive.tiktok.ConnState

/**
 * Layar TTS: grid silang bernomor + daftar clue. Grid, status, leaderboard, dan papan hasil
 * dipakai bersama dengan layar Sambung Kata (lihat SambungKataScreen.kt).
 */
@Composable
fun TtsScreen(vm: LiveViewModel, onExit: () -> Unit) {
    var showTest by rememberSaveable { mutableStateOf(vm.connState == ConnState.IDLE) }

    // Layar kontrol = panel di sekitar kanvas. Kanvas (TtsCanvas) tidak tahu apa-apa soal kontrol,
    // jadi nanti bisa dirender sendirian ke stream.
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Box(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)) {
            SMStatusRow(vm, showTest, onToggleTest = { showTest = !showTest }, onReset = vm::resetGame, onExit = onExit)
        }
        TtsCanvas(vm.snapshot, Modifier.weight(1f).fillMaxWidth())
        if (showTest) {
            Box(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
                TestPanel(onSend = vm::onComment)
            }
        }
    }
}

/**
 * Tampilan game TTS saja: header, progres, grid, clue, leaderboard, ticker chat, dan papan hasil.
 * Murni fungsi dari [RoomSnapshot]; tanpa tombol, panel tes, atau akses ke ViewModel.
 */
@Composable
fun TtsCanvas(snap: RoomSnapshot, modifier: Modifier = Modifier) {
    val state = snap.game as? TtsState

    Box(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state != null) {
                TtsHeader(state)
                LinearProgressIndicator(
                    progress = { if (state.totalCount == 0) 0f else state.foundCount.toFloat() / state.totalCount },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = Ok,
                    trackColor = Line,
                )
                CrosswordGrid(state.rows, state.cols, state.cells, Modifier.weight(1f).fillMaxWidth())
                TtsClues(state)
            } else {
                Spacer(Modifier.weight(1f))
            }

            SMLeaderboard(snap.leaderboard)

            if (snap.chat.isNotEmpty()) {
                snap.chat.takeLast(2).forEach {
                    Text("💬 ${it.player}: ${it.text}", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        snap.summary?.let { SMSummaryOverlay(it) }
    }
}

@Composable
private fun TtsHeader(state: TtsState) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCol)
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("TTS", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Jawab: nomor + jawaban, mis. \"3 jakarta\"", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("${state.foundCount}/${state.totalCount} kata", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Clue yang belum terjawab, dipisah mendatar dan menurun. Jumlah huruf ditulis di akhir tiap clue. */
@Composable
private fun TtsClues(state: TtsState) {
    val across = state.words.filter { it.across && !it.solved }.sortedBy { it.number }
    val down = state.words.filter { !it.across && !it.solved }.sortedBy { it.number }
    if (across.isEmpty() && down.isEmpty()) return

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCol.copy(alpha = 0.9f))
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (across.isNotEmpty()) {
            Text("➡ Mendatar", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            across.forEach { w ->
                Text("${w.number}. ${w.clue} (${w.length})", color = TextMain, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (down.isNotEmpty()) {
            Text("⬇ Menurun", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = if (across.isNotEmpty()) 4.dp else 0.dp))
            down.forEach { w ->
                Text("${w.number}. ${w.clue} (${w.length})", color = TextMain, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
