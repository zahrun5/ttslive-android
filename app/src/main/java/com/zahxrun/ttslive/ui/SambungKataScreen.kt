package com.zahxrun.ttslive.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zahxrun.ttslive.LiveViewModel
import com.zahxrun.ttslive.engine.LeaderboardEntry
import com.zahxrun.ttslive.engine.RoundSummary
import com.zahxrun.ttslive.engine.SambungKataCell
import com.zahxrun.ttslive.engine.SambungKataState
import com.zahxrun.ttslive.tiktok.ConnState

@Composable
fun SambungKataScreen(vm: LiveViewModel, onExit: () -> Unit) {
    val snap = vm.snapshot
    val state = snap.game as? SambungKataState
    var showTest by rememberSaveable { mutableStateOf(vm.connState == ConnState.IDLE) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state != null) {
                SMHeader(state)
                SMStatusRow(vm, showTest, onToggleTest = { showTest = !showTest }, onReset = vm::resetGame, onExit = onExit)
                LinearProgressIndicator(
                    progress = { if (state.totalCount == 0) 0f else state.foundCount.toFloat() / state.totalCount },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = Ok,
                    trackColor = Line,
                )
                CrosswordGrid(state.rows, state.cols, state.cells, Modifier.weight(1f).fillMaxWidth())
                SMRemaining(state)
            } else {
                Spacer(Modifier.weight(1f))
            }

            SMLeaderboard(snap.leaderboard)

            if (snap.chat.isNotEmpty()) {
                snap.chat.takeLast(2).forEach {
                    Text("💬 ${it.player}: ${it.text}", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (showTest) TestPanel(onSend = vm::onComment)
        }

        snap.summary?.let { SMSummaryOverlay(it) }
    }
}

@Composable
private fun SMHeader(state: SambungKataState) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCol)
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Sambung Kata", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        // Huruf dasar: semua kata di grid disusun dari huruf-huruf ini.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            state.baseLetters.forEach { ch ->
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(ch.toString(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("${state.foundCount}/${state.totalCount} kata ditemukan", color = Muted, fontSize = 12.sp)
    }
}

@Composable
internal fun SMStatusRow(
    vm: LiveViewModel,
    showTest: Boolean,
    onToggleTest: () -> Unit,
    onReset: () -> Unit,
    onExit: () -> Unit,
) {
    val dot = when (vm.connState) {
        ConnState.CONNECTED -> Ok
        ConnState.CONNECTING, ConnState.RECONNECTING -> Gold
        ConnState.ERROR -> Color(0xFFF87171)
        else -> Muted
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(
            vm.connDetail,
            color = Muted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
        TextButton(onClick = onToggleTest) { Text(if (showTest) "Tutup tes" else "Tes", fontSize = 12.sp) }
        TextButton(onClick = onReset) { Text("Reset", fontSize = 12.sp) }
        TextButton(onClick = onExit) { Text("Keluar", fontSize = 12.sp) }
    }
}

/** Dipakai bersama oleh Sambung Kata dan TTS. Grid silang: hanya sel yang dipakai kata yang digambar. Huruf muncul setelah kata yang melewatinya ketemu. */
@Composable
internal fun CrosswordGrid(rows: Int, cols: Int, cells: List<SambungKataCell>, modifier: Modifier) {
    val byPos = HashMap<Int, SambungKataCell>()
    cells.forEach { byPos[it.row * cols + it.col] = it }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val cell = minOf(minOf(maxWidth / cols, maxHeight / rows), 40.dp)
        val letterSize = (cell.value * 0.6f).sp
        val numberSize = (cell.value * 0.3f).sp
        Column(Modifier.size(cell * cols, cell * rows)) {
            for (r in 0 until rows) {
                Row(Modifier.size(cell * cols, cell)) {
                    for (c in 0 until cols) {
                        val info = byPos[r * cols + c]
                        if (info == null) {
                            Spacer(Modifier.size(cell))
                        } else {
                            val open = info.letter != null
                            Box(
                                Modifier
                                    .size(cell)
                                    .padding(1.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (open) Ok else SurfaceCol)
                                    .border(BorderStroke(1.dp, if (open) Ok else Line), RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (info.letter != null) {
                                    Text(
                                        info.letter.toString(),
                                        color = OkDark,
                                        fontSize = letterSize,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                                if (info.number != null) {
                                    Text(
                                        "${info.number}",
                                        color = if (open) OkDark else Gold,
                                        fontSize = numberSize,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Kata yang belum ketemu, ditulis "nomor (jumlah huruf)" dipisah arah, tanpa membocorkan jawaban. */
@Composable
private fun SMRemaining(state: SambungKataState) {
    fun line(across: Boolean): String =
        state.words
            .filter { it.across == across && !it.solved }
            .sortedBy { it.number }
            .joinToString("  ") { "${it.number} (${it.length})" }

    val mendatar = line(true)
    val menurun = line(false)
    if (mendatar.isEmpty() && menurun.isEmpty()) return

    Column(Modifier.fillMaxWidth()) {
        if (mendatar.isNotEmpty()) {
            Text("➡ Mendatar: $mendatar", color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (menurun.isNotEmpty()) {
            Text("⬇ Menurun: $menurun", color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun SMLeaderboard(entries: List<LeaderboardEntry>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCol.copy(alpha = 0.9f))
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("🏆 Leaderboard", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (entries.isEmpty()) {
            Text("Belum ada yang jawab", color = Muted, fontSize = 12.sp)
        } else {
            entries.take(5).forEachIndexed { i, e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${i + 1}",
                        color = if (i == 0) Gold else Muted,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        modifier = Modifier.size(22.dp),
                    )
                    AvatarLabel(
                        avatar = e.emoji,
                        text = e.player,
                        color = TextMain,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${e.score}", color = Lilac, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun SMSummaryOverlay(summary: RoundSummary) {
    Box(Modifier.fillMaxSize().background(Color(0xE6120D22)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceCol)
                .border(BorderStroke(1.dp, Line), RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Semua kata ketemu! 🎉", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            if (summary.top.isNotEmpty()) {
                Text("Pencari terbaik", color = Muted, fontSize = 12.sp)
                val medals = listOf("🥇", "🥈", "🥉")
                summary.top.forEachIndexed { i, e ->
                    AvatarLabel(
                        avatar = e.emoji,
                        text = e.player,
                        prefix = medals.getOrElse(i) { "" },
                        suffix = " • +${e.points}",
                        color = TextMain,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text("Teka-teki baru sebentar lagi...", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
