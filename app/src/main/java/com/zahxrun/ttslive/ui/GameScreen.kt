package com.zahxrun.ttslive.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zahxrun.ttslive.LiveViewModel
import com.zahxrun.ttslive.engine.HitungCepatState
import com.zahxrun.ttslive.engine.LeaderboardEntry
import com.zahxrun.ttslive.engine.QuestionView
import com.zahxrun.ttslive.engine.RoundSummary
import com.zahxrun.ttslive.tiktok.ConnState

@Composable
fun GameScreen(vm: LiveViewModel, onExit: () -> Unit) {
    val snap = vm.snapshot
    val state = snap.game as? HitungCepatState
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
                Header(snap.gameTitle, state)
                LinearProgressIndicator(
                    progress = {
                        if (state.questions.isEmpty()) 0f
                        else state.solvedCount.toFloat() / state.questions.size
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape),
                    color = Ok,
                    trackColor = Line,
                )
                StatusRow(vm, showTest, onToggleTest = { showTest = !showTest }, onReset = vm::resetGame, onExit = onExit)
                QuestionGrid(state.questions, Modifier.weight(1f))
            }
            Leaderboard(snap.leaderboard)
            ChatTicker(snap.chat.map { "${it.player}: ${it.text}" })
            if (showTest) TestPanel(onSend = vm::onComment)
        }

        snap.summary?.let { SummaryOverlay(it) }
    }
}

@Composable
private fun Header(title: String, state: HitungCepatState) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = TextMain, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)) { append(title) }
                withStyle(SpanStyle(color = Gold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)) {
                    append("  Ronde ${state.roundNumber}")
                }
            },
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Ok, fontWeight = FontWeight.Bold, fontSize = 18.sp)) { append("${state.solvedCount}") }
                withStyle(SpanStyle(color = Muted, fontWeight = FontWeight.Bold, fontSize = 14.sp)) {
                    append("/${state.questions.size} terjawab")
                }
            },
        )
    }
}

@Composable
private fun StatusRow(
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

@Composable
private fun QuestionGrid(questions: List<QuestionView>, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        questions.chunked(2).forEach { pair ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { q -> QuestionCard(q, Modifier.weight(1f).fillMaxSize()) }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuestionCard(q: QuestionView, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val bg = if (q.solved) Ok.copy(alpha = 0.12f) else SurfaceCol
    val borderColor = if (q.solved) Ok.copy(alpha = 0.55f) else Line

    BoxWithConstraints(
        modifier
            .clip(shape)
            .background(bg)
            .border(BorderStroke(1.dp, borderColor), shape),
    ) {
        val density = LocalDensity.current
        // Setara CSS: font-size = min(34cqh, 12cqw); lebih kecil kalau sudah terjawab.
        val hFactor = if (q.solved) 0.26f else 0.34f
        val wFactor = if (q.solved) 0.09f else 0.12f
        val fontSize = with(density) { minOf(maxHeight.toPx() * hFactor, maxWidth.toPx() * wFactor).toSp() }

        Box(
            Modifier
                .padding(7.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (q.solved) Ok else Line),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${q.index + 1}",
                color = if (q.solved) OkDark else Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = if (q.solved) Muted else Paper)) { append(q.expression) }
                    if (q.solved && q.answer != null) {
                        withStyle(SpanStyle(color = Ok, fontWeight = FontWeight.Black)) { append("  = ${q.answer}") }
                    } else {
                        withStyle(SpanStyle(color = Muted)) { append("  = ?") }
                    }
                },
                fontSize = fontSize,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
            )
            if (q.solved && q.winnerName != null) {
                AvatarLabel(
                    avatar = q.winnerEmoji,
                    text = q.winnerName ?: "",
                    color = OkDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(CircleShape)
                        .background(Ok)
                        .padding(horizontal = 10.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun Leaderboard(entries: List<LeaderboardEntry>) {
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
private fun ChatTicker(lines: List<String>) {
    if (lines.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        lines.takeLast(3).forEach {
            Text("💬 $it", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun TestPanel(onSend: (String, String) -> Unit) {
    var name by remember { mutableStateOf("Penonton") }
    var text by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            label = { Text("Nama") },
            modifier = Modifier.weight(0.38f),
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            label = { Text("Komentar / jawaban") },
            modifier = Modifier.weight(0.62f),
        )
        TextButton(onClick = {
            if (text.isNotBlank()) {
                onSend(name, text)
                text = ""
            }
        }) { Text("Kirim") }
    }
}

@Composable
private fun SummaryOverlay(summary: RoundSummary) {
    Box(
        Modifier.fillMaxSize().background(Color(0xE6120D22)),
        contentAlignment = Alignment.Center,
    ) {
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
            Text("Ronde selesai!", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            if (summary.top.isNotEmpty()) {
                Text("Juara ronde ini", color = Muted, fontSize = 12.sp)
                val medals = listOf("🥇", "🥈", "🥉")
                summary.top.forEachIndexed { i, e ->
                    AvatarLabel(
                        avatar = e.emoji,
                        text = e.player,
                        prefix = medals.getOrElse(i) { "" },
                        suffix = " • +${e.points}",
                        color = TextMain,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (summary.total.isNotEmpty()) {
                Text("Skor total", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                summary.total.forEach { e ->
                    AvatarLabel(avatar = e.emoji, text = e.player, suffix = "  ${e.score}", color = Lilac, fontSize = 14.sp)
                }
            }
            Text("Soal baru sebentar lagi...", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
