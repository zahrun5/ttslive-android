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
import com.zahxrun.ttslive.engine.SusunKataSlot
import com.zahxrun.ttslive.engine.SusunKataState
import com.zahxrun.ttslive.tiktok.ConnState

@Composable
fun SusunKataScreen(vm: LiveViewModel, onExit: () -> Unit) {
    val snap = vm.snapshot
    val state = snap.game as? SusunKataState
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
                SKHeader(state)
                SKStatusRow(vm, showTest, onToggleTest = { showTest = !showTest }, onReset = vm::resetGame, onExit = onExit)
                LinearProgressIndicator(
                    progress = { if (state.totalCount == 0) 0f else state.foundCount.toFloat() / state.totalCount },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = Ok,
                    trackColor = Line,
                )
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                ) {
                    state.slots.forEach { SKSlotCard(it) }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            SKLeaderboard(snap.leaderboard)

            if (snap.chat.isNotEmpty()) {
                snap.chat.takeLast(2).forEach {
                    Text("💬 ${it.player}: ${it.text}", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (showTest) TestPanel(onSend = vm::onComment)
        }

        snap.summary?.let { SKSummaryOverlay(it) }
    }
}

@Composable
private fun SKHeader(state: SusunKataState) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCol)
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Susun Kata Acak", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(
            "Susun huruf jadi kata, ketik jawabannya di komentar!",
            color = TextMain,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(2.dp))
        Text("${state.foundCount}/${state.totalCount} kata tersusun", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SKStatusRow(
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

/** Satu slot: huruf acak (ungu) sebelum terjawab, kata asli (hijau) + nama penjawab sesudahnya. */
@Composable
private fun SKSlotCard(slot: SusunKataSlot) {
    val shape = RoundedCornerShape(10.dp)
    val solved = slot.solved
    val text = if (solved) (slot.answer ?: slot.scrambled) else slot.scrambled

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (solved) Ok.copy(alpha = 0.12f) else SurfaceCol)
            .border(BorderStroke(1.dp, if (solved) Ok.copy(alpha = 0.6f) else Line), shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val n = text.length
            val gap = 4.dp
            val badge = 28.dp
            val tile = minOf(34.dp, (maxWidth - badge - gap * n) / n)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
                Box(
                    Modifier.size(badge).clip(CircleShape).background(if (solved) Ok else Line),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${slot.id}", color = if (solved) OkDark else Muted, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
                text.forEach { ch ->
                    Box(
                        Modifier.size(tile).clip(RoundedCornerShape(6.dp)).background(if (solved) Ok else Line),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            ch.toString(),
                            color = if (solved) OkDark else Lilac,
                            fontSize = (tile.value * 0.58f).sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
        if (solved) {
            AvatarLabel(
                avatar = slot.solvedByEmoji,
                text = slot.solvedBy,
                color = Muted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp, start = 32.dp),
            )
        }
    }
}

@Composable
private fun SKLeaderboard(entries: List<LeaderboardEntry>) {
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
private fun SKSummaryOverlay(summary: RoundSummary) {
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
            Text("Semua kata tersusun! 🎉", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            if (summary.top.isNotEmpty()) {
                Text("Penyusun terbaik", color = Muted, fontSize = 12.sp)
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
            Text("Kata baru sebentar lagi...", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
