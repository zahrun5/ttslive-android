package com.zahxrun.ttslive.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.zahxrun.ttslive.engine.BoardSlot
import com.zahxrun.ttslive.engine.Family100State
import com.zahxrun.ttslive.engine.LeaderboardEntry
import com.zahxrun.ttslive.engine.RoundSummary
import com.zahxrun.ttslive.tiktok.ConnState

@Composable
fun Family100Screen(vm: LiveViewModel, onExit: () -> Unit) {
    val snap = vm.snapshot
    val state = snap.game as? Family100State
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
                // Header: pertanyaan
                QuestionHeader(state)

                // Status bar
                F100StatusRow(vm, showTest, onToggleTest = { showTest = !showTest }, onReset = vm::resetGame, onExit = onExit)

                // Progress
                LinearProgressIndicator(
                    progress = { if (state.totalAnswers == 0) 0f else state.foundCount.toFloat() / state.totalAnswers },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = Ok,
                    trackColor = Line,
                )

                // Board 7 slot
                BoardGrid(state.board, Modifier.weight(1f))
            }

            // Leaderboard
            F100Leaderboard(snap.leaderboard)

            // Chat ticker
            if (snap.chat.isNotEmpty()) {
                snap.chat.takeLast(2).forEach {
                    Text("💬 ${it.player}: ${it.text}", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (showTest) TestPanel(onSend = vm::onComment)
        }

        snap.summary?.let { F100SummaryOverlay(it) }
    }
}

@Composable
private fun QuestionHeader(state: Family100State) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCol)
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("Family 100", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(state.question, color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Text("${state.foundCount}/${state.totalAnswers} jawaban ditemukan", color = Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.End))
    }
}

@Composable
private fun F100StatusRow(vm: LiveViewModel, showTest: Boolean, onToggleTest: () -> Unit, onReset: () -> Unit, onExit: () -> Unit) {
    val dot = when (vm.connState) {
        ConnState.CONNECTED -> Ok
        ConnState.CONNECTING, ConnState.RECONNECTING -> Gold
        ConnState.ERROR -> Color(0xFFF87171)
        else -> Muted
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(vm.connDetail, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 6.dp))
        TextButton(onClick = onToggleTest) { Text(if (showTest) "Tutup tes" else "Tes", fontSize = 12.sp) }
        TextButton(onClick = onReset) { Text("Reset", fontSize = 12.sp) }
        TextButton(onClick = onExit) { Text("Keluar", fontSize = 12.sp) }
    }
}

@Composable
private fun BoardGrid(board: List<BoardSlot?>, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        board.forEachIndexed { i, slot ->
            BoardRow(index = i, slot = slot)
        }
    }
}

@Composable
private fun BoardRow(index: Int, slot: BoardSlot?) {
    val shape = RoundedCornerShape(10.dp)
    val filled = slot != null
    val bg = if (filled) Ok.copy(alpha = 0.15f) else SurfaceCol
    val borderCol = if (filled) Ok.copy(alpha = 0.6f) else Line

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(BorderStroke(1.dp, borderCol), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Nomor slot
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (filled) Ok else Line),
            contentAlignment = Alignment.Center,
        ) {
            Text("${index + 1}", color = if (filled) OkDark else Muted, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(12.dp))

        if (filled && slot != null) {
            Text(slot.text, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("${slot.points} poin", color = Ok, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                AvatarLabel(avatar = slot.foundByEmoji, text = slot.foundBy, color = Muted, fontSize = 11.sp)
            }
        } else {
            Text("???", color = Muted, fontSize = 16.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun F100Leaderboard(entries: List<LeaderboardEntry>) {
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
                    Text("${i + 1}", color = if (i == 0) Gold else Muted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.size(22.dp))
                    AvatarLabel(avatar = e.emoji, text = e.player, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${e.score}", color = Lilac, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun F100SummaryOverlay(summary: RoundSummary) {
    Box(Modifier.fillMaxSize().background(Color(0xE6120D22)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).clip(RoundedCornerShape(20.dp)).background(SurfaceCol)
                .border(BorderStroke(1.dp, Line), RoundedCornerShape(20.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Ronde selesai! 🎉", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            if (summary.top.isNotEmpty()) {
                Text("Penjawab terbaik", color = Muted, fontSize = 12.sp)
                val medals = listOf("🥇", "🥈", "🥉")
                summary.top.forEachIndexed { i, e ->
                    AvatarLabel(avatar = e.emoji, text = e.player, prefix = medals.getOrElse(i) { "" }, suffix = " • +${e.points}", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("Soal baru sebentar lagi...", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
