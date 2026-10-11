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
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.zahxrun.ttslive.engine.CariKataState
import com.zahxrun.ttslive.engine.CariKataWord
import com.zahxrun.ttslive.engine.LeaderboardEntry
import com.zahxrun.ttslive.engine.RoundSummary
import com.zahxrun.ttslive.tiktok.ConnState

private val CellText = Color(0xFF0B0716)

@Composable
fun CariKataScreen(vm: LiveViewModel, onExit: () -> Unit) {
    val snap = vm.snapshot
    val state = snap.game as? CariKataState
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
                CKHeader(state)
                CKStatusRow(vm, showTest, onToggleTest = { showTest = !showTest }, onReset = vm::resetGame, onExit = onExit)
                LinearProgressIndicator(
                    progress = { if (state.totalCount == 0) 0f else state.foundCount.toFloat() / state.totalCount },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = Ok,
                    trackColor = Line,
                )
                CKGrid(state, Modifier.weight(1f).fillMaxWidth())
                CKWordList(state.words)
            }

            CKLeaderboard(snap.leaderboard)

            if (snap.chat.isNotEmpty()) {
                snap.chat.takeLast(2).forEach {
                    Text("💬 ${it.player}: ${it.text}", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (showTest) TestPanel(onSend = vm::onComment)
        }

        snap.summary?.let { CKSummaryOverlay(it) }
    }
}

@Composable
private fun CKHeader(state: CariKataState) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCol)
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Cari Kata", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(
            state.theme,
            color = TextMain,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(2.dp))
        Text("${state.foundCount}/${state.totalCount} kata ditemukan", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun CKStatusRow(
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

/** Grid huruf persegi; sel kata yang sudah ketemu diwarnai sesuai warna kata. */
@Composable
private fun CKGrid(state: CariKataState, modifier: Modifier) {
    val size = state.size
    val owner = HashMap<Int, Color>()
    state.words.forEach { w ->
        if (w.found) w.cells.forEach { owner[it.row * size + it.col] = Color(w.color) }
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        val letterSize = (side.value / size * 0.52f).sp
        Column(
            Modifier
                .size(side)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceCol)
                .border(BorderStroke(1.dp, Line), RoundedCornerShape(12.dp))
                .padding(4.dp),
        ) {
            for (r in 0 until size) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (c in 0 until size) {
                        val tint = owner[r * size + c]
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(1.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(tint ?: Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                state.grid[r][c].toString(),
                                color = if (tint != null) CellText else TextMain,
                                fontSize = letterSize,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Daftar kata: sudah ketemu tampil katanya + penemu, belum ketemu tampil titik sepanjang kata. */
@Composable
private fun CKWordList(words: List<CariKataWord>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        words.chunked(3).forEach { rowWords ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                rowWords.forEach { w -> CKWordChip(w, Modifier.weight(1f)) }
                repeat(3 - rowWords.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CKWordChip(w: CariKataWord, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    val bg = if (w.found) Color(w.color) else SurfaceCol
    Column(
        modifier
            .clip(shape)
            .background(bg)
            .border(BorderStroke(1.dp, if (w.found) Color(w.color) else Line), shape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (w.found) {
            Text(w.word ?: "", color = CellText, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(
                "${w.foundByEmoji} ${w.foundBy}".trim(),
                color = CellText,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text("• ".repeat(w.length).trim(), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("${w.length} huruf", color = Muted, fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
private fun CKLeaderboard(entries: List<LeaderboardEntry>) {
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
                    Text(
                        "${e.emoji} ${e.player}".trim(),
                        color = TextMain,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${e.score}", color = Lilac, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CKSummaryOverlay(summary: RoundSummary) {
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
            Text("Semua kata ketemu! 🎉", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            if (summary.top.isNotEmpty()) {
                Text("Pencari terbaik", color = Muted, fontSize = 12.sp)
                val medals = listOf("🥇", "🥈", "🥉")
                summary.top.forEachIndexed { i, e ->
                    Text(
                        "${medals.getOrElse(i) { "" }} ${e.emoji} ${e.player} • +${e.points}",
                        color = TextMain,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text("Tema baru sebentar lagi...", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
